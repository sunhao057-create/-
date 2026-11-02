package com.projectmgmt.purchase;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRepository extends JpaRepository<Purchase, Long>, JpaSpecificationExecutor<Purchase> {
    boolean existsByCode(String code);

    boolean existsByWarehouse_Id(Long warehouseId);

    List<Purchase> findByProject_IdIn(Collection<Long> projectIds);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Purchase p
            where p.project.id = :projectId and p.budgetDeducted = true
            """)
    BigDecimal deductedTotal(@Param("projectId") Long projectId);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Purchase p
            where p.project.id = :projectId and p.budgetDeducted = false
              and (:excludeId is null or p.id <> :excludeId)
            """)
    BigDecimal pendingTotal(@Param("projectId") Long projectId, @Param("excludeId") Long excludeId);
}
