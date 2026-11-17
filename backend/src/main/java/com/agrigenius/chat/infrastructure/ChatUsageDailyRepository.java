package com.agrigenius.chat.infrastructure;

import com.agrigenius.chat.domain.ChatUsageDaily;
import com.agrigenius.chat.domain.ChatUsageDailyId;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ChatUsageDailyRepository extends JpaRepository<ChatUsageDaily, ChatUsageDailyId> {

    /** Atomically adds one request and its tokens to the farmer's row for {@code day}. */
    @Modifying
    @Query(value = """
            INSERT INTO chat_usage_daily (user_id, day, requests, input_tokens, output_tokens)
            VALUES (:userId, :day, 1, :inputTokens, :outputTokens)
            ON CONFLICT (user_id, day) DO UPDATE SET
                requests = chat_usage_daily.requests + 1,
                input_tokens = chat_usage_daily.input_tokens + EXCLUDED.input_tokens,
                output_tokens = chat_usage_daily.output_tokens + EXCLUDED.output_tokens
            """, nativeQuery = true)
    int recordRequest(UUID userId, LocalDate day, long inputTokens, long outputTokens);

    @Query(value = "SELECT COALESCE(SUM(requests), 0) FROM chat_usage_daily WHERE user_id = :userId AND day = :day",
            nativeQuery = true)
    long requestsFor(UUID userId, LocalDate day);

    /** Input + output tokens used by all farmers on {@code day}. */
    @Query(value = "SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM chat_usage_daily WHERE day = :day",
            nativeQuery = true)
    long totalTokensFor(LocalDate day);
}
