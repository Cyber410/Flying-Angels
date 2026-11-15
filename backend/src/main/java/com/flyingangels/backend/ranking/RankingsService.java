package com.flyingangels.backend.ranking;

import com.flyingangels.backend.athlete.*;
import com.flyingangels.backend.event.*;
import com.flyingangels.backend.ranking.dto.RankingsResponse;
import com.flyingangels.backend.ranking.dto.RankingsResponse.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

@Service @RequiredArgsConstructor
public class RankingsService {
    private final EventCategoryRepository categories;
    private final RankingQueryRepository query;
    private final AgeGroupService ageGroups;
    private final com.flyingangels.backend.meet.MeetRepository meets;
    private final EventRepository events;

    @Transactional(readOnly = true)
    public List<Category> categories() { return categories.findAllByOrderByNameAsc().stream().map(Category::from).toList(); }
    @Transactional(readOnly = true)
    public List<MeetChoice> meets() {
        return meets.findRankingChoices().stream().map(m -> new MeetChoice(m.getId(), m.getName(), m.getStartDate(), m.getEndDate())).toList();
    }
    @Transactional(readOnly = true)
    public List<EventChoice> events(Long meetId) {
        validateMeet(meetId);
        return events.findRankingChoices(meetId).stream().map(e -> new EventChoice(e.getId(), e.getName(), e.getEventDate(),
                e.getMeet().getId(), e.getMeet().getName(), e.getCategory().getId(), e.getCategory().getName(), e.getCategory().getUnit(),
                e.getRankingOrder() == null ? e.getCategory().getRankingOrder() : e.getRankingOrder())).toList();
    }

    @Transactional(readOnly = true)
    public RankingsResponse<StaffRow> staff(Long categoryId, String gender, Integer minAge, Integer maxAge, LocalDate ageAsOf, int page, int size) {
        return staff(categoryId, gender, minAge, maxAge, ageAsOf, page, size, null, null);
    }
    @Transactional(readOnly = true)
    public RankingsResponse<StaffRow> staff(Long categoryId, String gender, Integer minAge, Integer maxAge, LocalDate ageAsOf, int page, int size, Long meetId, Long eventId) {
        return rank(categoryId, gender, minAge, maxAge, ageAsOf, page, size, meetId, eventId, ranked -> {
            var result = ranked.result(); var athlete = result.getAthlete(); var event = result.getEvent();
            return new StaffRow(ranked.position(), athlete.getId(), athlete.getFirstName(), athlete.getLastName(), athlete.getGender(),
                    AgeRange.ageOn(athlete.getDateOfBirth(), ageAsOf), athlete.isArchived(), result.getId(), result.getResultLabel(), result.getResult(), result.getScore(),
                    event.getMeet().getId(), event.getMeet().getName(), event.getId(), event.getName(), event.getEventDate(), true,
                    ageGroups.classify(athlete.getDateOfBirth(), ageAsOf), ageAsOf);
        });
    }

    @Transactional(readOnly = true)
    public RankingsResponse<PortalRow> portal(Long authorizedAthleteId, Long categoryId, String gender, Integer minAge, Integer maxAge, LocalDate ageAsOf, int page, int size) {
        return portal(authorizedAthleteId, categoryId, gender, minAge, maxAge, ageAsOf, page, size, null, null);
    }
    @Transactional(readOnly = true)
    public RankingsResponse<PortalRow> portal(Long authorizedAthleteId, Long categoryId, String gender, Integer minAge, Integer maxAge, LocalDate ageAsOf, int page, int size, Long meetId, Long eventId) {
        if (authorizedAthleteId == null || authorizedAthleteId <= 0) throw new IllegalArgumentException("A scoped athlete identity is required");
        return rank(categoryId, gender, minAge, maxAge, ageAsOf, page, size, meetId, eventId, ranked -> {
            var result = ranked.result(); var athlete = result.getAthlete();
            boolean ownProfile = athlete.getId().equals(authorizedAthleteId);
            return new PortalRow(ranked.position(), athlete.getFirstName(), athlete.getLastName(), result.getResult(), result.getScore(), ownProfile,
                    ownProfile ? athlete.getId() : null, result.getEvent().getMeet().getId(), result.getEvent().getMeet().getName(),
                    result.getEvent().getId(), result.getEvent().getName(), result.getEvent().getEventDate(),
                    ageGroups.classify(athlete.getDateOfBirth(), ageAsOf), ageAsOf, result.getUnit());
        });
    }

    private <T> RankingsResponse<T> rank(Long categoryId, String gender, Integer minAge, Integer maxAge, LocalDate ageAsOf, int page, int size, Long meetId, Long eventId, Function<Ranked, T> project) {
        if (categoryId == null || categoryId <= 0) throw new IllegalArgumentException("Select a positive event category ID");
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be nonnegative and size between 1 and 100");
        var ages = new AgeRange(minAge, maxAge);
        if (ageGroups.configuration().configured() && ageAsOf == null) throw new IllegalArgumentException("ageAsOf is required when club age bands are configured");
        if (ages.active() && ageAsOf == null) throw new IllegalArgumentException("ageAsOf is required when filtering rankings by age");
        if (ageAsOf != null && (ageAsOf.getYear() < 1 || ageAsOf.getYear() > 9999)) throw new IllegalArgumentException("ageAsOf must use a year between 1 and 9999");
        String normalizedGender = gender == null || gender.isBlank() ? null : Athlete.normalizeGender(gender);
        if (normalizedGender != null && normalizedGender.length() > 30) throw new IllegalArgumentException("Gender must be 30 characters or fewer");
        var category = categories.findById(categoryId).orElseThrow(() -> new EventCategoryService.EventCategoryNotFoundException(categoryId));
        validateMeet(meetId);
        if (eventId != null && eventId < 1) throw new IllegalArgumentException("eventId must be positive");
        Event selected = null;
        if (eventId != null) {
            selected = events.findRankingEventById(eventId).orElseThrow(() -> new EventService.EventNotFoundException(eventId));
            if (!selected.getCategory().getId().equals(categoryId) || meetId != null && !selected.getMeet().getId().equals(meetId))
                throw new IllegalArgumentException("The selected event must match the category and meet");
            meetId = selected.getMeet().getId();
        } else if (meetId != null) selected = events.findByMeetIdAndCategoryId(meetId, categoryId).orElse(null);
        RankingOrder order = selected != null && selected.getRankingOrder() != null ? selected.getRankingOrder() : category.getRankingOrder();
        if (order == null) throw new IllegalArgumentException("Configure this event's ranking direction before ranking performances");
        var best = new ArrayList<>(query.bestResults(category, normalizedGender, ages, ageAsOf, meetId, eventId, order));
        Comparator<EventResult> byScore = Comparator.comparing(EventResult::getScore);
        if (order == RankingOrder.DESC) byScore = byScore.reversed();
        best.sort(byScore.thenComparing(r -> r.getAthlete().getLastName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.getAthlete().getFirstName(), String.CASE_INSENSITIVE_ORDER).thenComparing(r -> r.getAthlete().getId()));
        List<T> content = new ArrayList<>();
        long offset = (long) page * size;
        BigDecimal previous = null; int position = 0;
        for (int index = 0; index < best.size(); index++) {
            var result = best.get(index);
            if (previous == null || result.getScore().compareTo(previous) != 0) position = index + 1;
            previous = result.getScore();
            if (index >= offset && index < offset + size) content.add(project.apply(new Ranked(position, result)));
        }
        var status = !best.isEmpty() ? Status.AVAILABLE
                : (ages.active() || normalizedGender != null) && query.hasEligibleResults(category, meetId, eventId, order) ? Status.NO_MATCHING_ATHLETES : Status.NO_EVENT_RESULTS;
        return new RankingsResponse<>(new Category(category.getId(), category.getName(), category.getUnit(), order),
                new Filters(normalizedGender, minAge, maxAge, ageAsOf, ageGroups.labelForRange(minAge, maxAge), meetId, eventId), status,
                List.copyOf(content), page, size, best.size(), (int) ((best.size() + (long) size - 1) / size));
    }
    private void validateMeet(Long meetId) {
        if (meetId != null && meetId < 1) throw new IllegalArgumentException("meetId must be positive");
        if (meetId != null && !meets.existsById(meetId)) throw new com.flyingangels.backend.meet.MeetService.MeetNotFoundException(meetId);
    }
    private record Ranked(int position, EventResult result) {}
}
