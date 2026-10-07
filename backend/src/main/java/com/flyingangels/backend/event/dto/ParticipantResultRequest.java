package com.flyingangels.backend.event.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ParticipantResultRequest(
		@NotBlank(message = "Result is required")
		@Size(max = 50, message = "Result must be 50 characters or fewer") String result,
		@NotNull(message = "Score is required")
		@DecimalMin(value = "0.0", inclusive = true, message = "Score cannot be negative") BigDecimal score) {
}