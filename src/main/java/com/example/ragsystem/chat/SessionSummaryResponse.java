package com.example.ragsystem.chat;

import java.time.Instant;
import java.util.UUID;

public record SessionSummaryResponse(
        UUID id,
        String title,
        UUID documentId,
        Instant createdAt,
        Instant updatedAt
) {
    public static SessionSummaryResponse from(ChatSession session) {
        return new SessionSummaryResponse(session.getId(), session.getTitle(), session.getDocumentId(),
                session.getCreatedAt(), session.getUpdatedAt());
    }
}
