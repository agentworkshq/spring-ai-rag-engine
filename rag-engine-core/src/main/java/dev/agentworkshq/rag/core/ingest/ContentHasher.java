package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ContentHasher {

    private ContentHasher() {
    }

    public static byte[] read(SourceItem item, long maxFileSize) {
        requireWithinLimit(item, item.size(), maxFileSize);
        try (InputStream in = item.open()) {
            // the declared size can be stale, so the read itself is bounded too
            byte[] bytes = in.readNBytes((int) Math.min(maxFileSize + 1, Integer.MAX_VALUE - 8));
            requireWithinLimit(item, bytes.length, maxFileSize);
            return bytes;
        } catch (IOException e) {
            throw new ServiceException(e, ErrorMessages.RAG_E015, item.externalId());
        }
    }

    public static String fileHash(byte[] bytes) {
        return HexFormat.of().formatHex(sha256().digest(bytes));
    }

    public static String effectiveHash(String fileHash, String chunkingSignature) {
        MessageDigest digest = sha256();
        digest.update((fileHash + "|" + chunkingSignature).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void requireWithinLimit(SourceItem item, long size, long maxFileSize) {
        if (size > maxFileSize) {
            throw new ServiceException(ErrorMessages.RAG_E009, item.externalId(), size, maxFileSize);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
