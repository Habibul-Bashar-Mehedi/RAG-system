package com.example.ragsystem.embedding;

import com.example.ragsystem.common.NonRetryableException;
import com.example.ragsystem.config.RagProperties;
import com.example.ragsystem.support.StubHttpServer;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiEmbeddingClientTest {

    private RagProperties props(String baseUrl, String apiKey) {
        return new RagProperties(null, null, null,
                new RagProperties.Embedding(baseUrl, apiKey, "test-embedding", 2, 5, 2),
                null, null, null, new RagProperties.Retry(1, 0));
    }

    @Test
    void parsesEmbeddingsInIndexOrder() throws Exception {
        String json = """
                {"data":[
                  {"index":1,"embedding":[3.0,4.0]},
                  {"index":0,"embedding":[1.0,2.0]}
                ]}""";
        try (StubHttpServer server = new StubHttpServer(200, json)) {
            OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
                    RestClient.builder(), props(server.baseUrl(), "test-key"), new ObjectMapper());

            List<float[]> vectors = client.embed(List.of("a", "b"));

            assertThat(vectors).hasSize(2);
            assertThat(vectors.get(0)).containsExactly(1f, 2f);
            assertThat(vectors.get(1)).containsExactly(3f, 4f);
            assertThat(server.lastRequestBody()).contains("\"model\":\"test-embedding\"");
        }
    }

    @Test
    void failsFastWhenApiKeyMissing() {
        OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
                RestClient.builder(), props("http://localhost:1/v1", ""), new ObjectMapper());

        assertThatThrownBy(() -> client.embed("hello"))
                .isInstanceOf(NonRetryableException.class)
                .hasMessageContaining("API key is not configured");
    }
}
