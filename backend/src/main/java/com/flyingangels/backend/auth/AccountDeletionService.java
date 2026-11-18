package com.flyingangels.backend.auth;

import com.flyingangels.backend.auth.mail.InvitationMailOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;

@Service @RequiredArgsConstructor
public class AccountDeletionService {
    private final StaffUserRepository users;
    private final UserInvitationRepository legacyInvitations;
    private final AccountInvitationRepository invitations;
    private final InvitationMailOutbox outbox;
    private final Clock clock;

    @Transactional
    public void delete(Long id, Long actorId) {
        var accounts = users.lockAccounts();
        var actor = accounts.stream().filter(u -> u.getId().equals(actorId)
                && u.isActive() && u.getRole() == StaffRole.ADMIN).findFirst();
        if (actor.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "An active Admin must delete accounts");
        var user = accounts.stream().filter(u -> u.getId().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (id.equals(actorId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete your own account");
        if (user.isActive() && user.getRole() == StaffRole.ADMIN
                && accounts.stream().filter(u -> u.isActive() && u.getRole() == StaffRole.ADMIN).count() <= 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Keep at least one Admin active");
        outbox.cancelForUser(id);
        legacyInvitations.deleteByUserId(id);
        for (var invitation : invitations.lockOutstandingForAccount(id, user.getEmail())) {
            invitation.consume(null, clock.instant());
            outbox.cancelEnrollment(invitation.getId());
        }
        users.delete(user);
        users.flush();
    }
}
