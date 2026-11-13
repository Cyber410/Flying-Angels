package com.flyingangels.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks the Sprint 3 fixture itself, with plain SQL and no production code. If the schema changes and the
 * seed breaks, this test fails first, so Sprint 3 feature tests don't fail for confusing reasons.
 *
 * The SQL here is also an independent "answer key": a personal-best or ranking test can compare the API's answer
 * with what these queries prove the data contains.
 */
class Sprint3FixtureTest extends DatabaseIntegrationTest {

	private static final String VALID = "status = 'VALID'"; // NT/PENDING excluded: pending team decision (T138)

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Sprint3Fixture.Seeded data;

	@BeforeEach
	void seed() {
		data = Sprint3Fixture.seed(jdbcTemplate, LocalDate.now());
	}

	@Test
	void seedsTheExpectedRowCounts() {
		assertThat(count("SELECT COUNT(*) FROM athletes")).isEqualTo(9);
		assertThat(count("SELECT COUNT(*) FROM meets")).isEqualTo(6);
		assertThat(count("SELECT COUNT(*) FROM events")).isEqualTo(12);
		assertThat(count("SELECT COUNT(*) FROM event_results")).isEqualTo(19);
		assertThat(count("SELECT COUNT(*) FROM event_participations")).isEqualTo(28);
	}

	@Test
	void personalBestsIgnoreInvalidResults() {
		long alex = data.athletes().alex();
		assertThat(best("MIN", alex, data.categories().m100())).isEqualByComparingTo(Sprint3Fixture.Expected.ALEX_100M_PB);
		assertThat(best("MAX", alex, data.categories().longJump()))
				.isEqualByComparingTo(Sprint3Fixture.Expected.ALEX_LONG_JUMP_PB);

		// Alex's PB was set at Summer Invitational
		String meet = jdbcTemplate.queryForObject("""
				SELECT m.name FROM event_results r JOIN events e ON e.id = r.event_id JOIN meets m ON m.id = e.meet_id
				WHERE r.athlete_id = ? AND r.category_id = ? AND r.status = 'VALID' AND r.score = 12.96""",
				String.class, alex, data.categories().m100());
		assertThat(meet).isEqualTo("Summer Invitational");

		// the DQ 12.50 and FOUL 4.80 exist, with better numbers, so a test that ignores status would fail
		assertThat(count("SELECT COUNT(*) FROM event_results WHERE athlete_id = ? AND status IN ('DQ','FOUL')", alex))
				.isEqualTo(2);
	}

	@Test
	void progressionIsInMeetDateOrder() {
		List<String> marks = jdbcTemplate.queryForList("""
				SELECT r.result FROM event_results r JOIN events e ON e.id = r.event_id JOIN meets m ON m.id = e.meet_id
				WHERE r.athlete_id = ? AND r.category_id = ? AND r.status = 'VALID' ORDER BY m.start_date""",
				String.class, data.athletes().alex(), data.categories().m100());
		assertThat(marks).isEqualTo(Sprint3Fixture.Expected.ALEX_100M_PROGRESSION);
	}

	@Test
	void femaleHundredMetreRankingHasATie() {
		List<String> ranking = jdbcTemplate.queryForList("""
				SELECT CONCAT(RANK() OVER (ORDER BY best), ' ', first_name, ' ', last_name, ' ', best)
				FROM (SELECT a.first_name, a.last_name, MIN(r.score) AS best
				      FROM event_results r JOIN athletes a ON a.id = r.athlete_id
				      WHERE r.category_id = ? AND r.status = 'VALID' AND a.gender = 'Female'
				      GROUP BY a.id, a.first_name, a.last_name) bests
				ORDER BY best, last_name""", String.class, data.categories().m100());
		// scores come back with 4 decimal places (column scale 4), so trim them for the comparison
		assertThat(ranking.stream().map(row -> row.replaceAll("(\\.\\d\\d)00$", "$1")).toList())
				.isEqualTo(Sprint3Fixture.Expected.FEMALE_100M_RANKING);
	}

	@Test
	void ontarioIndoorHasAMultiEventAthleteAndLateSeasonOpenIsEmpty() {
		long ontario = data.meets().ontarioIndoor();
		String perMeet = "FROM event_participations p JOIN events e ON e.id = p.event_id WHERE e.meet_id = ?";
		assertThat(count("SELECT COUNT(*) " + perMeet, ontario))
				.isEqualTo(Sprint3Fixture.Expected.ONTARIO_INDOOR_ASSIGNMENTS);
		assertThat(count("SELECT COUNT(DISTINCT p.athlete_id) " + perMeet, ontario))
				.isEqualTo(Sprint3Fixture.Expected.ONTARIO_INDOOR_DISTINCT_ATHLETES);
		assertThat(count("SELECT COUNT(*) " + perMeet, data.meets().lateSeasonOpen()))
				.isEqualTo(Sprint3Fixture.Expected.LATE_SEASON_OPEN_ASSIGNMENTS);
		assertThat(Sprint3Fixture.Expected.PLANNED_AVAILABILITY)
				.hasSize(Sprint3Fixture.Expected.ONTARIO_INDOOR_DISTINCT_ATHLETES);
	}

	@Test
	void summerInvitationalResultRowsIncludeRepeatedPerformances() {
		assertThat(count("SELECT COUNT(*) FROM event_results r JOIN events e ON e.id = r.event_id WHERE e.meet_id = ?",
				data.meets().summerInvitational())).isEqualTo(Sprint3Fixture.Expected.SUMMER_INVITATIONAL_RESULT_ROWS);
		assertThat(count("SELECT COUNT(*) FROM event_results WHERE athlete_id = ? AND event_id = ?",
				data.athletes().alex(), data.eventId(data.meets().summerInvitational(), data.categories().m100())))
				.isEqualTo(2);
	}

	@Test
	void meetDatesCoverPastInProgressAndUpcoming() {
		LocalDate today = LocalDate.now();
		Map<String, Object> provincials = jdbcTemplate.queryForMap(
				"SELECT start_date, end_date FROM meets WHERE id = ?", data.meets().provincials());
		assertThat(((java.sql.Date) provincials.get("start_date")).toLocalDate()).isBefore(today);
		assertThat(((java.sql.Date) provincials.get("end_date")).toLocalDate()).isAfter(today);
		assertThat(count("SELECT COUNT(*) FROM meets WHERE end_date < ?", today)).isEqualTo(3);
		assertThat(count("SELECT COUNT(*) FROM meets WHERE start_date > ?", today)).isEqualTo(2);
	}

	@Test
	void csvEdgeCaseTextIsStoredExactly() {
		assertThat(count("SELECT COUNT(*) FROM athletes WHERE first_name = 'Émilie'")).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM athletes WHERE first_name = 'Sam \"Rocket\"'")).isEqualTo(1);
		assertThat(count("SELECT COUNT(*) FROM athletes WHERE last_name = '=1+2'")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT location FROM meets WHERE id = ?", String.class,
				data.meets().ontarioIndoor())).contains(",").contains("\n");
	}

	private BigDecimal best(String minOrMax, long athleteId, long categoryId) {
		return jdbcTemplate.queryForObject("SELECT " + minOrMax + "(score) FROM event_results WHERE athlete_id = ? "
				+ "AND category_id = ? AND " + VALID, BigDecimal.class, athleteId, categoryId);
	}

	private long count(String sql, Object... args) {
		Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
		return result == null ? 0 : result;
	}
}
