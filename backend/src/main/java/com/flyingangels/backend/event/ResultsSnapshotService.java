package com.flyingangels.backend.event;

import com.flyingangels.backend.event.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ResultsSnapshotService {
    private final EventRepository events;
    private final EventParticipationRepository participants;
    private final EventResultRepository results;

    @Transactional(readOnly = true)
    public ResultsSnapshotResponse load() {
        Map<Long, Integer> counts = new HashMap<>();
        for (var row : participants.countByEvent()) counts.put((Long) row[0], Math.toIntExact((Long) row[1]));
        var metadata = events.findRankingChoices(null).stream().map(event -> new EventResponse(
                event.getId(), event.getName(), event.getEventDate(), event.getLocation(), event.getEventType(),
                event.getExternalUrl(), event.getMeet().getId(), event.getMeet().getName(),
                EventCategoryResponse.from(event.getCategory()), event.getRankingOrder(),
                counts.getOrDefault(event.getId(), 0), event.getNote() == null ? "" : event.getNote())).toList();
        return new ResultsSnapshotResponse(metadata,
                results.findForResultsOverview().stream().map(ResultOverviewResponse.Row::from).toList());
    }
}
