package com.flyingangels.backend.ranking;

import com.flyingangels.backend.ranking.dto.RankingsResponse;
import com.flyingangels.backend.ranking.dto.RankingsResponse.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController @RequiredArgsConstructor
public class RankingsController {
    private final RankingsService rankings;
    private final com.flyingangels.backend.athlete.AgeGroupService ageGroups;
    @GetMapping("/api/rankings/categories") public List<Category> categories() { return rankings.categories(); }
    @GetMapping("/api/rankings/meets") public List<MeetChoice> meets() { return rankings.meets(); }
    @GetMapping("/api/rankings/events") public List<EventChoice> events(@RequestParam(required = false) Long meetId) { return rankings.events(meetId); }
    @GetMapping("/api/rankings")
    public RankingsResponse<StaffRow> rankings(@RequestParam Long categoryId, @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer minAge, @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ageAsOf,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String ageGroup, @RequestParam(required = false) String season,
            @RequestParam(required = false) Long meetId, @RequestParam(required = false) Long eventId) {
        if (season != null && !season.isBlank()) throw new IllegalArgumentException("Season filtering is not configured; season attribution must be agreed first");
        if (ageGroup != null && !ageGroup.isBlank()) {
            if (minAge != null || maxAge != null) throw new IllegalArgumentException("Select ageGroup or numeric age bounds, not both");
            var band = ageGroups.require(ageGroup, ageAsOf); minAge = band.minAge(); maxAge = band.maxAge();
        }
        return meetId == null && eventId == null ? rankings.staff(categoryId, gender, minAge, maxAge, ageAsOf, page, size)
                : rankings.staff(categoryId, gender, minAge, maxAge, ageAsOf, page, size, meetId, eventId);
    }
}
