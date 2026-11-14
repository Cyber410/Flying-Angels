package com.flyingangels.backend.availability;

import com.flyingangels.backend.auth.StaffPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.flyingangels.backend.availability.AvailabilityDtos.*;

@RestController @RequiredArgsConstructor
public class AvailabilityController {
    private final AvailabilityService availability;
    private final AvailabilityOverviewService overview;
    @GetMapping("/api/availability")
    public OverviewPage search(@RequestParam(required = false) Long meetId, @RequestParam(required = false) Status status,
                               @RequestParam(defaultValue = "ALL") com.flyingangels.backend.meet.MeetView view,
                               @RequestParam(defaultValue = "") String search,
                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return overview.search(meetId, status, view, search, page, size);
    }
    @GetMapping("/api/meets/{id}/availability")
    public List<RequestView> forMeet(@PathVariable Long id) { return availability.forMeet(id); }
    @GetMapping("/api/availability/{id}")
    public RequestView get(@PathVariable Long id) { return availability.get(id, null); }
    @GetMapping("/api/availability/{id}/responses")
    public List<ResponseAudit> history(@PathVariable Long id) { return availability.history(id); }
    @PutMapping("/api/availability/{id}/response")
    public RequestView respond(@PathVariable Long id, @AuthenticationPrincipal StaffPrincipal principal,
                               @Valid @RequestBody StaffReply body) {
        return availability.recordExternalResponse(id, principal == null ? null : principal.id(), body);
    }
}
