package com.example.ragsystem.ingestion;

public record Chunk(int chunkIndex, int pageNumber, String content) {
}
