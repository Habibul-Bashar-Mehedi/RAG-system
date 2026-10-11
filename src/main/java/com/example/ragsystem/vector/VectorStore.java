package com.example.ragsystem.vector;

import java.util.List;
import java.util.UUID;

public interface VectorStore {

    void ensureCollection();

    void upsert(List<VectorPoint> points);

    List<ScoredChunk> search(float[] queryVector, int topK, double minScore, String ownerId, UUID documentId);

    void deleteByDocument(UUID documentId);
}
