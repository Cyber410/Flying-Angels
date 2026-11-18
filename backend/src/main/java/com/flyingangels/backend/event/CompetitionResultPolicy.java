package com.flyingangels.backend.event;

import jakarta.persistence.criteria.*;
import java.math.BigDecimal;

/** The same recorded-competition eligibility rules for history and database ranking queries. */
public final class CompetitionResultPolicy {
    private CompetitionResultPolicy() {}

    public static RankingOrder order(Event event) {
        return event.getRankingOrder() == null ? event.getCategory().getRankingOrder() : event.getRankingOrder();
    }

    public static boolean eligible(EventResult result) {
        var event = result.getEvent();
        return event != null && event.getEventDate() != null && order(event) != null
                && result.getStatus() == ResultStatus.VALID && result.getScore() != null && result.getScore().signum() >= 0
                && result.getCategory().getId().equals(event.getCategory().getId())
                && result.getUnit() == event.getCategory().getUnit();
    }

    /** expectedUnit is resolved from the selected definition, including its legacy unit fallback. */
    public static Predicate eligible(CriteriaBuilder cb, Root<EventResult> result, Join<EventResult, Event> event, ResultUnit expectedUnit) {
        return cb.and(cb.isNotNull(event.get("eventDate")),
                cb.or(cb.isNotNull(event.get("rankingOrder")), cb.isNotNull(event.get("category").get("rankingOrder"))),
                cb.equal(result.get("status"), ResultStatus.VALID), cb.isNotNull(result.get("score")),
                cb.greaterThanOrEqualTo(result.<BigDecimal>get("score"), BigDecimal.ZERO),
                cb.equal(result.get("category").get("id"), event.get("category").get("id")),
                cb.equal(result.get("unit"), expectedUnit));
    }
}
