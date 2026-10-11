package com.example.ragsystem.chat;

import java.util.UUID;

public record SourceReference(
        UUID chunkId,
        UUID documentId,
        String filename,
        int pageNumber,
        double score,
        String snippet
) {
}
