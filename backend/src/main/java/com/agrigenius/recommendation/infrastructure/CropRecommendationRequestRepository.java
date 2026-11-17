package com.agrigenius.recommendation.infrastructure;

import com.agrigenius.recommendation.domain.CropRecommendationRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropRecommendationRequestRepository extends JpaRepository<CropRecommendationRequest, UUID> {

    /** Ownership-scoped lookup: another user's id behaves exactly like an unknown id. */
    Optional<CropRecommendationRequest> findByIdAndUserId(UUID id, UUID userId);

    Page<CropRecommendationRequest> findByUserIdOrderByRequestedAtDesc(UUID userId, Pageable pageable);
}
