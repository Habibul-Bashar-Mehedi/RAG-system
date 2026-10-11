package com.example.ragsystem.vector;

import com.example.ragsystem.common.RagException;
import com.example.ragsystem.common.RetryExecutor;
import com.example.ragsystem.config.RagProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "rag.vector-store", name = "type", havingValue = "qdrant", matchIfMissing = true)
public class QdrantVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;
    private final String collection;
    private final int dimension;
    private final AtomicBoolean collectionReady = new AtomicBoolean(false);

    public QdrantVectorStore(RestClient.Builder builder, RagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.collection = properties.vectorStore().collection();
        this.dimension = properties.embedding().dimension();
        Duration timeout = Duration.ofSeconds(properties.qdrant().timeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = builder.clone()
                .baseUrl(properties.qdrant().baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public void ensureCollection() {
        if (collectionReady.get()) {
            return;
        }
        synchronized (collectionReady) {
            if (collectionReady.get()) {
                return;
            }
            try {
                restClient.get().uri("/collections/{c}", collection).retrieve().toBodilessEntity();
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 404) {
                    createCollection();
                } else {
                    throw new RagException("Qdrant collection check failed: " + ex.getStatusCode(), ex);
                }
            } catch (RuntimeException ex) {
                throw new RagException("Unable to reach Qdrant at " + properties.qdrant().baseUrl(), ex);
            }
            collectionReady.set(true);
        }
    }

    private void createCollection() {
        Map<String, Object> body = Map.of(
                "vectors", Map.of("size", dimension, "distance", "Cosine"));
        restClient.put().uri("/collections/{c}", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        log.info("Created Qdrant collection '{}' with dimension {}", collection, dimension);
    }

    @Override
    public void upsert(List<VectorPoint> points) {
        if (points.isEmpty()) {
            return;
        }
        ensureCollection();
        List<Map<String, Object>> wirePoints = new ArrayList<>(points.size());
        for (VectorPoint point : points) {
            Map<String, Object> wire = new LinkedHashMap<>();
            wire.put("id", point.id());
            wire.put("vector", point.vector());
            wire.put("payload", point.payload());
            wirePoints.add(wire);
        }
        Map<String, Object> body = Map.of("points", wirePoints);
        RetryExecutor.execute("Qdrant upsert", properties.retry().maxAttempts(), properties.retry().backoffMs(),
                () -> restClient.put().uri("/collections/{c}/points?wait=true", collection)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity());
    }

    @Override
    public List<ScoredChunk> search(float[] queryVector, int topK, double minScore, String ownerId, UUID documentId) {
        ensureCollection();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", queryVector);
        body.put("limit", topK);
        body.put("with_payload", true);
        body.put("score_threshold", minScore);
        List<Map<String, Object>> must = new ArrayList<>();
        if (ownerId != null) {
            must.add(Map.of("key", "ownerId", "match", Map.of("value", ownerId)));
        }
        if (documentId != null) {
            must.add(Map.of("key", "documentId", "match", Map.of("value", documentId.toString())));
        }
        if (!must.isEmpty()) {
            body.put("filter", Map.of("must", must));
        }
        String response = RetryExecutor.execute("Qdrant search",
                properties.retry().maxAttempts(), properties.retry().backoffMs(),
                () -> restClient.post().uri("/collections/{c}/points/search", collection)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(String.class));
        return parseSearch(response);
    }

    private List<ScoredChunk> parseSearch(String response) {
        List<ScoredChunk> results = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(response);
            for (JsonNode node : root.path("result")) {
                JsonNode payload = node.path("payload");
                results.add(new ScoredChunk(
                        UUID.fromString(node.path("id").asText()),
                        UUID.fromString(payload.path("documentId").asText()),
                        payload.path("pageNumber").asInt(0),
                        payload.path("content").asText(""),
                        node.path("score").asDouble(0)));
            }
        } catch (Exception ex) {
            throw new RagException("Failed to parse Qdrant search response", ex);
        }
        return results;
    }

    @Override
    public void deleteByDocument(UUID documentId) {
        ensureCollection();
        Map<String, Object> body = Map.of("filter", Map.of("must", List.of(
                Map.of("key", "documentId", "match", Map.of("value", documentId.toString())))));
        RetryExecutor.execute("Qdrant delete", properties.retry().maxAttempts(), properties.retry().backoffMs(),
                () -> restClient.post().uri("/collections/{c}/points/delete?wait=true", collection)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity());
    }
}
