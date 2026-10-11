package com.example.ragsystem.rag;

import com.example.ragsystem.chat.SourceReference;
import com.example.ragsystem.config.RagProperties;
import com.example.ragsystem.document.Document;
import com.example.ragsystem.document.DocumentRepository;
import com.example.ragsystem.embedding.EmbeddingClient;
import com.example.ragsystem.llm.LlmClient;
import com.example.ragsystem.vector.ScoredChunk;
import com.example.ragsystem.vector.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final int SNIPPET_LENGTH = 240;

    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;
    private final LlmClient llmClient;
    private final PromptBuilder promptBuilder;
    private final DocumentRepository documentRepository;
    private final RagProperties properties;

    public RagService(EmbeddingClient embeddingClient,
                      VectorStore vectorStore,
                      LlmClient llmClient,
                      PromptBuilder promptBuilder,
                      DocumentRepository documentRepository,
                      RagProperties properties) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    public RagAnswer answer(String ownerId, UUID documentId, String question) {
        float[] queryVector = embeddingClient.embed(question);
        List<ScoredChunk> chunks = vectorStore.search(queryVector,
                properties.retrieval().topK(),
                properties.retrieval().minScore(),
                ownerId,
                documentId);

        Map<UUID, String> documentNames = documentNames(ownerId, chunks);
        chunks = chunks.stream()
                .filter(chunk -> documentNames.containsKey(chunk.documentId()))
                .toList();

        if (chunks.isEmpty()) {
            return new RagAnswer(PromptBuilder.NOT_FOUND_TOKEN, false, List.of());
        }

        String answer = llmClient.complete(
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(question, chunks, documentNames));

        boolean found = answer != null && !answer.contains(PromptBuilder.NOT_FOUND_TOKEN);
        List<SourceReference> sources = found ? toSources(chunks, documentNames) : List.of();
        return new RagAnswer(found ? answer.strip() : PromptBuilder.NOT_FOUND_TOKEN, found, sources);
    }

    private Map<UUID, String> documentNames(String ownerId, List<ScoredChunk> chunks) {
        List<UUID> ids = chunks.stream().map(ScoredChunk::documentId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return documentRepository.findAllById(ids).stream()
                .filter(document -> document.getOwnerId().equals(ownerId))
                .collect(Collectors.toMap(Document::getId, Document::getFilename, (a, b) -> a, LinkedHashMap::new));
    }

    private List<SourceReference> toSources(List<ScoredChunk> chunks, Map<UUID, String> names) {
        List<SourceReference> sources = new ArrayList<>(chunks.size());
        for (ScoredChunk chunk : chunks) {
            String content = chunk.content();
            String snippet = content.length() > SNIPPET_LENGTH ? content.substring(0, SNIPPET_LENGTH) + "..." : content;
            sources.add(new SourceReference(chunk.chunkId(), chunk.documentId(),
                    names.getOrDefault(chunk.documentId(), "document"),
                    chunk.pageNumber(), chunk.score(), snippet));
        }
        return sources;
    }

    public record RagAnswer(String answer, boolean found, List<SourceReference> sources) {
    }
}
