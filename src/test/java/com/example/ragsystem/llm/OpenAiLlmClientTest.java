package com.example.ragsystem.llm;

import com.example.ragsystem.config.RagProperties;
import com.example.ragsystem.support.StubHttpServer;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiLlmClientTest {

    private RagProperties props(String baseUrl) {
        return new RagProperties(null, null, null, null,
                new RagProperties.Llm(baseUrl, "test-key", "test-llm", 5, 0.0),
                null, null, new RagProperties.Retry(1, 0));
    }

    @Test
    void returnsAssistantContent() throws Exception {
        String json = """
                {"choices":[{"message":{"role":"assistant","content":"The warranty is two years [1]."}}]}""";
        try (StubHttpServer server = new StubHttpServer(200, json)) {
            OpenAiLlmClient client = new OpenAiLlmClient(
                    RestClient.builder(), props(server.baseUrl()), new ObjectMapper());

            String answer = client.complete("system", "user question");

            assertThat(answer).isEqualTo("The warranty is two years [1].");
            assertThat(server.lastRequestBody()).contains("test-llm");
        }
    }
}
