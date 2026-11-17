package com.agrigenius.recommendation.infrastructure;

import com.agrigenius.recommendation.domain.Crop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropRepository extends JpaRepository<Crop, String> {
}
