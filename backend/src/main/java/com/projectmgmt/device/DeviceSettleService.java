package com.projectmgmt.device;

import com.projectmgmt.org.OrgDepartment;
import com.projectmgmt.org.OrgDepartmentRepository;
import com.projectmgmt.role.FormModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceSettleService {

    private final DeviceWorkOrderRepository workOrderRepository;
    private final DeviceRepository deviceRepository;
    private final OrgDepartmentRepository departmentRepository;

    @Transactional
    public void onWorkflowEnded(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module == null) {
            return;
        }
        if (module != FormModule.DEVICE_REPAIR && module != FormModule.DEVICE_MOVE && module != FormModule.DEVICE_SCRAP) {
            return;
        }
        DeviceWorkOrder order = workOrderRepository.findById(bizId).orElse(null);
        if (order == null || order.isApplied()) {
            return;
        }
        Device device = order.getDevice();
        if (order.getKind() == DeviceWorkOrderKind.REPAIR) {
            device.setStatus(DeviceStatus.IN_USE);
        } else if (order.getKind() == DeviceWorkOrderKind.MOVE) {
            if (order.getTargetLocation() != null) {
                device.setLocation(order.getTargetLocation());
            }
            if (order.getTargetDepartment() != null) {
                device.setDepartment(order.getTargetDepartment());
            }
            device.setStatus(DeviceStatus.IN_USE);
        } else if (order.getKind() == DeviceWorkOrderKind.SCRAP) {
            device.setStatus(DeviceStatus.SCRAPPED);
            device.setEnabled(false);
        }
        order.setApplied(true);
        deviceRepository.save(device);
        workOrderRepository.save(order);
    }

    @Transactional
    public void restore(DeviceWorkOrder order) {
        if (order == null || order.getDevice() == null) {
            return;
        }
        Device device = order.getDevice();
        device.setStatus(order.getSnapshotStatus() == null ? DeviceStatus.IN_USE : order.getSnapshotStatus());
        if (order.getSnapshotLocation() != null) {
            device.setLocation(order.getSnapshotLocation());
        }
        if (order.getSnapshotDepartmentId() != null) {
            OrgDepartment dept = departmentRepository.findById(order.getSnapshotDepartmentId()).orElse(null);
            if (dept != null) {
                device.setDepartment(dept);
            }
        }
        device.setEnabled(order.isSnapshotEnabled());
        order.setApplied(false);
        deviceRepository.save(device);
    }
}
