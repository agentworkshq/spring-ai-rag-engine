package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.util.ClassUtils;

public class DefaultDocumentLoader implements DocumentLoader {

    private static final Set<String> TEXT = Set.of("txt", "text", "md", "markdown");
    private static final Set<String> TIKA = Set.of("docx", "doc", "pptx", "ppt", "xlsx", "xls", "odt", "rtf", "html", "htm");

    private static final String PDF_DEPENDENCY = "org.springframework.ai:spring-ai-pdf-document-reader";
    private static final String TIKA_DEPENDENCY = "org.springframework.ai:spring-ai-tika-document-reader";

    private final long maxFileSize;
    private final int maxPages;
    private final boolean pdfAvailable;
    private final boolean tikaAvailable;

    public DefaultDocumentLoader(long maxFileSize, int maxPages) {
        this(maxFileSize, maxPages,
                ClassUtils.isPresent("org.springframework.ai.reader.pdf.PagePdfDocumentReader", null)
                        && ClassUtils.isPresent("org.apache.pdfbox.Loader", null),
                ClassUtils.isPresent("org.springframework.ai.reader.tika.TikaDocumentReader", null));
    }

    DefaultDocumentLoader(long maxFileSize, int maxPages, boolean pdfAvailable, boolean tikaAvailable) {
        this.maxFileSize = maxFileSize;
        this.maxPages = maxPages;
        this.pdfAvailable = pdfAvailable;
        this.tikaAvailable = tikaAvailable;
    }

    @Override
    public List<Document> load(SourceItem item) {
        String extension = extension(item.filename());
        if (TEXT.contains(extension)) {
            return loadText(item);
        }
        if (extension.equals("pdf")) {
            if (!pdfAvailable) {
                throw new ServiceException(ErrorMessages.RAG_E011, "PDF", PDF_DEPENDENCY);
            }
            return loadPdf(item);
        }
        if (TIKA.contains(extension)) {
            if (!tikaAvailable) {
                throw new ServiceException(ErrorMessages.RAG_E011, extension, TIKA_DEPENDENCY);
            }
            return loadTika(item);
        }
        throw new ServiceException(ErrorMessages.RAG_E012, item.filename());
    }

    private List<Document> loadText(SourceItem item) {
        String text = new String(readBytes(item), StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return List.of();
        }
        return List.of(document(item, 0, text, Map.of(ChunkMetadata.FILENAME, item.filename())));
    }

    private List<Document> loadPdf(SourceItem item) {
        byte[] bytes = readBytes(item);
        try {
            int pages = PdfReaders.pageCount(bytes);
            if (pages > maxPages) {
                throw new ServiceException(ErrorMessages.RAG_E010, item.externalId(), pages, maxPages);
            }
        } catch (IOException e) {
            throw readFailure(item, e);
        }
        return indexed(item, PdfReaders.read(bytes), true);
    }

    private List<Document> loadTika(SourceItem item) {
        return indexed(item, TikaReaders.read(readBytes(item)), false);
    }

    private List<Document> indexed(SourceItem item, List<Document> parsed, boolean paged) {
        List<Document> result = new ArrayList<>();
        for (Document doc : parsed) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(ChunkMetadata.FILENAME, item.filename());
            if (paged && doc.getMetadata().get("page_number") != null) {
                metadata.put(ChunkMetadata.PAGE, doc.getMetadata().get("page_number"));
            }
            result.add(document(item, result.size(), text, metadata));
        }
        return result;
    }

    private Document document(SourceItem item, int index, String text, Map<String, Object> metadata) {
        String id = UUID.nameUUIDFromBytes((item.externalId() + ":" + index).getBytes(StandardCharsets.UTF_8)).toString();
        return Document.builder().id(id).text(text).metadata(metadata).build();
    }

    private byte[] readBytes(SourceItem item) {
        if (item.size() > maxFileSize) {
            throw new ServiceException(ErrorMessages.RAG_E009, item.externalId(), item.size(), maxFileSize);
        }
        try (InputStream in = item.open()) {
            // the declared size can be stale, so the read itself is bounded too
            byte[] bytes = in.readNBytes((int) Math.min(maxFileSize + 1, Integer.MAX_VALUE - 8));
            if (bytes.length > maxFileSize) {
                throw new ServiceException(ErrorMessages.RAG_E009, item.externalId(), (long) bytes.length, maxFileSize);
            }
            return bytes;
        } catch (IOException e) {
            throw readFailure(item, e);
        }
    }

    private static ServiceException readFailure(SourceItem item, IOException cause) {
        return new ServiceException(cause, ErrorMessages.RAG_E015, item.externalId());
    }

    private static String extension(String filename) {
        String name = Path.of(filename).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
