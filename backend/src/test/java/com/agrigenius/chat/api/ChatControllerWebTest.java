package com.agrigenius.chat.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.chat.application.AssistantReply.SourceRef;
import com.agrigenius.chat.application.ChatDisabledException;
import com.agrigenius.chat.application.ChatExceptions;
import com.agrigenius.chat.application.ChatProviderUnavailableException;
import com.agrigenius.chat.application.ChatService;
import com.agrigenius.chat.application.SendResult;
import com.agrigenius.chat.domain.ChatMessage;
import com.agrigenius.chat.domain.ChatMessageStatus;
import com.agrigenius.chat.domain.ChatSession;
import com.agrigenius.common.error.TooManyRequestsException;
import com.agrigenius.support.WebSliceTestConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(ChatController.class)
@Import(WebSliceTestConfig.class)
@ActiveProfiles("test")
class ChatControllerWebTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatService chat;

    private final UUID userId = UUID.randomUUID();

    private RequestPostProcessor farmer() {
        return jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("ROLE_FARMER"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private ChatSession session() {
        return new ChatSession(userId, "te", "Paddy pests", NOW);
    }

    // ------------------------------------------------------------ sessions

    @Test
    void createSessionReturns201WithLocation() throws Exception {
        ChatSession session = session();
        given(chat.createSession(userId, "te", "Paddy pests")).willReturn(session);

        mockMvc.perform(post("/api/chat/sessions").with(farmer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\" TE \",\"title\":\"Paddy pests\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/chat/sessions/" + session.getId()))
                .andExpect(jsonPath("$.id").value(session.getId().toString()))
                .andExpect(jsonPath("$.language").value("te"))
                .andExpect(jsonPath("$.title").value("Paddy pests"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.messageCount").value(0));
    }

    @Test
    void createSessionValidatesLanguageAndTitle() throws Exception {
        mockMvc.perform(post("/api/chat/sessions").with(farmer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("language"));
        mockMvc.perform(post("/api/chat/sessions").with(farmer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\",\"title\":\"" + "x".repeat(121) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verifyNoInteractions(chat);
    }

    @Test
    void sessionLimitReturns409() throws Exception {
        given(chat.createSession(any(), any(), any())).willThrow(new ChatExceptions.SessionLimitReached(50));
        mockMvc.perform(post("/api/chat/sessions").with(farmer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_SESSION_LIMIT_REACHED"));
    }

    @Test
    void listSessionsReturnsPageAndClampsSize() throws Exception {
        given(chat.listSessions(userId, 0, 50)).willReturn(new PageImpl<>(List.of(session()), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/chat/sessions?page=-3&size=500").with(farmer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        verify(chat).listSessions(userId, 0, 50);
    }

    @Test
    void foreignSessionIs404() throws Exception {
        UUID foreign = UUID.randomUUID();
        given(chat.getSession(userId, foreign)).willThrow(new ChatExceptions.SessionNotFound());

        mockMvc.perform(get("/api/chat/sessions/" + foreign).with(farmer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void deleteSessionReturns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/chat/sessions/" + id).with(farmer())).andExpect(status().isNoContent());
        verify(chat).deleteSession(userId, id);
    }

    @Test
    void listMessagesReturnsChronologicalPage() throws Exception {
        ChatSession s = session();
        ChatMessage q = ChatMessage.user(s.getId(), userId, "question", "te", NOW);
        ChatMessage a = ChatMessage.assistant(s.getId(), userId, ChatMessageStatus.NO_SOURCES, "answer", "te", NOW);
        given(chat.listMessages(userId, s.getId(), 0, 100)).willReturn(new PageImpl<>(List.of(q, a),
                PageRequest.of(0, 100), 2));
        given(chat.readSources(any())).willReturn(List.of());
        given(chat.readToolsUsed(any())).willReturn(List.of());

        mockMvc.perform(get("/api/chat/sessions/" + s.getId() + "/messages?size=1000").with(farmer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].role").value("USER"))
                .andExpect(jsonPath("$.items[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.items[1].status").value("NO_SOURCES"))
                .andExpect(jsonPath("$.size").value(100));
    }

    // ------------------------------------------------------------ send

    @Test
    void sendMessageReturnsQuestionAnswerUsageAndNotice() throws Exception {
        ChatSession s = session();
        ChatMessage q = ChatMessage.user(s.getId(), userId, "My paddy has brown spots", "te", NOW);
        ChatMessage a = ChatMessage.assistant(s.getId(), userId, ChatMessageStatus.OK, "It may be brown spot [S1].",
                "te", NOW);
        a.describeAnswer("[]", "[]", "Confirm with your local KVK.", "claude-opus-5-5", 1200, 300, 800, 950);
        given(chat.sendMessage(userId, s.getId(), "My paddy has brown spots"))
                .willReturn(new SendResult(q, a, "te", 37));
        given(chat.readSources(a)).willReturn(
                List.of(new SourceRef("Rice IPM", "Publisher", "https://example.org/rice", "CC-BY-4.0")));
        given(chat.readToolsUsed(a)).willReturn(List.of("getMyCropRecommendations"));

        mockMvc.perform(post("/api/chat/sessions/" + s.getId() + "/messages").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"My paddy has brown spots\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage.id").value(q.getId().toString()))
                .andExpect(jsonPath("$.userMessage.content").value("My paddy has brown spots"))
                .andExpect(jsonPath("$.assistantMessage.id").value(a.getId().toString()))
                .andExpect(jsonPath("$.assistantMessage.status").value("OK"))
                .andExpect(jsonPath("$.assistantMessage.language").value("te"))
                .andExpect(jsonPath("$.assistantMessage.sources[0].title").value("Rice IPM"))
                .andExpect(jsonPath("$.assistantMessage.sources[0].licence").value("CC-BY-4.0"))
                .andExpect(jsonPath("$.assistantMessage.toolsUsed[0]").value("getMyCropRecommendations"))
                .andExpect(jsonPath("$.assistantMessage.uncertainty").value("Confirm with your local KVK."))
                .andExpect(jsonPath("$.usage.remainingMessagesToday").value(37))
                .andExpect(jsonPath("$.notice").value(containsString("external AI provider")))
                // internal accounting is not exposed
                .andExpect(content().string(not(containsString("inputTokens"))))
                .andExpect(content().string(not(containsString("claude-opus"))));
    }

    @Test
    void missingContentIs400() throws Exception {
        mockMvc.perform(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("content"));
    }

    @Test
    void invalidContentFromServiceIs400WithFieldError() throws Exception {
        given(chat.sendMessage(any(), any(), any())).willThrow(new ChatExceptions.InvalidContent("must not be blank"));

        mockMvc.perform(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("content"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void windowThrottleReturns429WithRetryAfter() throws Exception {
        given(chat.sendMessage(any(), any(), any())).willThrow(new TooManyRequestsException(Duration.ofSeconds(120)));
        mockMvc.perform(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "120"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void dailyLimitReturns429WithCodeAndRetryAfter() throws Exception {
        given(chat.sendMessage(any(), any(), any()))
                .willThrow(new ChatExceptions.DailyLimitReached(40, Duration.ofHours(14)));
        mockMvc.perform(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "50400"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CHAT_DAILY_LIMIT_REACHED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(new ChatExceptions.SessionFull(200), 409, "CHAT_SESSION_FULL"),
                Arguments.of(new ChatExceptions.SessionNotFound(), 404, "NOT_FOUND"),
                Arguments.of(new ChatDisabledException(), 503, "CHAT_DISABLED"),
                Arguments.of(new ChatProviderUnavailableException(), 503, "CHAT_PROVIDER_UNAVAILABLE"),
                Arguments.of(new ChatExceptions.BudgetExhausted(), 503, "CHAT_BUDGET_EXHAUSTED"));
    }

    @ParameterizedTest
    @MethodSource("serviceErrors")
    void sendErrorsAreProblemJsonWithStableCodes(RuntimeException error, int status, String code) throws Exception {
        given(chat.sendMessage(any(), any(), any())).willThrow(error);
        mockMvc.perform(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    // ------------------------------------------------------------ security

    @Test
    void anonymousRequestsAre401() throws Exception {
        mockMvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/chat/sessions/" + UUID.randomUUID() + "/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chat);
    }

    @Test
    void nonFarmerRolesAre403() throws Exception {
        mockMvc.perform(get("/api/chat/sessions").with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/chat/sessions").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"en\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(chat);
    }

    @Test
    void userIdComesOnlyFromTheToken() throws Exception {
        given(chat.listSessions(eq(userId), anyInt(), anyInt())).willReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/chat/sessions?userId=" + UUID.randomUUID()).with(farmer()))
                .andExpect(status().isOk());
        verify(chat).listSessions(eq(userId), anyInt(), anyInt());
    }
}
