package com.flyingangels.backend.athlete.dto;

import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.event.EventResult;
import com.flyingangels.backend.event.dto.EventResultResponse;

import java.time.LocalDate;
import java.util.List;

public record AthleteProfileResponse(Long id, String firstName, String lastName, LocalDate dateOfBirth,
		String gender, String note, String flyStatus, String flyaStatus, List<EventResultResponse> events) {

	public static AthleteProfileResponse from(Athlete athlete, List<EventResult> centralResults) {
		return new AthleteProfileResponse(athlete.getId(), athlete.getFirstName(), athlete.getLastName(),
				athlete.getDateOfBirth(), athlete.getGender(), athlete.getNote(), athlete.getFlyStatus(),
				athlete.getFlyaStatus(), centralResults.isEmpty()
						? athlete.getEvents().stream().map(event -> EventResultResponse.fromLegacy(athlete, event)).toList()
						: centralResults.stream().map(EventResultResponse::from).toList());
	}
}