package com.flyingangels.backend.meet;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.Athlete;
import com.flyingangels.backend.availability.AvailabilityService;
import com.flyingangels.backend.availability.AvailabilityDtos.*;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.dto.MeetRequest;
import jakarta.persistence.EntityManagerFactory;
import org.apache.commons.csv.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class MeetExportIntegrationTest extends DatabaseIntegrationTest {
    @Autowired MeetService meets;
    @Autowired EventService events;
    @Autowired MeetExportService exports;
    @Autowired AvailabilityService availability;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    private Meet meet;
    private Event race, jump;
    private Athlete ada, bea;

    @BeforeEach void seed() {
        meet = meets.create(new MeetRequest("Club Championship", LocalDate.of(2050, 5, 10), "North Track", null));
        race = event(meet, "100m", RankingOrder.ASC); jump = event(meet, "Long jump", RankingOrder.DESC);
        ada = athlete("Ada", "Runner"); bea = athlete("Bea", "Runner");
    }
    private Athlete athlete(String first, String last) {
        return athleteRepository.saveAndFlush(new Athlete(first, last, "Private athlete note", "", "", LocalDate.of(2010, 1, 1), "Female"));
    }
    private Event event(Meet meet, String name, RankingOrder order) {
        return events.create(new EventRequest(name, meet.getStartDate(), "Track", null, null, order, meet.getId(), null, name));
    }
    private void assign(Event event, Athlete athlete) { events.assignParticipant(event.getId(), new EventParticipantRequest(athlete.getId())); }
    private EventResult result(Event event, Athlete athlete, String raw, String label) {
        return events.addResult(event.getId(), new EventResultRequest(athlete.getId(), raw, null, null, null, label));
    }
    private List<CSVRecord> rows(MeetExportType type) throws Exception { return parse(exports.download(meet.getId(), type).content()); }
    private List<CSVRecord> parse(byte[] bytes) throws Exception {
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"), "The CSV includes a UTF-8 BOM for spreadsheet encoding detection");
        try (var parser = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader(csv.substring(1)))) {
            return parser.getRecords();
        }
    }

    @Test void optionsShowIndependentCountsWithoutCreatingRecordsOrGeneratingAnExport() throws Exception {
        mockMvc.perform(get("/api/meets/{id}/exports", meet.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.meetId").value(meet.getId())).andExpect(jsonPath("$.options[0].type").value("REGISTRATION"))
                .andExpect(jsonPath("$.options[0].recordCount").value(0)).andExpect(jsonPath("$.options[0].available").value(false))
                .andExpect(jsonPath("$.options[1].type").value("RESULTS")).andExpect(jsonPath("$.options[1].available").value(false));
        assign(race, ada); result(race, ada, "12.4", "Heat 1"); result(race, ada, "12.3", "Final");
        var options = exports.options(meet.getId());
        assertEquals(1, options.options().get(0).recordCount()); assertEquals(2, options.options().get(1).recordCount());
        assertTrue(options.options().stream().allMatch(o -> o.available()));
        assertEquals(options, exports.options(meet.getId()));
        assertEquals(1, participationRepository.count()); assertEquals(2, eventResultRepository.count());
        assertEquals(0, availabilityResponseRepository.count());
    }

    @Test void registrationIsOneRowPerAssignmentEvenWithMultipleAttemptsAndUnavailableAthletes() throws Exception {
        assign(race, ada); assign(jump, ada); assign(race, bea);
        result(race, ada, "12.4", "Heat 1"); result(race, ada, "12.3", "Final");
        var request = availability.forMeet(meet.getId()).stream().filter(r -> r.athleteId().equals(bea.getId())).findFirst().orElseThrow();
        availability.recordExternalResponse(request.id(), 42L, new StaffReply(Answer.UNAVAILABLE, request.version(), request.assignmentRevision(), Channel.PHONE, "Private response note"));
        var rows = rows(MeetExportType.REGISTRATION);
        assertEquals(3, rows.size()); assertEquals(3, rows.stream().map(r -> r.get("participation_id")).distinct().count());
        assertEquals(2, rows.stream().filter(r -> r.get("athlete_id").equals(ada.getId().toString())).count());
        assertEquals("UNAVAILABLE", rows.stream().filter(r -> r.get("athlete_id").equals(bea.getId().toString())).findFirst().orElseThrow().get("availability_status"));
        assertEquals("PENDING", rows.get(0).get("availability_status"));
        assertFalse(rows.get(0).isMapped("result_id"));
    }

    @Test void resultsRetainEveryAttemptIncludingEqualPerformancesAndLabels() throws Exception {
        var heat = result(race, ada, "12.4", "Heat 1"); var finalResult = result(race, ada, "12.4", "Final");
        var attempt = result(jump, ada, "4.85m", "Attempt 2"); assign(jump, bea);
        var rows = rows(MeetExportType.RESULTS);
        assertEquals(3, rows.size());
        assertEquals(List.of(heat.getId().toString(), finalResult.getId().toString(), attempt.getId().toString()), rows.stream().map(r -> r.get("result_id")).toList());
        assertEquals(List.of("Heat 1", "Final", "Attempt 2"), rows.stream().map(r -> r.get("result_label")).toList());
        assertEquals("12.4", rows.get(0).get("result")); assertEquals("4.85m", rows.get(2).get("result"));
        assertEquals("SECONDS", rows.get(0).get("unit")); assertEquals("METRES", rows.get(2).get("unit"));
        assertEquals(0, new BigDecimal("4.85").compareTo(new BigDecimal(rows.get(2).get("score"))));
        assertEquals("DESC", rows.get(2).get("ranking_order")); assertFalse(rows.get(0).isMapped("participation_id"));
    }

    @Test void nonPerformanceStatusesAndPointsRemainRecordedRowsWithoutInventedScores() throws Exception {
        result(race, ada, "DNS", "Heat 1"); result(race, bea, "DNF", "Final"); result(jump, ada, "FOUL", "Attempt 1");
        var points = event(meet, "Combined points", RankingOrder.DESC); result(points, ada, "1234", null);
        var rows = rows(MeetExportType.RESULTS);
        assertEquals(List.of("DNS", "DNF", "FOUL", "VALID"), rows.stream().map(r -> r.get("status")).toList());
        assertTrue(rows.subList(0, 3).stream().allMatch(r -> r.get("score").isEmpty()));
        assertEquals("POINTS", rows.get(3).get("unit")); assertEquals("", rows.get(3).get("result_label"));
        assertEquals(0, new BigDecimal("1234").compareTo(new BigDecimal(rows.get(3).get("score"))));
    }

    @Test void emptyExportsReturnTypedJsonErrorsInsteadOfHeaderOnlyDownloadsEvenWhenCsvIsAccepted() throws Exception {
        for (var type : MeetExportType.values()) {
            mockMvc.perform(get("/api/meets/{id}/export", meet.getId()).param("type", type.name()).accept("text/csv"))
                    .andExpect(status().isConflict()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(type == MeetExportType.REGISTRATION ? "NO_REGISTRATION_RECORDS" : "NO_RESULT_RECORDS"))
                    .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
        }
        assign(race, ada);
        assertTrue(exports.options(meet.getId()).options().get(0).available());
        assertFalse(exports.options(meet.getId()).options().get(1).available());
        mockMvc.perform(get("/api/meets/{id}/export", meet.getId()).param("type", "RESULTS")).andExpect(status().isConflict());
        assertEquals(1, rows(MeetExportType.REGISTRATION).size());
    }

    @Test void exportsStayWithinSelectedMeetAndExcludeUnlinkedImports() throws Exception {
        result(race, ada, "12.4", "Selected");
        var otherMeet = meets.create(new MeetRequest("Other meet", LocalDate.of(2050, 6, 1), "Elsewhere", null));
        result(event(otherMeet, "100m", RankingOrder.ASC), ada, "11.9", "Other meet");
        eventResultRepository.saveAndFlush(new EventResult(null, race.getCategory(), ada, "11.8", new BigDecimal("11.8")));
        assertEquals(1, rows(MeetExportType.RESULTS).size()); assertEquals(1, rows(MeetExportType.REGISTRATION).size());
        assertEquals("Selected", rows(MeetExportType.RESULTS).get(0).get("result_label"));
        assertEquals(1, exports.options(meet.getId()).options().get(1).recordCount());
    }

    @Test void linkedLegacyResultsWithoutParticipationStillExportAsResults() throws Exception {
        eventResultRepository.saveAndFlush(new EventResult(race, race.getCategory(), ada, "12.4", new BigDecimal("12.4")));
        assertEquals(0, participationRepository.count()); assertEquals(1, rows(MeetExportType.RESULTS).size());
        assertFalse(exports.options(meet.getId()).options().get(0).available());
        assertEquals(0, participationRepository.count(), "Reading an export must not invent an assignment");
    }
    @Test void anOlderAssignmentWithoutAvailabilityStateExportsAsPendingWithoutCreatingAResponse() throws Exception {
        participationRepository.saveAndFlush(new EventParticipation(race, ada));
        assertEquals("PENDING", rows(MeetExportType.REGISTRATION).get(0).get("availability_status"));
        assertEquals(0, availabilityRequestRepository.count()); assertEquals(0, availabilityResponseRepository.count());
    }

    @Test void csvRoundTripsCommasQuotesMultilineValuesAndUnicode() throws Exception {
        meets.update(meet.getId(), new MeetRequest("Championship, \"North\"\r\nOntario", meet.getStartDate(), "École, Montréal", null));
        var athlete = athlete("Zoë, \"Z\"", "O'Neil\nRunner");
        var result = result(race, athlete, "12.4", "Final, \"A\"\nRound");
        var row = rows(MeetExportType.RESULTS).get(0);
        assertEquals("Championship, \"North\"\r\nOntario", row.get("meet_name")); assertEquals("École, Montréal", row.get("meet_location"));
        assertEquals("Zoë, \"Z\"", row.get("first_name")); assertEquals("O'Neil\nRunner", row.get("last_name"));
        assertEquals(result.getResultLabel(), row.get("result_label"));
        assertEquals(row.get("first_name"), rows(MeetExportType.REGISTRATION).get(0).get("first_name"));
    }

    @Test void spreadsheetFormulaPrefixesAreNeutralizedWithoutChangingStoredNames() throws Exception {
        var inputs = List.of("=1+2", "+SUM(1,2)", "-1+2", "@SUM(1,2)", "  =1+2", "\t=1+2", "\r=1+2", "\n=1+2", "\uFEFF=1+2", "\u00A0=1+2");
        Map<Long, String> expected = new HashMap<>();
        for (String input : inputs) { var athlete = athlete(input, "Formula"); assign(race, athlete); expected.put(athlete.getId(), input); }
        for (var row : rows(MeetExportType.REGISTRATION)) {
            Long athleteId = Long.valueOf(row.get("athlete_id"));
            assertEquals("'" + expected.get(athleteId), row.get("first_name"));
            assertEquals(expected.get(athleteId), athleteRepository.findById(athleteId).orElseThrow().getFirstName());
        }
    }

    @Test void formulaProtectionCoversAllUserTextColumnsIncludingLabelsAndLegacyRawResults() throws Exception {
        meets.update(meet.getId(), new MeetRequest("@Championship", meet.getStartDate(), "=Location", null));
        var category = eventCategoryRepository.saveAndFlush(new EventCategory("=Category", RankingOrder.ASC));
        var event = events.create(new EventRequest("+Race", meet.getStartDate(), null, null, null, RankingOrder.ASC, meet.getId(), category.getId(), null));
        var athlete = athleteRepository.saveAndFlush(new Athlete("-Name", "@Surname", "", "", "", null, "=Gender"));
        var legacy = new EventResult(event, category, athlete, "=1+2", null); legacy.setResultLabel("@Label");
        legacy.setMeasurement(ResultUnit.SECONDS, ResultStatus.UNPARSED); eventResultRepository.saveAndFlush(legacy);
        var row = rows(MeetExportType.RESULTS).get(0);
        for (String column : List.of("meet_name", "meet_location", "event_name", "category_name", "first_name", "last_name", "gender", "result", "result_label"))
            assertTrue(row.get(column).startsWith("'"), column + " must remain literal spreadsheet text");
        assertEquals("", row.get("date_of_birth")); assertEquals("", row.get("score"));
        assertEquals("UNPARSED", row.get("status"));
    }

    @Test void downloadsHaveCorrectMimeTypeAttachmentCacheHeadersAndExposedFilename() throws Exception {
        result(race, ada, "12.4", null);
        for (var type : MeetExportType.values()) {
            var response = mockMvc.perform(get("/api/meets/{id}/export", meet.getId()).param("type", type.name())
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173").accept("text/csv"))
                    .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meet-" + meet.getId() + "-club-championship-" + type.filenameSuffix() + ".csv\""))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Content-Disposition")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff")).andReturn().getResponse();
            var bytes = response.getContentAsByteArray();
            assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(bytes, 3));
            assertTrue(new String(bytes, StandardCharsets.UTF_8).endsWith("\r\n")); assertEquals(1, parse(bytes).size());
        }
    }

    @Test void rowOrderIsStableAndNullRecordingTimesSortAfterKnownAttempts() throws Exception {
        var later = result(race, ada, "12.2", "Later"); var earlier = result(race, ada, "12.4", "Earlier");
        var unknown = result(race, ada, "12.3", "Unknown"); result(race, bea, "12.8", "Bea");
        jdbc.update("update event_results set recorded_at = '2050-05-10 10:00:00' where id = ?", earlier.getId());
        jdbc.update("update event_results set recorded_at = '2050-05-10 11:00:00' where id = ?", later.getId());
        jdbc.update("update event_results set recorded_at = null where id = ?", unknown.getId());
        assertEquals(List.of("Earlier", "Later", "Unknown", "Bea"), rows(MeetExportType.RESULTS).stream().map(r -> r.get("result_label")).toList());
        assertEquals("", rows(MeetExportType.RESULTS).get(2).get("recorded_at"));
        assertArrayEquals(exports.download(meet.getId(), MeetExportType.RESULTS).content(), exports.download(meet.getId(), MeetExportType.RESULTS).content());
    }

    @Test void correctedResultsAndDeletedAssignmentsAreReflectedImmediately() throws Exception {
        var result = result(race, ada, "12.4", "Heat 1");
        events.updateResult(race.getId(), result.getId(), new EventResultRequest(ada.getId(), "12.1", null, null, null, "Corrected heat"));
        assertEquals("12.1", rows(MeetExportType.RESULTS).get(0).get("result"));
        assertEquals("Corrected heat", rows(MeetExportType.RESULTS).get(0).get("result_label"));
        events.removeParticipant(race.getId(), ada.getId());
        assertTrue(exports.options(meet.getId()).options().stream().noneMatch(o -> o.available()));
        assertThrows(MeetExportService.EmptyMeetExportException.class, () -> exports.download(meet.getId(), MeetExportType.RESULTS));
    }

    @Test void availabilityChangesInvalidateRegistrationStatusForEveryAssignedEvent() throws Exception {
        assign(race, ada); var request = availability.forMeet(meet.getId()).get(0);
        availability.recordExternalResponse(request.id(), 42L, new StaffReply(Answer.AVAILABLE, request.version(), request.assignmentRevision(), Channel.EMAIL, "Private"));
        assertEquals("AVAILABLE", rows(MeetExportType.REGISTRATION).get(0).get("availability_status"));
        assign(jump, ada);
        assertTrue(rows(MeetExportType.REGISTRATION).stream().allMatch(r -> r.get("availability_status").equals("PENDING")));
        assertEquals(1, availabilityResponseRepository.count());
    }

    @Test void csvOmitsInternalNotesAndIdentifiersAndLeavesVersionsAndResultsUnchanged() throws Exception {
        result(race, ada, "12.4", "Heat 1");
        events.saveNote(race.getId(), null, "Private event note"); events.saveNote(race.getId(), ada.getId(), "Private performance note");
        var participant = participationRepository.findByEventIdAndAthleteId(race.getId(), ada.getId()).orElseThrow();
        var request = availability.forMeet(meet.getId()).get(0);
        for (var type : MeetExportType.values()) {
            String csv = new String(exports.download(meet.getId(), type).content(), StandardCharsets.UTF_8);
            assertFalse(csv.contains("Private")); assertFalse(csv.contains("client_id")); assertFalse(csv.contains("access_link"));
        }
        assertEquals(participant.getVersion(), participationRepository.findById(participant.getId()).orElseThrow().getVersion());
        assertEquals(request.version(), availability.get(request.id(), null).version()); assertEquals(1, eventResultRepository.count());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/meets/0/exports", "/api/meets/-1/export?type=RESULTS", "/api/meets/text/export?type=RESULTS",
            "/api/meets/1/export", "/api/meets/1/export?type=INVALID", "/api/meets/1/export?type=registration"})
    void invalidMeetOrExportSelectionReturnsBadRequest(String path) throws Exception {
        mockMvc.perform(get(path).accept("text/csv")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test void missingMeetsReturnNotFoundForBothOptionsAndDownloads() throws Exception {
        for (String path : List.of("/api/meets/999999999/exports", "/api/meets/999999999/export?type=REGISTRATION", "/api/meets/999999999/export?type=RESULTS"))
            mockMvc.perform(get(path).accept("text/csv")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEET_NOT_FOUND"));
    }

    @Test void exportQueriesFetchRelationshipsTogetherInsteadOfPerRow() {
        for (int i = 0; i < 8; i++) result(race, athlete("Athlete " + i, "Runner"), "12.4", "Final");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics(); boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear(); exports.download(meet.getId(), MeetExportType.REGISTRATION);
            assertTrue(statistics.getPrepareStatementCount() <= 4, "Registration export should batch relationship and availability loading");
            statistics.clear(); exports.download(meet.getId(), MeetExportType.RESULTS);
            assertTrue(statistics.getPrepareStatementCount() <= 3, "Results export should fetch every row's context in its query");
        } finally { statistics.setStatisticsEnabled(enabled); }
    }
}
