package com.example.ragsystem.document;

import com.example.ragsystem.common.RequestUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Upload and manage PDF documents")
public class DocumentController {

    private final DocumentService documentService;
    private final RequestUser requestUser;

    public DocumentController(DocumentService documentService, RequestUser requestUser) {
        this.documentService = documentService;
        this.requestUser = requestUser;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF document for processing")
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestHeader(value = RequestUser.HEADER, required = false) String userId) {
        DocumentResponse response = documentService.upload(file, requestUser.resolve(userId));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    @Operation(summary = "List documents owned by the caller")
    public List<DocumentResponse> list(@RequestHeader(value = RequestUser.HEADER, required = false) String userId) {
        return documentService.list(requestUser.resolve(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get processing status of a document")
    public DocumentResponse get(@PathVariable UUID id,
                                @RequestHeader(value = RequestUser.HEADER, required = false) String userId) {
        return documentService.get(id, requestUser.resolve(userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and its vectors")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestHeader(value = RequestUser.HEADER, required = false) String userId) {
        documentService.delete(id, requestUser.resolve(userId));
        return ResponseEntity.noContent().build();
    }
}
