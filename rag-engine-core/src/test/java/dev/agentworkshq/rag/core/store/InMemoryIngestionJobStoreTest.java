package dev.agentworkshq.rag.core.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryIngestionJobStoreTest {

    private final InMemoryIngestionJobStore store = new InMemoryIngestionJobStore();

    @Test
    void startCreatesRunningJob() {
        IngestionJobRecord job = store.start("docs");

        assertThat(job.id()).isNotNull();
        assertThat(job.status()).isEqualTo(IngestionStatus.RUNNING);
        assertThat(job.startedAt()).isNotNull();
        assertThat(job.finishedAt()).isNull();
    }

    @Test
    void finishStoresCounts() {
        IngestionJobRecord job = store.start("docs");

        IngestionJobRecord done = store.finish(job.id(), 1, 2, 3, 4);

        assertThat(done.status()).isEqualTo(IngestionStatus.DONE);
        assertThat(done.added()).isEqualTo(1);
        assertThat(done.updated()).isEqualTo(2);
        assertThat(done.deleted()).isEqualTo(3);
        assertThat(done.skipped()).isEqualTo(4);
        assertThat(done.finishedAt()).isNotNull();
        assertThat(store.findById(job.id())).contains(done);
    }

    @Test
    void failStoresMessage() {
        IngestionJobRecord job = store.start("docs");

        IngestionJobRecord failed = store.fail(job.id(), "boom");

        assertThat(failed.status()).isEqualTo(IngestionStatus.FAILED);
        assertThat(failed.errorMessage()).isEqualTo("boom");
    }

    @Test
    void unknownJobThrowsNotFound() {
        assertThatThrownBy(() -> store.finish(99L, 0, 0, 0, 0)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findRecentIsNewestFirstAndLimited() {
        IngestionJobRecord first = store.start("docs");
        IngestionJobRecord second = store.start("docs");
        store.start("wiki");

        assertThat(store.findRecent("docs", 10)).extracting(IngestionJobRecord::id)
                .containsExactly(second.id(), first.id());
        assertThat(store.findRecent("docs", 1)).hasSize(1);
    }

    @Test
    void concurrentStartsGetUniqueIds() {
        var futures = IntStream.range(0, 100)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> store.start("docs").id()))
                .toList();

        assertThat(futures.stream().map(CompletableFuture::join).distinct()).hasSize(100);
    }
}
