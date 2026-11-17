package com.agrigenius.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agrigenius.chat.application.AssistantReply.ReplyStatus;
import com.agrigenius.chat.application.AssistantReply.SourceRef;
import com.agrigenius.chat.config.ChatProperties;
import com.agrigenius.chat.domain.ChatMessage;
import com.agrigenius.chat.domain.ChatMessageStatus;
import com.agrigenius.chat.domain.ChatRole;
import com.agrigenius.chat.domain.ChatSession;
import com.agrigenius.chat.domain.ChatSessionStatus;
import com.agrigenius.chat.infrastructure.ChatMessageRepository;
import com.agrigenius.chat.infrastructure.ChatSessionRepository;
import com.agrigenius.chat.infrastructure.ChatUsageDailyRepository;
import com.agrigenius.common.error.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    @Mock ChatSessionRepository sessions;
    @Mock ChatMessageRepository messages;
    @Mock ChatUsageDailyRepository usage;
    @Mock ChatAssistant assistant;

    /** Records whether a transaction is open, so tests can prove the assistant runs outside one. */
    private final TrackingTransactionManager txManager = new TrackingTransactionManager();
    private final UUID userId = UUID.randomUUID();
    private ChatSession session;
    private ChatService service;

    @BeforeEach
    void setUp() {
        service = newService(properties(20, 40, 2_000_000L, 10, 200, 50));
        session = new ChatSession(userId, "te", "Paddy", NOW);
        given(sessions.findByIdAndUserId(session.getId(), userId)).willReturn(Optional.of(session));
        given(messages.save(any(ChatMessage.class))).willAnswer(inv -> inv.getArgument(0));
        given(messages.findRecentForHistory(any(), any(), any(), any())).willReturn(List.of());
        given(usage.requestsFor(userId, TODAY)).willReturn(3L);
        given(usage.totalTokensFor(TODAY)).willReturn(1_000L);
    }

    private ChatService newService(ChatProperties props) {
        return new ChatService(sessions, messages, usage, assistant, props, Clock.fixed(NOW, ZoneOffset.UTC),
                new TransactionTemplate(txManager), JsonMapper.builder().build());
    }

    private static ChatProperties properties(int perWindow, int daily, long budget, int history, int perSession,
            int activeSessions) {
        return new ChatProperties("low", perWindow, Duration.ofMinutes(10), daily, budget, history, 2000,
                perSession, activeSessions, Duration.ofSeconds(60));
    }

    private static AssistantReply okReply() {
        return new AssistantReply(ReplyStatus.OK, "Brown spot is often caused by a fungus [S1].",
                List.of(new SourceRef("Rice IPM", "Publisher", "https://example.org/rice", "CC-BY-4.0")),
                List.of("getMyCropRecommendations"), "Confirm with your local KVK.", "claude-opus-5-5", 1200, 300, 800);
    }

    // ------------------------------------------------------------ validation

    @Test
    void sanitizeRemovesControlCharactersButKeepsNewlinesAndTabs() {
        assertThat(ChatService.sanitize("  Hello\u0000 wor\u0007ld\r\nline\ttab  ")).isEqualTo("Hello world\nline\ttab");
        assertThat(ChatService.sanitize(null)).isEmpty();
    }

    @Test
    void blankContentIsRejected() {
        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), " \u0001\u0002 "))
                .isInstanceOf(ChatExceptions.InvalidContent.class)
                .satisfies(e -> assertThat(((ChatExceptions.InvalidContent) e).code()).isEqualTo("VALIDATION_FAILED"));
        verifyNoInteractions(assistant);
    }

    @Test
    void tooLongContentIsRejected() {
        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), "a".repeat(2001)))
                .isInstanceOf(ChatExceptions.InvalidContent.class);
        verifyNoInteractions(assistant);
    }

    @Test
    void contentAtTheLimitIsAccepted() {
        given(assistant.reply(any())).willReturn(okReply());
        assertThat(service.sendMessage(userId, session.getId(), "a".repeat(2000))).isNotNull();
    }

    @Test
    void foreignOrUnknownSessionIs404() {
        UUID other = UUID.randomUUID();
        given(sessions.findByIdAndUserId(other, userId)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.sendMessage(userId, other, "hello"))
                .isInstanceOf(ChatExceptions.SessionNotFound.class);
        verifyNoInteractions(assistant);
        verify(messages, never()).save(any());
    }

    @Test
    void fullSessionIsRejected() {
        session.recordMessages(199, NOW);
        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), "hello"))
                .isInstanceOf(ChatExceptions.SessionFull.class)
                .satisfies(e -> assertThat(((ChatExceptions.SessionFull) e).code()).isEqualTo("CHAT_SESSION_FULL"));
    }

    // ------------------------------------------------------------ limits

    @Test
    void perUserWindowThrottleReturns429() {
        service = newService(properties(2, 40, 2_000_000L, 10, 200, 50));
        given(assistant.reply(any())).willReturn(okReply());
        service.sendMessage(userId, session.getId(), "one");
        service.sendMessage(userId, session.getId(), "two");
        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), "three"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfter()).isPositive());
    }

    @Test
    void dailyLimitReturns429UntilUtcMidnight() {
        given(usage.requestsFor(userId, TODAY)).willReturn(40L);
        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), "hello"))
                .isInstanceOf(ChatExceptions.DailyLimitReached.class)
                .satisfies(e -> {
                    ChatExceptions.DailyLimitReached limit = (ChatExceptions.DailyLimitReached) e;
                    assertThat(limit.code()).isEqualTo("CHAT_DAILY_LIMIT_REACHED");
                    assertThat(limit.retryAfter()).isEqualTo(Duration.ofHours(14));
                });
        verifyNoInteractions(assistant);
    }

    @Test
    void globalTokenBudgetReturns503() {
        given(usage.totalTokensFor(TODAY)).willReturn(2_000_000L);
        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), "hello"))
                .isInstanceOf(ChatExceptions.BudgetExhausted.class)
                .satisfies(e -> assertThat(((ChatExceptions.BudgetExhausted) e).code()).isEqualTo("CHAT_BUDGET_EXHAUSTED"));
        verifyNoInteractions(assistant);
    }

    // ------------------------------------------------------------ success path

    @Test
    void questionIsStoredBeforeTheAssistantRunsOutsideAnyTransaction() {
        AtomicBoolean txOpenDuringCall = new AtomicBoolean(true);
        given(assistant.reply(any())).willAnswer(inv -> {
            txOpenDuringCall.set(txManager.active);
            return okReply();
        });

        service.sendMessage(userId, session.getId(), "My paddy leaves have brown spots");

        assertThat(txOpenDuringCall).isFalse();
        InOrder order = inOrder(messages, assistant, sessions, usage);
        order.verify(messages).save(any(ChatMessage.class));
        order.verify(assistant).reply(any());
        order.verify(messages).save(any(ChatMessage.class));
        order.verify(sessions).recordMessages(eq(session.getId()), eq(2), any());
        order.verify(usage).recordRequest(userId, TODAY, 1200L, 300L);
    }

    @Test
    void successStoresAnswerWithProvenanceAndReturnsRemainingAllowance() {
        given(assistant.reply(any())).willReturn(okReply());

        SendResult result = service.sendMessage(userId, session.getId(), "  hello\u0000 ");

        assertThat(result.userMessage().getRole()).isEqualTo(ChatRole.USER);
        assertThat(result.userMessage().getContent()).isEqualTo("hello");
        ChatMessage answer = result.assistantMessage();
        assertThat(answer.getRole()).isEqualTo(ChatRole.ASSISTANT);
        assertThat(answer.getStatus()).isEqualTo(ChatMessageStatus.OK);
        assertThat(answer.getLanguage()).isEqualTo("te");
        assertThat(answer.getModel()).isEqualTo("claude-opus-5-5");
        assertThat(answer.getInputTokens()).isEqualTo(1200);
        assertThat(answer.getOutputTokens()).isEqualTo(300);
        assertThat(answer.getCacheReadTokens()).isEqualTo(800);
        assertThat(answer.getLatencyMs()).isNotNull();
        assertThat(service.readSources(answer)).extracting(SourceRef::title).containsExactly("Rice IPM");
        assertThat(service.readToolsUsed(answer)).containsExactly("getMyCropRecommendations");
        assertThat(result.language()).isEqualTo("te");
        assertThat(result.remainingMessagesToday()).isEqualTo(36); // 40 - (3 + 1)
    }

    @Test
    void noSourcesAndRefusedStatusesAreStoredAsIs() {
        given(assistant.reply(any())).willReturn(new AssistantReply(ReplyStatus.NO_SOURCES,
                "I don't have verified information on this.", List.of(), List.of(), null, "m", 10, 5, 0));
        assertThat(service.sendMessage(userId, session.getId(), "q").assistantMessage().getStatus())
                .isEqualTo(ChatMessageStatus.NO_SOURCES);

        given(assistant.reply(any())).willReturn(new AssistantReply(ReplyStatus.REFUSED,
                "I can't help with market prices.", null, null, null, "m", 10, 5, 0));
        ChatMessage refused = service.sendMessage(userId, session.getId(), "q2").assistantMessage();
        assertThat(refused.getStatus()).isEqualTo(ChatMessageStatus.REFUSED);
        assertThat(refused.getSourcesJson()).isEqualTo("[]");
        assertThat(refused.getToolsUsedJson()).isEqualTo("[]");
    }

    @Test
    void turnCarriesSessionLanguageUserIdAndHistoryOldestFirst() {
        ChatMessage older = ChatMessage.user(session.getId(), userId, "first question", "te", NOW.minusSeconds(60));
        ChatMessage newer = ChatMessage.assistant(session.getId(), userId, ChatMessageStatus.OK, "first answer", "te",
                NOW.minusSeconds(50));
        given(messages.findRecentForHistory(any(), any(), any(), any())).willReturn(List.of(newer, older));
        AtomicReference<ChatTurn> captured = new AtomicReference<>();
        given(assistant.reply(any())).willAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return okReply();
        });

        SendResult result = service.sendMessage(userId, session.getId(), "second question");

        ChatTurn turn = captured.get();
        assertThat(turn.userId()).isEqualTo(userId);
        assertThat(turn.sessionId()).isEqualTo(session.getId());
        assertThat(turn.language()).isEqualTo("te");
        assertThat(turn.userMessage()).isEqualTo("second question");
        assertThat(turn.history()).extracting(ChatTurn.HistoryEntry::role).containsExactly("USER", "ASSISTANT");
        assertThat(turn.history()).extracting(ChatTurn.HistoryEntry::content)
                .containsExactly("first question", "first answer");

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(messages).findRecentForHistory(eq(session.getId()), eq(result.userMessage().getId()),
                eq(ChatService.HISTORY_STATUSES), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(10);
        assertThat(ChatService.HISTORY_STATUSES).doesNotContain(ChatMessageStatus.FAILED);
    }

    @Test
    void zeroHistoryDoesNotQueryHistory() {
        service = newService(properties(20, 40, 2_000_000L, 0, 200, 50));
        given(assistant.reply(any())).willReturn(okReply());
        service.sendMessage(userId, session.getId(), "q");
        verify(messages, never()).findRecentForHistory(any(), any(), any(), any());
    }

    // ------------------------------------------------------------ failures

    @Test
    void disabledChatRemovesTheQuestionAndStoresNothingElse() {
        given(assistant.reply(any())).willThrow(new ChatDisabledException());
        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);

        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), "hello"))
                .isInstanceOf(ChatDisabledException.class);

        verify(messages).save(saved.capture());
        verify(messages).deleteById(saved.getValue().getId());
        verify(sessions, never()).recordMessages(any(), anyInt(), any());
        verify(usage, never()).recordRequest(any(), any(), anyLong(), anyLong());
    }

    @Test
    void providerFailureStoresFailedAnswerUpdatesCountersAndRethrows() {
        ChatProviderUnavailableException failure = new ChatProviderUnavailableException();
        given(assistant.reply(any())).willThrow(failure);
        List<ChatMessage> saved = new ArrayList<>();
        given(messages.save(any(ChatMessage.class))).willAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), "hello")).isSameAs(failure);

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getRole()).isEqualTo(ChatRole.USER);
        assertThat(saved.get(1).getRole()).isEqualTo(ChatRole.ASSISTANT);
        assertThat(saved.get(1).getStatus()).isEqualTo(ChatMessageStatus.FAILED);
        assertThat(saved.get(1).getContent()).isEqualTo(ChatService.FAILED_TEXT);
        verify(messages, never()).deleteById(any());
        verify(sessions).recordMessages(eq(session.getId()), eq(2), any());
        verify(usage).recordRequest(userId, TODAY, 0L, 0L);
    }

    @Test
    void unexpectedAssistantErrorIsReportedAsProviderUnavailable() {
        given(assistant.reply(any())).willThrow(new IllegalStateException("secret provider detail"));
        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), "hello"))
                .isInstanceOf(ChatProviderUnavailableException.class)
                .hasMessageNotContaining("secret");
    }

    @Test
    void emptyReplyIsTreatedAsProviderFailure() {
        given(assistant.reply(any())).willReturn(new AssistantReply(ReplyStatus.OK, "  ", List.of(), List.of(),
                null, "m", 1, 1, 0));
        assertThatThrownBy(() -> service.sendMessage(userId, session.getId(), "hello"))
                .isInstanceOf(ChatProviderUnavailableException.class);
    }

    // ------------------------------------------------------------ sessions

    @Test
    void createSessionNormalisesLanguageAndCleansTitle() {
        given(sessions.countByUserIdAndStatus(userId, ChatSessionStatus.ACTIVE)).willReturn(0L);
        given(sessions.save(any(ChatSession.class))).willAnswer(inv -> inv.getArgument(0));

        ChatSession created = service.createSession(userId, " TE ", "  Paddy\u0000 pests\n" + "x".repeat(200));

        assertThat(created.getLanguage()).isEqualTo("te");
        assertThat(created.getTitle()).startsWith("Paddy pests x").hasSize(ChatService.MAX_TITLE_CHARS);
        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getStatus()).isEqualTo(ChatSessionStatus.ACTIVE);
    }

    @Test
    void createSessionWithBlankTitleStoresNoTitle() {
        given(sessions.save(any(ChatSession.class))).willAnswer(inv -> inv.getArgument(0));
        assertThat(service.createSession(userId, "en", "   ").getTitle()).isNull();
    }

    @Test
    void createSessionRejectsTooManyActiveSessions() {
        given(sessions.countByUserIdAndStatus(userId, ChatSessionStatus.ACTIVE)).willReturn(50L);
        assertThatThrownBy(() -> service.createSession(userId, "en", null))
                .isInstanceOf(ChatExceptions.SessionLimitReached.class)
                .satisfies(e -> assertThat(((ChatExceptions.SessionLimitReached) e).code())
                        .isEqualTo("CHAT_SESSION_LIMIT_REACHED"));
        verify(sessions, never()).save(any());
    }

    @Test
    void getAndDeleteAreOwnerScoped() {
        UUID foreign = UUID.randomUUID();
        given(sessions.findByIdAndUserId(foreign, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSession(userId, foreign)).isInstanceOf(ChatExceptions.SessionNotFound.class);
        assertThatThrownBy(() -> service.deleteSession(userId, foreign))
                .isInstanceOf(ChatExceptions.SessionNotFound.class);
        assertThatThrownBy(() -> service.listMessages(userId, foreign, 0, 50))
                .isInstanceOf(ChatExceptions.SessionNotFound.class);
        verify(sessions, never()).delete(any());
        verify(messages, never()).findPageForSession(any(), any(), any());

        service.deleteSession(userId, session.getId());
        verify(sessions).delete(session);
    }

    @Test
    void remainingMessagesNeverNegative() {
        given(usage.requestsFor(userId, TODAY)).willReturn(55L);
        assertThat(service.remainingMessagesToday(userId)).isZero();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    /** Minimal transaction manager that tracks whether a transaction is currently open. */
    static final class TrackingTransactionManager implements PlatformTransactionManager {
        volatile boolean active;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            active = true;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            active = false;
        }

        @Override
        public void rollback(TransactionStatus status) {
            active = false;
        }
    }
}
