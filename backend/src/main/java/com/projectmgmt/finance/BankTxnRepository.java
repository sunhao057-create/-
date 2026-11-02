package com.projectmgmt.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankTxnRepository extends JpaRepository<BankTxn, Long>, JpaSpecificationExecutor<BankTxn> {
    List<BankTxn> findByAccount_IdOrderByTxnDateDescIdDesc(Long accountId);

    List<BankTxn> findAllByOrderByTxnDateDescIdDesc();

    Optional<BankTxn> findBySourceTypeAndSourceId(BankTxnSource sourceType, Long sourceId);

    boolean existsByAccount_Id(Long accountId);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from BankTxn t
            where t.account.id = :accountId and t.direction = :direction
            """)
    BigDecimal sumByAccountAndDirection(@Param("accountId") Long accountId, @Param("direction") BankTxnDirection direction);
}
