package com.projectmgmt.deposit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DepositRepository extends JpaRepository<Deposit, Long>, JpaSpecificationExecutor<Deposit> {

    boolean existsByCode(String code);

    List<Deposit> findByApply_Id(Long applyId);

    boolean existsByApply_Id(Long applyId);
}
