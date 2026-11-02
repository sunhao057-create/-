package com.projectmgmt.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSalaryItemRepository extends JpaRepository<UserSalaryItem, Long> {
    long countByUser_Id(Long userId);
}
