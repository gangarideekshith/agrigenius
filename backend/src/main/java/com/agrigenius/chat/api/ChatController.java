package com.agrigenius.chat.api;

import com.agrigenius.chat.api.dto.ChatDtos.AssistantMessageResponse;
import com.agrigenius.chat.api.dto.ChatDtos.CreateSessionRequest;
import com.agrigenius.chat.api.dto.ChatDtos.MessageResponse;
import com.agrigenius.chat.api.dto.ChatDtos.PageResponse;
import com.agrigenius.chat.api.dto.ChatDtos.SendMessageRequest;
import com.agrigenius.chat.api.dto.ChatDtos.SendMessageResponse;
import com.agrigenius.chat.api.dto.ChatDtos.SessionResponse;
import com.agrigenius.chat.api.dto.ChatDtos.SourceResponse;
import com.agrigenius.chat.api.dto.ChatDtos.UsageResponse;
import com.agrigenius.chat.api.dto.ChatDtos.UserMessageResponse;
import com.agrigenius.chat.application.ChatExceptions;
import com.agrigenius.chat.application.ChatService;
import com.agrigenius.chat.application.SendResult;
import com.agrigenius.chat.domain.ChatMessage;
import com.agrigenius.chat.domain.ChatSession;
import com.agrigenius.common.web.RequestIdFilter;
import com.agrigenius.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in farmer's chat sessions and messages. The user id always comes from the verified
 * token, and every lookup is owner-scoped: another farmer's session is answered with 404.
 * Access to {@code /api/chat/**} requires ROLE_FARMER (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/chat/sessions")
@Tag(name = "Chat")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ChatController {

    static final String PRIVACY_NOTICE =
            "Your message is processed by an external AI provider. Do not share personal or financial details.";
    static final int MAX_SESSIONS_PAGE_SIZE = 50;
    static final int MAX_MESSAGES_PAGE_SIZE = 100;

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    @Operation(summary = "Start a chat session")
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ChatSession session = chat.createSession(userId(jwt), request.language(), request.title());
        return ResponseEntity.created(URI.create("/api/chat/sessions/" + session.getId())).body(toResponse(session));
    }

    @GetMapping
    @Operation(summary = "List my chat sessions, newest activity first")
    public PageResponse<SessionResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, MAX_SESSIONS_PAGE_SIZE);
        Page<ChatSession> result = chat.listSessions(userId(jwt), safePage, safeSize);
        return new PageResponse<>(result.getContent().stream().map(ChatController::toResponse).toList(),
                safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of my chat sessions")
    public SessionResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return toResponse(chat.getSession(userId(jwt), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one of my chat sessions and all its messages")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        chat.deleteSession(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "List the messages of one of my sessions, oldest first")
    public PageResponse<MessageResponse> messages(@PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @AuthenticationPrincipal Jwt jwt) {
        int safePage = Math.max(page, 0);
        int safeSize = clamp(size, MAX_MESSAGES_PAGE_SIZE);
        Page<ChatMessage> result = chat.listMessages(userId(jwt), id, safePage, safeSize);
        return new PageResponse<>(result.getContent().stream().map(this::toResponse).toList(),
                safePage, safeSize, result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send a message and get the assistant's answer")
    public SendMessageResponse send(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        SendResult result = chat.sendMessage(userId(jwt), id, request.content());
        ChatMessage question = result.userMessage();
        ChatMessage answer = result.assistantMessage();
        return new SendMessageResponse(
                new UserMessageResponse(question.getId(), question.getContent(), question.getCreatedAt()),
                new AssistantMessageResponse(answer.getId(), answer.getStatus().name(), result.language(),
                        answer.getContent(), sources(answer), chat.readToolsUsed(answer), answer.getUncertainty(),
                        answer.getCreatedAt()),
                new UsageResponse(result.remainingMessagesToday()),
                PRIVACY_NOTICE);
    }

    // Controller-local handlers: these two errors need fields the shared handler does not add.

    @ExceptionHandler(ChatExceptions.InvalidContent.class)
    ResponseEntity<ProblemDetail> invalidContent(ChatExceptions.InvalidContent ex) {
        ProblemDetail problem = problem(ex.status(), ex.title(), ex.detail(), ex.code());
        problem.setProperty("errors", List.of(Map.of("field", "content", "message", ex.fieldMessage())));
        return ResponseEntity.status(ex.status()).body(problem);
    }

    @ExceptionHandler(ChatExceptions.DailyLimitReached.class)
    ResponseEntity<ProblemDetail> dailyLimit(ChatExceptions.DailyLimitReached ex) {
        ProblemDetail problem = problem(ex.status(), ex.title(), ex.detail(), ex.code());
        return ResponseEntity.status(ex.status())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, ex.retryAfter().toSeconds())))
                .body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        String requestId = RequestIdFilter.current();
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private static int clamp(int size, int max) {
        return Math.min(Math.max(size, 1), max);
    }

    private static SessionResponse toResponse(ChatSession s) {
        return new SessionResponse(s.getId(), s.getTitle(), s.getLanguage(), s.getStatus().name(),
                s.getMessageCount(), s.getCreatedAt(), s.getLastMessageAt());
    }

    private MessageResponse toResponse(ChatMessage m) {
        return new MessageResponse(m.getId(), m.getRole().name(), m.getStatus().name(), m.getContent(),
                m.getLanguage(), sources(m), chat.readToolsUsed(m), m.getUncertainty(), m.getCreatedAt());
    }

    private List<SourceResponse> sources(ChatMessage m) {
        return chat.readSources(m).stream()
                .map(s -> new SourceResponse(s.title(), s.publisher(), s.url(), s.licence()))
                .toList();
    }
}
