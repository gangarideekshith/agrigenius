package com.agrigenius.recommendation.api;

import com.agrigenius.config.OpenApiConfig;
import com.agrigenius.recommendation.api.dto.CreateRecommendationRequest;
import com.agrigenius.recommendation.api.dto.PageResponse;
import com.agrigenius.recommendation.api.dto.RecommendationResponse;
import com.agrigenius.recommendation.api.dto.RecommendationSummary;
import com.agrigenius.recommendation.application.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI crop recommendations for the signed-in farmer. Requires ROLE_FARMER (see SecurityConfig);
 * the user id always comes from the verified token, so farmers only ever see their own data.
 */
@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Crop recommendations")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class RecommendationController {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Get the top crop candidates for soil and climate inputs",
            description = "Agronomic suitability only. Returns 503 if the recommendation model is unavailable.")
    public ResponseEntity<RecommendationResponse> create(@Valid @RequestBody CreateRecommendationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        RecommendationResponse response = service.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/recommendations/" + response.id())).body(response);
    }

    @GetMapping("/history")
    @Operation(summary = "List the signed-in farmer's recommendations, newest first")
    public PageResponse<RecommendationSummary> history(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size, @AuthenticationPrincipal Jwt jwt) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return service.history(userId(jwt), safePage, safeSize);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the signed-in farmer's recommendations")
    public RecommendationResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.get(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
