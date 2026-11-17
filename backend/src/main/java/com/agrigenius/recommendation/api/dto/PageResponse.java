package com.agrigenius.recommendation.api.dto;

import java.util.List;

/** A page of results. {@code page} is zero-based. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
