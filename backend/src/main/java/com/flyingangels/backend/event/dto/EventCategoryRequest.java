package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.event.RankingOrder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventCategoryRequest(
		@NotBlank(message = "Event category name is required")
		@Size(max = 150, message = "Event category name must be 150 characters or fewer") String name,
		@NotNull(message = "Ranking order is required") RankingOrder rankingOrder) {
}