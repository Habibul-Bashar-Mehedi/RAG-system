package com.example.ragsystem.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    long countByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
