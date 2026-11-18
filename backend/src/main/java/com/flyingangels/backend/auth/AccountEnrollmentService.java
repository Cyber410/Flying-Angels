package com.flyingangels.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountEnrollmentService {
    private final AccountInvitationRepository invitations;
    private final StaffUserRepository users;
    private final StaffUserService accounts;
    private final com.flyingangels.backend.auth.mail.InvitationMailOutbox outbox;
    private final Clock clock;
    @Value("${app.auth.activation-url}") private String activationUrl;
    private static final SecureRandom RANDOM=new SecureRandom();

    public record Summary(Long id,String email,String name,StaffRole role,Long athleteId,Instant expiresAt,
                          String status) {}
    public record Info(String email,String name,StaffRole role,boolean verified,Instant expiresAt,Instant codeExpiresAt) {}
    public record Token(@NotBlank @Size(max=100) String token) {
        @Override public String toString() { return "Token[REDACTED]"; }
    }
    public record Verification(@NotBlank @Size(max=100) String token,@NotBlank @Pattern(regexp="[0-9]{6}") String code) {
        @Override public String toString() { return "Verification[REDACTED]"; }
    }
    public record Completion(@NotBlank @Size(max=100) String token,@NotBlank @Size(min=12,max=72) String password) {
        @Override public String toString() { return "Completion[REDACTED]"; }
    }

    @Transactional(readOnly=true)
    public List<Summary> list() { return invitations.findByConsumedAtIsNullOrderByCreatedAtDesc().stream().map(this::summary).toList(); }

    @Transactional
    public Summary invite(InvitationService.InviteRequest request,Long actorId) {
        if(actorId==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in as an Admin to invite users");
        users.lockAccounts(); accounts.ensureUnique(request.email(),null);
        accounts.validateAthleteLink(request.role(),request.athleteId(),null);
        if(request.athleteId()!=null && invitations.findByAthleteIdAndConsumedAtIsNull(request.athleteId()).stream()
                .anyMatch(i->i.getExpiresAt().isAfter(clock.instant())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This athlete already has a pending invitation. Cancel it before inviting another email.");
        String email=StaffUser.normalizeEmail(request.email());
        if(invitations.findByEmailAndConsumedAtIsNull(email).stream().anyMatch(i->i.getExpiresAt().isAfter(clock.instant())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"An invitation already exists for this email. Resend it from Pending invitations.");
        var invitation=new AccountInvitation(request,actorId,clock.instant(),clock.instant().plus(Duration.ofHours(48)));
        issue(invitation);
        return summary(invitation);
    }

    @Transactional
    public Summary resend(Long id) {
        var invitation=invitations.lockById(id).orElseThrow(AccountEnrollmentService::unavailable);
        if(invitation.getConsumedAt()!=null) throw unavailable();
        if(!clock.instant().isBefore(invitation.getExpiresAt())) throw new IllegalArgumentException("This invitation expired. Create a new invitation.");
        requireResendAllowed(invitation); accounts.ensureUnique(invitation.getEmail(),null);
        issue(invitation); return summary(invitation);
    }

    @Transactional
    public void cancel(Long id) {
        var invitation=invitations.lockById(id).orElseThrow(AccountEnrollmentService::unavailable);
        if(invitation.getConsumedAt()!=null) throw unavailable();
        invitation.consume(null,clock.instant()); outbox.cancelEnrollment(id);
    }

    @Transactional
    public Info inspect(String token) { return info(find(token)); }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public Info verify(Verification request) {
        var invitation=find(request.token());
        var now=clock.instant();
        if(!now.isBefore(invitation.getCodeExpiresAt())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The verification code expired. Request another code.");
        byte[] expected=invitation.getCodeHash().getBytes(StandardCharsets.UTF_8);
        byte[] actual=hash(request.token()+":"+request.code()).getBytes(StandardCharsets.UTF_8);
        if(!MessageDigest.isEqual(expected,actual)) {
            invitation.failed();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,invitation.getFailures()>=5?"Too many incorrect codes. Ask an Admin for a new invitation.":"The verification code is incorrect.");
        }
        invitation.verify(now); return info(invitation);
    }

    @Transactional
    public Info resendCode(String token) {
        var invitation=find(token); requireResendAllowed(invitation);
        String code=code(); invitation.issue(hash(token),hash(token+":"+code),clock.instant());
        outbox.enqueueEnrollment(invitation,url(token),code); return info(invitation);
    }

    @Transactional
    public AuthDtos.UserResponse complete(Completion request) {
        users.lockAccounts();
        var invitation=find(request.token());
        if(users.findById(invitation.getCreatedBy()).filter(u->u.isActive()&&u.getRole()==StaffRole.ADMIN).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This invitation is no longer authorized. Contact an academy administrator.");
        if(invitation.getVerifiedAt()==null || !clock.instant().isBefore(invitation.getCodeExpiresAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Verify your email with a current code before creating the account.");
        accounts.ensureUnique(invitation.getEmail(),null);
        accounts.validateAthleteLink(invitation.getRole(),invitation.getAthleteId(),null);
        var user=new StaffUser(invitation.getEmail(),invitation.getName(),accounts.hash(request.password()),invitation.getRole());
        user.linkAthlete(invitation.getAthleteId()); users.saveAndFlush(user);
        invitation.consume(user.getId(),clock.instant()); outbox.cancelEnrollment(invitation.getId());
        return AuthDtos.UserResponse.from(user);
    }

    private void issue(AccountInvitation invitation) {
        byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); String code=code();
        invitation.issue(hash(token),hash(token+":"+code),clock.instant());
        if(invitation.getId()==null) invitations.saveAndFlush(invitation);
        outbox.enqueueEnrollment(invitation,url(token),code);
    }
    private String url(String token) {
        URI url=URI.create(activationUrl);
        if(!List.of("http","https").contains(url.getScheme())||url.getHost()==null||url.getFragment()!=null||url.getUserInfo()!=null)
            throw new IllegalArgumentException("Configure an HTTP(S) activation URL without credentials or a fragment");
        return activationUrl+"#token="+token;
    }
    private void requireResendAllowed(AccountInvitation invitation) {
        if(invitation.getFailures()>=5) throw unavailable();
        if(invitation.getSends()>=5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Invitation email limit reached. Ask an Admin for a new invitation after expiry.");
        if(clock.instant().isBefore(invitation.getSentAt().plusSeconds(60))) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Wait one minute before requesting another code.");
    }
    private AccountInvitation find(String token) {
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}")) throw unavailable();
        return invitations.lockByTokenHash(hash(token)).filter(i->i.available(clock.instant())).orElseThrow(AccountEnrollmentService::unavailable);
    }
    private Info info(AccountInvitation i) { return new Info(i.getEmail(),i.getName(),i.getRole(),i.getVerifiedAt()!=null,i.getExpiresAt(),i.getCodeExpiresAt()); }
    private Summary summary(AccountInvitation i) {
        return new Summary(i.getId(),i.getEmail(),i.getName(),i.getRole(),i.getAthleteId(),i.getExpiresAt(),i.available(clock.instant())?"AWAITING_VERIFICATION":"EXPIRED_OR_LOCKED");
    }
    private static String code() { return String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1_000_000)); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invitation is invalid, expired, locked, or already used"); }
}
