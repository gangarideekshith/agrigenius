package com.agrigenius.chat.infrastructure;

import com.agrigenius.chat.domain.ChatMessage;
import com.agrigenius.chat.domain.ChatMessageStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Chronological; a question sorts before its answer when both share a timestamp. */
    @Query("select m from ChatMessage m where m.sessionId = :sessionId and m.userId = :userId "
            + "order by m.createdAt asc, m.role desc")
    Page<ChatMessage> findPageForSession(UUID sessionId, UUID userId, Pageable pageable);

    /**
     * Most recent messages of a session, newest first, excluding {@code excludeId} and messages whose
     * status is not in {@code statuses}. Callers reverse the list for chronological order.
     */
    @Query("select m from ChatMessage m where m.sessionId = :sessionId and m.id <> :excludeId "
            + "and m.status in :statuses order by m.createdAt desc, m.role asc")
    List<ChatMessage> findRecentForHistory(UUID sessionId, UUID excludeId, Collection<ChatMessageStatus> statuses,
            Pageable pageable);
}
