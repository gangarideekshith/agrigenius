package com.agrigenius.reminder.api;

import com.agrigenius.config.OpenApiConfig;
import com.agrigenius.recommendation.api.dto.PageResponse;
import com.agrigenius.reminder.api.dto.ReminderRequest;
import com.agrigenius.reminder.api.dto.ReminderResponse;
import com.agrigenius.reminder.api.dto.ReminderSummaryResponse;
import com.agrigenius.reminder.application.ReminderFilter;
import com.agrigenius.reminder.application.ReminderService;
import com.agrigenius.reminder.domain.ReminderCategory;
import com.agrigenius.reminder.domain.ReminderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Farm task reminders for the signed-in farmer (ROLE_FARMER, see SecurityConfig). The owner is
 * always the token subject; ids of other farmers' reminders return 404.
 */
@RestController
@RequestMapping("/api/reminders")
@Validated
@Tag(name = "Reminders")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class ReminderController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    private final ReminderService service;

    public ReminderController(ReminderService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a reminder", description = "The time must be in the future.")
    public ResponseEntity<ReminderResponse> create(@Valid @RequestBody ReminderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ReminderResponse response = service.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/reminders/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "List reminders with optional filters, earliest first",
            description = "from/to are inclusive local dates in timeZone; q searches title, crop and description.")
    public PageResponse<ReminderResponse> list(
            @RequestParam(required = false) ReminderStatus status,
            @RequestParam(required = false) ReminderCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 40) String timeZone,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @AuthenticationPrincipal Jwt jwt) {
        ReminderFilter filter = new ReminderFilter(status, category, from, to, q, timeZone);
        return service.list(userId(jwt), filter, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @GetMapping("/today")
    @Operation(summary = "Today's reminders (not cancelled) in the given time zone")
    public List<ReminderResponse> today(@RequestParam(required = false) @Size(max = 40) String timeZone,
            @AuthenticationPrincipal Jwt jwt) {
        return service.today(userId(jwt), timeZone);
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Pending reminders due in the next N days (default 7, max 60)")
    public List<ReminderResponse> upcoming(@RequestParam(required = false) Integer days,
            @AuthenticationPrincipal Jwt jwt) {
        return service.upcoming(userId(jwt), days);
    }

    @GetMapping("/overdue")
    @Operation(summary = "Pending reminders whose time has passed")
    public List<ReminderResponse> overdue(@AuthenticationPrincipal Jwt jwt) {
        return service.overdue(userId(jwt));
    }

    @GetMapping("/summary")
    @Operation(summary = "Reminder counts for the dashboard")
    public ReminderSummaryResponse summary(@RequestParam(required = false) @Size(max = 40) String timeZone,
            @AuthenticationPrincipal Jwt jwt) {
        return service.summary(userId(jwt), timeZone);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one reminder")
    public ReminderResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(userId(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace a pending reminder's details")
    public ReminderResponse update(@PathVariable UUID id, @Valid @RequestBody ReminderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return service.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a reminder and its notifications")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Mark a pending reminder as done")
    public ReminderResponse complete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.complete(userId(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending reminder")
    public ReminderResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.cancel(userId(jwt), id);
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Move a completed or cancelled reminder back to pending")
    public ReminderResponse reopen(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.reopen(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
