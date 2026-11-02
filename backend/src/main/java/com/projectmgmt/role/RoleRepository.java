package com.projectmgmt.role;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<SysRole, Long> {
    @EntityGraph(attributePaths = "permissions")
    Optional<SysRole> findByCode(String code);

    Optional<SysRole> findFirstByName(String name);

    @EntityGraph(attributePaths = "permissions")
    @Override
    List<SysRole> findAll();

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
