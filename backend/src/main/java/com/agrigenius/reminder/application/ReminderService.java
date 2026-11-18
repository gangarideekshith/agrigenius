package com.agrigenius.reminder.application;

import com.agrigenius.recommendation.api.dto.PageResponse;
import com.agrigenius.reminder.api.dto.ReminderRequest;
import com.agrigenius.reminder.api.dto.ReminderResponse;
import com.agrigenius.reminder.api.dto.ReminderSummaryResponse;
import com.agrigenius.reminder.config.ReminderProperties;
import com.agrigenius.reminder.domain.Reminder;
import com.agrigenius.reminder.domain.ReminderDetails;
import com.agrigenius.reminder.domain.ReminderPriority;
import com.agrigenius.reminder.domain.ReminderStatus;
import com.agrigenius.reminder.infrastructure.ReminderRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reminder use cases. Every method takes the owner's id from the verified access token and
 * scopes every read and write to it; other farmers' reminders behave as if they do not exist.
 */
@Service
public class ReminderService {

    private final ReminderRepository repository;
    private final ReminderProperties properties;
    private final Clock clock;

    public ReminderService(ReminderRepository repository, ReminderProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ReminderResponse create(UUID userId, ReminderRequest request) {
        Instant now = now();
        ReminderDetails details = toDetails(request, now, true);
        ensureBelowPendingLimit(userId);
        Reminder reminder = repository.save(new Reminder(UUID.randomUUID(), userId, details, now));
        return toResponse(reminder, now);
    }

    @Transactional(readOnly = true)
    public ReminderResponse get(UUID userId, UUID id) {
        return toResponse(find(userId, id), now());
    }

    /**
     * Full replacement of the editable fields. Only PENDING reminders can be edited. Keeping the
     * current (possibly past) time is allowed; moving the time requires a future time.
     */
    @Transactional
    public ReminderResponse update(UUID userId, UUID id, ReminderRequest request) {
        Reminder reminder = find(userId, id);
        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw ReminderException.notEditable();
        }
        Instant now = now();
        boolean timeChanged = !instantOf(request).equals(reminder.getScheduledAt());
        reminder.update(toDetails(request, now, timeChanged), now);
        return toResponse(reminder, now);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repository.delete(find(userId, id));
    }

    /** PENDING to COMPLETED; repeating it is a no-op. */
    @Transactional
    public ReminderResponse complete(UUID userId, UUID id) {
        Reminder reminder = find(userId, id);
        Instant now = now();
        switch (reminder.getStatus()) {
            case PENDING -> reminder.complete(now);
            case COMPLETED -> { }
            case CANCELLED -> throw ReminderException.invalidTransition(
                    "A cancelled reminder cannot be completed. Reopen it first.");
        }
        return toResponse(reminder, now);
    }

    /** PENDING to CANCELLED; repeating it is a no-op. */
    @Transactional
    public ReminderResponse cancel(UUID userId, UUID id) {
        Reminder reminder = find(userId, id);
        Instant now = now();
        switch (reminder.getStatus()) {
            case PENDING -> reminder.cancel(now);
            case CANCELLED -> { }
            case COMPLETED -> throw ReminderException.invalidTransition(
                    "A completed reminder cannot be cancelled. Reopen it first.");
        }
        return toResponse(reminder, now);
    }

    /** COMPLETED or CANCELLED back to PENDING (subject to the pending limit); PENDING is a no-op. */
    @Transactional
    public ReminderResponse reopen(UUID userId, UUID id) {
        Reminder reminder = find(userId, id);
        Instant now = now();
        if (reminder.getStatus() != ReminderStatus.PENDING) {
            ensureBelowPendingLimit(userId);
            reminder.reopen(now);
        }
        return toResponse(reminder, now);
    }

    /** Filtered, paged list ordered by scheduled time (earliest first). */
    @Transactional(readOnly = true)
    public PageResponse<ReminderResponse> list(UUID userId, ReminderFilter filter, int page, int size) {
        ZoneId zone = zone(filter.timeZone());
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw ReminderException.invalidDateRange();
        }
        String q = normalize(filter.query());
        Specification<Reminder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("scheduledAt"), startOf(filter.from(), zone)));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("scheduledAt"), startOf(filter.to().plusDays(1), zone)));
            }
            if (q != null) {
                String pattern = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("cropName"), "")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Sort sort = Sort.by(Sort.Order.asc("scheduledAt"), Sort.Order.asc("id"));
        Page<Reminder> result = repository.findAll(spec, PageRequest.of(page, size, sort));
        Instant now = now();
        return new PageResponse<>(result.map(r -> toResponse(r, now)).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /** All non-cancelled reminders scheduled on today's date in {@code timeZone}. */
    @Transactional(readOnly = true)
    public List<ReminderResponse> today(UUID userId, String timeZone) {
        ZoneId zone = zone(timeZone);
        Instant now = now();
        LocalDate today = LocalDate.ofInstant(now, zone);
        return repository
                .findByUserIdAndStatusNotAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        userId, ReminderStatus.CANCELLED, startOf(today, zone), startOf(today.plusDays(1), zone),
                        limit())
                .stream().map(r -> toResponse(r, now)).toList();
    }

    /** Pending reminders due from now until {@code days} days ahead. */
    @Transactional(readOnly = true)
    public List<ReminderResponse> upcoming(UUID userId, Integer days) {
        Instant now = now();
        return repository
                .findByUserIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        userId, ReminderStatus.PENDING, now, now.plus(Duration.ofDays(upcomingDays(days))), limit())
                .stream().map(r -> toResponse(r, now)).toList();
    }

    /** Pending reminders whose time has passed, oldest first. */
    @Transactional(readOnly = true)
    public List<ReminderResponse> overdue(UUID userId) {
        Instant now = now();
        return repository.findByUserIdAndStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
                        userId, ReminderStatus.PENDING, now, limit())
                .stream().map(r -> toResponse(r, now)).toList();
    }

    @Transactional(readOnly = true)
    public ReminderSummaryResponse summary(UUID userId, String timeZone) {
        ZoneId zone = zone(timeZone);
        Instant now = now();
        LocalDate today = LocalDate.ofInstant(now, zone);
        int days = properties.upcomingDefaultDays();
        return new ReminderSummaryResponse(
                repository.countByUserIdAndStatus(userId, ReminderStatus.PENDING),
                repository.countByUserIdAndStatusAndScheduledAtBefore(userId, ReminderStatus.PENDING, now),
                repository.countByUserIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(userId,
                        ReminderStatus.PENDING, startOf(today, zone), startOf(today.plusDays(1), zone)),
                repository.countByUserIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(userId,
                        ReminderStatus.PENDING, now, now.plus(Duration.ofDays(days))),
                days,
                repository.countByUserIdAndStatus(userId, ReminderStatus.COMPLETED),
                repository.countByUserIdAndStatus(userId, ReminderStatus.CANCELLED));
    }

    private Reminder find(UUID userId, UUID id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(ReminderNotFoundException::new);
    }

    private void ensureBelowPendingLimit(UUID userId) {
        int limit = properties.maxPendingPerFarmer();
        if (repository.countByUserIdAndStatus(userId, ReminderStatus.PENDING) >= limit) {
            throw ReminderException.limitReached(limit);
        }
    }

    private Instant instantOf(ReminderRequest request) {
        return request.scheduledDate().atTime(request.scheduledTime()).atZone(zone(request.timeZone())).toInstant()
                .truncatedTo(ChronoUnit.MICROS);
    }

    private ReminderDetails toDetails(ReminderRequest request, Instant now, boolean checkTime) {
        Instant scheduledAt = instantOf(request);
        if (checkTime) {
            if (scheduledAt.isBefore(now.minus(properties.pastGrace()))) {
                throw ReminderException.inPast();
            }
            if (scheduledAt.isAfter(now.plus(Duration.ofDays(properties.maxDaysAhead())))) {
                throw ReminderException.tooFarAhead(properties.maxDaysAhead());
            }
        }
        return new ReminderDetails(
                request.title().strip(),
                normalize(request.description()),
                request.category(),
                normalize(request.cropName()),
                scheduledAt,
                zone(request.timeZone()).getId(),
                request.priority() == null ? ReminderPriority.MEDIUM : request.priority());
    }

    /** Validates an IANA region id (e.g. Asia/Kolkata); blank means the configured default. */
    ZoneId zone(String timeZone) {
        String id = normalize(timeZone);
        if (id == null) {
            return ZoneId.of(properties.defaultTimeZone());
        }
        if (!ZoneId.getAvailableZoneIds().contains(id)) {
            throw ReminderException.invalidTimeZone();
        }
        return ZoneId.of(id);
    }

    private int upcomingDays(Integer days) {
        if (days == null) {
            return properties.upcomingDefaultDays();
        }
        return Math.min(Math.max(days, 1), properties.upcomingMaxDays());
    }

    private PageRequest limit() {
        return PageRequest.of(0, properties.listLimit());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private static Instant startOf(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static ReminderResponse toResponse(Reminder r, Instant now) {
        ZonedDateTime local = r.getScheduledAt().atZone(ZoneId.of(r.getTimeZone()));
        return new ReminderResponse(r.getId(), r.getTitle(), r.getDescription(), r.getCategory(), r.getCropName(),
                r.getScheduledAt(), local.toLocalDate(), local.toLocalTime(), r.getTimeZone(), r.getStatus(),
                r.getPriority(), r.isOverdue(now), r.getNotifiedAt(), r.getCompletedAt(), r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
