package com.example.ragsystem.llm;

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
import java.util.List;
import java.util.Map;

@Component
public class OpenAiLlmClient implements LlmClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    public OpenAiLlmClient(RestClient.Builder builder, RagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        Duration timeout = Duration.ofSeconds(properties.llm().timeoutSeconds());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        RestClient.Builder configured = builder.clone()
                .baseUrl(properties.llm().baseUrl())
                .requestFactory(requestFactory);
        if (properties.llm().apiKey() != null && !properties.llm().apiKey().isBlank()) {
            configured = configured.defaultHeader("Authorization", "Bearer " + properties.llm().apiKey());
        }
        this.restClient = configured.build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (properties.llm().apiKey() == null || properties.llm().apiKey().isBlank()) {
            throw new NonRetryableException("LLM API key is not configured (set OPENAI_API_KEY / rag.llm.api-key)");
        }
        Map<String, Object> body = Map.of(
                "model", properties.llm().model(),
                "temperature", properties.llm().temperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        String response = RetryExecutor.execute("LLM request",
                properties.retry().maxAttempts(), properties.retry().backoffMs(),
                () -> {
                    try {
                        return restClient.post().uri("/chat/completions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body)
                                .retrieve()
                                .body(String.class);
                    } catch (RestClientResponseException ex) {
                        if (ex.getStatusCode().is4xxClientError()) {
                            throw new NonRetryableException("LLM API rejected the request: " + ex.getStatusCode());
                        }
                        throw ex;
                    }
                });
        return parse(response);
    }

    private String parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new RagException("LLM API returned an empty response");
            }
            return content.asText();
        } catch (RagException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RagException("Failed to parse LLM response", ex);
        }
    }
}
