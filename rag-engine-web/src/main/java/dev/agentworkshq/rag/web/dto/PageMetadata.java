package dev.agentworkshq.rag.web.dto;

public record PageMetadata(int currentPage, int pageSize, int totalPages, long totalItems) {
}
