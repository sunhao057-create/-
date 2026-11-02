package com.projectmgmt.contract;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractCollectionRepository
        extends JpaRepository<ContractCollection, Long>, JpaSpecificationExecutor<ContractCollection> {
    boolean existsByCode(String code);

    boolean existsByContract_Id(Long contractId);

    boolean existsByAccount_Id(Long accountId);

    List<ContractCollection> findByContract_IdOrderByReceivedDateDescIdDesc(Long contractId);

    List<ContractCollection> findByContract_IdIn(Collection<Long> contractIds);

    @Query("""
            select coalesce(sum(c.amount), 0)
            from ContractCollection c
            where c.contract.id = :contractId
              and (:excludeId is null or c.id <> :excludeId)
            """)
    BigDecimal sumAmountByContractId(@Param("contractId") Long contractId, @Param("excludeId") Long excludeId);
}
