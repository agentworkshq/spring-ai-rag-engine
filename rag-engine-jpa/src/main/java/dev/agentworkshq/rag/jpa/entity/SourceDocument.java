package dev.agentworkshq.rag.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "RAG_SOURCE_DOCUMENT",
        uniqueConstraints = @UniqueConstraint(name = "UK_RAG_SOURCE_DOCUMENT",
                columnNames = {"SOURCE_NAME", "EXTERNAL_ID", "TENANT_ID"}))
public class SourceDocument extends BaseEntity {

    @Column(name = "SOURCE_NAME", nullable = false)
    private String sourceName;

    @Column(name = "EXTERNAL_ID", nullable = false)
    private String externalId;

    @Column(name = "TENANT_ID", nullable = false, length = 128)
    private String tenantId = "default";

    @Column(name = "CONTENT_HASH", nullable = false, length = 128)
    private String contentHash;

    @Column(name = "CHUNK_COUNT", nullable = false)
    private int chunkCount;

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }
}
