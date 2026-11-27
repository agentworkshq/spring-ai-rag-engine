package dev.agentworkshq.rag.core.ingest;

import dev.agentworkshq.rag.core.FakeEmbeddingModel;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

class FailingStore implements VectorStore {

    private final SimpleVectorStore delegate = SimpleVectorStore.builder(new FakeEmbeddingModel()).build();

    // the number of add calls that still succeed, negative for no limit
    int addsBeforeFailure = -1;
    boolean failFilterDelete;
    boolean failIdDelete;
    final RuntimeException failure = new IllegalStateException("store failed");
    final List<String> deletedIds = new ArrayList<>();
    int writes;

    @Override
    public void add(List<Document> documents) {
        if (addsBeforeFailure == 0) {
            throw failure;
        }
        addsBeforeFailure--;
        writes++;
        delegate.add(documents);
    }

    @Override
    public void delete(List<String> ids) {
        if (failIdDelete) {
            throw failure;
        }
        writes++;
        deletedIds.addAll(ids);
        delegate.delete(ids);
    }

    @Override
    public void delete(Filter.Expression filter) {
        if (failFilterDelete) {
            throw failure;
        }
        writes++;
        delegate.delete(filter);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return delegate.similaritySearch(request);
    }

    void recover() {
        addsBeforeFailure = -1;
        failFilterDelete = false;
        failIdDelete = false;
    }

    List<Document> chunks(String tenant, String filename) {
        return similaritySearch(SearchRequest.builder().query("fox").topK(100000).similarityThresholdAll()
                .filterExpression(new FilterExpressionBuilder().eq(ChunkMetadata.TENANT_ID, tenant).build()).build())
                .stream()
                .filter(d -> filename.equals(d.getMetadata().get(ChunkMetadata.FILENAME)))
                .toList();
    }
}
