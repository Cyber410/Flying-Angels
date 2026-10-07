package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.event.Event;
import com.flyingangels.backend.event.RankingOrder;

import java.time.LocalDate;

public record EventResponse(Long id, String name, LocalDate eventDate, String location, String eventType,
		String externalUrl, Long meetId, String meetName, EventCategoryResponse category,
		RankingOrder rankingOrder, int participantCount) {

	public static EventResponse from(Event event) {
		return new EventResponse(event.getId(), event.getName(), event.getEventDate(), event.getLocation(),
				event.getEventType(), event.getExternalUrl(), event.getMeet().getId(), event.getMeet().getName(),
				EventCategoryResponse.from(event.getCategory()),
				event.getRankingOrder(), event.getResults().size());
	}
}