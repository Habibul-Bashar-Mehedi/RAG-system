package com.example.ragsystem.chat;

import com.example.ragsystem.common.NotFoundException;
import com.example.ragsystem.common.RagException;
import com.example.ragsystem.document.Document;
import com.example.ragsystem.document.DocumentService;
import com.example.ragsystem.rag.RagService;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentService documentService;
    private final RagService ragService;
    private final ObjectMapper objectMapper;

    public ChatService(ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository,
                       DocumentService documentService,
                       RagService ragService,
                       ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.documentService = documentService;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SessionSummaryResponse createSession(String ownerId, CreateSessionRequest request) {
        UUID documentId = request.documentId();
        String title = request.title() == null || request.title().isBlank() ? "New chat" : request.title().trim();
        if (documentId != null) {
            Document document = documentService.requireOwned(documentId, ownerId);
            if (title.equals("New chat")) {
                title = document.getFilename();
            }
        }
        ChatSession session = new ChatSession();
        session.setOwnerId(ownerId);
        session.setDocumentId(documentId);
        session.setTitle(title);
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        return SessionSummaryResponse.from(sessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<SessionSummaryResponse> listSessions(String ownerId) {
        return sessionRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId).stream()
                .map(SessionSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse getSession(String ownerId, UUID sessionId) {
        ChatSession session = requireSession(sessionId, ownerId);
        List<MessageResponse> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toMessageResponse)
                .toList();
        return new SessionDetailResponse(SessionSummaryResponse.from(session), messages);
    }

    @Transactional
    public void deleteSession(String ownerId, UUID sessionId) {
        ChatSession session = requireSession(sessionId, ownerId);
        messageRepository.deleteBySessionId(session.getId());
        sessionRepository.delete(session);
    }

    public ChatAnswerResponse ask(String ownerId, UUID sessionId, String question) {
        ChatSession session = requireSession(sessionId, ownerId);
        saveMessage(sessionId, Role.USER, question, null);

        RagService.RagAnswer answer = ragService.answer(ownerId, session.getDocumentId(), question);

        ChatMessage assistant = saveMessage(sessionId, Role.ASSISTANT, answer.answer(), writeSources(answer.sources()));
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
        return new ChatAnswerResponse(sessionId, answer.answer(), answer.found(), answer.sources(), assistant.getCreatedAt());
    }

    private ChatSession requireSession(UUID sessionId, String ownerId) {
        return sessionRepository.findByIdAndOwnerId(sessionId, ownerId)
                .orElseThrow(() -> new NotFoundException("Chat session not found: " + sessionId));
    }

    private ChatMessage saveMessage(UUID sessionId, Role role, String content, String sources) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setSources(sources);
        message.setCreatedAt(Instant.now());
        return messageRepository.save(message);
    }

    private MessageResponse toMessageResponse(ChatMessage message) {
        return new MessageResponse(message.getId(), message.getRole(), message.getContent(),
                readSources(message.getSources()), message.getCreatedAt());
    }

    private String writeSources(List<SourceReference> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception ex) {
            throw new RagException("Failed to serialize sources", ex);
        }
    }

    private List<SourceReference> readSources(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
