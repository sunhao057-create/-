package com.projectmgmt.workflow;

import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.workflow.dto.InboxItemView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inbox")
@RequiredArgsConstructor
public class InboxController {

    private final WorkflowService workflowService;

    @GetMapping
    public ApiResponse<List<InboxItemView>> list(
            @RequestParam(defaultValue = "TODO") String type,
            @RequestParam(defaultValue = "false") boolean overdue
    ) {
        return ApiResponse.ok(workflowService.inbox(type, overdue));
    }
}
