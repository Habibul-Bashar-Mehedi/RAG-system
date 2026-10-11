package com.example.ragsystem.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        Role role,
        String content,
        List<SourceReference> sources,
        Instant createdAt
) {
}
