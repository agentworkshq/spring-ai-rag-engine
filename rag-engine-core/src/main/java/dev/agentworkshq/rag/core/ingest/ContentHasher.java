package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ContentHasher {

    private ContentHasher() {
    }

    public static String fileHash(SourceItem item) {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(item.open(), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new ServiceException(e, ErrorMessages.RAG_E015, item.externalId());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String effectiveHash(String fileHash, String chunkingSignature) {
        MessageDigest digest = sha256();
        digest.update((fileHash + "|" + chunkingSignature).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
