package com.flyingangels.backend.meet.dto;

import com.flyingangels.backend.availability.AvailabilityDtos.Summary;
import com.flyingangels.backend.meet.Meet;
import java.time.LocalDate;

/** Retains the meet-list fields while adding one availability summary per meet. */
public record MeetOverviewResponse(Long id, String name, LocalDate meetDate, String location, String externalUrl,
                                    long eventCount, LocalDate startDate, LocalDate endDate, Summary availabilitySummary, java.time.LocalTime startTime) {
    public static MeetOverviewResponse from(Meet meet, long eventCount, Summary summary) {
        return new MeetOverviewResponse(meet.getId(), meet.getName(), meet.getMeetDate(), meet.getLocation(),
                meet.getExternalUrl(), eventCount, meet.getStartDate(), meet.getEndDate(), summary, meet.getStartTime());
    }
}
