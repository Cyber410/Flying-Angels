package com.flyingangels.backend.availability;

import com.flyingangels.backend.meet.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import static com.flyingangels.backend.availability.AvailabilityDtos.*;

@Service @RequiredArgsConstructor
public class AvailabilityOverviewService {
    private final AvailabilityOverviewRepository overview;
    private final MeetRepository meets;
    private final Clock clock;
    private final com.flyingangels.backend.event.EventParticipationRepository assignments;
    private final AvailabilityResponseRepository responses;

    @Transactional(readOnly = true)
    public OverviewPage search(Long meetId, Status status, MeetView view, String search, int page, int size) {
        if (meetId != null && meetId < 1) throw new IllegalArgumentException("meetId must be positive");
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("page must be nonnegative and size must be 1..100 with a supported offset");
        search = search == null ? "" : search.trim();
        if (search.length() > 200) throw new IllegalArgumentException("Search must be 200 characters or fewer");
        if (meetId != null && !meets.existsById(meetId)) throw new MeetService.MeetNotFoundException(meetId);
        var effectiveView = view == null ? MeetView.ALL : view;
        status = status == null ? null : status.canonical();
        LocalDate today = LocalDate.now(clock);
        var result = overview.search(meetId, status, effectiveView, search, today, PageRequest.of(page, size));
        var ids = result.getContent().stream().map(AvailabilityRequest::getId).toList();
        record Pair(Long meetId, Long athleteId) {}
        java.util.Map<Pair, java.util.List<AssignedEvent>> events = new java.util.HashMap<>();
        java.util.Map<Long, ResponseAudit> latest = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            for (var p : assignments.findForAvailabilityRequests(ids)) events.computeIfAbsent(new Pair(p.getEvent().getMeet().getId(), p.getAthlete().getId()), k -> new java.util.ArrayList<>())
                    .add(new AssignedEvent(p.getEvent().getId(), p.getEvent().getName(), p.getEvent().getCategory().getId(), p.getEvent().getCategory().getName(), p.getEvent().getEventDate()));
            responses.latestForRequests(ids).forEach(r -> latest.put(r.getRequest().getId(), ResponseAudit.from(r)));
        }
        return new OverviewPage(new OverviewFilters(meetId, status, effectiveView, search, today),
                result.getContent().stream().map(r -> OverviewRow.from(r, clock, events.getOrDefault(new Pair(r.getMeet().getId(), r.getAthlete().getId()), java.util.List.of()), latest.get(r.getId()))).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(), overview.summary(meetId));
    }
}
