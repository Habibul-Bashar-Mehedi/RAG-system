package com.example.ragsystem;

import com.example.ragsystem.document.Document;
import com.example.ragsystem.document.DocumentRepository;
import com.example.ragsystem.document.DocumentStatus;
import com.example.ragsystem.embedding.EmbeddingClient;
import com.example.ragsystem.llm.LlmClient;
import com.example.ragsystem.support.TestPdfFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DocumentRepository documentRepository;

    @MockitoBean
    private EmbeddingClient embeddingClient;
    @MockitoBean
    private LlmClient llmClient;

    @BeforeEach
    void stubModels() {
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{1f, 0f, 0f});
        when(embeddingClient.embed(anyList())).thenAnswer(invocation -> {
            List<?> inputs = invocation.getArgument(0);
            List<float[]> vectors = new ArrayList<>();
            for (int i = 0; i < inputs.size(); i++) {
                vectors.add(new float[]{1f, 0f, 0f});
            }
            return vectors;
        });
        when(llmClient.complete(anyString(), anyString())).thenReturn("The warranty period is two years [1].");
    }

    private String newUser() {
        return "user-" + UUID.randomUUID();
    }

    private UUID upload(String user, byte[] pdf, String filename) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", filename, MediaType.APPLICATION_PDF_VALUE, pdf))
                        .header("X-User-Id", user))
                .andExpect(status().isAccepted())
                .andReturn();
        return UUID.fromString(json(result).get("id").asText());
    }

    private void awaitReady(UUID id) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Document document = documentRepository.findById(id).orElseThrow();
            if (document.getStatus() == DocumentStatus.READY) {
                return;
            }
            if (document.getStatus() == DocumentStatus.FAILED) {
                throw new AssertionError("Processing failed: " + document.getErrorMessage());
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Document was not processed in time");
    }

    private String createSession(String user, UUID documentId) throws Exception {
        String body = objectMapper.writeValueAsString(documentId == null ? Map.of() : Map.of("documentId", documentId.toString()));
        MvcResult result = mockMvc.perform(post("/api/chat/sessions")
                        .header("X-User-Id", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("id").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void uploadProcessAskAndRecoverHistory() throws Exception {
        String user = newUser();
        UUID documentId = upload(user, TestPdfFactory.pdf("The warranty period is two years from purchase."), "manual.pdf");
        awaitReady(documentId);

        String sessionId = createSession(user, documentId);

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("X-User-Id", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is the warranty period?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("two years")))
                .andExpect(jsonPath("$.sources[0].pageNumber").value(1))
                .andExpect(jsonPath("$.sources[0].filename").value("manual.pdf"));

        mockMvc.perform(get("/api/chat/sessions/{id}", sessionId).header("X-User-Id", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("USER"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));
    }

    @Test
    void rejectsInvalidUploads() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "not a pdf".getBytes()))
                        .header("X-User-Id", newUser()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "fake.pdf", MediaType.APPLICATION_PDF_VALUE, "not really a pdf".getBytes()))
                        .header("X-User-Id", newUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        String user = newUser();
        String sessionId = createSession(user, null);

        mockMvc.perform(post("/api/chat/sessions/{id}/messages", sessionId)
                        .header("X-User-Id", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.question").exists());
    }

    @Test
    void enforcesUserIsolation() throws Exception {
        String alice = newUser();
        String bob = newUser();
        UUID documentId = upload(alice, TestPdfFactory.pdf("Confidential warranty information for the internal product line."), "secret.pdf");
        awaitReady(documentId);
        String aliceSession = createSession(alice, documentId);

        mockMvc.perform(get("/api/documents/{id}", documentId).header("X-User-Id", bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/chat/sessions/{id}", aliceSession).header("X-User-Id", bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/chat/sessions/{id}/messages", aliceSession)
                        .header("X-User-Id", bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Tell me the secret\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/chat/sessions")
                        .header("X-User-Id", bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("documentId", documentId.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadUnknownContentTypeStillRequiresPdfMagic() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "report.pdf", null, TestPdfFactory.pdf("Valid pdf content")))
                        .header("X-User-Id", newUser()))
                .andExpect(status().isAccepted());
    }
}
