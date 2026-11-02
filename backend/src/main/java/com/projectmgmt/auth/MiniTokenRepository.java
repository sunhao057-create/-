package com.projectmgmt.auth;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MiniTokenRepository extends JpaRepository<SysMiniToken, String> {
    Optional<SysMiniToken> findByIdAndKindAndUsedFalseAndExpiresAtAfter(String id, String kind, LocalDateTime now);

    @Transactional
    void deleteByUserId(Long userId);

    @Transactional
    void deleteByExpiresAtBefore(LocalDateTime time);
}
