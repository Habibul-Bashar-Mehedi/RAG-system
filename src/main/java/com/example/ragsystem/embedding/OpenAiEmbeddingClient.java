package com.example.ragsystem.embedding;

import com.example.ragsystem.common.NonRetryableException;
import com.example.ragsystem.common.RagException;
import com.example.ragsystem.common.RetryExecutor;
import com.example.ragsystem.config.RagProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    public OpenAiEmbeddingClient(RestClient.Builder builder, RagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(properties.embedding().timeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        RestClient.Builder configured = builder.clone()
                .baseUrl(properties.embedding().baseUrl())
                .requestFactory(requestFactory);
        if (properties.embedding().apiKey() != null && !properties.embedding().apiKey().isBlank()) {
            configured = configured.defaultHeader("Authorization", "Bearer " + properties.embedding().apiKey());
        }
        this.restClient = configured.build();
    }

    @Override
    public int dimension() {
        return properties.embedding().dimension();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        int batchSize = properties.embedding().maxBatch();
        for (int start = 0; start < texts.size(); start += batchSize) {
            List<String> batch = texts.subList(start, Math.min(start + batchSize, texts.size()));
            vectors.addAll(embedBatch(batch));
        }
        return vectors;
    }

    private List<float[]> embedBatch(List<String> batch) {
        ensureConfigured();
        Map<String, Object> body = Map.of(
                "model", properties.embedding().model(),
                "input", batch);
        String response = RetryExecutor.execute("Embedding request",
                properties.retry().maxAttempts(), properties.retry().backoffMs(),
                () -> {
                    try {
                        return restClient.post().uri("/embeddings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body)
                                .retrieve()
                                .body(String.class);
                    } catch (RestClientResponseException ex) {
                        if (ex.getStatusCode().is4xxClientError()) {
                            throw new NonRetryableException("Embedding API rejected the request: " + ex.getStatusCode());
                        }
                        throw ex;
                    }
                });
        return parse(response, batch.size());
    }

    private List<float[]> parse(String response, int expected) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new RagException("Embedding API returned no data");
            }
            List<IndexedVector> indexed = new ArrayList<>(data.size());
            for (JsonNode node : data) {
                JsonNode embedding = node.path("embedding");
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = (float) embedding.get(i).asDouble();
                }
                indexed.add(new IndexedVector(node.path("index").asInt(indexed.size()), vector));
            }
            indexed.sort(Comparator.comparingInt(IndexedVector::index));
            List<float[]> vectors = new ArrayList<>(indexed.size());
            for (IndexedVector iv : indexed) {
                vectors.add(iv.vector());
            }
            if (vectors.size() != expected) {
                throw new RagException("Embedding API returned " + vectors.size() + " vectors for " + expected + " inputs");
            }
            return vectors;
        } catch (RagException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RagException("Failed to parse embedding response", ex);
        }
    }

    private void ensureConfigured() {
        if (properties.embedding().apiKey() == null || properties.embedding().apiKey().isBlank()) {
            throw new NonRetryableException("Embedding API key is not configured (set OPENAI_API_KEY / rag.embedding.api-key)");
        }
    }

    private record IndexedVector(int index, float[] vector) {
    }
}
