package com.agrigenius.chat.infrastructure;

import com.agrigenius.chat.domain.ChatSession;
import com.agrigenius.chat.domain.ChatSessionStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, ChatSessionStatus status);

    /**
     * Atomically adds {@code messages} to the session's counter (no read-modify-write, so concurrent
     * sends to the same session cannot lose updates or fail on optimistic locking).
     */
    @Modifying
    @Query("update ChatSession s set s.messageCount = s.messageCount + :messages, s.lastMessageAt = :at, "
            + "s.version = s.version + 1 where s.id = :id")
    int recordMessages(UUID id, int messages, Instant at);

    /** Newest activity first; sessions without messages sort by creation time. */
    @Query("select s from ChatSession s where s.userId = :userId "
            + "order by coalesce(s.lastMessageAt, s.createdAt) desc, s.createdAt desc")
    Page<ChatSession> findPageForUser(UUID userId, Pageable pageable);
}
