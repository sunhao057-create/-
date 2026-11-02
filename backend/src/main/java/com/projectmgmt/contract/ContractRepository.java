package com.projectmgmt.contract;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContractRepository extends JpaRepository<Contract, Long>, JpaSpecificationExecutor<Contract> {
    boolean existsByCode(String code);

    boolean existsByCustomer_Id(Long customerId);

    List<Contract> findByCustomer_IdOrderByIdDesc(Long customerId);
}
