package dev.agentworkshq.rag.core.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.ai.document.Document;

public class RecursiveChunker implements ChunkingStrategy {

    private static final List<Pattern> SEPARATORS = List.of(
            Pattern.compile("\\n\\s*\\n"),
            Pattern.compile("\\n"),
            Pattern.compile("(?<=[.!?])\\s+"),
            Pattern.compile("\\s+"));
    private static final List<String> JOINERS = List.of("\n\n", "\n", " ", " ");

    private final int chunkSize;
    private final int overlap;
    private final int baseSize;

    public RecursiveChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("chunkSize must be positive and overlap must be in [0, chunkSize)");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
        // room for the overlap prefix and the space that joins it to the segment
        this.baseSize = overlap == 0 ? chunkSize : Math.max(1, chunkSize - overlap - 1);
    }

    @Override
    public List<Document> split(List<Document> documents) {
        List<Document> result = new ArrayList<>();
        for (Document parent : documents) {
            String text = parent.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            List<String> segments = segments(text.strip(), 0);
            for (int i = 0; i < segments.size(); i++) {
                String chunk = withOverlap(i == 0 ? null : segments.get(i - 1), segments.get(i));
                result.add(Chunks.of(parent, i, chunk));
            }
        }
        return result;
    }

    private String withOverlap(String previous, String segment) {
        if (previous == null || overlap == 0) {
            return segment;
        }
        int room = Math.min(overlap, chunkSize - segment.length() - 1);
        if (room <= 0) {
            return segment;
        }
        String tail = previous.length() <= room ? previous : previous.substring(previous.length() - room);
        if (tail.length() < previous.length()) {
            // avoid starting mid-word when a boundary exists
            int space = tail.indexOf(' ');
            if (space >= 0) {
                tail = tail.substring(space + 1);
            }
        }
        tail = tail.strip();
        return tail.isEmpty() ? segment : tail + " " + segment;
    }

    private List<String> segments(String text, int level) {
        List<String> result = new ArrayList<>();
        if (text.length() <= baseSize) {
            if (!text.isBlank()) {
                result.add(text);
            }
            return result;
        }
        if (level >= SEPARATORS.size()) {
            for (int i = 0; i < text.length(); i += baseSize) {
                String piece = text.substring(i, Math.min(text.length(), i + baseSize)).strip();
                if (!piece.isEmpty()) {
                    result.add(piece);
                }
            }
            return result;
        }
        String joiner = JOINERS.get(level);
        StringBuilder current = new StringBuilder();
        for (String part : SEPARATORS.get(level).split(text)) {
            part = part.strip();
            if (part.isEmpty()) {
                continue;
            }
            if (part.length() > baseSize) {
                flush(current, result);
                result.addAll(segments(part, level + 1));
            } else if (current.isEmpty()) {
                current.append(part);
            } else if (current.length() + joiner.length() + part.length() <= baseSize) {
                current.append(joiner).append(part);
            } else {
                flush(current, result);
                current.append(part);
            }
        }
        flush(current, result);
        return result;
    }

    private static void flush(StringBuilder current, List<String> result) {
        if (!current.isEmpty()) {
            result.add(current.toString());
            current.setLength(0);
        }
    }
}
