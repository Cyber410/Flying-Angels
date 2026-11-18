package com.flyingangels.backend.meet;

import com.flyingangels.backend.event.Event;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.*;

@Repository @RequiredArgsConstructor
public class MeetOverviewRepository {
    private final EntityManager entityManager;

    public List<Meet> search(MeetView view, String search, LocalDate today) {
        var cb = entityManager.getCriteriaBuilder(); var query = cb.createQuery(Meet.class);
        var meet = query.from(Meet.class); List<Predicate> filters = new ArrayList<>();
        filters.add(view.predicate(cb, meet, today));
        if (!search.isEmpty()) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            filters.add(cb.or(cb.like(cb.lower(meet.get("name")), pattern, '!'), cb.like(cb.lower(meet.get("location")), pattern, '!')));
        }
        var start = MeetView.startDate(cb, meet);
        query.select(meet).where(filters.toArray(Predicate[]::new))
                .orderBy(cb.asc(cb.<Integer>selectCase().when(cb.isNull(start), 1).otherwise(0)),
                        view == MeetView.UPCOMING ? cb.asc(start) : cb.desc(start), cb.asc(cb.lower(meet.get("name"))), cb.asc(meet.get("id")));
        return entityManager.createQuery(query).getResultList();
    }
    public Map<Long, Long> eventCounts(Collection<Long> meetIds) {
        if (meetIds.isEmpty()) return Map.of();
        var cb = entityManager.getCriteriaBuilder(); var query = cb.createTupleQuery(); var event = query.from(Event.class);
        var meetId = event.get("meet").<Long>get("id");
        query.multiselect(meetId, cb.count(event)).where(meetId.in(meetIds)).groupBy(meetId);
        Map<Long, Long> counts = new HashMap<>();
        for (var row : entityManager.createQuery(query).getResultList()) counts.put(row.get(0, Long.class), row.get(1, Long.class));
        return counts;
    }
}
