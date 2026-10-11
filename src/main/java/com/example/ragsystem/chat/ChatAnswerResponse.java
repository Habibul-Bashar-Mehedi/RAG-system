package com.example.ragsystem.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatAnswerResponse(
        UUID sessionId,
        String answer,
        boolean found,
        List<SourceReference> sources,
        Instant createdAt
) {
}
