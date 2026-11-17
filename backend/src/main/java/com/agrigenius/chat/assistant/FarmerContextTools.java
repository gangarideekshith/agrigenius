package com.agrigenius.chat.assistant;

import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.application.FarmerProfileService;
import com.agrigenius.recommendation.api.dto.PageResponse;
import com.agrigenius.recommendation.api.dto.RecommendationResponse;
import com.agrigenius.recommendation.api.dto.RecommendationSummary;
import com.agrigenius.recommendation.application.RecommendationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Read-only tools the model may call (ADR-0007).
 *
 * <p>No tool takes a user id: the farmer is identified ONLY by {@link #USER_ID_KEY} in the Spring AI
 * {@link ToolContext}, which the assistant sets from the verified JWT. A prompt-injected request for
 * "another farmer" therefore has nothing to pass. Results use field allowlists (no name, mobile or
 * email) and errors become short safe strings.
 */
public class FarmerContextTools {

    static final String USER_ID_KEY = "agrigenius.userId";
    static final String COLLECTOR_KEY = "agrigenius.toolUsage";
    static final List<String> TOOL_NAMES = List.of("getMyProfile", "getMyCropRecommendations", "getMyCropRecommendation");

    private static final Logger log = LoggerFactory.getLogger(FarmerContextTools.class);
    private static final Map<String, Object> NOT_FOUND = Map.of("result", "not found");
    private static final Map<String, Object> NOT_AVAILABLE = Map.of("result", "not available");

    private final FarmerProfileService profiles;
    private final RecommendationService recommendations;

    public FarmerContextTools(FarmerProfileService profiles, RecommendationService recommendations) {
        this.profiles = profiles;
        this.recommendations = recommendations;
    }

    @Tool(name = "getMyProfile",
            description = "Get the current farmer's preferred language and location (state, district, village). "
                    + "Contains no contact details.")
    public Map<String, Object> getMyProfile(ToolContext context) {
        record("getMyProfile", context);
        try {
            FarmerProfileResponse p = profiles.getForAccount(userId(context));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("preferredLanguage", p.preferredLanguage());
            out.put("state", p.state());
            out.put("district", p.district());
            out.put("village", p.village());
            return out;
        } catch (RuntimeException ex) {
            log.warn("Tool getMyProfile failed: {}", ex.getClass().getSimpleName());
            return NOT_AVAILABLE;
        }
    }

    @Tool(name = "getMyCropRecommendations",
            description = "List the current farmer's most recent crop recommendations (newest first), "
                    + "with each request's status and top crop. Results come from a demonstration model.")
    public Map<String, Object> getMyCropRecommendations(
            @ToolParam(description = "How many recent recommendations to return, 1 to 5") int limit,
            ToolContext context) {
        record("getMyCropRecommendations", context);
        int size = Math.max(1, Math.min(5, limit));
        try {
            PageResponse<RecommendationSummary> page = recommendations.history(userId(context), 0, size);
            List<Map<String, Object>> items = page.items().stream().map(FarmerContextTools::summary).toList();
            return Map.of("items", items);
        } catch (RuntimeException ex) {
            log.warn("Tool getMyCropRecommendations failed: {}", ex.getClass().getSimpleName());
            return NOT_AVAILABLE;
        }
    }

    @Tool(name = "getMyCropRecommendation",
            description = "Get one of the current farmer's own crop recommendations by its id: "
                    + "ranked crop candidates with confidence and any input warnings. Demonstration model results.")
    public Map<String, Object> getMyCropRecommendation(
            @ToolParam(description = "The recommendation id (a UUID) from getMyCropRecommendations") String recommendationId,
            ToolContext context) {
        record("getMyCropRecommendation", context);
        UUID id;
        try {
            id = UUID.fromString(recommendationId == null ? "" : recommendationId.trim());
        } catch (IllegalArgumentException ex) {
            return NOT_FOUND;
        }
        try {
            return detail(recommendations.get(userId(context), id));
        } catch (com.agrigenius.common.error.ApiException ex) {
            // Owner-scoped lookup: another farmer's id and an unknown id look the same.
            return NOT_FOUND;
        } catch (RuntimeException ex) {
            log.warn("Tool getMyCropRecommendation failed: {}", ex.getClass().getSimpleName());
            return NOT_AVAILABLE;
        }
    }

    private static Map<String, Object> summary(RecommendationSummary s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id().toString());
        out.put("status", s.status());
        out.put("requestedAt", String.valueOf(s.requestedAt()));
        out.put("topCrop", s.topCrop() == null ? null : s.topCrop().name());
        out.put("topConfidence", s.topConfidence());
        return out;
    }

    private static Map<String, Object> detail(RecommendationResponse r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", r.id().toString());
        out.put("status", r.status());
        out.put("requestedAt", String.valueOf(r.requestedAt()));
        out.put("dataLabel", r.model() == null ? null : r.model().dataLabel());
        out.put("candidates", r.candidates() == null ? List.of() : r.candidates().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("rank", c.rank());
                    m.put("crop", c.crop() == null ? null : c.crop().name());
                    m.put("suitabilityScore", c.suitabilityScore());
                    m.put("confidence", c.confidence());
                    return m;
                }).toList());
        out.put("warnings", r.warnings() == null ? List.of() : r.warnings().stream()
                .map(w -> w.code() + (w.feature() == null ? "" : ":" + w.feature())).toList());
        return out;
    }

    private static UUID userId(ToolContext context) {
        Object value = context == null ? null : context.getContext().get(USER_ID_KEY);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        throw new IllegalStateException("Tool called without an authenticated farmer");
    }

    private static void record(String name, ToolContext context) {
        if (context != null && context.getContext().get(COLLECTOR_KEY) instanceof ToolUsageCollector collector) {
            collector.record(name);
        }
    }
}
