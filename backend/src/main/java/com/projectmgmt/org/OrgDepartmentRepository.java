package com.projectmgmt.org;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgDepartmentRepository extends JpaRepository<OrgDepartment, Long> {
    List<OrgDepartment> findAllByOrderBySortOrderAscIdAsc();

    boolean existsByParent_Id(Long parentId);
}
