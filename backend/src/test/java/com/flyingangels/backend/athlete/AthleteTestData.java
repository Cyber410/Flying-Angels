package com.flyingangels.backend.athlete;

import java.time.LocalDate;
import java.util.List;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * Builds the sample athletes shared by the Athlete tests.
 * Athlete has no public ID setter (the database generates IDs), so the ID is set by reflection here.
 * Values follow the real import data: gender "Female"/"Male", team status "In Roster" or "" (not on that team).
 */
final class AthleteTestData {

	static final String IN_ROSTER = "In Roster";
	static final String NOT_ON_TEAM = "";

	private AthleteTestData() {
	}

	/** eventsAndResults are pairs: event name, result, event name, result, ... */
	static Athlete athlete(Long id, String firstName, String lastName, LocalDate dateOfBirth, String gender,
			String flyStatus, String flyaStatus, String note, String... eventsAndResults) {
		Athlete athlete = new Athlete(firstName, lastName, note, flyStatus, flyaStatus, dateOfBirth, gender);
		ReflectionTestUtils.setField(athlete, "id", id);
		for (int i = 0; i < eventsAndResults.length; i += 2) {
			athlete.addEvent(eventsAndResults[i], eventsAndResults[i + 1]);
		}
		return athlete;
	}

	/** FLY team, two events. */
	static Athlete janeDoe() {
		return athlete(1L, "Jane", "Doe", LocalDate.of(2004, 3, 15), "Female", IN_ROSTER, NOT_ON_TEAM,
				"Needs review", "100M", "12.40", "LONG JUMP", "4.85");
	}

	/** FLYA team, one event. */
	static Athlete janeSmith() {
		return athlete(2L, "Jane", "Smith", LocalDate.of(2003, 7, 2), "Female", NOT_ON_TEAM, IN_ROSTER,
				"", "200M", "26.10");
	}

	/** On both teams, one event. */
	static Athlete janetKing() {
		return athlete(3L, "Janet", "King", LocalDate.of(2005, 11, 20), "Female", IN_ROSTER, IN_ROSTER,
				"", "HIGH JUMP", "1.55");
	}

	/** FLYA team, male, no events. */
	static Athlete markSmith() {
		return athlete(4L, "Mark", "Smith", LocalDate.of(2002, 1, 9), "Male", NOT_ON_TEAM, IN_ROSTER, "");
	}

	static List<Athlete> roster() {
		return List.of(janeDoe(), janeSmith(), janetKing(), markSmith());
	}
}
