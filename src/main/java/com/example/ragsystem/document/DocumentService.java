package com.example.ragsystem.document;

import com.example.ragsystem.common.BadRequestException;
import com.example.ragsystem.common.NotFoundException;
import com.example.ragsystem.common.RagException;
import com.example.ragsystem.config.RagProperties;
import com.example.ragsystem.ingestion.IngestionService;
import com.example.ragsystem.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final IngestionService ingestionService;
    private final VectorStore vectorStore;
    private final RagProperties properties;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository chunkRepository,
                           IngestionService ingestionService,
                           VectorStore vectorStore,
                           RagProperties properties) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ingestionService = ingestionService;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    public DocumentResponse upload(MultipartFile file, String ownerId) {
        validate(file);
        Path storagePath = store(file);
        Document document = new Document();
        document.setOwnerId(ownerId);
        document.setFilename(sanitizeFilename(file.getOriginalFilename()));
        document.setContentType(file.getContentType());
        document.setSizeBytes(file.getSize());
        document.setStatus(DocumentStatus.PENDING);
        document.setStoragePath(storagePath.toString());
        document.setCreatedAt(Instant.now());
        document.setUpdatedAt(Instant.now());
        Document saved = documentRepository.save(document);

        ingestionService.triggerProcessing(saved.getId());
        return DocumentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(String ownerId) {
        return documentRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(DocumentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID id, String ownerId) {
        return DocumentResponse.from(requireOwned(id, ownerId));
    }

    @Transactional
    public void delete(UUID id, String ownerId) {
        Document document = requireOwned(id, ownerId);
        chunkRepository.deleteByDocumentId(id);
        try {
            vectorStore.deleteByDocument(id);
        } catch (RuntimeException ex) {
            log.warn("Vector cleanup failed for document {}: {}", id, ex.getMessage());
        }
        documentRepository.delete(document);
        try {
            Files.deleteIfExists(Path.of(document.getStoragePath()));
        } catch (IOException ex) {
            log.warn("Failed to delete file for document {}: {}", id, ex.getMessage());
        }
    }

    public Document requireOwned(UUID id, String ownerId) {
        return documentRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + id));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty");
        }
        String name = sanitizeFilename(file.getOriginalFilename());
        boolean pdfName = name.toLowerCase().endsWith(".pdf");
        boolean pdfType = file.getContentType() != null && file.getContentType().contains("pdf");
        if (!pdfName && !pdfType) {
            throw new BadRequestException("Only PDF files are supported");
        }
        if (!hasPdfMagic(file)) {
            throw new BadRequestException("File content is not a valid PDF");
        }
    }

    private boolean hasPdfMagic(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(4);
            if (header.length < 4) {
                return false;
            }
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                if (header[i] != PDF_MAGIC[i]) {
                    return false;
                }
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private Path store(MultipartFile file) {
        try {
            Path dir = Path.of(properties.upload().dir()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + ".pdf");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException ex) {
            throw new RagException("Failed to store uploaded file", ex);
        }
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "document.pdf";
        }
        String base = Path.of(original).getFileName().toString();
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
