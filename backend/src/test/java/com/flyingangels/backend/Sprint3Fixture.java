package com.flyingangels.backend;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

/**
 * Shared Sprint 3 test data for UC-08 (personal bests), UC-09 (rankings), UC-11 (meet views) and UC-12 (CSV
 * exports), and the data behind the end-to-end demo in the Sprint 3 Use Cases PDF (p.10).
 * Test case IDs (T1xx) refer to claude/sprint-3-test-cases-uc07-uc12.md in the project.
 *
 * <p>How it is built, and why:
 * <ul>
 * <li>Only tables that already exist on development (671a9ae) are seeded, and only with SQL, the same way
 * MeetContractTestSupport does it. No production class is used, so this keeps compiling while Sprint 3 classes are
 * added or renamed. If a column is renamed, the seed fails with a clear SQL error instead of a silent wrong value.</li>
 * <li>Availability, users and athlete accounts are NOT seeded: those tables do not exist yet. The intended
 * availability statuses are listed in {@link Expected#PLANNED_AVAILABILITY} so UC-10/11/12 tests can add them
 * once the table exists.</li>
 * <li>All meet dates are relative to the {@code today} you pass in, so Upcoming / Past / in-progress tests are
 * deterministic. Use the same "today" the code under test uses (agree the meet timezone first, see T163/T171).</li>
 * </ul>
 *
 * <p>Usage, in a test that extends {@link DatabaseIntegrationTest} (its @BeforeEach empties the tables first,
 * and JUnit runs the superclass @BeforeEach before yours):
 * <pre>
 * &#64;Autowired JdbcTemplate jdbcTemplate;
 * Sprint3Fixture.Seeded data;
 *
 * &#64;BeforeEach
 * void seed() {
 *     data = Sprint3Fixture.seed(jdbcTemplate, LocalDate.now());
 * }
 * </pre>
 *
 * <p>All names and places are fictional.
 */
public final class Sprint3Fixture {

	public static final String FEMALE = "Female";
	public static final String MALE = "Male";

	/** IDs of everything seeded. Event IDs are looked up by meet + category with {@link #eventId}. */
	public record Seeded(Athletes athletes, Meets meets, Categories categories, Map<String, Long> eventIds) {
		public long eventId(long meetId, long categoryId) {
			Long id = eventIds.get(meetId + ":" + categoryId);
			if (id == null) {
				throw new IllegalArgumentException("Sprint3Fixture has no event for meet " + meetId
						+ " and category " + categoryId);
			}
			return id;
		}
	}

	public record Athletes(long alex, long jordan, long morgan, long casey, long devon, long riley,
			long emilie, long sam, long quinn) {
	}

	public record Meets(long winterOpener, long springClassic, long summerInvitational, long provincials,
			long ontarioIndoor, long lateSeasonOpen) {
	}

	public record Categories(long m100, long m200, long longJump) {
	}

	/**
	 * What the fixture is built to produce. These are the expected answers for the Sprint 3 tests, worked out by
	 * hand from the seed below. Sprint3FixtureTest re-checks the main ones with plain SQL.
	 */
	public static final class Expected {

		private Expected() {
		}

		// ----- UC-08 personal bests -----

		/** T129: Alex 100M PB = 12.96 at Summer Invitational. Her DQ 12.50 in the same meet must be ignored (T137). */
		public static final String ALEX_100M_PB = "12.96";
		/** T130: Alex Long Jump PB = 4.35 at Spring Classic. Her FOUL 4.80 at Summer Invitational must be ignored. */
		public static final String ALEX_LONG_JUMP_PB = "4.35";
		/** T133: Alex 100M valid results in meet-date order: improving over time. */
		public static final List<String> ALEX_100M_PROGRESSION = List.of("13.40", "13.10", "12.96");
		/** T134 (rule pending): Morgan has two equal 100M bests, 12.96 at Spring Classic and at Summer Invitational. */
		public static final String MORGAN_100M_EQUAL_BEST = "12.96";
		/** T139: Morgan has exactly one Long Jump result (3.95), too few points for a chart. */
		public static final String MORGAN_LONG_JUMP_ONLY_RESULT = "3.95";
		/** T137: Riley's only result is a 200M DNF, so she has no valid performance in any event. */
		public static final String RILEY_ONLY_STATUS = "DNF";
		// T136: Emilie, Sam and Quinn have no results at all.

		// ----- UC-09 rankings -----

		/**
		 * T143/T145/T146: Female 100M ranking, valid results only. Tie gives 1, 2, 2, 4.
		 * ASSUMES NT and PENDING are excluded (T138, not decided). If PENDING counted, Casey's 12.00 would rank 1.
		 */
		public static final List<String> FEMALE_100M_RANKING = List.of(
				"1 Jordan Ellis 12.74",
				"2 Alex Carter 12.96",
				"2 Morgan Lee 12.96",
				"4 Casey Nguyen 13.18");
		/** T146: with no gender filter, Devon (Male, 12.20) ranks 1 and everyone above moves down one place. */
		public static final String DEVON_100M_PB = "12.20";

		// ----- UC-11 meet views (relative to "today") -----

		// T169: past = Winter Opener, Spring Classic, Summer Invitational (most recent first in the Past tab).
		// T170/T171: Provincials runs today-1 to today+1, so it must NOT be in Past (where it goes is pending).
		// Upcoming = Ontario Indoor (today+16) and Late Season Open (today+45).

		// ----- UC-11 / UC-12 counts for Ontario Indoor Invitational -----

		/** T184/T185: one registration row per athlete-event assignment. Alex is in 100M and 200M. */
		public static final int ONTARIO_INDOOR_ASSIGNMENTS = 9;
		/** T172: each athlete counted once in availability summaries. */
		public static final int ONTARIO_INDOOR_DISTINCT_ATHLETES = 8;
		/** T186/T187: Summer Invitational has 8 recorded performances; Alex has 2 rows in 100M (12.96 and DQ). */
		public static final int SUMMER_INVITATIONAL_RESULT_ROWS = 8;
		/** T193/T194: Late Season Open has an event but no assignments and no results. */
		public static final int LATE_SEASON_OPEN_ASSIGNMENTS = 0;

		/**
		 * Not seeded (no availability table yet). Intended statuses for Ontario Indoor, so counts are
		 * 2 Available, 1 Unavailable, 5 Pending = 8 athletes (T172, T185). Status name Pending vs Awaiting Response
		 * is still undecided (T156).
		 */
		public static final Map<String, String> PLANNED_AVAILABILITY = Map.of(
				"Alex Carter", "AVAILABLE",
				"Casey Nguyen", "AVAILABLE",
				"Morgan Lee", "UNAVAILABLE",
				"Jordan Ellis", "PENDING",
				"Riley Brooks", "PENDING",
				"Émilie Tremblay", "PENDING",
				"Sam \"Rocket\" Patel", "PENDING",
				"Quinn =1+2", "PENDING");
	}

	private final JdbcTemplate jdbc;
	private final Map<String, Long> eventIds = new HashMap<>();
	private final Set<String> participations = new HashSet<>();

	private Sprint3Fixture(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Seeds everything and returns the IDs. Expects empty tables (DatabaseIntegrationTest clears them). */
	public static Seeded seed(JdbcTemplate jdbc, LocalDate today) {
		return new Sprint3Fixture(jdbc).seedAll(today);
	}

	private Seeded seedAll(LocalDate today) {
		// ----- Event categories: two timed (lower is better), one measured (higher is better) -----
		Categories categories = new Categories(
				category("100M", "ASC", "SECONDS"),
				category("200M", "ASC", "SECONDS"),
				category("LONG JUMP", "DESC", "METRES"));

		// ----- Athletes -----
		Athletes athletes = new Athletes(
				athlete("Alex", "Carter", LocalDate.of(2012, 4, 10), FEMALE),
				athlete("Jordan", "Ellis", LocalDate.of(2012, 9, 2), FEMALE),
				athlete("Morgan", "Lee", LocalDate.of(2011, 12, 15), FEMALE),
				athlete("Casey", "Nguyen", LocalDate.of(2012, 6, 21), FEMALE),
				athlete("Devon", "Clarke", LocalDate.of(2012, 2, 3), MALE),
				athlete("Riley", "Brooks", LocalDate.of(2013, 1, 30), FEMALE),
				// CSV edge cases (T189, T190): accent, double quote, spreadsheet formula
				athlete("Émilie", "Tremblay", LocalDate.of(2012, 5, 5), FEMALE),
				athlete("Sam \"Rocket\"", "Patel", LocalDate.of(2012, 8, 14), MALE),
				athlete("Quinn", "=1+2", LocalDate.of(2012, 3, 3), MALE));

		// ----- Meets (dates relative to today) -----
		Meets meets = new Meets(
				meet("Winter Opener", today.minusDays(120), today.minusDays(120), "Mississauga"),
				meet("Spring Classic", today.minusDays(60), today.minusDays(60), "Brampton"),
				meet("Summer Invitational", today.minusDays(30), today.minusDays(30), "Toronto"),
				// multi-day meet in progress today (T170, T171)
				meet("Provincial Championships", today.minusDays(1), today.plusDays(1), "Ottawa"),
				// comma and line break in a text field that is exported (T189)
				meet("Ontario Indoor Invitational", today.plusDays(16), today.plusDays(16),
						"Toronto Pan Am Centre, Field House\nEast Entrance"),
				// upcoming meet with an event but nobody assigned (T193, T194)
				meet("Late Season Open", today.plusDays(45), today.plusDays(45), "Hamilton"));

		long m100 = categories.m100();
		long m200 = categories.m200();
		long lj = categories.longJump();

		// ----- Events -----
		event(meets.winterOpener(), m100, "100M", "ASC");
		event(meets.winterOpener(), lj, "LONG JUMP", "DESC");
		event(meets.springClassic(), m100, "100M", "ASC");
		event(meets.springClassic(), lj, "LONG JUMP", "DESC");
		event(meets.summerInvitational(), m100, "100M", "ASC");
		event(meets.summerInvitational(), m200, "200M", "ASC");
		event(meets.summerInvitational(), lj, "LONG JUMP", "DESC");
		event(meets.provincials(), m100, "100M", "ASC");
		event(meets.ontarioIndoor(), m100, "100M", "ASC");
		event(meets.ontarioIndoor(), m200, "200M", "ASC");
		event(meets.ontarioIndoor(), lj, "LONG JUMP", "DESC");
		event(meets.lateSeasonOpen(), m100, "100M", "ASC");

		// ----- Results. Each result also assigns the athlete, like POST /api/events/{id}/results does. -----
		long w = meets.winterOpener();
		result(w, m100, athletes.alex(), "13.40", "VALID");
		result(w, m100, athletes.morgan(), "13.30", "VALID");
		result(w, m100, athletes.jordan(), "NT", "NT"); // T138: NT, no mark
		result(w, m100, athletes.casey(), "13.18", "VALID");
		result(w, lj, athletes.alex(), "4.10", "VALID");

		long s = meets.springClassic();
		result(s, m100, athletes.alex(), "13.10", "VALID");
		result(s, m100, athletes.jordan(), "12.74", "VALID");
		result(s, m100, athletes.morgan(), "12.96", "VALID");
		result(s, m100, athletes.devon(), "12.20", "VALID");
		result(s, lj, athletes.alex(), "4.35", "VALID");
		result(s, lj, athletes.jordan(), "4.20", "VALID");

		long su = meets.summerInvitational();
		result(su, m100, athletes.alex(), "12.96", "VALID");
		result(su, m100, athletes.alex(), "12.50", "DQ"); // better number but disqualified: never a PB (T137)
		result(su, m100, athletes.jordan(), "12.90", "VALID");
		result(su, m100, athletes.morgan(), "12.96", "VALID"); // equal to her Spring Classic best (T134)
		result(su, m100, athletes.casey(), "12.00", "PENDING"); // T138: would rank 1 if PENDING counted
		result(su, m200, athletes.riley(), "DNF", "DNF"); // Riley's only result (T137)
		result(su, lj, athletes.alex(), "4.80", "FOUL"); // better number but a foul: never a PB
		result(su, lj, athletes.morgan(), "3.95", "VALID"); // single point, no chart (T139)

		// ----- Assignments with no results -----
		assign(meets.provincials(), m100, athletes.devon());

		long oi = meets.ontarioIndoor();
		assign(oi, m100, athletes.alex());
		assign(oi, m200, athletes.alex()); // same athlete, second event in the same meet (T157, T172)
		assign(oi, m100, athletes.jordan());
		assign(oi, m100, athletes.riley());
		assign(oi, m100, athletes.emilie());
		assign(oi, m200, athletes.morgan());
		assign(oi, m200, athletes.sam());
		assign(oi, lj, athletes.casey());
		assign(oi, lj, athletes.quinn());

		return new Seeded(athletes, meets, categories, Map.copyOf(eventIds));
	}

	// ---------- SQL helpers ----------

	private long category(String name, String rankingOrder, String unit) {
		return insert("event_categories", Map.of("name", name, "ranking_order", rankingOrder, "unit", unit));
	}

	private long athlete(String firstName, String lastName, LocalDate dateOfBirth, String gender) {
		return insert("athletes", Map.of("first_name", firstName, "last_name", lastName,
				"date_of_birth", dateOfBirth, "gender", gender, "archived", false));
	}

	private long meet(String name, LocalDate start, LocalDate end, String location) {
		return insert("meets", Map.of("name", name, "meet_date", start, "start_date", start,
				"end_date", end, "location", location));
	}

	private void event(long meetId, long categoryId, String name, String rankingOrder) {
		LocalDate eventDate = jdbc.queryForObject("SELECT start_date FROM meets WHERE id = ?",
				java.sql.Date.class, meetId).toLocalDate();
		long id = insert("events", Map.of("name", name, "event_date", eventDate, "ranking_order", rankingOrder,
				"meet_id", meetId, "category_id", categoryId));
		eventIds.put(meetId + ":" + categoryId, id);
	}

	/** Inserts the participation once per athlete and event (the table has a unique key on the pair). */
	private void assign(long meetId, long categoryId, long athleteId) {
		long eventId = eventIdFor(meetId, categoryId);
		if (participations.add(eventId + ":" + athleteId)) {
			insert("event_participations", Map.of("event_id", eventId, "athlete_id", athleteId));
		}
	}

	/**
	 * Inserts one recorded performance. Marks like "NT" and "DNF" have no score. A DQ or FOUL keeps its number on
	 * purpose, so the tests prove the status (not a missing score) is what excludes it.
	 */
	private void result(long meetId, long categoryId, long athleteId, String mark, String status) {
		assign(meetId, categoryId, athleteId);
		long eventId = eventIdFor(meetId, categoryId);
		LocalDate meetDate = jdbc.queryForObject("SELECT start_date FROM meets WHERE id = ?",
				java.sql.Date.class, meetId).toLocalDate();
		String unit = jdbc.queryForObject("SELECT unit FROM event_categories WHERE id = ?", String.class,
				categoryId);
		Map<String, Object> values = new HashMap<>();
		values.put("event_id", eventId);
		values.put("category_id", categoryId);
		values.put("athlete_id", athleteId);
		values.put("result", mark);
		values.put("score", isNumber(mark) ? new BigDecimal(mark) : null);
		values.put("unit", unit);
		values.put("status", status);
		values.put("recorded_at", Timestamp.from(meetDate.atTime(10, 0).toInstant(ZoneOffset.UTC)));
		values.put("source", "MANUAL");
		insert("event_results", values);
	}

	private long eventIdFor(long meetId, long categoryId) {
		Long id = eventIds.get(meetId + ":" + categoryId);
		if (id == null) {
			throw new IllegalStateException("SEED FAILED: no event for meet " + meetId + " and category "
					+ categoryId + ". Create the event before adding results or assignments.");
		}
		return id;
	}

	private static boolean isNumber(String mark) {
		return mark.matches("\\d+(\\.\\d+)?");
	}

	private long insert(String table, Map<String, ?> values) {
		return new SimpleJdbcInsert(jdbc)
				.withTableName(table)
				.usingColumns(values.keySet().toArray(String[]::new))
				.usingGeneratedKeyColumns("id")
				.executeAndReturnKey(new HashMap<>(values))
				.longValue();
	}
}
