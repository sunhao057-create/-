package com.projectmgmt.workflow;

import com.projectmgmt.role.FormModule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {
    Optional<WorkflowInstance> findByFormModuleAndBizId(String formModule, Long bizId);

    List<WorkflowInstance> findByStarterIdAndStatusOrderByIdDesc(Long starterId, String status);

    List<WorkflowInstance> findByStatusIn(List<String> statuses);

    default Optional<WorkflowInstance> findByFormModuleAndBizId(FormModule formModule, Long bizId) {
        return findByFormModuleAndBizId(formModule.name(), bizId);
    }
}
