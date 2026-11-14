package com.flyingangels.backend.auth;

import com.flyingangels.backend.athlete.AthleteHistoryService;
import com.flyingangels.backend.athlete.dto.AthleteHistoryResponse;
import com.flyingangels.backend.availability.AvailabilityService;
import com.flyingangels.backend.availability.AvailabilityDtos.RequestView;
import com.flyingangels.backend.meet.MeetView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
public class PortalHistoryController {
    private final AthleteSessionService sessions;
    private final AthleteHistoryService history;
    private final AvailabilityService availability;
    @GetMapping("/api/portal/history")
    public AthleteHistoryResponse history(@AuthenticationPrincipal Object identity, @RequestParam(required = false) Long categoryId) {
        return history.history(sessions.require(identity).athleteId(), categoryId);
    }
    @GetMapping("/api/portal/meets")
    public List<RequestView> meets(@AuthenticationPrincipal Object identity, @RequestParam(defaultValue = "UPCOMING") MeetView view) {
        return availability.forAthlete(sessions.require(identity).athleteId(), view);
    }
    @GetMapping("/api/portal/meets/{id}")
    public RequestView meet(@AuthenticationPrincipal Object identity, @PathVariable Long id) {
        return availability.nominatedMeet(sessions.require(identity).athleteId(), id);
    }
}
