package com.projectmgmt.formview;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormViewRepository extends JpaRepository<FormView, Long> {
    List<FormView> findByModuleOrderByPinTopDescIdAsc(String module);
}
