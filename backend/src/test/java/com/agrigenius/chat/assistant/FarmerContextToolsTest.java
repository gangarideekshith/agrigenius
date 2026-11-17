package com.agrigenius.chat.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.application.FarmerProfileService;
import com.agrigenius.recommendation.api.dto.PageResponse;
import com.agrigenius.recommendation.api.dto.RecommendationResponse;
import com.agrigenius.recommendation.api.dto.RecommendationSummary;
import com.agrigenius.recommendation.application.RecommendationNotFoundException;
import com.agrigenius.recommendation.application.RecommendationService;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

class FarmerContextToolsTest {

    private final FarmerProfileService profiles = mock(FarmerProfileService.class);
    private final RecommendationService recommendations = mock(RecommendationService.class);
    private final FarmerContextTools tools = new FarmerContextTools(profiles, recommendations);
    private final UUID userId = UUID.randomUUID();
    private final ToolUsageCollector collector = new ToolUsageCollector();

    private ToolContext context() {
        Map<String, Object> map = new HashMap<>();
        map.put(FarmerContextTools.USER_ID_KEY, userId);
        map.put(FarmerContextTools.COLLECTOR_KEY, collector);
        return new ToolContext(map);
    }

    @Test
    void noToolAcceptsAUserIdentifier() {
        List<Method> toolMethods = Arrays.stream(FarmerContextTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class)).toList();

        assertThat(toolMethods).extracting(m -> m.getAnnotation(Tool.class).name())
                .containsExactlyInAnyOrderElementsOf(FarmerContextTools.TOOL_NAMES);
        for (Method m : toolMethods) {
            for (Parameter p : m.getParameters()) {
                assertThat(p.getType()).as(m.getName()).isNotEqualTo(UUID.class);
                String name = p.getName().toLowerCase(Locale.ROOT);
                assertThat(name).as(m.getName() + " parameter " + p.getName())
                        .doesNotContain("user").doesNotContain("farmer").doesNotContain("account").doesNotContain("owner");
            }
        }
    }

    @Test
    void profileExposesOnlyAllowlistedFields() {
        given(profiles.getForAccount(userId)).willReturn(new FarmerProfileResponse(userId, "Ravi Kumar", "+919876543210",
                "ravi@example.com", "te", "Telangana", "Warangal", "Hanamkonda", "Plot 4", "FARMER", Instant.now()));

        Map<String, Object> result = tools.getMyProfile(context());

        assertThat(result).containsOnlyKeys("preferredLanguage", "state", "district", "village")
                .containsEntry("district", "Warangal");
        assertThat(result.toString()).doesNotContain("Ravi").doesNotContain("9876543210").doesNotContain("ravi@");
        assertThat(collector.names()).containsExactly("getMyProfile");
    }

    @Test
    void withoutAnAuthenticatedFarmerToolsReturnNotAvailable() {
        Map<String, Object> result = tools.getMyProfile(new ToolContext(Map.of()));

        assertThat(result).containsEntry("result", "not available");
        verifyNoInteractions(profiles);
    }

    @Test
    void recommendationsLimitIsClampedAndScopedToTheCaller() {
        given(recommendations.history(eq(userId), eq(0), anyInt())).willReturn(new PageResponse<>(List.of(
                new RecommendationSummary(UUID.randomUUID(), "COMPLETED", Instant.now(),
                        new RecommendationResponse.CropRef("rice", "Rice"), "HIGH")), 0, 5, 1, 1));

        Map<String, Object> high = tools.getMyCropRecommendations(99, context());
        tools.getMyCropRecommendations(0, context());

        verify(recommendations).history(userId, 0, 5);
        verify(recommendations).history(userId, 0, 1);
        assertThat(high.toString()).contains("Rice").contains("HIGH");
        assertThat(collector.names()).containsExactly("getMyCropRecommendations");
    }

    @Test
    void anotherFarmersOrUnknownRecommendationIsNotFound() {
        UUID foreign = UUID.randomUUID();
        given(recommendations.get(userId, foreign)).willThrow(new RecommendationNotFoundException());

        assertThat(tools.getMyCropRecommendation(foreign.toString(), context())).containsEntry("result", "not found");
        assertThat(tools.getMyCropRecommendation("not-a-uuid", context())).containsEntry("result", "not found");
        assertThat(tools.getMyCropRecommendation(null, context())).containsEntry("result", "not found");
        verify(recommendations).get(userId, foreign);
    }

    @Test
    void recommendationDetailIsSummarisedWithoutInternals() {
        UUID id = UUID.randomUUID();
        given(recommendations.get(userId, id)).willReturn(new RecommendationResponse(id, "COMPLETED", Instant.now(),
                new RecommendationResponse.ModelSummary("crop-recommendation-rf", "1.0.0", "DEMO_PUBLIC_DATASET"),
                List.of("nitrogenKgHa"),
                List.of(new RecommendationResponse.Candidate(1, new RecommendationResponse.CropRef("rice", "Rice"), 94, 0.94, "HIGH",
                        List.of(), new RecommendationResponse.Economics(false, "n/a"))),
                List.of(new RecommendationResponse.Warning("INPUT_OUTSIDE_TRAINING_RANGE", "rainfallMm")), "advisory"));

        Map<String, Object> result = tools.getMyCropRecommendation(id.toString(), context());

        assertThat(result).containsEntry("dataLabel", "DEMO_PUBLIC_DATASET").containsKeys("candidates", "warnings");
        assertThat(result.toString()).contains("Rice").contains("INPUT_OUTSIDE_TRAINING_RANGE:rainfallMm");
    }

    @Test
    void serviceFailureBecomesASafeString() {
        given(recommendations.history(any(), anyInt(), anyInt())).willThrow(new IllegalStateException("SQL state 08006 at host"));

        Map<String, Object> result = tools.getMyCropRecommendations(3, context());

        assertThat(result).containsOnlyKeys("result").containsEntry("result", "not available");
    }
}
