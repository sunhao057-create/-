package com.projectmgmt.customeropen;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerAccountOpenRepository
        extends JpaRepository<CustomerAccountOpen, Long>, JpaSpecificationExecutor<CustomerAccountOpen> {
    boolean existsByUsernameAndSettledFalse(String username);

    boolean existsByUsernameAndIdNotAndSettledFalse(String username, Long id);
}
