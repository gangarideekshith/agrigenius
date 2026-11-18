package com.agrigenius.notification.api;

import com.agrigenius.config.OpenApiConfig;
import com.agrigenius.notification.api.dto.MarkAllReadResponse;
import com.agrigenius.notification.api.dto.NotificationResponse;
import com.agrigenius.notification.api.dto.UnreadCountResponse;
import com.agrigenius.notification.application.NotificationService;
import com.agrigenius.recommendation.api.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in farmer's in-app notifications (ROLE_FARMER, see SecurityConfig). */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class NotificationController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List notifications, newest first")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @AuthenticationPrincipal Jwt jwt) {
        return service.list(userId(jwt), unreadOnly, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications (polled by the app)")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(service.unreadCount(userId(jwt)));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read (idempotent)")
    public NotificationResponse markRead(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.markRead(userId(jwt), id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark every unread notification as read")
    public MarkAllReadResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return new MarkAllReadResponse(service.markAllRead(userId(jwt)));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
