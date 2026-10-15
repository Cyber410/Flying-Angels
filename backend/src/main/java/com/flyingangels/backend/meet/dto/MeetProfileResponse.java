package com.flyingangels.backend.meet.dto;

import com.flyingangels.backend.event.dto.EventResponse;
import com.flyingangels.backend.meet.Meet;

import java.util.List;

public record MeetProfileResponse(MeetResponse meet, List<EventResponse> events) {

	public static MeetProfileResponse from(Meet meet) {
		return new MeetProfileResponse(MeetResponse.from(meet),
				meet.getEvents().stream().map(EventResponse::from).toList());
	}
}