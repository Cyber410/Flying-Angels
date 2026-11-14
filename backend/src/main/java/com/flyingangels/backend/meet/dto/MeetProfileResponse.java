package com.flyingangels.backend.meet.dto;

import com.flyingangels.backend.event.dto.EventResponse;
import com.flyingangels.backend.meet.Meet;

import java.util.List;

public record MeetProfileResponse(MeetResponse meet, List<EventResponse> events,
        List<com.flyingangels.backend.availability.AvailabilityDtos.RequestView> availabilityRequests) {

	public static MeetProfileResponse from(Meet meet, List<com.flyingangels.backend.availability.AvailabilityDtos.RequestView> requests) {
		return new MeetProfileResponse(MeetResponse.from(meet),
				meet.getEvents().stream().map(EventResponse::from).toList(), requests);
	}
}
