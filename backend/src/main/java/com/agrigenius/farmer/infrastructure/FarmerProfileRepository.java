package com.agrigenius.farmer.infrastructure;

import com.agrigenius.farmer.domain.FarmerProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmerProfileRepository extends JpaRepository<FarmerProfile, UUID> {
}
