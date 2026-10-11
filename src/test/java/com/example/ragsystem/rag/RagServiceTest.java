package com.example.ragsystem.rag;

import com.example.ragsystem.chat.SourceReference;
import com.example.ragsystem.config.RagProperties;
import com.example.ragsystem.document.Document;
import com.example.ragsystem.document.DocumentRepository;
import com.example.ragsystem.embedding.EmbeddingClient;
import com.example.ragsystem.llm.LlmClient;
import com.example.ragsystem.vector.InMemoryVectorStore;
import com.example.ragsystem.vector.VectorPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceTest {

    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final LlmClient llmClient = mock(LlmClient.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final InMemoryVectorStore vectorStore = new InMemoryVectorStore();

    private RagService service;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        RagProperties properties = new RagProperties(null,
                new RagProperties.Retrieval(5, 0.0), null,
                new RagProperties.Embedding(null, "key", "m", 3, 5, 8),
                null, null, null, null);
        service = new RagService(embeddingClient, vectorStore, llmClient, new PromptBuilder(),
                documentRepository, properties);

        when(embeddingClient.embed(anyString())).thenReturn(new float[]{1f, 0f, 0f});

        Document document = new Document();
        document.setId(documentId);
        document.setOwnerId("alice");
        document.setFilename("manual.pdf");
        when(documentRepository.findAllById(any())).thenReturn(List.of(document));
    }

    private void indexOwner(String ownerId) {
        vectorStore.upsert(List.of(new VectorPoint(UUID.randomUUID().toString(), new float[]{1f, 0f, 0f},
                Map.of("documentId", documentId.toString(), "ownerId", ownerId,
                        "pageNumber", 3, "content", "The warranty period is two years."))));
    }

    @Test
    void returnsGroundedAnswerWithSources() {
        indexOwner("alice");
        when(llmClient.complete(anyString(), anyString())).thenReturn("The warranty period is two years [1].");

        RagService.RagAnswer answer = service.answer("alice", documentId, "What is the warranty period?");

        assertThat(answer.found()).isTrue();
        assertThat(answer.answer()).contains("two years");
        assertThat(answer.sources()).hasSize(1);
        SourceReference source = answer.sources().getFirst();
        assertThat(source.pageNumber()).isEqualTo(3);
        assertThat(source.filename()).isEqualTo("manual.pdf");
    }

    @Test
    void returnsNotFoundWhenNoContext() {
        RagService.RagAnswer answer = service.answer("alice", documentId, "Unrelated question?");

        assertThat(answer.found()).isFalse();
        assertThat(answer.answer()).isEqualTo(PromptBuilder.NOT_FOUND_TOKEN);
        assertThat(answer.sources()).isEmpty();
        verify(llmClient, never()).complete(anyString(), anyString());
    }

    @Test
    void preventsCrossUserRetrieval() {
        indexOwner("bob");

        RagService.RagAnswer answer = service.answer("alice", documentId, "What is the warranty period?");

        assertThat(answer.found()).isFalse();
        verify(llmClient, never()).complete(anyString(), anyString());
    }
}
