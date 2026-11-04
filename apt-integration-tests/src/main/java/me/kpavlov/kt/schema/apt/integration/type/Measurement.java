package me.kpavlov.kt.schema.apt.integration.type;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Test model to verify built-in bounds and formats of small, character, UUID and date types in APT.
 */
public record Measurement(
    byte level,
    short code,
    char grade,
    Byte boxedLevel,
    Short boxedCode,
    Character boxedGrade,
    UUID id,
    Instant at,
    LocalDate day,
    LocalTime clock,
    LocalDateTime local
) {
}
