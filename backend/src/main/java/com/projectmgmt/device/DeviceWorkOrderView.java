package com.projectmgmt.device;

import com.projectmgmt.project.AttachmentView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DeviceWorkOrderView(
        Long id,
        String code,
        String kind,
        Long deviceId,
        String deviceCode,
        String deviceName,
        String reason,
        LocalDate applyDate,
        String targetLocation,
        Long targetDepartmentId,
        String targetDepartmentName,
        boolean applied,
        String workflowStatus,
        String workflowStatusLabel,
        List<AttachmentView> files,
        LocalDateTime createdAt,
        String grade,
        String customerName,
        String customerAddress,
        String contactName,
        String contactPhone,
        String instrumentName,
        String instrumentModel,
        String brand,
        String fault,
        String clientOrg,
        String accountManager,
        String intakeRemark,
        Long clientCustomerId,
        String clientCustomerName,
        Long serviceCustomerId,
        String serviceCustomerName
) {
}
