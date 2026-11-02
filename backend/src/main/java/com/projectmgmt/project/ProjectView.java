package com.projectmgmt.project;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.projectmgmt.customer.CustomerView;
import com.projectmgmt.user.StaffView;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectView(
        Long id,
        String name,
        String code,
        CustomerView customer,
        Long contractId,
        String contractCode,
        String contractName,
        String companyName,
        String companyCode,
        String companyLevel,
        StaffView manager,
        Long regionId,
        String regionName,
        StaffView regionLeader,
        String overview,
        Integer progressPercent,
        ProjectStatus status,
        String statusLabel,
        List<AttachmentView> attachments,
        LocalDateTime createdAt,
        @JsonProperty("isPublic") boolean isPublic
) {
    public static ProjectView from(Project project) {
        return new ProjectView(
                project.getId(),
                project.getName(),
                project.getCode(),
                CustomerView.from(project.getCustomer()),
                project.getContract() == null ? null : project.getContract().getId(),
                project.getContract() == null ? null : project.getContract().getCode(),
                project.getContract() == null ? null : project.getContract().getName(),
                project.getCompanyName(),
                project.getCompanyCode(),
                project.getCompanyLevel(),
                StaffView.from(project.getManager()),
                project.getRegion().getId(),
                project.getRegion().getName(),
                project.getRegionLeader() == null ? null : StaffView.from(project.getRegionLeader()),
                project.getOverview(),
                project.getProgressPercent(),
                project.getStatus(),
                project.getStatus() == null ? ProjectStatus.INITIATED.label() : project.getStatus().label(),
                project.getAttachments().stream().map(AttachmentView::from).toList(),
                project.getCreatedAt(),
                project.isPublic()
        );
    }
}
