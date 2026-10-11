package com.example.ragsystem.vector;

import java.util.UUID;

public record ScoredChunk(UUID chunkId, UUID documentId, int pageNumber, String content, double score) {
}
