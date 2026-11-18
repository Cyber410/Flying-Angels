package com.flyingangels.backend.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.flyingangels.backend.auth.AccountEnrollmentService.*;

@RestController @RequiredArgsConstructor
public class AccountEnrollmentController {
    private final AccountEnrollmentService enrollment;
    @GetMapping("/api/users/pending-invitations")
    public List<Summary> list() { return enrollment.list(); }
    @PostMapping("/api/users/pending-invitations") @ResponseStatus(HttpStatus.CREATED)
    public Summary invite(@Valid @RequestBody InvitationService.InviteRequest request,@AuthenticationPrincipal StaffPrincipal principal) {
        return enrollment.invite(request,principal==null?null:principal.id());
    }
    @PostMapping("/api/users/pending-invitations/{id}/resend")
    public Summary resend(@PathVariable Long id) { return enrollment.resend(id); }
    @DeleteMapping("/api/users/pending-invitations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) { enrollment.cancel(id); }
    @PostMapping("/api/auth/enrollment/info")
    public ResponseEntity<Info> info(@Valid @RequestBody Token request) { return privateResponse(enrollment.inspect(request.token())); }
    @PostMapping("/api/auth/enrollment/verify")
    public ResponseEntity<Info> verify(@Valid @RequestBody Verification request) { return privateResponse(enrollment.verify(request)); }
    @PostMapping("/api/auth/enrollment/resend")
    public ResponseEntity<Info> resendCode(@Valid @RequestBody Token request) { return privateResponse(enrollment.resendCode(request.token())); }
    @PostMapping("/api/auth/enrollment/complete")
    public ResponseEntity<AuthDtos.UserResponse> complete(@Valid @RequestBody Completion request) { return privateResponse(enrollment.complete(request)); }
    private <T> ResponseEntity<T> privateResponse(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
