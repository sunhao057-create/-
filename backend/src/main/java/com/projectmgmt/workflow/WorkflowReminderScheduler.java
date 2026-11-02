package com.projectmgmt.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时提醒调度：定期扫描超时未办理的审批待办，向流程节点上配置的提醒人派发提醒。
 * 可用 app.workflow.reminder.enabled=false 关闭。
 */
@Component
@ConditionalOnProperty(name = "app.workflow.reminder.enabled", havingValue = "true", matchIfMissing = true)
public class WorkflowReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowReminderScheduler.class);

    private final WorkflowService workflowService;

    public WorkflowReminderScheduler(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Scheduled(fixedDelayString = "${app.workflow.reminder.interval:5m}")
    public void scan() {
        try {
            workflowService.dispatchOverdueReminders();
        } catch (Exception e) {
            log.warn("超时提醒扫描失败：{}", e.getMessage());
        }
    }
}
