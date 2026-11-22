package dev.agentworkshq.rag.jpa.entity;

import dev.agentworkshq.rag.core.store.IngestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "RAG_INGESTION_JOB",
        indexes = @Index(name = "IDX_RAG_JOB_SOURCE_CREATED", columnList = "SOURCE_NAME, CREATED_ON"))
@SQLRestriction("IS_DELETED = false")
public class IngestionJob extends BaseEntity {

    @Column(name = "SOURCE_NAME", nullable = false)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 16)
    private IngestionStatus status;

    @Column(name = "STARTED_AT", nullable = false)
    private Instant startedAt;

    @Column(name = "FINISHED_AT")
    private Instant finishedAt;

    @Column(name = "ADDED_COUNT", nullable = false)
    private int addedCount;

    @Column(name = "UPDATED_COUNT", nullable = false)
    private int updatedCount;

    @Column(name = "DELETED_COUNT", nullable = false)
    private int deletedCount;

    @Column(name = "SKIPPED_COUNT", nullable = false)
    private int skippedCount;

    @Column(name = "ERROR_MESSAGE", length = 500)
    private String errorMessage;

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getAddedCount() {
        return addedCount;
    }

    public void setAddedCount(int addedCount) {
        this.addedCount = addedCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getDeletedCount() {
        return deletedCount;
    }

    public void setDeletedCount(int deletedCount) {
        this.deletedCount = deletedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
