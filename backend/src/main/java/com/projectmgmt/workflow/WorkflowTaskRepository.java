package com.projectmgmt.workflow;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, Long> {
    List<WorkflowTask> findByInstanceAndStatus(WorkflowInstance instance, String status);

    List<WorkflowTask> findByInstanceAndNodeKeyAndStatus(WorkflowInstance instance, String nodeKey, String status);

    List<WorkflowTask> findByStatusAndNodeTypeAndDueAtBefore(String status, String nodeType, LocalDateTime threshold);

    List<WorkflowTask> findByAssigneeIdAndNodeTypeAndStatusAndKindOrderByIdDesc(
            Long assigneeId,
            String nodeType,
            String status,
            String kind
    );

    List<WorkflowTask> findByAssigneeIdAndNodeTypeAndStatusOrderByHandledAtDesc(
            Long assigneeId,
            String nodeType,
            String status
    );

    List<WorkflowTask> findByAssigneeIdAndNodeTypeOrderByIdDesc(Long assigneeId, String nodeType);

    List<WorkflowTask> findByInstanceAndStatusAndAssigneeId(WorkflowInstance instance, String status, Long assigneeId);

    void deleteByInstance(WorkflowInstance instance);
}
