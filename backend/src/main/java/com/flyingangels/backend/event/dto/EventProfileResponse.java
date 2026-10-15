package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.event.Event;

import java.util.List;

public record EventProfileResponse(EventResponse event, List<EventResultResponse> results) {

	public static EventProfileResponse from(Event event) {
		return new EventProfileResponse(EventResponse.from(event),
				event.getResults().stream().map(EventResultResponse::from).toList());
	}
}