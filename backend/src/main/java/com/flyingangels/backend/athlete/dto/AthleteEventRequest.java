package com.flyingangels.backend.athlete.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AthleteEventRequest(
		@NotBlank(message = "Event name is required")
		@Size(max = 100, message = "Event name must be 100 characters or fewer") String eventName,
		@NotBlank(message = "Event result is required")
		@Size(max = 50, message = "Event result must be 50 characters or fewer") String result) {
}