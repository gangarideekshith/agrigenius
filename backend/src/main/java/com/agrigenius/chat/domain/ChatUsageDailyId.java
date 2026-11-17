package com.agrigenius.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ChatUsageDailyId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    protected ChatUsageDailyId() {
        // for JPA
    }

    public ChatUsageDailyId(UUID userId, LocalDate day) {
        this.userId = userId;
        this.day = day;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getDay() {
        return day;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChatUsageDailyId other && Objects.equals(userId, other.userId)
                && Objects.equals(day, other.day);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, day);
    }
}
