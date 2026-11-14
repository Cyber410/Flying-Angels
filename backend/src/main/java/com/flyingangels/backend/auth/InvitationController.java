package com.flyingangels.backend.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static com.flyingangels.backend.auth.InvitationService.*;

@RestController @RequiredArgsConstructor
public class InvitationController {
    private final InvitationService invitations;
    private final com.flyingangels.backend.auth.mail.InvitationMailOutbox outbox;
    @GetMapping("/api/users/{id}/invitation/delivery")
    public org.springframework.http.ResponseEntity<com.flyingangels.backend.auth.mail.InvitationMailOutbox.DeliveryStatus> delivery(@PathVariable Long id) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate()).body(outbox.status(id));
    }
    @PostMapping("/api/users/invitations") @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.UserResponse invite(@Valid @RequestBody InviteRequest request) { return invitations.invite(request); }
    @PostMapping("/api/users/{id}/invitation/resend")
    public AuthDtos.UserResponse resend(@PathVariable Long id) { return invitations.resend(id); }
    @PostMapping("/api/auth/invitations/validate")
    public InvitationInfo validate(@Valid @RequestBody TokenRequest request) { return invitations.validate(request.token()); }
    @PostMapping("/api/auth/invitations/accept")
    public AuthDtos.UserResponse accept(@Valid @RequestBody AcceptRequest request) { return invitations.accept(request); }
}
