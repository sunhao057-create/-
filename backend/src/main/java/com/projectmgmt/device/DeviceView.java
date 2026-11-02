package com.projectmgmt.device;

import java.time.LocalDateTime;

public record DeviceView(
        Long id,
        String code,
        String name,
        String model,
        String brand,
        String serialNo,
        String location,
        String remark,
        String status,
        String statusLabel,
        Long departmentId,
        String departmentName,
        Long managerId,
        String managerName,
        boolean enabled,
        String photoUrl,
        LocalDateTime createdAt
) {
    public static DeviceView from(Device device) {
        DeviceStatus status = device.getStatus() == null ? DeviceStatus.IN_USE : device.getStatus();
        return new DeviceView(
                device.getId(),
                device.getCode(),
                device.getName(),
                device.getModel(),
                device.getBrand(),
                device.getSerialNo(),
                device.getLocation(),
                device.getRemark(),
                status.name(),
                DeviceLabels.status(status),
                device.getDepartment() == null ? null : device.getDepartment().getId(),
                device.getDepartment() == null ? null : device.getDepartment().getName(),
                device.getManager() == null ? null : device.getManager().getId(),
                device.getManager() == null ? null : device.getManager().getDisplayName(),
                device.isEnabled(),
                device.getPhotoUrl(),
                device.getCreatedAt()
        );
    }
}
