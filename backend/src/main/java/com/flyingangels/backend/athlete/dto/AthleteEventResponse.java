package com.flyingangels.backend.athlete.dto;

import com.flyingangels.backend.athlete.AthleteEvent;

public record AthleteEventResponse(String eventName, String result) {

	public static AthleteEventResponse from(AthleteEvent event) {
		return new AthleteEventResponse(event.getEventName(), event.getResult());
	}
}