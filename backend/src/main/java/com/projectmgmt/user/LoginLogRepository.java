package com.projectmgmt.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginLogRepository extends JpaRepository<SysLoginLog, Long> {
    Page<SysLoginLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<SysLoginLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<SysLoginLog> findByUsernameContainingIgnoreCaseOrderByCreatedAtDesc(String username, Pageable pageable);

    void deleteByUserId(Long userId);
}
