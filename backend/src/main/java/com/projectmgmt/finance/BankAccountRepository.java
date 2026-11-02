package com.projectmgmt.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long>, JpaSpecificationExecutor<BankAccount> {
    boolean existsByAccountNo(String accountNo);

    boolean existsByAccountNoAndIdNot(String accountNo, Long id);

    java.util.Optional<BankAccount> findByAccountNo(String accountNo);
}
