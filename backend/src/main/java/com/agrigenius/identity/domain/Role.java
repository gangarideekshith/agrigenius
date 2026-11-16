package com.agrigenius.identity.domain;

/**
 * Platform roles. Only {@link #FARMER} can self-register; other roles are created by
 * administrators in later milestones but are already understood by authorization.
 */
public enum Role {
    FARMER,
    ADMIN,
    AGRICULTURAL_EXPERT,
    BUYER,
    VENDOR
}
