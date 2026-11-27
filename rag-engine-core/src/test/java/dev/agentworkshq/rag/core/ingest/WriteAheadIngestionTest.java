package dev.agentworkshq.rag.core.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.agentworkshq.rag.core.ingest.DocumentIngestResult.Outcome;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class WriteAheadIngestionTest {

    private static final String ALPHA = "Alpha content. ".repeat(20);
    private static final String OMEGA = "Omega content. ".repeat(40);
    private static final String SHORT = "Short content.";

    private final FailingStore store = new FailingStore();
    private final FailingRegistry registry = new FailingRegistry();
    private final MapSource source = new MapSource();
    private final IngestionPipeline pipeline = new IngestionPipeline(() -> store,
            new DefaultDocumentLoader(1024 * 1024, 10), new RecursiveChunker(100, 10), registry,
            new InMemoryIngestionJobStore(), "RECURSIVE:100:10", 2, false, 0.5, 1024 * 1024);

    private List<String> otherChunks;
    private List<SourceDocumentRecord> otherRecords;

    // tenant B holds the same documents and must come out of every test unchanged
    @BeforeEach
    void ingestOtherTenant() {
        source.files.put("a.txt", ALPHA);
        source.files.put("keep.txt", SHORT);
        pipeline.ingest(source, "B");
        otherChunks = ids("B", "a.txt");
        otherRecords = registry.findBySource("docs", "B");
        assertThat(otherChunks).isNotEmpty();
    }

    @AfterEach
    void otherTenantIsUntouched() {
        assertThat(ids("B", "a.txt")).isEqualTo(otherChunks);
        assertThat(store.chunks("B", "a.txt")).allSatisfy(c -> assertThat(c.getText()).contains("Alpha"));
        assertThat(ids("B", "keep.txt")).hasSize(1);
        assertThat(registry.findBySource("docs", "B")).isEqualTo(otherRecords);
    }

    private List<String> ids(String tenant, String filename) {
        return store.chunks(tenant, filename).stream().map(Document::getId).sorted().toList();
    }

    private List<String> texts(String filename) {
        return store.chunks("A", filename).stream().map(Document::getText).toList();
    }

    private SourceDocumentRecord record(String name) {
        return registry.find("docs", name, "A").orElseThrow();
    }

    private void assertDirty(String name, int chunkCount) {
        assertThat(record(name).contentHash()).isEqualTo(SourceDocumentRecord.DIRTY_HASH);
        assertThat(record(name).chunkCount()).isEqualTo(chunkCount);
    }

    private void assertClean(String name, String word) {
        assertThat(record(name).contentHash()).matches("[0-9a-f]{64}");
        assertThat(texts(name)).hasSize(record(name).chunkCount())
                .allSatisfy(t -> assertThat(t).contains(word));
    }

    private static int chunkCountOf(String content) {
        return new RecursiveChunker(100, 10).split(List.of(new Document(content))).size();
    }

    @Test
    void failedAddDuringAnUpdateKeepsTheOldChunksAndTheNextRunRepairsIt() {
        pipeline.ingest(source, "A");
        List<String> before = ids("A", "a.txt");
        source.files.put("a.txt", OMEGA);
        store.addsBeforeFailure = 0;

        IngestionResult failed = pipeline.ingest(source, "A");

        assertThat(failed.failed()).isEqualTo(1);
        assertThat(ids("A", "a.txt")).isEqualTo(before);
        assertThat(texts("a.txt")).allSatisfy(t -> assertThat(t).contains("Alpha"));
        assertDirty("a.txt", chunkCountOf(OMEGA));

        store.recover();
        IngestionResult retry = pipeline.ingest(source, "A");

        assertThat(retry.updated()).isEqualTo(1);
        assertClean("a.txt", "Omega");
    }

    @Test
    void failedAddOfANewDocumentLeavesADirtyEntryThatTheSweepRemoves() {
        pipeline.ingest(source, "A");
        source.files.put("new.txt", OMEGA);
        store.addsBeforeFailure = 2;

        IngestionResult failed = pipeline.ingest(source, "A");

        assertThat(failed.failed()).isEqualTo(1);
        assertThat(texts("new.txt")).isNotEmpty();
        assertDirty("new.txt", chunkCountOf(OMEGA));

        store.recover();
        source.files.remove("new.txt");
        IngestionResult sweep = pipeline.ingest(source, "A");

        assertThat(sweep.deleted()).isEqualTo(1);
        assertThat(texts("new.txt")).isEmpty();
        assertThat(registry.find("docs", "new.txt", "A")).isEmpty();
    }

    @Test
    void failedChunkDeleteInTheSweepKeepsTheEntry() {
        pipeline.ingest(source, "A");
        int count = record("a.txt").chunkCount();
        source.files.remove("a.txt");
        store.failFilterDelete = true;

        IngestionResult failed = pipeline.ingest(source, "A");

        assertThat(failed.failed()).isEqualTo(1);
        assertThat(failed.deleted()).isZero();
        assertThat(texts("a.txt")).hasSize(count);
        assertDirty("a.txt", count);

        store.recover();
        assertThat(pipeline.ingest(source, "A").deleted()).isEqualTo(1);
        assertThat(texts("a.txt")).isEmpty();
        assertThat(registry.find("docs", "a.txt", "A")).isEmpty();
    }

    @Test
    void failedRegistryDeleteLeavesADirtyEntrySoReturningContentIsIngestedAgain() {
        pipeline.ingest(source, "A");
        int count = record("a.txt").chunkCount();
        source.files.remove("a.txt");
        registry.failDelete = true;

        IngestionResult failed = pipeline.ingest(source, "A");

        assertThat(failed.failed()).isEqualTo(1);
        assertThat(texts("a.txt")).isEmpty();
        assertDirty("a.txt", count);

        registry.recover();
        source.files.put("a.txt", ALPHA);
        IngestionResult back = pipeline.ingest(source, "A");

        assertThat(back.updated()).isEqualTo(1);
        assertThat(back.skipped()).isEqualTo(1);
        assertClean("a.txt", "Alpha");
    }

    @Test
    void failureToMarkTheEntryChangesNothingInTheStore() {
        pipeline.ingest(source, "A");
        SourceDocumentRecord before = record("a.txt");
        registry.failSave = r -> r.contentHash().equals(SourceDocumentRecord.DIRTY_HASH);
        int writes = store.writes;

        source.files.put("a.txt", OMEGA);
        assertThat(pipeline.ingest(source, "A").failed()).isEqualTo(1);
        source.files.remove("a.txt");
        assertThat(pipeline.ingest(source, "A").failed()).isEqualTo(1);

        assertThat(store.writes).isEqualTo(writes);
        assertThat(record("a.txt")).isEqualTo(before);
    }

    @Test
    void failureToFinalizeTheEntryLeavesItDirty() {
        registry.failSave = r -> !r.contentHash().equals(SourceDocumentRecord.DIRTY_HASH);

        assertThat(pipeline.ingest(source, "A").failed()).isEqualTo(2);
        assertDirty("keep.txt", 1);

        registry.recover();
        assertThat(pipeline.ingest(source, "A").updated()).isEqualTo(2);
        assertClean("keep.txt", "Short");
    }

    @Test
    void updateToFewerChunksRemovesExactlyTheStaleIds() {
        source.files.put("a.txt", OMEGA);
        pipeline.ingest(source, "A");
        List<String> before = ids("A", "a.txt");
        source.files.put("a.txt", SHORT);

        pipeline.ingest(source, "A");

        assertClean("a.txt", "Short");
        List<String> after = ids("A", "a.txt");
        assertThat(after).hasSize(1).isSubsetOf(before);
        assertThat(store.deletedIds).containsExactlyInAnyOrderElementsOf(
                before.stream().filter(id -> !after.contains(id)).toList());
    }

    @Test
    void failedDeleteOfStaleIdsLeavesADirtyEntryWithTheUpperBound() {
        source.files.put("a.txt", OMEGA);
        pipeline.ingest(source, "A");
        int large = record("a.txt").chunkCount();
        source.files.put("a.txt", SHORT);
        store.failIdDelete = true;

        assertThat(pipeline.ingest(source, "A").failed()).isEqualTo(1);
        assertDirty("a.txt", large);

        store.recover();
        pipeline.ingest(source, "A");
        assertClean("a.txt", "Short");
    }

    @Test
    void aFailedLargerAttemptFollowedByASmallerVersionLeavesNoStaleIds() {
        pipeline.ingest(source, "A");
        source.files.put("a.txt", OMEGA);
        store.addsBeforeFailure = 3;
        pipeline.ingest(source, "A");
        assertThat(texts("a.txt")).hasSizeGreaterThan(chunkCountOf(ALPHA));

        store.recover();
        source.files.put("a.txt", SHORT);
        pipeline.ingest(source, "A");

        assertClean("a.txt", "Short");
        assertThat(texts("a.txt")).hasSize(1);
    }

    @Test
    void aDirtyEntryIsNeverSkipped() {
        pipeline.ingest(source, "A");
        SourceDocumentRecord clean = record("a.txt");
        SourceDocumentRecord dirty = new SourceDocumentRecord("docs", "a.txt", "A", SourceDocumentRecord.DIRTY_HASH,
                clean.chunkCount());
        registry.save(dirty);

        IngestionResult result = pipeline.ingest(source, "A");

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(record("a.txt")).isEqualTo(clean);

        registry.save(dirty);
        assertThat(pipeline.ingestDocument("docs", "A", MapSource.item("a.txt", ALPHA)).outcome())
                .isEqualTo(Outcome.UPDATED);
    }

    @Test
    void aDirtyDocumentIsListedAndCanBeDeletedEvenAfterAFailedDelete() {
        store.addsBeforeFailure = 1;
        assertThatThrownBy(() -> pipeline.ingestDocument("uploads", "A", MapSource.item("up.txt", OMEGA)))
                .isSameAs(store.failure);
        assertThat(pipeline.listDocuments("uploads", "A"))
                .extracting(SourceDocumentRecord::externalId, SourceDocumentRecord::contentHash)
                .containsExactly(tuple("up.txt", SourceDocumentRecord.DIRTY_HASH));

        store.recover();
        store.failFilterDelete = true;
        assertThatThrownBy(() -> pipeline.deleteDocument("uploads", "A", "up.txt")).isSameAs(store.failure);
        assertThat(pipeline.listDocuments("uploads", "A")).hasSize(1);

        store.recover();
        pipeline.deleteDocument("uploads", "A", "up.txt");

        assertThat(pipeline.listDocuments("uploads", "A")).isEmpty();
        assertThat(store.chunks("A", "up.txt")).isEmpty();
    }
}
