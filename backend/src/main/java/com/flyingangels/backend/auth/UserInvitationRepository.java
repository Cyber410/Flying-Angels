package com.flyingangels.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {
    Optional<UserInvitation> findByTokenHash(String tokenHash);
    void deleteByUserId(Long userId);
}
