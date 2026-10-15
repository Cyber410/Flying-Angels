package com.flyingangels.backend.athlete.dto;

import com.flyingangels.backend.athlete.Athlete;

import java.time.LocalDate;

public record AthleteSummaryResponse(Long id, String firstName, String lastName, LocalDate dateOfBirth,
		String gender, String flyStatus, String flyaStatus) {

	public static AthleteSummaryResponse from(Athlete athlete) {
		return new AthleteSummaryResponse(athlete.getId(), athlete.getFirstName(), athlete.getLastName(),
				athlete.getDateOfBirth(), athlete.getGender(), athlete.getFlyStatus(), athlete.getFlyaStatus());
	}
}