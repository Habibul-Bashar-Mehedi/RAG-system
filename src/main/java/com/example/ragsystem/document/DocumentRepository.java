package com.example.ragsystem.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<Document> findByIdAndOwnerId(UUID id, String ownerId);

    boolean existsByIdAndOwnerId(UUID id, String ownerId);
}
