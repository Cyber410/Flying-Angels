package com.flyingangels.backend.ranking;

import com.flyingangels.backend.athlete.AgeRange;
import com.flyingangels.backend.event.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Selects one canonical best result per athlete in SQL, before global rank positions or pagination. */
@Repository @RequiredArgsConstructor
public class RankingQueryRepository {
    private final EntityManager entityManager;

    public List<EventResult> bestResults(EventCategory category, String gender, AgeRange ages, LocalDate ageAsOf) {
        return bestResults(category, gender, ages, ageAsOf, null, null, category.getRankingOrder());
    }
    public List<EventResult> bestResults(EventCategory category, String gender, AgeRange ages, LocalDate ageAsOf,
                                         Long meetId, Long eventId, RankingOrder order) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(EventResult.class);
        var result = query.from(EventResult.class);
        Join<EventResult, Event> event = result.join("event");
        var athlete = result.join("athlete");
        var filters = new ArrayList<Predicate>();
        filters.add(comparable(cb, result, event, category, order));
        filters.add(scope(cb, event, meetId, eventId));
        if (gender != null) filters.add(cb.equal(cb.lower(athlete.get("gender")), gender.toLowerCase(Locale.ROOT)));
        if (ages.active()) filters.add(ages.predicate(cb, athlete.get("dateOfBirth"), cb.literal(ageAsOf)));

        var better = query.subquery(Integer.class);
        var other = better.from(EventResult.class);
        Join<EventResult, Event> otherEvent = other.join("event");
        Predicate betterScore = order == RankingOrder.DESC
                ? cb.greaterThan(other.<BigDecimal>get("score"), result.get("score"))
                : cb.lessThan(other.<BigDecimal>get("score"), result.get("score"));
        var sameScore = cb.equal(other.get("score"), result.get("score"));
        better.select(cb.literal(1)).where(comparable(cb, other, otherEvent, category, order), scope(cb, otherEvent, meetId, eventId),
                cb.equal(other.get("athlete").get("id"), result.get("athlete").get("id")),
                cb.or(betterScore, cb.and(sameScore, earlier(cb, other, otherEvent, result, event))));
        filters.add(cb.not(cb.exists(better)));
        query.select(result).where(filters.toArray(Predicate[]::new));

        var graph = entityManager.createEntityGraph(EventResult.class);
        graph.addAttributeNodes("athlete", "category");
        graph.addSubgraph("event").addAttributeNodes("meet", "category");
        return entityManager.createQuery(query).setHint("jakarta.persistence.fetchgraph", graph).getResultList();
    }

    public boolean hasEligibleResults(EventCategory category) {
        return hasEligibleResults(category, null, null, category.getRankingOrder());
    }
    public boolean hasEligibleResults(EventCategory category, Long meetId, Long eventId, RankingOrder order) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Integer.class);
        var result = query.from(EventResult.class);
        Join<EventResult, Event> event = result.join("event");
        query.select(cb.literal(1)).where(comparable(cb, result, event, category, order), scope(cb, event, meetId, eventId));
        return !entityManager.createQuery(query).setMaxResults(1).getResultList().isEmpty();
    }

    private Predicate scope(CriteriaBuilder cb, Join<EventResult, Event> event, Long meetId, Long eventId) {
        return cb.and(meetId == null ? cb.conjunction() : cb.equal(event.get("meet").get("id"), meetId),
                eventId == null ? cb.conjunction() : cb.equal(event.get("id"), eventId));
    }
    private Predicate comparable(CriteriaBuilder cb, Root<EventResult> result, Join<EventResult, Event> event, EventCategory category, RankingOrder order) {
        return cb.and(CompetitionResultPolicy.eligible(cb, result, event, category.getUnit()),
                cb.equal(result.get("category").get("id"), category.getId()),
                // Compare mapped enum paths directly: a computed enum expression can bind as an ordinal on MySQL.
                cb.or(cb.equal(event.get("rankingOrder"), order),
                        cb.and(cb.isNull(event.get("rankingOrder")), cb.equal(event.get("category").get("rankingOrder"), order))));
    }

    /** Match history's tie representative: earliest competition, then known recording time, then result ID. */
    private Predicate earlier(CriteriaBuilder cb, Root<EventResult> other, Join<EventResult, Event> otherEvent,
            Root<EventResult> result, Join<EventResult, Event> event) {
        var otherDate = otherEvent.<LocalDate>get("eventDate");
        var date = event.<LocalDate>get("eventDate");
        var otherTime = other.<Instant>get("recordedAt");
        var time = result.<Instant>get("recordedAt");
        var earlierTime = cb.or(cb.and(cb.isNotNull(otherTime), cb.isNull(time)), cb.lessThan(otherTime, time));
        var sameTime = cb.or(cb.equal(otherTime, time), cb.and(cb.isNull(otherTime), cb.isNull(time)));
        return cb.or(cb.lessThan(otherDate, date), cb.and(cb.equal(otherDate, date),
                cb.or(earlierTime, cb.and(sameTime, cb.lessThan(other.<Long>get("id"), result.get("id"))))));
    }
}
