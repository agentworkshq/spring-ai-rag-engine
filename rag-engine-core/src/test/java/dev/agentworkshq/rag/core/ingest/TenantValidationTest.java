package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class TenantValidationTest {

    private static final String[] INVALID = {null, "", "   ", "a\nb", "a\u0000b", "a\u007Fb", "x".repeat(101)};

    private final InMemoryDocumentRegistry registry = new InMemoryDocumentRegistry();
    private final InMemoryIngestionJobStore jobs = new InMemoryIngestionJobStore();
    private int storeRequests;
    private int scans;

    // asking for the store counts as a touch, so a rejected call must not even get that far
    private final IngestionPipeline pipeline = new IngestionPipeline(() -> {
        storeRequests++;
        return null;
    }, new DefaultDocumentLoader(1024, 10), new RecursiveChunker(100, 10), registry, jobs, "RECURSIVE:100:10", 50,
            false, 0.5, 1024);

    private final RagSource source = new RagSource() {
        @Override
        public String name() {
            return "docs";
        }

        @Override
        public Stream<SourceItem> scan() {
            scans++;
            return Stream.empty();
        }
    };

    private final IngestionService service = new IngestionService(new SourceRegistry(List.of(source)), pipeline);

    private static SourceItem item() {
        return new SourceItem("a.txt", "a.txt", "text/plain", Instant.now(), 1,
                () -> new ByteArrayInputStream(new byte[] {'a'}));
    }

    private static void assertRejected(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ServiceException.class).satisfies(e -> {
            ErrorMessages code = ((ServiceException) e).getErrorMessage();
            assertThat(code).isEqualTo(ErrorMessages.RAG_E031);
            assertThat(code.getHttpStatus()).isEqualTo(400);
        });
    }

    @Test
    void everyEntryPointRejectsAnInvalidTenantAndTouchesNothing() {
        for (String tenant : INVALID) {
            assertRejected(() -> new IngestionRequest("docs", tenant));
            assertRejected(() -> service.ingest("docs", tenant));
            assertRejected(() -> service.ingestDocument("uploads", tenant, item()));
            assertRejected(() -> service.deleteDocument("uploads", tenant, "a.txt"));
            assertRejected(() -> service.listDocuments("uploads", tenant));
            assertRejected(() -> pipeline.ingest(source, tenant));
            assertRejected(() -> pipeline.ingestDocument("uploads", tenant, item()));
            assertRejected(() -> pipeline.deleteDocument("uploads", tenant, "a.txt"));
            assertRejected(() -> pipeline.listDocuments("uploads", tenant));
        }

        assertThat(storeRequests).isZero();
        assertThat(scans).isZero();
        assertThat(jobs.findRecent("docs", 10)).isEmpty();
    }

    @Test
    void aTenantOfTheMaximumLengthIsAccepted() {
        String tenant = "x".repeat(100);

        assertThat(new IngestionRequest("docs", tenant).tenantId()).isEqualTo(tenant);
        assertThat(service.listDocuments("uploads", tenant)).isEmpty();
    }
}
