package com.flyingangels.backend.event.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EventParticipantRequest(
		@NotNull(message = "Athlete ID is required")
		@Positive(message = "Athlete ID must be positive") Long athleteId) {
}