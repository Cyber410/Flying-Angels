package com.flyingangels.backend.meet;

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockHttpServletResponse;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;

/**
 * Shared setup for the UC-03 integration tests, written against Dev's API on development (MeetController,
 * MeetEventController, and the requests in docs/postman/flying-angels-backend.postman_collection.json).
 *
 * These tests deliberately use no meet, event or result production class. Meets and events are created through
 * the API as JSON strings; categories, athletes and results are inserted with SQL; and every database check is a
 * SQL query. So when Dev changes Meet, Event, EventResult or a DTO, these classes still compile and only the
 * assertions show what changed.
 *
 * When a seed request fails, the helpers fail with a message starting "SEED FAILED", so a missing endpoint is
 * easy to tell apart from a failed assertion in the test itself.
 */
abstract class MeetContractTestSupport extends DatabaseIntegrationTest {

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	// ---------- JSON request bodies ----------

	/** Meet body for POST/PUT /api/meets. A null argument is sent as JSON null. */
	protected static String meetJson(String name, String startDate, String endDate, String location) {
		return "{\"name\":" + jsonString(name)
				+ ",\"startDate\":" + jsonString(startDate)
				+ ",\"endDate\":" + jsonString(endDate)
				+ ",\"location\":" + jsonString(location) + "}";
	}

	protected static String meetJson(String name, LocalDate startDate, LocalDate endDate, String location) {
		return meetJson(name, startDate.toString(), endDate.toString(), location);
	}

	/** Meet body with only a name and a start date. A null startDate is sent as JSON null. */
	protected static String startDateOnlyMeetJson(String name, String startDate) {
		return "{\"name\":" + jsonString(name) + ",\"startDate\":" + jsonString(startDate) + "}";
	}

	/** Body of the old proposed POST /api/meets/{meetId}/events. Only the disabled tests still send it. */
	protected static String categoryIdsJson(long... categoryIds) {
		return "{\"categoryIds\":" + jsonArray(categoryIds) + "}";
	}

	/**
	 * Body for POST /api/meets/{meetId}/events/bulk: one event per category, named after the category and held on
	 * eventDate. Category name and ranking order are read from the database.
	 */
	protected String eventBatchJson(long meetId, LocalDate eventDate, long... categoryIds) {
		String events = Arrays.stream(categoryIds).mapToObj(categoryId -> {
			Map<String, Object> category = jdbcTemplate.queryForMap(
					"SELECT name, ranking_order FROM event_categories WHERE id = ?", categoryId);
			return "{\"name\":" + jsonString((String) category.get("name"))
					+ ",\"eventDate\":" + jsonString(eventDate.toString())
					+ ",\"rankingOrder\":" + jsonString((String) category.get("ranking_order"))
					+ ",\"meetId\":" + meetId
					+ ",\"categoryId\":" + categoryId + "}";
		}).collect(Collectors.joining(",", "[", "]"));
		return "{\"events\":" + events + "}";
	}

	/** Body for POST /api/meets/{meetId}/events/copy. */
	protected static String copyEventsJson(long sourceMeetId, long... eventIds) {
		return "{\"sourceMeetId\":" + sourceMeetId + ",\"eventIds\":" + jsonArray(eventIds) + "}";
	}

	private static String jsonString(String value) {
		if (value == null) {
			return "null";
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static String jsonArray(long... values) {
		return Arrays.stream(values).mapToObj(Long::toString).collect(Collectors.joining(",", "[", "]"));
	}

	// ---------- Seeding through the API ----------

	/** Creates a meet through POST /api/meets and returns its ID. */
	protected long createMeet(String name, LocalDate startDate, LocalDate endDate, String location)
			throws Exception {
		return createMeet(meetJson(name, startDate, endDate, location));
	}

	/** Creates a meet through POST /api/meets from a ready-made body and returns its ID. */
	protected long createMeet(String body) throws Exception {
		MockHttpServletResponse response = mockMvc.perform(post("/api/meets")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andReturn().getResponse();
		requireSeedStatus(response, 201, "POST /api/meets");
		return readLong(response, "$.id", "POST /api/meets");
	}

	/**
	 * Adds one event per category through POST /api/meets/{meetId}/events/bulk, held on the meet's start date,
	 * and returns the new event IDs in response order.
	 */
	protected List<Long> addEvents(long meetId, long... categoryIds) throws Exception {
		String request = "POST /api/meets/" + meetId + "/events/bulk";
		LocalDate startDate = jdbcTemplate.queryForObject("SELECT start_date FROM meets WHERE id = ?",
				java.sql.Date.class, meetId).toLocalDate();
		MockHttpServletResponse response = mockMvc.perform(post("/api/meets/{meetId}/events/bulk", meetId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(eventBatchJson(meetId, startDate, categoryIds)))
				.andReturn().getResponse();
		requireSeedStatus(response, 201, request);
		List<?> ids = readList(response, "$[*].id", request);
		if (ids.size() != categoryIds.length) {
			fail("SEED FAILED: " + request + " returned " + ids.size() + " events for " + categoryIds.length
					+ " category IDs. Body: " + response.getContentAsString());
		}
		List<Long> eventIds = new ArrayList<>();
		for (Object id : ids) {
			eventIds.add(((Number) id).longValue());
		}
		return eventIds;
	}

	private static void requireSeedStatus(MockHttpServletResponse response, int expected, String request)
			throws Exception {
		if (response.getStatus() != expected) {
			fail("SEED FAILED: " + request + " returned " + response.getStatus()
					+ ". Body: " + response.getContentAsString());
		}
	}

	private static long readLong(MockHttpServletResponse response, String path, String request) throws Exception {
		Object value = readJson(response, path, request);
		if (!(value instanceof Number number)) {
			fail("SEED FAILED: " + request + " has no number at " + path + ". Body: "
					+ response.getContentAsString());
			return 0;
		}
		return number.longValue();
	}

	private static List<?> readList(MockHttpServletResponse response, String path, String request)
			throws Exception {
		Object value = readJson(response, path, request);
		if (!(value instanceof List<?> list)) {
			fail("SEED FAILED: " + request + " did not return a list at " + path + ". Body: "
					+ response.getContentAsString());
			return List.of();
		}
		return list;
	}

	private static Object readJson(MockHttpServletResponse response, String path, String request)
			throws Exception {
		String body = response.getContentAsString();
		try {
			return JsonPath.read(body, path);
		} catch (RuntimeException exception) {
			fail("SEED FAILED: " + request + " body could not be read at " + path + ". Body: " + body);
			return null;
		}
	}

	// ---------- Seeding with SQL ----------

	/** Inserts an event category (the catalog the Add Events dialog shows) and returns its ID. */
	protected long insertCategory(String name, String rankingOrder) {
		return insert("event_categories", Map.of("name", name, "ranking_order", rankingOrder));
	}

	/** Inserts an athlete with only the columns these tests need and returns its ID. */
	protected long insertAthlete(String firstName, String lastName, LocalDate dateOfBirth, String gender) {
		return insert("athletes", Map.of("first_name", firstName, "last_name", lastName,
				"date_of_birth", dateOfBirth, "gender", gender));
	}

	/**
	 * Inserts a row in event_results and returns its ID. In the current schema an athlete assigned to an event
	 * with no result yet is a row with a null result; pass null for result and score for that.
	 * eventId null is an imported result that belongs to no event.
	 */
	protected long insertEventResult(Long eventId, long categoryId, long athleteId, String result, String score) {
		Map<String, Object> values = new HashMap<>();
		values.put("event_id", eventId);
		values.put("category_id", categoryId);
		values.put("athlete_id", athleteId);
		values.put("result", result);
		values.put("score", score == null ? null : new BigDecimal(score));
		return insert("event_results", values);
	}

	private long insert(String table, Map<String, ?> values) {
		return new SimpleJdbcInsert(jdbcTemplate)
				.withTableName(table)
				.usingColumns(values.keySet().toArray(String[]::new))
				.usingGeneratedKeyColumns("id")
				.executeAndReturnKey(new HashMap<>(values))
				.longValue();
	}

	// ---------- Database checks with SQL ----------

	protected long count(String sql, Object... args) {
		Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
		return result == null ? 0 : result;
	}

	protected long meetCount() {
		return count("SELECT COUNT(*) FROM meets");
	}

	protected long meetExists(long meetId) {
		return count("SELECT COUNT(*) FROM meets WHERE id = ?", meetId);
	}

	protected long eventCount() {
		return count("SELECT COUNT(*) FROM events");
	}

	protected long eventCountForMeet(long meetId) {
		return count("SELECT COUNT(*) FROM events WHERE meet_id = ?", meetId);
	}

	protected long eventExists(long eventId) {
		return count("SELECT COUNT(*) FROM events WHERE id = ?", eventId);
	}

	protected long resultCountForEvent(long eventId) {
		return count("SELECT COUNT(*) FROM event_results WHERE event_id = ?", eventId);
	}

	protected long resultCountForMeet(long meetId) {
		return count("SELECT COUNT(*) FROM event_results r JOIN events e ON r.event_id = e.id WHERE e.meet_id = ?",
				meetId);
	}

	protected long athleteExists(long athleteId) {
		return count("SELECT COUNT(*) FROM athletes WHERE id = ?", athleteId);
	}

	/** Events whose meet no longer exists. */
	protected long orphanEventCount() {
		return count("SELECT COUNT(*) FROM events e LEFT JOIN meets m ON e.meet_id = m.id WHERE m.id IS NULL");
	}

	/** Results that point at an event that no longer exists (imported results with no event are not orphans). */
	protected long orphanResultCount() {
		return count("SELECT COUNT(*) FROM event_results r LEFT JOIN events e ON r.event_id = e.id "
				+ "WHERE r.event_id IS NOT NULL AND e.id IS NULL");
	}

	/** Categories that appear more than once in the same meet. Must always be 0. */
	protected long duplicateCategoriesInMeet(long meetId) {
		return count("SELECT COUNT(*) FROM (SELECT category_id FROM events WHERE meet_id = ? "
				+ "GROUP BY category_id HAVING COUNT(*) > 1) duplicates", meetId);
	}

	/** An ID no meet has: 1000 past the largest one. */
	protected long unknownMeetId() {
		return count("SELECT COALESCE(MAX(id), 0) + 1000 FROM meets");
	}

	protected long unknownEventId() {
		return count("SELECT COALESCE(MAX(id), 0) + 1000 FROM events");
	}

	/** JSON numbers small enough for an int are read back by JsonPath as Integer. */
	protected static Integer json(long id) {
		return Math.toIntExact(id);
	}
}
