package com.flyingangels.backend.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.flyingangels.backend.auth.InvitationService.*;

@RestController @RequiredArgsConstructor
public class InvitationController {
    private final InvitationService invitations;
    private final AccountEnrollmentService enrollment;
    private final com.flyingangels.backend.auth.mail.InvitationMailOutbox outbox;
    @GetMapping("/api/users/{id}/invitation/delivery")
    public org.springframework.http.ResponseEntity<com.flyingangels.backend.auth.mail.InvitationMailOutbox.DeliveryStatus> delivery(@PathVariable Long id) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate()).body(outbox.status(id));
    }
    @PostMapping("/api/users/invitations") @ResponseStatus(HttpStatus.CREATED)
    public AccountEnrollmentService.Summary invite(@Valid @RequestBody InviteRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal StaffPrincipal principal) {
        return enrollment.invite(request,principal==null?null:principal.id());
    }
    @PostMapping("/api/users/{id}/invitation/resend")
    public AuthDtos.UserResponse resend(@PathVariable Long id) { return invitations.resend(id); }
    @PostMapping("/api/auth/invitations/validate")
    public InvitationInfo validate(@Valid @RequestBody TokenRequest request) { return invitations.validate(request.token()); }
    @PostMapping("/api/auth/invitations/accept")
    public AuthDtos.UserResponse accept(@Valid @RequestBody AcceptRequest request) { return invitations.accept(request); }
}
