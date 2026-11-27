package dev.agentworkshq.rag.core.answer;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.ingest.ChunkMetadata;
import dev.agentworkshq.rag.core.text.Texts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final Pattern REFERENCE = Pattern.compile("\\[(\\d+(?:\\s*,\\s*\\d+)*)]");
    private static final Pattern CONTEXT_TAG = Pattern.compile("<\\s*(/?)\\s*context\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");
    private static final int SNIPPET_LENGTH = 200;
    private static final int HEADER_NAME_LENGTH = 100;

    private final Supplier<VectorStore> vectorStores;
    private final Supplier<ChatClient.Builder> chatClients;
    private final Reranker reranker;
    private final RagSettings settings;

    public RagService(Supplier<VectorStore> vectorStores, Supplier<ChatClient.Builder> chatClients,
                      Reranker reranker, RagSettings settings) {
        this.vectorStores = vectorStores;
        this.chatClients = chatClients;
        this.reranker = reranker;
        this.settings = settings;
    }

    public RagAnswer ask(String question, RagContext context) {
        long start = System.nanoTime();
        validate(question);
        VectorStore store = vectorStores.get();
        if (store == null) {
            throw new ServiceException(ErrorMessages.RAG_E019);
        }
        ChatClient.Builder builder = chatClients.get();
        if (builder == null) {
            throw new ServiceException(ErrorMessages.RAG_E020);
        }
        ChatClient chat = builder.build();

        String query = settings.queryRewrite() ? rewrite(chat, question) : question;
        int topK = Math.min(context.topK() != null ? context.topK() : settings.topK(), settings.maxTopK());
        List<Document> retrieved = store.similaritySearch(searchRequest(query, topK, context));
        List<Document> ranked = rank(question, retrieved, topK);
        if (ranked.isEmpty()) {
            log.info("Answered with no context: retrieved={} ms={}", retrieved.size(), millisSince(start));
            return noAnswer();
        }

        List<Document> included = new ArrayList<>();
        String contextBlock = contextBlock(ranked, included);
        String text = chat.prompt(new Prompt(List.of(
                new SystemMessage(RagPrompts.system(settings.noAnswerMessage())),
                new UserMessage(RagPrompts.user(contextBlock, question))))).call().content();
        if (text == null || text.isBlank()) {
            return noAnswer();
        }

        List<Citation> citations = settings.citeSources() ? citations(included, usedNumbers(text, included.size()))
                : List.of();
        boolean answered = !text.strip().equals(settings.noAnswerMessage().strip());
        log.info("Answered: retrieved={} included={} answered={} ms={}", retrieved.size(), included.size(), answered,
                millisSince(start));
        return new RagAnswer(text, citations, answered);
    }

    private void validate(String question) {
        if (question == null || question.isBlank()) {
            throw new ServiceException(ErrorMessages.RAG_E022);
        }
        if (question.length() > settings.maxQuestionLength()) {
            throw new ServiceException(ErrorMessages.RAG_E023, settings.maxQuestionLength());
        }
    }

    private String rewrite(ChatClient chat, String question) {
        String rewritten = chat.prompt(new Prompt(List.of(
                new SystemMessage(RagPrompts.REWRITE_SYSTEM), new UserMessage(question)))).call().content();
        return rewritten == null || rewritten.isBlank() ? question : rewritten.strip();
    }

    private SearchRequest searchRequest(String query, int topK, RagContext context) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = b.eq(ChunkMetadata.TENANT_ID, context.tenantId());
        if (!context.sourceNames().isEmpty()) {
            filter = b.and(filter, b.in(ChunkMetadata.SOURCE_NAME, context.sourceNames().toArray()));
        }
        return SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(settings.similarityThreshold())
                .filterExpression(filter.build())
                .build();
    }

    private List<Document> rank(String question, List<Document> retrieved, int topK) {
        Map<String, Document> unique = new LinkedHashMap<>();
        for (Document doc : retrieved) {
            unique.putIfAbsent(doc.getId(), doc);
        }
        List<Document> ranked = reranker.rerank(question, new ArrayList<>(unique.values()));
        return ranked.size() > topK ? ranked.subList(0, topK) : ranked;
    }

    private String contextBlock(List<Document> ranked, List<Document> included) {
        StringBuilder block = new StringBuilder();
        for (Document doc : ranked) {
            String entry = "[" + (included.size() + 1) + "] " + header(doc) + "\n" + neutralize(doc.getText()) + "\n\n";
            if (!included.isEmpty() && block.length() + entry.length() > settings.maxContextChars()) {
                break;
            }
            if (included.isEmpty() && entry.length() > settings.maxContextChars()) {
                entry = entry.substring(0, settings.maxContextChars());
            }
            block.append(entry);
            included.add(doc);
        }
        return block.toString().strip();
    }

    private static String header(Document doc) {
        Object filename = doc.getMetadata().get(ChunkMetadata.FILENAME);
        Object page = doc.getMetadata().get(ChunkMetadata.PAGE);
        String header = filename != null ? headerName(filename.toString()) : "unknown";
        return page != null ? header + " (page " + page + ")" : header;
    }

    // a file name must not be able to start a line of its own or look like a citation or a tag
    private static String headerName(String filename) {
        String flat = WHITESPACE.matcher(filename).replaceAll(" ")
                .replace('<', '(').replace('[', '(').replace('>', ')').replace(']', ')');
        return Texts.clean(flat, HEADER_NAME_LENGTH);
    }

    // chunk text must not be able to close the context block early
    private static String neutralize(String text) {
        return CONTEXT_TAG.matcher(text).replaceAll("&lt;$1context&gt;");
    }

    private static Set<Integer> usedNumbers(String answer, int max) {
        Set<Integer> used = new TreeSet<>();
        Matcher matcher = REFERENCE.matcher(answer);
        while (matcher.find()) {
            for (String number : matcher.group(1).split("\\s*,\\s*")) {
                try {
                    int n = Integer.parseInt(number);
                    if (n >= 1 && n <= max) {
                        used.add(n);
                    }
                } catch (NumberFormatException ignored) {
                    // a number too large to parse cannot refer to a chunk
                }
            }
        }
        return used;
    }

    private static List<Citation> citations(List<Document> included, Set<Integer> used) {
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < included.size(); i++) {
            Document doc = included.get(i);
            Map<String, Object> metadata = doc.getMetadata();
            citations.add(new Citation(i + 1, asString(metadata.get(ChunkMetadata.DOCUMENT_ID)),
                    asString(metadata.get(ChunkMetadata.FILENAME)), asPage(metadata.get(ChunkMetadata.PAGE)),
                    snippet(doc.getText()), used.contains(i + 1)));
        }
        return citations;
    }

    private static String snippet(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= SNIPPET_LENGTH ? flat : flat.substring(0, SNIPPET_LENGTH);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asPage(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private RagAnswer noAnswer() {
        return new RagAnswer(settings.noAnswerMessage(), List.of(), false);
    }

    private static long millisSince(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
