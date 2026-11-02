package com.projectmgmt.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeviceRepository extends JpaRepository<Device, Long>, JpaSpecificationExecutor<Device> {
    boolean existsByCode(String code);

    java.util.Optional<Device> findByCode(String code);

    boolean existsByDepartment_Id(Long departmentId);
}
