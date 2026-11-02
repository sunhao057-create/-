package com.projectmgmt.project;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {
    boolean existsByCode(String code);

    Optional<Project> findByCode(String code);

    boolean existsByCustomer_Id(Long customerId);

    boolean existsByContract_Id(Long contractId);

    java.util.List<Project> findByCustomer_IdOrderByIdDesc(Long customerId);

    java.util.List<Project> findByContract_IdOrderByIdDesc(Long contractId);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByManager_Id(Long managerId);

    boolean existsByCreatedBy_Id(Long userId);

    boolean existsByRegionLeader_Id(Long userId);
}
