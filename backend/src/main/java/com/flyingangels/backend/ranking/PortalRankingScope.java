package com.flyingangels.backend.ranking;

import com.flyingangels.backend.event.*;
import com.flyingangels.backend.ranking.dto.RankingsResponse.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class PortalRankingScope {
    private final EventRepository events;

    public List<EventChoice> events(Long athleteId, Long meetId) {
        var choices = events.findPortalRankingChoices(athleteId);
        if (meetId != null && choices.stream().noneMatch(e -> e.getMeet().getId().equals(meetId))) throw denied();
        return choices.stream().filter(e -> meetId == null || e.getMeet().getId().equals(meetId))
                .map(e -> new EventChoice(e.getId(), e.getName(), e.getEventDate(), e.getMeet().getId(),
                        e.getMeet().getName(), e.getCategory().getId(), e.getCategory().getName(), e.getCategory().getUnit(),
                        e.getRankingOrder() == null ? e.getCategory().getRankingOrder() : e.getRankingOrder())).toList();
    }

    public List<MeetChoice> meets(Long athleteId) {
        var result = new LinkedHashMap<Long, MeetChoice>();
        for (var event : events.findPortalRankingChoices(athleteId)) {
            var meet = event.getMeet();
            result.putIfAbsent(meet.getId(), new MeetChoice(meet.getId(), meet.getName(), meet.getStartDate(), meet.getEndDate()));
        }
        return List.copyOf(result.values());
    }

    public List<Category> categories(Long athleteId) {
        var result = new LinkedHashMap<Long, Category>();
        for (var event : events.findPortalRankingChoices(athleteId))
            result.putIfAbsent(event.getCategory().getId(), Category.from(event.getCategory()));
        return List.copyOf(result.values());
    }

    public void requireEvent(Long athleteId, Long categoryId, Long meetId, Long eventId) {
        if (eventId == null) throw new IllegalArgumentException("Select one of your assigned meet events to view rankings");
        if (events(athleteId, null).stream().noneMatch(e -> e.id().equals(eventId)
                && e.categoryId().equals(categoryId) && (meetId == null || e.meetId().equals(meetId)))) throw denied();
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Rankings are available only for your assigned meet events");
    }
}
