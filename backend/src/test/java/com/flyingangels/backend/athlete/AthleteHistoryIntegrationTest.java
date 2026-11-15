package com.flyingangels.backend.athlete;

import com.flyingangels.backend.DatabaseIntegrationTest;
import com.flyingangels.backend.athlete.dto.*;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.*;
import com.flyingangels.backend.meet.*;
import com.flyingangels.backend.meet.dto.MeetRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AthleteHistoryIntegrationTest extends DatabaseIntegrationTest {
    @Autowired AthleteHistoryService history;
    @Autowired AthleteService athletes;
    @Autowired MeetService meets;
    @Autowired EventService events;
    @Autowired EventCategoryService categories;
    private Athlete ada;
    private Meet early, late;

    @BeforeEach void seed() {
        ada = athletes.create(new AthleteRequest("Ada", "Runner", "Staff note", "", "", LocalDate.of(2010, 1, 1), "Female", List.of()));
        early = meets.create(new MeetRequest("Spring Meet", LocalDate.of(2026, 5, 10), "Track", null));
        late = meets.create(new MeetRequest("Summer Meet", LocalDate.of(2026, 6, 10), "Field", null));
    }
    private Event event(Meet meet, String name, RankingOrder order) {
        return events.create(new EventRequest(name, meet.getStartDate(), meet.getLocation(), null, null, order, meet.getId(), null, name));
    }
    private EventResult result(Event event, String value, String label) {
        return events.addResult(event.getId(), new EventResultRequest(ada.getId(), value, null, null, null, label));
    }
    private PerformanceSummary summary(AthleteHistoryResponse data, Long categoryId) {
        return data.performances().stream().filter(s -> s.categoryId().equals(categoryId)).findFirst().orElseThrow();
    }
    private void score(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }

    @Test void noHistoryHasAnExplicitEmptyStateAndReadDoesNotCreateData() throws Exception {
        var data = history.history(ada.getId(), null);
        assertEquals(AthleteHistoryResponse.HistoryStatus.NO_COMPETITION_HISTORY, data.status());
        assertEquals(ada.getId(), data.athlete().id());
        assertTrue(data.meets().isEmpty()); assertTrue(data.performances().isEmpty()); assertTrue(data.unlinkedResults().isEmpty());
        mockMvc.perform(get("/api/athletes/{id}/history", ada.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_COMPETITION_HISTORY"));
        assertEquals(0, eventResultRepository.count()); assertEquals(0, participationRepository.count());
    }

    @Test void assignedEventsWithoutResultsRemainVisibleWithoutAnInventedPersonalBest() {
        var race = event(early, "100m", RankingOrder.ASC);
        var jump = event(early, "Long Jump", RankingOrder.DESC);
        var assignment = events.assignParticipant(race.getId(), new EventParticipantRequest(ada.getId()));
        events.assignParticipant(jump.getId(), new EventParticipantRequest(ada.getId()));
        var data = history.history(ada.getId(), null);
        assertEquals(AthleteHistoryResponse.HistoryStatus.AVAILABLE, data.status());
        assertEquals(2, data.meets().get(0).events().size());
        assertEquals(assignment.getId(), data.meets().get(0).events().get(0).participationId());
        assertTrue(data.meets().get(0).events().stream().allMatch(e -> e.status() == AthleteHistoryResponse.EventHistoryStatus.NO_RESULTS && e.attempts().isEmpty()));
        assertTrue(data.performances().stream().allMatch(s -> s.personalBest() == null && s.progression().isEmpty()
                && s.progressionStatus() == PerformanceSummary.ProgressionStatus.NO_VALID_PERFORMANCE));
    }

    @Test void historyRetainsEveryLabeledAttemptAndProgressionUsesCompetitionDatesRatherThanEntryDates() throws Exception {
        var earlyRace = event(early, "100m", RankingOrder.ASC);
        var lateRace = event(late, "100m", RankingOrder.ASC);
        var lateResult = result(lateRace, "12.1s", "Final"); // Recorded before the older competition.
        var heat = result(earlyRace, "13.0s", "Heat 1");
        var finalResult = result(earlyRace, "12.4s", "Final");
        var other = athletes.create(new AthleteRequest("Other", "Runner", "", "", "", LocalDate.of(2011, 1, 1), "Male", List.of()));
        events.addResult(earlyRace.getId(), new EventResultRequest(other.getId(), "10.0", null));
        var data = history.history(ada.getId(), null);
        assertEquals(List.of(early.getId(), late.getId()), data.meets().stream().map(AthleteHistoryResponse.MeetHistory::meetId).toList());
        assertEquals(List.of(heat.getId(), finalResult.getId()), data.meets().get(0).events().get(0).attempts().stream().map(AthleteHistoryResponse.Attempt::resultId).toList());
        assertEquals(List.of("Heat 1", "Final"), data.meets().get(0).events().get(0).attempts().stream().map(AthleteHistoryResponse.Attempt::resultLabel).toList());
        var summary = summary(data, earlyRace.getCategory().getId());
        assertEquals(3, summary.recordedAttemptCount()); assertEquals(3, summary.validAttemptCount());
        assertEquals(lateResult.getId(), summary.personalBest().resultId()); score("12.1", summary.personalBest().score());
        assertEquals(List.of(finalResult.getId(), lateResult.getId()), summary.progression().stream().map(PerformanceSummary.Point::resultId).toList());
        assertEquals(PerformanceSummary.ProgressionStatus.PROGRESSION_AVAILABLE, summary.progressionStatus());
        mockMvc.perform(get("/api/athletes/{id}/history", ada.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.meets[0].events[0].attempts[0].resultLabel").value("Heat 1"))
                .andExpect(jsonPath("$.performances[0].personalBest.score").value(12.1));
    }

    @Test void timedMeasuredAndPointsBestsUseNormalizedScoresAndTheConfiguredDirection() {
        var race = event(early, "100m", RankingOrder.ASC);
        var jump = event(early, "Long Jump", RankingOrder.DESC);
        var points = event(early, "Decathlon points", RankingOrder.DESC);
        result(race, "12.40s", "Heat"); var bestTime = result(race, "12.1s", "Final");
        result(jump, "435cm", "Attempt 1"); var bestJump = result(jump, "4.70m", "Attempt 2");
        result(points, "1200", null); var bestPoints = result(points, "1250", null);
        var data = history.history(ada.getId(), null);
        assertEquals(bestTime.getId(), summary(data, race.getCategory().getId()).personalBest().resultId());
        assertEquals(bestJump.getId(), summary(data, jump.getCategory().getId()).personalBest().resultId());
        assertEquals(bestPoints.getId(), summary(data, points.getCategory().getId()).personalBest().resultId());
        score("4.70", summary(data, jump.getCategory().getId()).personalBest().score());
        assertEquals(ResultUnit.POINTS, summary(data, points.getCategory().getId()).unit());
    }

    @Test void nonPerformancesAndUnusableScoresStayVisibleButDoNotBecomePersonalBests() {
        var race = event(early, "100m", RankingOrder.ASC);
        for (String status : List.of("NT", "DNS", "DNF", "DQ", "FOUL")) result(race, status, status);
        var unparsed = new EventResult(race, race.getCategory(), ada, "12.2h", null);
        unparsed.setMeasurement(ResultUnit.SECONDS, ResultStatus.UNPARSED); eventResultRepository.save(unparsed);
        var pending = new EventResult(race, race.getCategory(), ada, null, null); eventResultRepository.save(pending);
        var nullScore = new EventResult(race, race.getCategory(), ada, "12.2", null); eventResultRepository.save(nullScore);
        var wrongUnit = new EventResult(race, race.getCategory(), ada, "1", BigDecimal.ONE);
        wrongUnit.setMeasurement(ResultUnit.METRES, ResultStatus.VALID); eventResultRepository.save(wrongUnit);
        eventResultRepository.save(new EventResult(race, race.getCategory(), ada, "-1", new BigDecimal("-1")));
        var data = history.history(ada.getId(), null);
        var summary = summary(data, race.getCategory().getId());
        assertEquals(10, summary.recordedAttemptCount()); assertEquals(0, summary.validAttemptCount());
        assertNull(summary.personalBest()); assertTrue(summary.progression().isEmpty());
        assertTrue(data.meets().get(0).events().get(0).attempts().stream().noneMatch(AthleteHistoryResponse.Attempt::eligibleForPersonalBest));
    }

    @Test void oneCompetitionNeverClaimsAMultiPointTrendAndTiedBestsPreferEarlierCompetition() {
        var race = event(early, "100m", RankingOrder.ASC);
        var first = result(race, "12.40", "Heat 1");
        result(race, "12.4", "Final");
        var single = summary(history.history(ada.getId(), null), race.getCategory().getId());
        assertEquals(first.getId(), single.personalBest().resultId());
        assertEquals(1, single.progression().size()); assertEquals(PerformanceSummary.ProgressionStatus.SINGLE_COMPETITION, single.progressionStatus());
        result(event(late, "100m", RankingOrder.ASC), "12.400", "Final");
        var multiple = summary(history.history(ada.getId(), null), race.getCategory().getId());
        assertEquals(first.getId(), multiple.personalBest().resultId()); assertEquals(2, multiple.progression().size());
    }

    @Test void categoryFilterPreservesAvailableOptionsAndHasAnExplicitNoMatchesState() {
        var race = event(early, "100m", RankingOrder.ASC);
        var jump = event(early, "Long Jump", RankingOrder.DESC);
        result(race, "12.4", null); result(jump, "4.5m", null);
        var filtered = history.history(ada.getId(), jump.getCategory().getId());
        assertEquals(2, filtered.availableCategories().size()); assertEquals(1, filtered.performances().size());
        assertEquals(jump.getId(), filtered.meets().get(0).events().get(0).eventId());
        var unrelated = categories.create(new EventCategoryRequest("200m", RankingOrder.ASC, ResultUnit.SECONDS));
        var empty = history.history(ada.getId(), unrelated.getId());
        assertEquals(AthleteHistoryResponse.HistoryStatus.NO_MATCHING_EVENTS, empty.status());
        assertEquals(2, empty.availableCategories().size()); assertTrue(empty.meets().isEmpty()); assertTrue(empty.performances().isEmpty());
    }

    @Test void importsRemainSeparateAndDoNotInventACompetitionDateOrBeatACompetitionPersonalBest() {
        var imported = athletes.create(new AthleteRequest("Imported", "Runner", "", "", "", LocalDate.of(2010, 1, 1), "Female",
                List.of(new AthleteEventRequest("100m", "9.5"), new AthleteEventRequest("Long Jump", "5.0m"))));
        events.recordImportedResults(imported, List.of(imported.getEvents().get(0)));
        var race = event(early, "100m", RankingOrder.ASC);
        var recorded = events.addResult(race.getId(), new EventResultRequest(imported.getId(), "12.4", null));
        var data = history.history(imported.getId(), null);
        assertEquals(recorded.getId(), summary(data, race.getCategory().getId()).personalBest().resultId());
        assertEquals(2, data.unlinkedResults().size());
        assertTrue(data.unlinkedResults().stream().allMatch(r -> r.meetId() == null && r.eventId() == null));
        assertEquals(1, data.unlinkedResults().stream().filter(r -> r.categoryName().equals("100m")).count());
        var filtered = history.history(imported.getId(), race.getCategory().getId());
        assertEquals(1, filtered.unlinkedResults().size());
    }

    @Test void editedAndDeletedResultsImmediatelyChangeHistoryAndPersonalBestsWithoutLosingAssignments() {
        var race = event(early, "100m", RankingOrder.ASC);
        var heat = result(race, "12.4", "Heat 1"); var finalResult = result(race, "12.1", "Final");
        events.updateResult(race.getId(), finalResult.getId(), new EventResultRequest(ada.getId(), "13.0", null));
        var changed = history.history(ada.getId(), null);
        assertEquals(heat.getId(), summary(changed, race.getCategory().getId()).personalBest().resultId());
        assertEquals("Final", changed.meets().get(0).events().get(0).attempts().get(1).resultLabel());
        events.deleteResult(race.getId(), heat.getId());
        assertEquals(finalResult.getId(), summary(history.history(ada.getId(), null), race.getCategory().getId()).personalBest().resultId());
        events.deleteResult(race.getId(), finalResult.getId());
        var empty = history.history(ada.getId(), null);
        assertEquals(AthleteHistoryResponse.EventHistoryStatus.NO_RESULTS, empty.meets().get(0).events().get(0).status());
        assertNull(empty.performances().get(0).personalBest()); assertEquals(1, participationRepository.count());
    }

    @Test void differentComparisonOrdersRemainSeparateAndLegacyResultsNeedNoSyntheticAssignment() {
        var ascending = event(early, "100m", RankingOrder.ASC);
        var descending = event(late, "100m", RankingOrder.DESC);
        var raw = new EventResult(ascending, ascending.getCategory(), ada, "12.4", new BigDecimal("12.4"));
        eventResultRepository.saveAndFlush(raw); // Represents a legacy linked result with no participation row.
        result(descending, "13.0", null);
        var data = history.history(ada.getId(), ascending.getCategory().getId());
        assertEquals(2, data.performances().size());
        assertTrue(data.performances().stream().allMatch(s -> s.progression().size() == 1));
        assertNull(data.meets().get(0).events().get(0).participationId());
        assertEquals(1, participationRepository.count(), "A history read must not synthesize assignments for old results");
    }

    @Test void invalidAndMissingIdentitiesReturnUsefulHttpErrors() throws Exception {
        mockMvc.perform(get("/api/athletes/{id}/history", -1)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/athletes/{id}/history", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/athletes/{id}/history", ada.getId()).param("categoryId", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/athletes/{id}/history", ada.getId()).param("categoryId", "not-a-number")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/athletes/{id}/history", ada.getId()).param("categoryId", String.valueOf(Long.MAX_VALUE))).andExpect(status().isNotFound());
    }
}
