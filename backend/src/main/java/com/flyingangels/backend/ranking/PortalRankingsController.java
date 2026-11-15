package com.flyingangels.backend.ranking;

import com.flyingangels.backend.auth.*;
import com.flyingangels.backend.ranking.dto.RankingsResponse;
import com.flyingangels.backend.ranking.dto.RankingsResponse.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;

@RestController @RequiredArgsConstructor
public class PortalRankingsController {
    private final RankingsService rankings;
    private final AthleteSessionService sessions;
    private final com.flyingangels.backend.athlete.AgeGroupService ageGroups;
    @GetMapping("/api/portal/rankings/categories")
    public List<Category> categories(@AuthenticationPrincipal Object identity) {
        sessions.require(identity); return rankings.categories();
    }
    @GetMapping("/api/portal/rankings/meets")
    public List<MeetChoice> meets(@AuthenticationPrincipal Object identity) {
        sessions.require(identity); return rankings.meets();
    }
    @GetMapping("/api/portal/rankings/events")
    public List<EventChoice> events(@AuthenticationPrincipal Object identity, @RequestParam(required = false) Long meetId) {
        sessions.require(identity); return rankings.events(meetId);
    }
    @GetMapping("/api/portal/rankings")
    public RankingsResponse<PortalRow> rankings(@AuthenticationPrincipal Object identity,
            @RequestParam Long categoryId, @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer minAge, @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ageAsOf,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String ageGroup, @RequestParam(required = false) String season,
            @RequestParam(required = false) Long meetId, @RequestParam(required = false) Long eventId) {
        var principal = sessions.require(identity);
        if (season != null && !season.isBlank()) throw new IllegalArgumentException("Season filtering is not configured; season attribution must be agreed first");
        if (ageGroup != null && !ageGroup.isBlank()) {
            if (minAge != null || maxAge != null) throw new IllegalArgumentException("Select ageGroup or numeric age bounds, not both");
            var band = ageGroups.require(ageGroup, ageAsOf); minAge = band.minAge(); maxAge = band.maxAge();
        }
        return meetId == null && eventId == null ? rankings.portal(principal.athleteId(), categoryId, gender, minAge, maxAge, ageAsOf, page, size)
                : rankings.portal(principal.athleteId(), categoryId, gender, minAge, maxAge, ageAsOf, page, size, meetId, eventId);
    }
}
