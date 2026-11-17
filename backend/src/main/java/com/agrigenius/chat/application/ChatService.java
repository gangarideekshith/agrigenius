package com.agrigenius.chat.application;

import com.agrigenius.chat.application.AssistantReply.SourceRef;
import com.agrigenius.chat.config.ChatProperties;
import com.agrigenius.chat.domain.ChatMessage;
import com.agrigenius.chat.domain.ChatMessageStatus;
import com.agrigenius.chat.domain.ChatSession;
import com.agrigenius.chat.domain.ChatSessionStatus;
import com.agrigenius.chat.infrastructure.ChatMessageRepository;
import com.agrigenius.chat.infrastructure.ChatSessionRepository;
import com.agrigenius.chat.infrastructure.ChatUsageDailyRepository;
import com.agrigenius.common.error.TooManyRequestsException;
import com.agrigenius.identity.application.FixedWindowCounter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Farmer chat sessions and messages: ownership, validation, limits, persistence and usage accounting.
 *
 * <p>The assistant call is made outside any database transaction: the question is stored first in its
 * own transaction and the answer is stored in a second one, so a slow or failing provider never holds
 * a database connection. Message content is never logged.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    static final Set<String> LANGUAGES = Set.of("en", "hi", "te", "kn", "ta");
    static final int MAX_TITLE_CHARS = 120;
    static final int MAX_STORED_CONTENT_CHARS = 8000;
    static final int MAX_UNCERTAINTY_CHARS = 500;
    static final int MAX_MODEL_CHARS = 60;
    static final String FAILED_TEXT =
            "Sorry, the assistant could not answer right now. Your question was saved; please try again later.";
    /** Messages that represent a real exchange and may be replayed to the model as history. */
    static final Set<ChatMessageStatus> HISTORY_STATUSES =
            EnumSet.of(ChatMessageStatus.OK, ChatMessageStatus.NO_SOURCES, ChatMessageStatus.REFUSED);
    /** C0 controls except tab and newline (carriage returns are dropped too). */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");

    private final ChatSessionRepository sessions;
    private final ChatMessageRepository messages;
    private final ChatUsageDailyRepository usage;
    private final ChatAssistant assistant;
    private final ChatProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final JsonMapper jsonMapper;
    private final FixedWindowCounter perUserThrottle;

    public ChatService(ChatSessionRepository sessions, ChatMessageRepository messages, ChatUsageDailyRepository usage,
            ChatAssistant assistant, ChatProperties properties, Clock clock, TransactionTemplate tx,
            JsonMapper jsonMapper) {
        this.sessions = sessions;
        this.messages = messages;
        this.usage = usage;
        this.assistant = assistant;
        this.properties = properties;
        this.clock = clock;
        this.tx = tx;
        this.jsonMapper = jsonMapper;
        this.perUserThrottle = new FixedWindowCounter(properties.messagesPerWindow(), properties.window(), clock,
                100_000);
    }

    // ---------------------------------------------------------------- sessions

    @Transactional
    public ChatSession createSession(UUID userId, String language, String title) {
        String lang = language == null ? null : language.strip().toLowerCase(java.util.Locale.ROOT);
        if (lang == null || !LANGUAGES.contains(lang)) {
            throw new IllegalArgumentException("language must be validated before createSession");
        }
        if (sessions.countByUserIdAndStatus(userId, ChatSessionStatus.ACTIVE) >= properties.maxActiveSessions()) {
            throw new ChatExceptions.SessionLimitReached(properties.maxActiveSessions());
        }
        ChatSession session = sessions.save(new ChatSession(userId, lang, cleanTitle(title), now()));
        log.info("Chat session created sessionId={}", session.getId());
        return session;
    }

    @Transactional(readOnly = true)
    public Page<ChatSession> listSessions(UUID userId, int page, int size) {
        return sessions.findPageForUser(userId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public ChatSession getSession(UUID userId, UUID sessionId) {
        return sessions.findByIdAndUserId(sessionId, userId).orElseThrow(ChatExceptions.SessionNotFound::new);
    }

    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        ChatSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(ChatExceptions.SessionNotFound::new);
        sessions.delete(session); // messages are removed by ON DELETE CASCADE
        log.info("Chat session deleted sessionId={}", sessionId);
    }

    @Transactional(readOnly = true)
    public Page<ChatMessage> listMessages(UUID userId, UUID sessionId, int page, int size) {
        getSession(userId, sessionId);
        return messages.findPageForSession(sessionId, userId, PageRequest.of(page, size));
    }

    // ---------------------------------------------------------------- send

    /** Stores the question, asks the assistant (outside any transaction) and stores the answer. */
    public SendResult sendMessage(UUID userId, UUID sessionId, String rawContent) {
        String content = sanitize(rawContent);
        if (content.isEmpty()) {
            throw new ChatExceptions.InvalidContent("must not be blank");
        }
        if (content.length() > properties.maxContentChars()) {
            throw new ChatExceptions.InvalidContent(
                    "must be at most " + properties.maxContentChars() + " characters");
        }

        ChatSession session = tx.execute(status -> sessions.findByIdAndUserId(sessionId, userId)
                .filter(ChatSession::isActive)
                .orElse(null));
        if (session == null) {
            throw new ChatExceptions.SessionNotFound();
        }
        if (session.getMessageCount() + 2 > properties.maxMessagesPerSession()) {
            throw new ChatExceptions.SessionFull(properties.maxMessagesPerSession());
        }

        String throttleKey = userId.toString();
        if (!perUserThrottle.tryIncrement(throttleKey)) {
            throw new TooManyRequestsException(perUserThrottle.retryAfter(throttleKey));
        }
        LocalDate today = today();
        long usedToday = usage.requestsFor(userId, today);
        if (usedToday >= properties.dailyMessageLimit()) {
            throw new ChatExceptions.DailyLimitReached(properties.dailyMessageLimit(), untilUtcMidnight());
        }
        if (usage.totalTokensFor(today) >= properties.dailyTokenBudget()) {
            log.warn("Chat global daily token budget exhausted day={}", today);
            throw new ChatExceptions.BudgetExhausted();
        }

        String language = session.getLanguage();
        Instant askedAt = now();
        ChatMessage question = tx.execute(status ->
                messages.save(ChatMessage.user(sessionId, userId, content, language, askedAt)));
        List<ChatTurn.HistoryEntry> history = history(sessionId, question.getId());

        long started = System.nanoTime();
        AssistantReply reply;
        try {
            reply = assistant.reply(new ChatTurn(userId, sessionId, language, content, history));
            if (reply == null || reply.status() == null || reply.content() == null || reply.content().isBlank()) {
                throw new IllegalStateException("Assistant returned an empty reply");
            }
        } catch (ChatDisabledException disabled) {
            tx.executeWithoutResult(status -> messages.deleteById(question.getId()));
            log.info("Chat disabled; question discarded sessionId={}", sessionId);
            throw disabled;
        } catch (RuntimeException failure) {
            int latency = elapsedMillis(started);
            storeFailure(userId, sessionId, language, latency, today);
            log.warn("Chat reply failed sessionId={} messageId={} latencyMs={} error={}",
                    sessionId, question.getId(), latency, failure.getClass().getSimpleName());
            if (failure instanceof ChatProviderUnavailableException unavailable) {
                throw unavailable;
            }
            throw new ChatProviderUnavailableException();
        }

        int latency = elapsedMillis(started);
        ChatMessage answer = tx.execute(status -> {
            ChatMessage stored = ChatMessage.assistant(sessionId, userId, ChatMessageStatus.valueOf(reply.status().name()),
                    truncate(reply.content(), MAX_STORED_CONTENT_CHARS), language, now());
            stored.describeAnswer(toJson(reply.sources()), toJson(reply.toolsUsed()),
                    truncate(reply.uncertainty(), MAX_UNCERTAINTY_CHARS), truncate(reply.model(), MAX_MODEL_CHARS),
                    reply.inputTokens(), reply.outputTokens(), reply.cacheReadTokens(), latency);
            ChatMessage saved = messages.save(stored);
            sessions.recordMessages(sessionId, 2, saved.getCreatedAt());
            usage.recordRequest(userId, today, nonNegative(reply.inputTokens()), nonNegative(reply.outputTokens()));
            return saved;
        });

        log.info("Chat reply sessionId={} messageId={} status={} inputTokens={} outputTokens={} latencyMs={}",
                sessionId, answer.getId(), answer.getStatus(), reply.inputTokens(), reply.outputTokens(), latency);
        int remaining = (int) Math.max(0, properties.dailyMessageLimit() - (usedToday + 1));
        return new SendResult(question, answer, language, remaining);
    }

    /** Remaining messages today for the farmer (for UI display). */
    @Transactional(readOnly = true)
    public int remainingMessagesToday(UUID userId) {
        return (int) Math.max(0, properties.dailyMessageLimit() - usage.requestsFor(userId, today()));
    }

    // ---------------------------------------------------------------- helpers

    private void storeFailure(UUID userId, UUID sessionId, String language, int latency, LocalDate today) {
        tx.executeWithoutResult(status -> {
            ChatMessage failed = ChatMessage.assistant(sessionId, userId, ChatMessageStatus.FAILED, FAILED_TEXT,
                    language, now());
            failed.describeAnswer("[]", "[]", null, null, null, null, null, latency);
            messages.save(failed);
            sessions.recordMessages(sessionId, 2, failed.getCreatedAt());
            usage.recordRequest(userId, today, 0, 0);
        });
    }

    private List<ChatTurn.HistoryEntry> history(UUID sessionId, UUID excludeId) {
        if (properties.historyMessages() <= 0) {
            return List.of();
        }
        List<ChatMessage> recent = tx.execute(status -> messages.findRecentForHistory(sessionId, excludeId,
                HISTORY_STATUSES, PageRequest.of(0, properties.historyMessages())));
        List<ChatTurn.HistoryEntry> entries = new ArrayList<>(recent.size());
        for (ChatMessage m : recent) {
            entries.add(new ChatTurn.HistoryEntry(m.getRole().name(), m.getContent()));
        }
        Collections.reverse(entries);
        return List.copyOf(entries);
    }

    /** Removes control characters (keeping newline and tab) and surrounding whitespace. */
    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        return CONTROL_CHARS.matcher(raw).replaceAll("").strip();
    }

    private static String cleanTitle(String title) {
        if (title == null) {
            return null;
        }
        String clean = sanitize(title).replace('\n', ' ').replace('\t', ' ');
        return clean.isEmpty() ? null : truncate(clean, MAX_TITLE_CHARS);
    }

    private String toJson(List<?> values) {
        return jsonMapper.writeValueAsString(values == null ? List.of() : values);
    }

    /** Reads a stored sources array back into references. */
    public List<SourceRef> readSources(ChatMessage message) {
        return List.of(jsonMapper.readValue(message.getSourcesJson(), SourceRef[].class));
    }

    /** Reads a stored tools array back into names. */
    public List<String> readToolsUsed(ChatMessage message) {
        return List.of(jsonMapper.readValue(message.getToolsUsedJson(), String[].class));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static long nonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private static int elapsedMillis(long startedNanos) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Duration untilUtcMidnight() {
        Instant midnight = today().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Duration.between(clock.instant(), midnight);
    }
}
