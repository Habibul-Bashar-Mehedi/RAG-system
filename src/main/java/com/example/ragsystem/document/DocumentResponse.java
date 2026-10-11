package com.example.ragsystem.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        int pageCount,
        int chunkCount,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getFilename(), d.getContentType(), d.getSizeBytes(),
                d.getStatus(), d.getPageCount(), d.getChunkCount(), d.getErrorMessage(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
