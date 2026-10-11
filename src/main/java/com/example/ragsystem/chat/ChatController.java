package com.example.ragsystem.chat;

import com.example.ragsystem.common.RequestUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat/sessions")
@Tag(name = "Chat", description = "Persistent chat sessions with RAG answers")
public class ChatController {

    private final ChatService chatService;
    private final RequestUser requestUser;

    public ChatController(ChatService chatService, RequestUser requestUser) {
        this.chatService = chatService;
        this.requestUser = requestUser;
    }

    @PostMapping
    @Operation(summary = "Create a new chat session, optionally bound to a document")
    public ResponseEntity<SessionSummaryResponse> create(
            @Valid @RequestBody CreateSessionRequest request,
            @RequestHeader(value = RequestUser.HEADER, required = false) String userId) {
        SessionSummaryResponse session = chatService.createSession(requestUser.resolve(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping
    @Operation(summary = "List chat sessions owned by the caller")
    public List<SessionSummaryResponse> list(
            @RequestHeader(value = RequestUser.HEADER, required = false) String userId) {
        return chatService.listSessions(requestUser.resolve(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a chat session with its full message history")
    public SessionDetailResponse detail(
            @PathVariable UUID id,
            @RequestHeader(value = RequestUser.HEADER, required = false) String userId) {
        return chatService.getSession(requestUser.resolve(userId), id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a chat session and its messages")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(value = RequestUser.HEADER, required = false) String userId) {
        chatService.deleteSession(requestUser.resolve(userId), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Ask a question within a session and get a grounded answer")
    public ChatAnswerResponse ask(
            @PathVariable UUID id,
            @Valid @RequestBody ChatQuestionRequest request,
            @RequestHeader(value = RequestUser.HEADER, required = false) String userId) {
        return chatService.ask(requestUser.resolve(userId), id, request.question().trim());
    }
}
