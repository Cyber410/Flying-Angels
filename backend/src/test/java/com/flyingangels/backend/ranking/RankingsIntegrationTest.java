package com.flyingangels.backend.ranking;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.athlete.dto.AthleteRequest;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.*;
import com.flyingangels.backend.meet.dto.MeetRequest;
import com.flyingangels.backend.ranking.dto.RankingsResponse;
import com.flyingangels.backend.ranking.dto.RankingsResponse.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RankingsIntegrationTest extends DatabaseIntegrationTest {
    @Autowired RankingsService rankings;
    @Autowired RankingQueryRepository query;
    @Autowired AthleteHistoryService history;
    @Autowired AthleteService athletes;
    @Autowired MeetService meets;
    @Autowired EventService events;
    @Autowired EventCategoryService categories;
    @Autowired JdbcTemplate jdbc;
    private Athlete ada, ben, cara, unknown;
    private Meet early, late;
    private Event race, laterRace;

    @BeforeEach void seed() {
        ada = athlete("Ada", "Alpha", "2012-05-15", "Female");
        ben = athlete("Ben", "Bravo", "2010-01-01", "Male");
        cara = athlete("Cara", "Charlie", "2011-06-01", "Female");
        unknown = athlete("Unknown", "Zulu", null, "Female");
        early = meets.create(new MeetRequest("Spring", LocalDate.of(2026, 5, 10), "Track", null));
        late = meets.create(new MeetRequest("Summer", LocalDate.of(2026, 6, 10), "Track", null));
        race = event(early, "100m", RankingOrder.ASC);
        laterRace = event(late, "100m", RankingOrder.ASC);
    }
    private Athlete athlete(String first, String last, String dob, String gender) {
        return athletes.create(new AthleteRequest(first, last, "Private coaching note", "In Roster", "", dob == null ? null : LocalDate.parse(dob), gender, List.of()));
    }
    private Event event(Meet meet, String category, RankingOrder order) {
        return events.create(new EventRequest(category, meet.getStartDate(), meet.getLocation(), null, null, order, meet.getId(), null, category));
    }
    private EventResult add(Event event, Athlete athlete, String value) {
        return events.addResult(event.getId(), new EventResultRequest(athlete.getId(), value, null, null, null, "Final"));
    }
    private RankingsResponse<StaffRow> rank(Event event) { return rankings.staff(event.getCategory().getId(), null, null, null, null, 0, 25); }
    private void score(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }

    @Test void meetFiltersChooseTheBestInsideThatMeetBeforeRankingAndPaging() {
        var earlyBest = add(race, ada, "12.4"); add(race, ada, "12.8"); add(race, ben, "12.4"); add(race, cara, "13.0");
        add(laterRace, ada, "10.0"); add(laterRace, ben, "9.9");
        var scoped = rankings.staff(race.getCategory().getId(), null, null, null, null, 0, 25, early.getId(), null);
        assertEquals(3, scoped.totalElements()); assertEquals(List.of(1, 1, 3), scoped.content().stream().map(StaffRow::position).toList());
        assertEquals(earlyBest.getId(), scoped.content().get(0).resultId()); assertEquals(early.getId(), scoped.filters().meetId());
        var page = rankings.portal(ada.getId(), race.getCategory().getId(), null, null, null, null, 1, 1, early.getId(), null);
        assertEquals(1, page.content().get(0).position()); assertEquals("Ben", page.content().get(0).firstName()); assertEquals(3, page.totalElements());
        assertEquals(early.getId(), page.content().get(0).meetId());
    }

    @Test void anExplicitMeetEventUsesItsConfiguredOrderAndReturnsTheEventContext() {
        add(race, ada, "12.4");
        jdbc.update("update events set ranking_order = 'DESC' where id = ?", laterRace.getId());
        add(laterRace, ada, "10.0"); add(laterRace, ben, "20.0");
        var selected = rankings.portal(unknown.getId(), race.getCategory().getId(), null, null, null, null, 0, 25, null, laterRace.getId());
        assertEquals(RankingOrder.DESC, selected.category().rankingOrder()); assertEquals("Ben", selected.content().get(0).firstName());
        assertEquals(late.getId(), selected.filters().meetId()); assertEquals(laterRace.getId(), selected.filters().eventId());
        assertEquals(laterRace.getId(), selected.content().get(0).eventId()); assertEquals("Summer", selected.content().get(0).meetName());
        assertEquals(ResultUnit.SECONDS, selected.content().get(0).unit()); assertFalse(selected.content().get(0).profileAccessible());
        assertNull(selected.content().get(0).athleteId());
        var meetOnly = rankings.staff(race.getCategory().getId(), null, null, null, null, 0, 25, late.getId(), null);
        assertEquals(ben.getId(), meetOnly.content().get(0).athleteId()); assertEquals(1, rank(race).totalElements());
    }

    @Test void rankingChoicesContainAllMeetsAndEventsIncludingEmptyAndUnassignedCompetitions() {
        var jump = event(late, "Long Jump", RankingOrder.DESC);
        var empty = meets.create(new MeetRequest("Empty future meet", LocalDate.of(2050, 1, 1), "Track", null));
        assertEquals(Set.of(early.getId(), late.getId(), empty.getId()), rankings.meets().stream().map(MeetChoice::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(3, rankings.events(null).size());
        assertEquals(Set.of(laterRace.getId(), jump.getId()), rankings.events(late.getId()).stream().map(EventChoice::id).collect(java.util.stream.Collectors.toSet()));
        assertTrue(rankings.events(empty.getId()).isEmpty()); assertEquals(0, participationRepository.count());
    }

    @Test void scopedEmptyStatusesNeverUseResultsFromAnotherMeet() {
        add(race, ada, "12.4");
        var none = rankings.portal(unknown.getId(), race.getCategory().getId(), "Male", null, null, null, 0, 25, late.getId(), null);
        assertEquals(Status.NO_EVENT_RESULTS, none.status());
        add(laterRace, ada, "12.2");
        var unmatched = rankings.portal(unknown.getId(), race.getCategory().getId(), "Male", null, null, null, 0, 25, late.getId(), null);
        assertEquals(Status.NO_MATCHING_ATHLETES, unmatched.status());
    }

    @Test void invalidOrMismatchedRankingScopesAreRejected() throws Exception {
        String base = "/api/rankings?categoryId=" + race.getCategory().getId();
        for (String scope : List.of("&meetId=0", "&eventId=-1", "&meetId=bad", "&eventId=" + race.getId() + "&meetId=" + late.getId()))
            mockMvc.perform(get(base + scope)).andExpect(status().isBadRequest());
        mockMvc.perform(get(base + "&meetId=" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        mockMvc.perform(get(base + "&eventId=" + Long.MAX_VALUE)).andExpect(status().isNotFound());
        var jump = event(early, "Long Jump", RankingOrder.DESC);
        mockMvc.perform(get(base + "&eventId=" + jump.getId())).andExpect(status().isBadRequest());
    }

    @Test void oneBestPerAthleteAcrossMeetsMatchesHistoryAndKeepsCanonicalContext() throws Exception {
        add(race, ada, "12.6"); add(race, ada, "12.4"); var best = add(laterRace, ada, "12.0");
        add(race, ben, "11.8"); add(race, cara, "12.5");
        var page = rank(race);
        assertEquals(List.of(ben.getId(), ada.getId(), cara.getId()), page.content().stream().map(StaffRow::athleteId).toList());
        assertEquals(3, page.totalElements()); assertEquals(3, query.bestResults(race.getCategory(), null, new AgeRange(null, null), null).size());
        var row = page.content().get(1);
        assertEquals(best.getId(), row.resultId()); assertEquals(late.getId(), row.meetId()); assertEquals("Final", row.resultLabel());
        assertEquals(history.history(ada.getId(), race.getCategory().getId()).performances().get(0).personalBest().resultId(), row.resultId());
        mockMvc.perform(get("/api/rankings").param("categoryId", race.getCategory().getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[1].position").value(2)).andExpect(jsonPath("$.content[1].meetName").value("Summer"));
    }

    @Test void comparisonUsesNormalizedSecondsMetresAndPointsInTheirOwnDisciplines() {
        add(race, ada, "1min"); add(race, ben, "59.9s");
        var jump = event(early, "Long Jump", RankingOrder.DESC);
        add(jump, ada, "500cm"); add(jump, ben, "4.95m");
        var points = event(early, "Decathlon points", RankingOrder.DESC);
        add(points, ada, "1200"); add(points, ben, "1250");
        assertEquals(ben.getId(), rank(race).content().get(0).athleteId());
        assertEquals(ada.getId(), rank(jump).content().get(0).athleteId()); score("5", rank(jump).content().get(0).score());
        assertEquals(ResultUnit.METRES, rank(jump).category().unit());
        assertEquals(ben.getId(), rank(points).content().get(0).athleteId()); assertEquals(ResultUnit.POINTS, rank(points).category().unit());
    }

    @Test void tiesShareCompetitionPositionsAcrossPageBoundariesWithStableNameOrdering() {
        add(race, ben, "12.40"); add(race, ada, "12.4"); add(race, cara, "12.5"); add(race, unknown, "13.0");
        var all = rank(race);
        assertEquals(List.of(1, 1, 3, 4), all.content().stream().map(StaffRow::position).toList());
        assertEquals(ada.getId(), all.content().get(0).athleteId());
        var second = rankings.staff(race.getCategory().getId(), null, null, null, null, 1, 1);
        var third = rankings.staff(race.getCategory().getId(), null, null, null, null, 2, 1);
        assertEquals(1, second.content().get(0).position()); assertEquals(3, third.content().get(0).position());
        assertEquals(4, third.totalElements()); assertEquals(4, third.totalPages());
        var beyond = rankings.staff(race.getCategory().getId(), null, null, null, null, Integer.MAX_VALUE, 100);
        assertTrue(beyond.content().isEmpty()); assertEquals(Status.AVAILABLE, beyond.status()); assertEquals(4, beyond.totalElements());
    }

    @Test void equalBestRepresentativesMatchHistoryWithEarlierDatesKnownTimesAndStableIds() {
        add(laterRace, ada, "12.4"); var earlier = add(race, ada, "12.40");
        var nullTime = add(race, ben, "12.3"); var knownTime = add(race, ben, "12.30");
        jdbc.update("update event_results set recorded_at = null where id = ?", nullTime.getId());
        var firstNull = add(race, cara, "12.2"); var secondNull = add(race, cara, "12.20");
        jdbc.update("update event_results set recorded_at = null where id in (?, ?)", firstNull.getId(), secondNull.getId());
        Map<Long, Long> selected = new HashMap<>(); rank(race).content().forEach(row -> selected.put(row.athleteId(), row.resultId()));
        assertEquals(earlier.getId(), selected.get(ada.getId())); assertEquals(knownTime.getId(), selected.get(ben.getId()));
        assertEquals(firstNull.getId(), selected.get(cara.getId()));
        for (var athlete : List.of(ada, ben, cara))
            assertEquals(history.history(athlete.getId(), race.getCategory().getId()).performances().get(0).personalBest().resultId(), selected.get(athlete.getId()));
    }

    @Test void filtersUseOneExplicitReferenceDateRatherThanAgeAtEachCompetition() {
        add(race, ada, "12.4"); add(laterRace, ben, "12.0"); add(race, unknown, "12.8");
        assertEquals(3, rank(race).totalElements());
        var before = rankings.staff(race.getCategory().getId(), " women ", 14, 14, LocalDate.of(2026, 5, 14), 0, 25);
        assertEquals(Status.NO_MATCHING_ATHLETES, before.status());
        var birthday = rankings.staff(race.getCategory().getId(), "FEMALE", 14, 14, LocalDate.of(2026, 5, 15), 0, 25);
        assertEquals(1, birthday.totalElements()); assertEquals(ada.getId(), birthday.content().get(0).athleteId());
        assertEquals(14, birthday.content().get(0).ageAtReference()); assertEquals("Female", birthday.filters().gender());
        assertEquals(1, rankings.staff(race.getCategory().getId(), null, null, 14, LocalDate.of(2026, 5, 15), 0, 25).totalElements());
        assertEquals(0, rankings.staff(race.getCategory().getId(), null, 0, 150, LocalDate.of(2009, 1, 1), 0, 25).totalElements());
        assertEquals(3, rankings.staff(race.getCategory().getId(), "  ", null, null, null, 0, 25).totalElements());
    }

    @Test void leapDayBirthdaysAdvanceOnMarchFirstInANonLeapYear() {
        var leap = athlete("Leap", "Birthday", "2012-02-29", "Female"); add(race, leap, "12.4");
        assertEquals(1, rankings.staff(race.getCategory().getId(), null, 13, 13, LocalDate.of(2026, 2, 28), 0, 25).totalElements());
        assertEquals(0, rankings.staff(race.getCategory().getId(), null, 14, 14, LocalDate.of(2026, 2, 28), 0, 25).totalElements());
        assertEquals(1, rankings.staff(race.getCategory().getId(), null, 14, 14, LocalDate.of(2026, 3, 1), 0, 25).totalElements());
    }

    @Test void importsInvalidScoresOtherDefinitionsAndOppositeOrderOverridesCannotDominateValidResults() {
        var valid = add(race, ada, "12.4");
        for (String status : List.of("NT", "DNS", "DNF", "DQ", "FOUL")) add(race, ben, status);
        var wrongUnit = new EventResult(race, race.getCategory(), ada, "1", BigDecimal.ONE);
        wrongUnit.setMeasurement(ResultUnit.METRES, ResultStatus.VALID); eventResultRepository.save(wrongUnit);
        eventResultRepository.save(new EventResult(race, race.getCategory(), ada, "-1", new BigDecimal("-1")));
        eventResultRepository.save(new EventResult(race, race.getCategory(), ben, "12.0", null));
        var unparsed = new EventResult(race, race.getCategory(), ben, "12.2h", null);
        unparsed.setMeasurement(ResultUnit.SECONDS, ResultStatus.UNPARSED); eventResultRepository.save(unparsed);
        eventResultRepository.save(new EventResult(null, race.getCategory(), ada, "0.5", new BigDecimal("0.5")));
        var other = event(early, "200m", RankingOrder.ASC); add(other, ada, "0.1");
        jdbc.update("update events set ranking_order = 'DESC' where id = ?", laterRace.getId());
        add(laterRace, ada, "0.01");
        var ranked = rank(race);
        assertEquals(1, ranked.totalElements()); assertEquals(valid.getId(), ranked.content().get(0).resultId());
    }

    @Test void noRankableResultsAndNoMatchingFiltersHaveDistinctEmptyStates() {
        events.assignParticipant(race.getId(), new EventParticipantRequest(ada.getId()));
        assertEquals(Status.NO_EVENT_RESULTS, rank(race).status());
        add(race, ada, "DNS"); assertEquals(Status.NO_EVENT_RESULTS, rank(race).status());
        add(race, ada, "12.4");
        assertEquals(Status.NO_MATCHING_ATHLETES, rankings.staff(race.getCategory().getId(), "Male", null, null, null, 0, 25).status());
        var empty = event(early, "Long Jump", RankingOrder.DESC);
        assertEquals(Status.NO_EVENT_RESULTS, rankings.staff(empty.getCategory().getId(), "Male", null, null, null, 0, 25).status());
        assertTrue(rankings.categories().stream().anyMatch(category -> category.id().equals(empty.getCategory().getId())));
    }

    @Test void manyAttemptsNeverConsumeOtherAthletesPagesAndReadsDoNotWriteResults() {
        for (int i = 0; i < 30; i++) add(race, ada, "12.4");
        add(race, ben, "13.0");
        long count = eventResultRepository.count();
        var page = rankings.staff(race.getCategory().getId(), null, null, null, null, 1, 1);
        assertEquals(ben.getId(), page.content().get(0).athleteId()); assertEquals(2, page.content().get(0).position());
        assertEquals(2, page.totalElements()); assertEquals(2, page.totalPages()); assertEquals(count, eventResultRepository.count());
    }

    @Test void editsDeletionAndArchiveStateUseCanonicalRecordsWithoutPersistedRankCopies() {
        var first = add(race, ada, "12.0"); var fallback = add(laterRace, ada, "12.5"); var benResult = add(race, ben, "12.2");
        assertEquals(ada.getId(), rank(race).content().get(0).athleteId());
        events.updateResult(race.getId(), first.getId(), new EventResultRequest(ada.getId(), "13.0", null));
        assertEquals(ben.getId(), rank(race).content().get(0).athleteId());
        assertEquals(fallback.getId(), rank(race).content().get(1).resultId());
        athletes.setArchived(List.of(ben.getId()), true);
        assertTrue(rank(race).content().get(0).archived());
        events.deleteResult(race.getId(), benResult.getId());
        assertEquals(1, rank(race).totalElements()); assertEquals(ada.getId(), rank(race).content().get(0).athleteId());
    }

    @Test void legacyMissingCategoryUnitAndEventOrderUseTheSameFallbackAsHistory() {
        var result = add(race, ada, "12.4");
        jdbc.update("update event_categories set unit = null where id = ?", race.getCategory().getId());
        jdbc.update("update events set ranking_order = null where id = ?", race.getId());
        assertEquals(result.getId(), rank(race).content().get(0).resultId());
        assertEquals(ResultUnit.SECONDS, rank(race).category().unit());
    }

    @Test void selectedCategoryAndFiltersAreValidatedBeforeQueries() throws Exception {
        mockMvc.perform(get("/api/rankings")).andExpect(status().isBadRequest());
        for (String category : List.of("0", "-1", "bad"))
            mockMvc.perform(get("/api/rankings").param("categoryId", category)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rankings").param("categoryId", Long.toString(Long.MAX_VALUE))).andExpect(status().isNotFound());
        String category = race.getCategory().getId().toString();
        mockMvc.perform(get("/api/rankings").param("categoryId", category).param("minAge", "12")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rankings").param("categoryId", category).param("minAge", "15").param("maxAge", "12").param("ageAsOf", "2026-05-15")).andExpect(status().isBadRequest());
        for (String age : List.of("-1", "151"))
            mockMvc.perform(get("/api/rankings").param("categoryId", category).param("maxAge", age).param("ageAsOf", "2026-05-15")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rankings").param("categoryId", category).param("ageAsOf", "bad-date")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rankings").param("categoryId", category).param("page", "-1")).andExpect(status().isBadRequest());
        for (String size : List.of("0", "101"))
            mockMvc.perform(get("/api/rankings").param("categoryId", category).param("size", size)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/rankings").param("categoryId", category).param("gender", "x".repeat(31))).andExpect(status().isBadRequest());
        assertThrows(IllegalArgumentException.class, () -> rankings.staff(race.getCategory().getId(), null, null, null, LocalDate.MAX, 0, 25));
    }
}
