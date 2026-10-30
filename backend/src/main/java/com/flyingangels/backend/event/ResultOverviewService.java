package com.flyingangels.backend.event;

import com.flyingangels.backend.athlete.AgeRange;
import com.flyingangels.backend.event.dto.ResultOverviewResponse;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ResultOverviewService {
    private final EventResultRepository results;

    @Transactional(readOnly=true)
    public ResultOverviewResponse search(String search,Long meetId,Long eventId,Long categoryId,String gender,
                                         Integer minAge,Integer maxAge,String sort,int page,int size) {
        var ages = new AgeRange(minAge,maxAge);
        for (Long id : new Long[]{meetId,eventId,categoryId})
            if (id != null && id <= 0) throw new IllegalArgumentException("Filter IDs must be positive");
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be nonnegative and size between 1 and 100");
        String ordering = clean(sort);
        if (!List.of("date","athlete","performance").contains(ordering))
            throw new IllegalArgumentException("sort must be date, athlete or performance");
        String name = clean(search);
        String sex = clean(gender);
        Specification<EventResult> filter = (root,query,cb) -> {
            var event = root.join("event",JoinType.INNER);
            var meet = event.join("meet",JoinType.INNER);
            var athlete = root.join("athlete");
            var category = root.join("category");
            var predicates = new ArrayList<Predicate>();
            if (!name.isEmpty()) {
                String literal = name.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
                predicates.add(cb.like(cb.lower(cb.concat(cb.concat(athlete.get("firstName")," "),athlete.get("lastName"))),"%"+literal+"%",'\\'));
            }
            if (meetId != null) predicates.add(cb.equal(meet.get("id"),meetId));
            if (eventId != null) predicates.add(cb.equal(event.get("id"),eventId));
            if (categoryId != null) predicates.add(cb.equal(category.get("id"),categoryId));
            if (!sex.isEmpty()) predicates.add(cb.equal(cb.lower(athlete.get("gender")),sex));
            var reference = cb.<LocalDate>coalesce().value(meet.get("startDate")).value(meet.get("meetDate"));
            predicates.add(ages.predicate(cb,athlete.get("dateOfBirth"),reference));
            // Avoid ORDER BY in the count query used for pagination.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                var order = new ArrayList<jakarta.persistence.criteria.Order>();
                if (ordering.equals("performance")) {
                    // Compare attempts only within the same reusable event definition.
                    order.add(cb.asc(cb.lower(category.get("name"))));
                    order.add(cb.asc(category.get("id")));
                    var valid = cb.and(cb.equal(root.get("status"),ResultStatus.VALID),cb.isNotNull(root.get("score")));
                    order.add(cb.asc(cb.<Integer>selectCase().when(valid,0).otherwise(1)));
                    var score = cb.<BigDecimal>selectCase()
                            .when(cb.equal(event.get("rankingOrder"),RankingOrder.DESC),cb.neg(root.<BigDecimal>get("score")))
                            .otherwise(root.get("score"));
                    order.add(cb.asc(score));
                } else if (ordering.equals("athlete")) {
                    order.add(cb.asc(cb.lower(athlete.get("lastName"))));
                    order.add(cb.asc(cb.lower(athlete.get("firstName"))));
                }
                order.add(cb.desc(event.get("eventDate")));
                order.add(cb.asc(cb.lower(athlete.get("lastName"))));
                order.add(cb.asc(cb.lower(athlete.get("firstName"))));
                order.add(cb.asc(root.get("id")));
                query.orderBy(order);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return ResultOverviewResponse.from(results.findAll(filter,PageRequest.of(page,size)));
    }
    private static String clean(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
}
