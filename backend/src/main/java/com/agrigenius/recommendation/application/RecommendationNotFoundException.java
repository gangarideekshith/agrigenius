package com.agrigenius.recommendation.application;

import com.agrigenius.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Unknown id, or an id belonging to another user (deliberately indistinguishable). */
public class RecommendationNotFoundException extends ApiException {

    public RecommendationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", "Recommendation not found.");
    }
}
