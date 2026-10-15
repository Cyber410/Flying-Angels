package com.flyingangels.backend.meet.dto;

import com.flyingangels.backend.meet.Meet;

import java.time.LocalDate;

public record MeetResponse(Long id, String name, LocalDate meetDate, String location, String externalUrl,
		int eventCount) {

	public static MeetResponse from(Meet meet) {
		return new MeetResponse(meet.getId(), meet.getName(), meet.getMeetDate(), meet.getLocation(),
				meet.getExternalUrl(), meet.getEvents().size());
	}
}