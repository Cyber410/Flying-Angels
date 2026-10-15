package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.event.RankingOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record EventRequest(
		@NotBlank(message = "Event name is required")
		@Size(max = 150, message = "Event name must be 150 characters or fewer") String name,
		@NotNull(message = "Event date is required")
		LocalDate eventDate,
		@Size(max = 150, message = "Location must be 150 characters or fewer") String location,
		@Size(max = 100, message = "Event type must be 100 characters or fewer") String eventType,
		@Pattern(regexp = "https?://.+", message = "External URL must be a valid HTTP or HTTPS URL")
		@Size(max = 500, message = "External URL must be 500 characters or fewer") String externalUrl,
		@NotNull(message = "Ranking order is required") RankingOrder rankingOrder,
		@NotNull(message = "Meet ID is required") Long meetId,
		Long categoryId,
		@Size(max = 150, message = "New category name must be 150 characters or fewer") String categoryName) {
}