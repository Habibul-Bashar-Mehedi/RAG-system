package com.example.ragsystem.ingestion;

import com.example.ragsystem.document.Document;
import com.example.ragsystem.document.DocumentChunk;
import com.example.ragsystem.document.DocumentChunkRepository;
import com.example.ragsystem.document.DocumentRepository;
import com.example.ragsystem.document.DocumentStatus;
import com.example.ragsystem.embedding.EmbeddingClient;
import com.example.ragsystem.vector.VectorPoint;
import com.example.ragsystem.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final PdfTextExtractor extractor;
    private final TextChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public IngestionService(DocumentRepository documentRepository,
                            DocumentChunkRepository chunkRepository,
                            PdfTextExtractor extractor,
                            TextChunker chunker,
                            EmbeddingClient embeddingClient,
                            VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.extractor = extractor;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    @Async
    public void triggerProcessing(UUID documentId) {
        process(documentId);
    }

    public void process(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return;
        }
        try {
            document.setStatus(DocumentStatus.PROCESSING);
            document.setErrorMessage(null);
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);

            List<PageText> pages = extractor.extract(Path.of(document.getStoragePath()));
            List<Chunk> chunks = chunker.chunk(pages);
            if (chunks.isEmpty()) {
                fail(document, "No extractable text found in the PDF");
                return;
            }

            List<String> texts = chunks.stream().map(Chunk::content).toList();
            List<float[]> vectors = embeddingClient.embed(texts);
            if (vectors.size() != chunks.size()) {
                fail(document, "Embedding count mismatch");
                return;
            }

            chunkRepository.deleteByDocumentId(documentId);
            vectorStore.deleteByDocument(documentId);

            List<DocumentChunk> entities = new ArrayList<>(chunks.size());
            for (Chunk chunk : chunks) {
                DocumentChunk entity = new DocumentChunk();
                entity.setDocumentId(documentId);
                entity.setOwnerId(document.getOwnerId());
                entity.setChunkIndex(chunk.chunkIndex());
                entity.setPageNumber(chunk.pageNumber());
                entity.setContent(chunk.content());
                entity.setCharCount(chunk.content().length());
                entity.setCreatedAt(Instant.now());
                entities.add(entity);
            }
            List<DocumentChunk> stored = chunkRepository.saveAll(entities);

            List<VectorPoint> points = new ArrayList<>(stored.size());
            for (int i = 0; i < stored.size(); i++) {
                Chunk chunk = chunks.get(i);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("documentId", documentId.toString());
                payload.put("ownerId", document.getOwnerId());
                payload.put("pageNumber", chunk.pageNumber());
                payload.put("chunkIndex", chunk.chunkIndex());
                payload.put("content", chunk.content());
                points.add(new VectorPoint(stored.get(i).getId().toString(), vectors.get(i), payload));
            }
            vectorStore.upsert(points);

            document.setPageCount(pages.size());
            document.setChunkCount(chunks.size());
            document.setStatus(DocumentStatus.READY);
            document.setUpdatedAt(Instant.now());
            documentRepository.save(document);
            log.info("Processed document {} ({} pages, {} chunks)", documentId, pages.size(), chunks.size());
        } catch (Exception ex) {
            log.error("Failed to process document {}", documentId, ex);
            fail(document, ex.getMessage());
        }
    }

    private void fail(Document document, String message) {
        document.setStatus(DocumentStatus.FAILED);
        document.setErrorMessage(message == null ? "Unknown processing error" : message);
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);
    }
}
