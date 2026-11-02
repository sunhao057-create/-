package com.projectmgmt.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowHistoryRepository extends JpaRepository<WorkflowHistory, Long> {
    List<WorkflowHistory> findByInstanceOrderByIdAsc(WorkflowInstance instance);

    void deleteByInstance(WorkflowInstance instance);
}
