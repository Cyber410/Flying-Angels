package com.flyingangels.backend.event;

import com.flyingangels.backend.event.dto.PerformanceSummary;
import com.flyingangels.backend.event.dto.PerformanceSummary.Point;
import com.flyingangels.backend.event.dto.PerformanceSummary.ProgressionStatus;
import org.springframework.stereotype.Service;
import java.util.*;

/** Derives recorded competition bests without copying or changing result records. */
@Service
public class PerformanceSummaryService {
    public static final Comparator<EventResult> CHRONOLOGICAL = Comparator
            .comparing((EventResult r) -> r.getEvent().getEventDate(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(EventResult::getRecordedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(EventResult::getId);

    public boolean eligible(EventResult result) {
        return CompetitionResultPolicy.eligible(result);
    }

    public List<PerformanceSummary> summarize(Collection<Event> events, List<EventResult> results) {
        Map<SeriesKey, EventCategory> definitions = new HashMap<>();
        for (var event : events) definitions.putIfAbsent(SeriesKey.from(event), event.getCategory());
        Map<SeriesKey, List<EventResult>> grouped = new HashMap<>();
        for (var result : results) {
            if (result.getEvent() != null) grouped.computeIfAbsent(SeriesKey.from(result.getEvent()), ignored -> new ArrayList<>()).add(result);
        }
        return definitions.entrySet().stream()
                .sorted(Comparator.<Map.Entry<SeriesKey, EventCategory>, String>comparing(e -> e.getValue().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(e -> e.getKey().categoryId()).thenComparing(e -> e.getKey().unit())
                        .thenComparing(e -> e.getKey().order(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(entry -> summarize(entry.getKey(), entry.getValue(), grouped.getOrDefault(entry.getKey(), List.of())))
                .toList();
    }

    private PerformanceSummary summarize(SeriesKey key, EventCategory category, List<EventResult> attempts) {
        Comparator<EventResult> byScore = Comparator.comparing(EventResult::getScore);
        if (key.order() == RankingOrder.DESC) byScore = byScore.reversed();
        Comparator<EventResult> bestFirst = byScore.thenComparing(CHRONOLOGICAL);
        var valid = attempts.stream().filter(this::eligible).sorted(CHRONOLOGICAL).toList();
        var personalBest = valid.stream().min(bestFirst).map(Point::from).orElse(null);
        Map<Long, EventResult> perCompetition = new LinkedHashMap<>();
        for (var result : valid) perCompetition.merge(result.getEvent().getId(), result,
                (previous, next) -> bestFirst.compare(previous, next) <= 0 ? previous : next);
        var progression = perCompetition.values().stream().sorted(CHRONOLOGICAL).map(Point::from).toList();
        var status = progression.isEmpty() ? ProgressionStatus.NO_VALID_PERFORMANCE
                : progression.size() == 1 ? ProgressionStatus.SINGLE_COMPETITION : ProgressionStatus.PROGRESSION_AVAILABLE;
        return new PerformanceSummary(key.categoryId(), category.getName(), key.unit(), key.order(), attempts.size(), valid.size(), personalBest, status, progression);
    }

    /** Event instances can override order; never silently compare incompatible configurations. */
    private record SeriesKey(Long categoryId, ResultUnit unit, RankingOrder order) {
        static SeriesKey from(Event event) {
            var category = event.getCategory();
            return new SeriesKey(category.getId(), category.getUnit(), CompetitionResultPolicy.order(event));
        }
    }
}
