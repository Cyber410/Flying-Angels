package com.flyingangels.backend.meet;

import com.flyingangels.backend.availability.AvailabilityOverviewRepository;
import com.flyingangels.backend.availability.AvailabilityDtos.Summary;
import com.flyingangels.backend.meet.dto.MeetOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service @RequiredArgsConstructor
public class MeetOverviewService {
    private final MeetOverviewRepository meets;
    private final AvailabilityOverviewRepository availability;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MeetOverviewResponse> search(MeetView view, String search) {
        search = search == null ? "" : search.trim();
        if (search.length() > 200) throw new IllegalArgumentException("Search must be 200 characters or fewer");
        var found = meets.search(view == null ? MeetView.ALL : view, search, LocalDate.now(clock));
        var ids = found.stream().map(Meet::getId).toList();
        var summaries = availability.summaries(ids); var eventCounts = meets.eventCounts(ids);
        return found.stream().map(m -> MeetOverviewResponse.from(m, eventCounts.getOrDefault(m.getId(), 0L),
                summaries.getOrDefault(m.getId(), Summary.empty()))).toList();
    }
}
