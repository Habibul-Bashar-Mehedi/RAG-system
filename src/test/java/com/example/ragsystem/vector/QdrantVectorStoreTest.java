package com.example.ragsystem.vector;

import com.example.ragsystem.config.RagProperties;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantVectorStoreTest {

    private HttpServer server;
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, Integer> calls = new ConcurrentHashMap<>();
    private UUID documentId;

    @BeforeEach
    void setUp() throws IOException {
        documentId = UUID.randomUUID();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String key = method + " " + path;
            calls.merge(key, 1, Integer::sum);
            bodies.put(key, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String response = switch (key) {
                case "GET /collections/rag_chunks" -> "{}";
                case "POST /collections/rag_chunks/points/search" -> """
                        {"result":[{"id":"%s","score":0.91,"payload":{"documentId":"%s","pageNumber":2,"content":"warranty text"}}]}
                        """.formatted(UUID.randomUUID(), documentId);
                default -> "{}";
            };
            int status = key.equals("GET /collections/rag_chunks") ? 404 : 200;
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private QdrantVectorStore store() {
        RagProperties properties = new RagProperties(null, null,
                new RagProperties.VectorStore("qdrant", "rag_chunks"),
                new RagProperties.Embedding(null, "key", "m", 3, 5, 8),
                null,
                new RagProperties.Qdrant("http://127.0.0.1:" + server.getAddress().getPort(), 5),
                null, new RagProperties.Retry(1, 0));
        return new QdrantVectorStore(RestClient.builder(), properties, new ObjectMapper());
    }

    @Test
    void createsCollectionUpsertsSearchesAndDeletes() {
        QdrantVectorStore store = store();

        String chunkId = UUID.randomUUID().toString();
        store.upsert(List.of(new VectorPoint(chunkId, new float[]{1f, 0f, 0f},
                Map.of("documentId", documentId.toString(), "ownerId", "alice", "pageNumber", 2, "content", "warranty text"))));

        assertThat(calls.get("GET /collections/rag_chunks")).isEqualTo(1);
        assertThat(calls.get("PUT /collections/rag_chunks")).isEqualTo(1);
        assertThat(bodies.get("PUT /collections/rag_chunks")).contains("\"size\":3");

        List<ScoredChunk> results = store.search(new float[]{1f, 0f, 0f}, 5, 0.0, "alice", documentId);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().documentId()).isEqualTo(documentId);
        assertThat(results.getFirst().pageNumber()).isEqualTo(2);
        assertThat(results.getFirst().score()).isEqualTo(0.91);

        String searchBody = bodies.get("POST /collections/rag_chunks/points/search");
        assertThat(searchBody).contains("ownerId").contains("alice").contains(documentId.toString());

        store.deleteByDocument(documentId);
        assertThat(calls.get("POST /collections/rag_chunks/points/delete")).isEqualTo(1);
        assertThat(bodies.get("POST /collections/rag_chunks/points/delete")).contains(documentId.toString());
    }
}
