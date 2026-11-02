package com.projectmgmt.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {
    Optional<SysUser> findByUsername(String username);

    boolean existsByUsername(String username);

    List<SysUser> findByStatusOrderByDisplayNameAsc(UserStatus status);

    List<SysUser> findByDisplayNameAndStatus(String displayName, UserStatus status);

    long countByRole(String role);

    List<SysUser> findByDepartment_IdOrderByDisplayNameAsc(Long departmentId);

    boolean existsByDepartment_Id(Long departmentId);

    List<SysUser> findByDepartment_IdAndDeptLeaderTrueAndStatus(Long departmentId, UserStatus status);
}
