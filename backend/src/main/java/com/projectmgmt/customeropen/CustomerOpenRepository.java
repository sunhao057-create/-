package com.projectmgmt.customeropen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerOpenRepository extends JpaRepository<CustomerOpen, Long>, JpaSpecificationExecutor<CustomerOpen> {
    boolean existsByCompanyNameAndSettledFalse(String companyName);

    boolean existsByCompanyNameAndIdNotAndSettledFalse(String companyName, Long id);
}
