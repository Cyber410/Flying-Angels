package com.flyingangels.backend.event.dto;

import com.flyingangels.backend.event.EventResult;
import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.athlete.AthleteEvent;

import java.math.BigDecimal;

public record EventResultResponse(Long resultId, Long athleteId, String firstName, String lastName,
		Long categoryId, String categoryName, String result, BigDecimal score) {

	public static EventResultResponse from(EventResult eventResult) {
		return new EventResultResponse(eventResult.getId(), eventResult.getAthlete().getId(),
				eventResult.getAthlete().getFirstName(), eventResult.getAthlete().getLastName(),
				eventResult.getCategory().getId(), eventResult.getCategory().getName(), eventResult.getResult(),
				eventResult.getScore());
	}

	public static EventResultResponse fromLegacy(Athlete athlete, AthleteEvent event) {
		return new EventResultResponse(null, athlete.getId(), athlete.getFirstName(), athlete.getLastName(),
				null, event.getEventName(), event.getResult(), null);
	}
}