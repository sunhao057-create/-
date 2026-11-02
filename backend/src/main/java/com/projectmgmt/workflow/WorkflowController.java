package com.projectmgmt.workflow;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.ApiResponse;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.workflow.dto.AdvanceWorkflowRequest;
import com.projectmgmt.workflow.dto.FormFieldView;
import com.projectmgmt.workflow.dto.ReturnWorkflowRequest;
import com.projectmgmt.workflow.dto.UpsertWorkflowRequest;
import com.projectmgmt.workflow.dto.WorkflowRuntimeView;
import com.projectmgmt.workflow.dto.WorkflowView;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final PermissionService permissionService;

    @GetMapping
    public ApiResponse<List<WorkflowView>> list() {
        permissionService.require(FormModule.WORKFLOW, "VIEW");
        return ApiResponse.ok(workflowService.list());
    }

    @GetMapping("/form-fields")
    public ApiResponse<List<FormFieldView>> formFields(@RequestParam String module) {
        requireDesignView(module);
        return ApiResponse.ok(workflowService.formFields(module));
    }

    @GetMapping("/for-form")
    public ApiResponse<WorkflowView> forForm(@RequestParam String module) {
        requireDesignView(module);
        return ApiResponse.ok(workflowService.forForm(module));
    }

    @GetMapping("/runtime")
    public ApiResponse<WorkflowRuntimeView> runtime(
            @RequestParam String module,
            @RequestParam(required = false) Long bizId
    ) {
        requireFormView(module);
        return ApiResponse.ok(workflowService.runtime(module, bizId));
    }

    @PostMapping("/advance")
    public ApiResponse<WorkflowRuntimeView> advance(@Valid @RequestBody AdvanceWorkflowRequest request) {
        requireFormEdit(request.formModule());
        return ApiResponse.ok(workflowService.advance(request.formModule(), request.bizId(), request.comment()));
    }

    @PostMapping("/return")
    public ApiResponse<WorkflowRuntimeView> returnTo(@Valid @RequestBody ReturnWorkflowRequest request) {
        requireFormEdit(request.formModule());
        return ApiResponse.ok(workflowService.returnTo(
                request.formModule(), request.bizId(), request.targetNodeKey(), request.reason()));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkflowView> get(@PathVariable Long id) {
        WorkflowView view = workflowService.get(id);
        requireDesignView(view.formModule());
        return ApiResponse.ok(view);
    }

    @PostMapping
    public ApiResponse<WorkflowView> create(@Valid @RequestBody UpsertWorkflowRequest request) {
        requireDesign(request.formModule());
        return ApiResponse.ok(workflowService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkflowView> update(@PathVariable Long id, @Valid @RequestBody UpsertWorkflowRequest request) {
        requireDesign(request.formModule());
        return ApiResponse.ok(workflowService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.require(FormModule.WORKFLOW, "DELETE");
        workflowService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<WorkflowView> publish(@PathVariable Long id) {
        requireDesign(workflowService.get(id).formModule());
        return ApiResponse.ok(workflowService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public ApiResponse<WorkflowView> unpublish(@PathVariable Long id) {
        requireDesign(workflowService.get(id).formModule());
        return ApiResponse.ok(workflowService.unpublish(id));
    }

    private void requireDesignView(String module) {
        if (permissionService.currentAllowed(FormModule.WORKFLOW, "VIEW")
                || permissionService.currentAllowed(module, "FLOW")) {
            return;
        }
        throw ApiException.forbidden("没有权限执行该操作");
    }

    private void requireDesign(String module) {
        if (permissionService.currentAllowed(FormModule.WORKFLOW, "EDIT")
                || permissionService.currentAllowed(module, "FLOW")) {
            return;
        }
        throw ApiException.forbidden("没有权限执行该操作");
    }

    private void requireFormView(String module) {
        permissionService.require(module, "VIEW");
    }

    private void requireFormEdit(String module) {
        permissionService.require(module, "EDIT");
    }
}
