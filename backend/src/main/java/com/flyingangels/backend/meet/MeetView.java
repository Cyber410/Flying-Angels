package com.flyingangels.backend.meet;

import jakarta.persistence.criteria.*;
import java.time.LocalDate;

public enum MeetView {
    UPCOMING, PAST, ALL;

    public static boolean isUpcoming(Meet meet, LocalDate today) {
        return meet.getEndDate() != null && !meet.getEndDate().isBefore(today);
    }
    public static java.time.Instant responseDeadline(Meet meet, java.time.Clock clock) {
        return meet.getStartDate() == null ? null : meet.getStartDate().atTime(meet.getStartTime()).atZone(clock.getZone()).toInstant();
    }
    public static boolean canRespond(Meet meet, java.time.Clock clock) {
        var deadline = responseDeadline(meet, clock);
        return deadline != null && clock.instant().isBefore(deadline);
    }

    public static Expression<LocalDate> startDate(CriteriaBuilder cb, Path<Meet> meet) {
        return cb.<LocalDate>coalesce().value(meet.get("startDate")).value(meet.get("meetDate"));
    }
    public Predicate predicate(CriteriaBuilder cb, Path<Meet> meet, LocalDate today) {
        var end = cb.<LocalDate>coalesce().value(meet.get("endDate")).value(startDate(cb, meet));
        return switch (this) {
            case UPCOMING -> cb.greaterThanOrEqualTo(end, today);
            case PAST -> cb.lessThan(end, today);
            case ALL -> cb.conjunction();
        };
    }
}
