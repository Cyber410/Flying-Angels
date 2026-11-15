package com.flyingangels.backend.availability;

import com.flyingangels.backend.meet.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;
import static com.flyingangels.backend.availability.AvailabilityDtos.*;

@Repository @RequiredArgsConstructor
public class AvailabilityOverviewRepository {
    private final EntityManager entityManager;

    public Summary summary(Long meetId) {
        var cb = entityManager.getCriteriaBuilder(); var query = cb.createTupleQuery(); var request = query.from(AvailabilityRequest.class);
        var active = cb.gt(request.get("assignmentCount"), 0);
        query.multiselect(cb.count(request), cb.coalesce(count(cb, status(cb, request, Status.PENDING)), 0L),
                cb.coalesce(count(cb, status(cb, request, Status.AVAILABLE)), 0L), cb.coalesce(count(cb, status(cb, request, Status.UNAVAILABLE)), 0L))
                .where(meetId == null ? active : cb.and(active, cb.equal(request.get("meet").get("id"), meetId)));
        var row = entityManager.createQuery(query).getSingleResult();
        return new Summary(row.get(0, Long.class), row.get(1, Long.class), row.get(2, Long.class), row.get(3, Long.class));
    }

    /** Counts request rows, never event assignments or historical confirmations. */
    public Map<Long, Summary> summaries(Collection<Long> meetIds) {
        if (meetIds.isEmpty()) return Map.of();
        var cb = entityManager.getCriteriaBuilder(); var query = cb.createTupleQuery();
        var request = query.from(AvailabilityRequest.class);
        var meetId = request.get("meet").<Long>get("id");
        query.multiselect(meetId, cb.count(request), count(cb, status(cb, request, Status.AWAITING_RESPONSE)),
                count(cb, status(cb, request, Status.AVAILABLE)), count(cb, status(cb, request, Status.UNAVAILABLE)))
                .where(meetId.in(meetIds), cb.gt(request.get("assignmentCount"), 0)).groupBy(meetId);
        Map<Long, Summary> result = new HashMap<>();
        for (var row : entityManager.createQuery(query).getResultList()) result.put(row.get(0, Long.class),
                new Summary(row.get(1, Long.class), row.get(2, Long.class), row.get(3, Long.class), row.get(4, Long.class)));
        return result;
    }

    public Page<AvailabilityRequest> search(Long meetId, Status status, MeetView view, String search,
                                             LocalDate today, Pageable pageable) {
        var cb = entityManager.getCriteriaBuilder();
        var countQuery = cb.createQuery(Long.class); var counted = countQuery.from(AvailabilityRequest.class);
        countQuery.select(cb.count(counted)).where(filters(cb, counted, meetId, status, view, search, today));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        if (pageable.getOffset() >= total) return new PageImpl<>(List.of(), pageable, total);
        var query = cb.createQuery(AvailabilityRequest.class); var request = query.from(AvailabilityRequest.class);
        request.fetch("meet"); request.fetch("athlete");
        var start = MeetView.startDate(cb, request.get("meet"));
        query.select(request).where(filters(cb, request, meetId, status, view, search, today))
                .orderBy(cb.asc(cb.<Integer>selectCase().when(cb.isNull(start), 1).otherwise(0)),
                        view == MeetView.UPCOMING ? cb.asc(start) : cb.desc(start),
                        cb.asc(request.get("meet").get("id")),
                        cb.asc(cb.lower(request.get("athlete").get("lastName"))),
                        cb.asc(cb.lower(request.get("athlete").get("firstName"))), cb.asc(request.get("id")));
        var rows = entityManager.createQuery(query).setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList();
        return new PageImpl<>(rows, pageable, total);
    }

    private Predicate[] filters(CriteriaBuilder cb, Root<AvailabilityRequest> request, Long meetId, Status status,
                                MeetView view, String search, LocalDate today) {
        List<Predicate> filters = new ArrayList<>();
        filters.add(cb.gt(request.get("assignmentCount"), 0));
        filters.add(view.predicate(cb, request.get("meet"), today));
        if (meetId != null) filters.add(cb.equal(request.get("meet").get("id"), meetId));
        if (status != null) filters.add(status(cb, request, status));
        if (!search.isEmpty()) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            var name = cb.concat(cb.concat(request.get("athlete").get("firstName"), " "), request.get("athlete").get("lastName"));
            filters.add(cb.or(cb.like(cb.lower(name), pattern, '!'),
                    cb.like(cb.lower(request.get("meet").get("name")), pattern, '!')));
        }
        return filters.toArray(Predicate[]::new);
    }
    /** Match the entity's effective status, including confirmations invalidated by assignment changes. */
    private Predicate status(CriteriaBuilder cb, Root<AvailabilityRequest> request, Status status) {
        var covered = cb.equal(request.get("answeredRevision"), request.get("assignmentRevision"));
        return switch (status) {
            case PENDING, AWAITING_RESPONSE -> cb.or(cb.isNull(request.get("lastAnswer")), cb.isNull(request.get("answeredRevision")),
                    cb.notEqual(request.get("answeredRevision"), request.get("assignmentRevision")));
            case AVAILABLE -> cb.and(covered, cb.equal(request.get("lastAnswer"), Answer.AVAILABLE));
            case UNAVAILABLE -> cb.and(covered, cb.equal(request.get("lastAnswer"), Answer.UNAVAILABLE));
        };
    }
    private Expression<Long> count(CriteriaBuilder cb, Predicate predicate) {
        return cb.sum(cb.<Long>selectCase().when(predicate, 1L).otherwise(0L));
    }
}
