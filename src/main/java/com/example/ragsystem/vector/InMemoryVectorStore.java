package com.example.ragsystem.vector;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "rag.vector-store", name = "type", havingValue = "memory")
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, VectorPoint> points = new ConcurrentHashMap<>();

    @Override
    public void ensureCollection() {
        // no-op
    }

    @Override
    public void upsert(List<VectorPoint> newPoints) {
        for (VectorPoint point : newPoints) {
            points.put(point.id(), point);
        }
    }

    @Override
    public List<ScoredChunk> search(float[] queryVector, int topK, double minScore, String ownerId, UUID documentId) {
        List<ScoredChunk> results = new ArrayList<>();
        for (VectorPoint point : points.values()) {
            Object pointOwner = point.payload().get("ownerId");
            if (ownerId != null && (pointOwner == null || !ownerId.equals(pointOwner.toString()))) {
                continue;
            }
            Object docId = point.payload().get("documentId");
            if (documentId != null && (docId == null || !documentId.toString().equals(docId.toString()))) {
                continue;
            }
            double score = cosine(queryVector, point.vector());
            if (score < minScore) {
                continue;
            }
            results.add(new ScoredChunk(
                    UUID.fromString(point.id()),
                    UUID.fromString(String.valueOf(point.payload().get("documentId"))),
                    ((Number) point.payload().getOrDefault("pageNumber", 0)).intValue(),
                    String.valueOf(point.payload().getOrDefault("content", "")),
                    score));
        }
        results.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        return results.size() > topK ? results.subList(0, topK) : results;
    }

    @Override
    public void deleteByDocument(UUID documentId) {
        points.values().removeIf(p -> documentId.toString().equals(String.valueOf(p.payload().get("documentId"))));
    }

    private double cosine(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
