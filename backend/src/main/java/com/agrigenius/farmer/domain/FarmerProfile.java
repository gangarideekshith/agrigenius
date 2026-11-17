package com.agrigenius.farmer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A farmer's profile, one per FARMER account. References the account by id only so the
 * farmer module does not depend on identity's persistence model.
 */
@Entity
@Table(name = "farmer_profiles")
public class FarmerProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "preferred_language", nullable = false, length = 5)
    private String preferredLanguage;

    @Column(name = "state", length = 60)
    private String state;

    @Column(name = "district", length = 60)
    private String district;

    @Column(name = "village", length = 100)
    private String village;

    @Column(name = "address_line", length = 200)
    private String addressLine;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FarmerProfile() {
        // for JPA
    }

    public FarmerProfile(UUID userId, String fullName, PreferredLanguage preferredLanguage,
            String state, String district, String village, Instant now) {
        this.userId = Objects.requireNonNull(userId);
        this.fullName = Objects.requireNonNull(fullName);
        this.preferredLanguage = preferredLanguage.code();
        this.state = state;
        this.district = district;
        this.village = village;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Applies an owner-requested profile edit. Values are already validated and trimmed. */
    public void update(String fullName, PreferredLanguage preferredLanguage, String state, String district,
            String village, String addressLine) {
        this.fullName = Objects.requireNonNull(fullName);
        this.preferredLanguage = preferredLanguage.code();
        this.state = state;
        this.district = district;
        this.village = village;
        this.addressLine = addressLine;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public String getState() {
        return state;
    }

    public String getDistrict() {
        return district;
    }

    public String getVillage() {
        return village;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "FarmerProfile[userId=" + userId + "]";
    }
}
