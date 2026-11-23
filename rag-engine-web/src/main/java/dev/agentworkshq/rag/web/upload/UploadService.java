package dev.agentworkshq.rag.web.upload;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.ingest.DefaultDocumentLoader;
import dev.agentworkshq.rag.core.ingest.DocumentIngestResult;
import dev.agentworkshq.rag.core.ingest.IngestionLimits;
import dev.agentworkshq.rag.core.ingest.IngestionService;
import dev.agentworkshq.rag.core.ingest.SourceItem;
import dev.agentworkshq.rag.core.ingest.SourceRegistry;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import dev.agentworkshq.rag.web.RagWebProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public class UploadService {

    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final IngestionService ingestionService;
    private final IngestionLimits limits;
    private final RagWebProperties.Upload properties;

    public UploadService(IngestionService ingestionService, IngestionLimits limits,
                         RagWebProperties.Upload properties) {
        this.ingestionService = ingestionService;
        this.limits = limits;
        this.properties = properties;
    }

    public DocumentIngestResult upload(MultipartFile file, String tenantId) {
        String name = FileNames.sanitize(file.getOriginalFilename());
        if (!DefaultDocumentLoader.supports(name)) {
            throw new ServiceException(ErrorMessages.RAG_E012, name);
        }
        requireQuota(name, tenantId);
        if (file.getSize() > limits.maxFileSize()) {
            throw new ServiceException(ErrorMessages.RAG_E030, limits.maxFileSize());
        }

        Path temp = null;
        try {
            temp = Files.createTempFile("rag-upload-", ".tmp");
            long size = copyWithCap(file, temp);
            if (name.toLowerCase().endsWith(".pdf")) {
                requirePdfMagic(temp);
            }
            Path source = temp;
            SourceItem item = new SourceItem(name, name, contentType(file), Instant.now(), size,
                    () -> Files.newInputStream(source));
            return ingestionService.ingestDocument(SourceRegistry.UPLOADS, tenantId, item);
        } catch (IOException e) {
            throw new ServiceException(e, ErrorMessages.RAG_E015, "upload");
        } finally {
            deleteQuietly(temp);
        }
    }

    public List<SourceDocumentRecord> list(String tenantId) {
        return ingestionService.listDocuments(SourceRegistry.UPLOADS, tenantId);
    }

    public SourceDocumentRecord get(String externalId, String tenantId) {
        String name = FileNames.requireValid(externalId);
        return list(tenantId).stream().filter(d -> d.externalId().equals(name)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.RAG_E002, name));
    }

    public void delete(String externalId, String tenantId) {
        ingestionService.deleteDocument(SourceRegistry.UPLOADS, tenantId, FileNames.requireValid(externalId));
    }

    private void requireQuota(String name, String tenantId) {
        List<SourceDocumentRecord> existing = list(tenantId);
        boolean replacing = existing.stream().anyMatch(d -> d.externalId().equals(name));
        if (!replacing && existing.size() >= properties.maxDocumentsPerTenant()) {
            throw new ServiceException(ErrorMessages.RAG_E029, properties.maxDocumentsPerTenant());
        }
    }

    private long copyWithCap(MultipartFile file, Path target) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limits.maxFileSize()) {
                    throw new ServiceException(ErrorMessages.RAG_E030, limits.maxFileSize());
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static void requirePdfMagic(Path file) throws IOException {
        byte[] head = new byte[PDF_MAGIC.length];
        try (InputStream in = Files.newInputStream(file)) {
            if (in.readNBytes(head, 0, head.length) != head.length || !Arrays.equals(head, PDF_MAGIC)) {
                throw new ServiceException(ErrorMessages.RAG_E028);
            }
        }
    }

    private static String contentType(MultipartFile file) {
        return file.getContentType() != null ? file.getContentType() : "application/octet-stream";
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // nothing more can be done for a temp file that cannot be removed
        }
    }
}
