package dev.agentworkshq.rag.core.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

public record SourceItem(
        String externalId,
        String filename,
        String contentType,
        Instant lastModified,
        long size,
        Opener opener) {

    @FunctionalInterface
    public interface Opener {
        InputStream open() throws IOException;
    }

    public InputStream open() throws IOException {
        return opener.open();
    }

    @Override
    public String toString() {
        return "SourceItem[size=" + size + "]";
    }
}
