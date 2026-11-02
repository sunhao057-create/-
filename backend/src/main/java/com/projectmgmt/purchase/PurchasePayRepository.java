package com.projectmgmt.purchase;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchasePayRepository extends JpaRepository<PurchasePay, Long>, JpaSpecificationExecutor<PurchasePay> {
    boolean existsByCode(String code);

    boolean existsByPurchase_Id(Long purchaseId);

    boolean existsByAccount_Id(Long accountId);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from PurchasePay p
            where p.purchase.id = :purchaseId
              and (:excludeId is null or p.id <> :excludeId)
            """)
    BigDecimal paidOrPending(@Param("purchaseId") Long purchaseId, @Param("excludeId") Long excludeId);
}
