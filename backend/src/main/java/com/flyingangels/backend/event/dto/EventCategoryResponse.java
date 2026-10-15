package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.event.EventCategory;
import com.flyingangels.backend.event.RankingOrder;

public record EventCategoryResponse(Long id, String name, RankingOrder rankingOrder) {

	public static EventCategoryResponse from(EventCategory category) {
		return new EventCategoryResponse(category.getId(), category.getName(), category.getRankingOrder());
	}
}