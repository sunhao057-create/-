package com.projectmgmt.workflow;

import com.projectmgmt.role.FormModule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRepository extends JpaRepository<WorkflowDefinition, Long> {
    Optional<WorkflowDefinition> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<WorkflowDefinition> findAllByOrderByIdDesc();

    List<WorkflowDefinition> findAllByFormModuleAndPublishedTrue(String formModule);

    Optional<WorkflowDefinition> findFirstByFormModuleAndPublishedTrue(String formModule);

    Optional<WorkflowDefinition> findFirstByFormModuleOrderByIdDesc(String formModule);

    default List<WorkflowDefinition> findAllByFormModuleAndPublishedTrue(FormModule formModule) {
        return findAllByFormModuleAndPublishedTrue(formModule.name());
    }

    default Optional<WorkflowDefinition> findFirstByFormModuleAndPublishedTrue(FormModule formModule) {
        return findFirstByFormModuleAndPublishedTrue(formModule.name());
    }
}
