package me.kpavlov.kt.schema.apt.integration.type;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/**
 * Simple test model to verify Java enum schema generation.
 */
@JsonClassDescription("Current lifecycle status of an entity.")
public enum Status {
    ACTIVE, INACTIVE, PENDING
}
