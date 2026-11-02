package com.projectmgmt.device;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeviceWorkOrderRepository extends JpaRepository<DeviceWorkOrder, Long>, JpaSpecificationExecutor<DeviceWorkOrder> {
    List<DeviceWorkOrder> findByDevice_IdOrderByIdDesc(Long deviceId);

    boolean existsByCode(String code);

    boolean existsByDevice_IdAndAppliedFalse(Long deviceId);

    boolean existsByDevice_Id(Long deviceId);
}
