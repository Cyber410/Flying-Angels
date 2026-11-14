package com.flyingangels.backend.athlete;

import com.flyingangels.backend.athlete.dto.AthleteHistoryResponse;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.event.dto.EventResultResponse;
import com.flyingangels.backend.meet.Meet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.flyingangels.backend.athlete.dto.AthleteHistoryResponse.*;

@Service @RequiredArgsConstructor
public class AthleteHistoryService {
    private final AthleteRepository athletes;
    private final EventParticipationRepository participations;
    private final EventResultRepository results;
    private final EventCategoryRepository categories;
    private final PerformanceSummaryService summaries;

    @Transactional(readOnly = true)
    public AthleteHistoryResponse history(Long athleteId, Long categoryId) {
        if (athleteId == null || athleteId <= 0 || categoryId != null && categoryId <= 0)
            throw new IllegalArgumentException("Athlete and category IDs must be positive");
        var athlete = athletes.findById(athleteId).orElseThrow(() -> new AthleteService.AthleteNotFoundException(athleteId));
        EventCategory selected = categoryId == null ? null : categories.findById(categoryId)
                .orElseThrow(() -> new EventCategoryService.EventCategoryNotFoundException(categoryId));
        var assigned = participations.findByAthleteId(athleteId);
        var recorded = results.findByAthleteId(athleteId);
        Map<Long, Event> allEvents = new LinkedHashMap<>();
        Map<Long, Long> participationIds = new HashMap<>();
        for (var assignment : assigned) {
            allEvents.put(assignment.getEvent().getId(), assignment.getEvent());
            participationIds.put(assignment.getEvent().getId(), assignment.getId());
        }
        for (var result : recorded) if (result.getEvent() != null) allEvents.putIfAbsent(result.getEvent().getId(), result.getEvent());
        var availableCategories = allEvents.values().stream().map(Event::getCategory)
                .collect(java.util.stream.Collectors.toMap(EventCategory::getId, c -> c, (first, other) -> first))
                .values().stream().sorted(Comparator.comparing(EventCategory::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(EventCategory::getId))
                .map(category -> new Category(category.getId(), category.getName())).toList();
        var events = allEvents.values().stream().filter(e -> selected == null || e.getCategory().getId().equals(categoryId))
                .sorted(Comparator.comparing(Event::getEventDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Event::getId)).toList();
        Set<Long> eventIds = new HashSet<>(); events.forEach(e -> eventIds.add(e.getId()));
        var competitionResults = recorded.stream().filter(r -> r.getEvent() != null && eventIds.contains(r.getEvent().getId()))
                .sorted(PerformanceSummaryService.CHRONOLOGICAL).toList();
        Map<Long, List<EventResult>> byEvent = new HashMap<>();
        competitionResults.forEach(r -> byEvent.computeIfAbsent(r.getEvent().getId(), ignored -> new ArrayList<>()).add(r));
        Map<Long, Meet> meets = new HashMap<>();
        Map<Long, List<EventHistory>> histories = new HashMap<>();
        for (var event : events) {
            var category = event.getCategory();
            var attempts = byEvent.getOrDefault(event.getId(), List.of()).stream().map(r -> Attempt.from(r, summaries.eligible(r))).toList();
            var history = new EventHistory(event.getId(), event.getName(), event.getEventDate(), category.getId(), category.getName(),
                    category.getUnit(), event.getRankingOrder() == null ? category.getRankingOrder() : event.getRankingOrder(), participationIds.get(event.getId()),
                    attempts.isEmpty() ? EventHistoryStatus.NO_RESULTS : EventHistoryStatus.RESULTS_RECORDED, attempts);
            meets.put(event.getMeet().getId(), event.getMeet());
            histories.computeIfAbsent(event.getMeet().getId(), ignored -> new ArrayList<>()).add(history);
        }
        var meetHistory = meets.values().stream().sorted(Comparator.comparing(AthleteHistoryService::startDate,
                        Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Meet::getId))
                .map(meet -> new MeetHistory(meet.getId(), meet.getName(), startDate(meet), meet.getEndDate() == null ? startDate(meet) : meet.getEndDate(),
                        meet.getLocation(), List.copyOf(histories.get(meet.getId())))).toList();
        var status = allEvents.isEmpty() ? HistoryStatus.NO_COMPETITION_HISTORY
                : events.isEmpty() ? HistoryStatus.NO_MATCHING_EVENTS : HistoryStatus.AVAILABLE;
        return new AthleteHistoryResponse(new AthleteIdentity(athlete.getId(), athlete.getFirstName(), athlete.getLastName()), categoryId,
                status, availableCategories, meetHistory, summaries.summarize(events, competitionResults), unlinked(athlete, recorded, selected));
    }

    private List<EventResultResponse> unlinked(Athlete athlete, List<EventResult> recorded, EventCategory selected) {
        var historical = recorded.stream().filter(r -> r.getEvent() == null).sorted(Comparator.comparing(EventResult::getId)).toList();
        List<EventResultResponse> values = new ArrayList<>();
        Set<HistoricalIdentity> represented = new HashSet<>();
        historical.forEach(r -> represented.add(HistoricalIdentity.of(r.getCategory().getName(), r.getResult())));
        historical.stream().filter(r -> selected == null || r.getCategory().getId().equals(selected.getId()))
                .map(EventResultResponse::from).forEach(values::add);
        athlete.getEvents().stream().sorted(Comparator.comparing(AthleteEvent::getId)).filter(legacy -> selected == null || selected.getName().equalsIgnoreCase(legacy.getEventName().trim()))
                .filter(legacy -> !represented.contains(HistoricalIdentity.of(legacy.getEventName(), legacy.getResult())))
                .map(legacy -> EventResultResponse.fromLegacy(athlete, legacy)).forEach(values::add);
        return List.copyOf(values);
    }
    private static java.time.LocalDate startDate(Meet meet) { return meet.getStartDate() == null ? meet.getMeetDate() : meet.getStartDate(); }
    private record HistoricalIdentity(String name, String result) {
        static HistoricalIdentity of(String name, String result) { return new HistoricalIdentity(name.trim().toLowerCase(Locale.ROOT), result); }
    }
}
