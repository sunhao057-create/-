package com.projectmgmt.warehouse;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockOutRepository extends JpaRepository<StockOut, Long>, JpaSpecificationExecutor<StockOut> {
    boolean existsByCode(String code);

    boolean existsByWarehouse_Id(Long warehouseId);

    boolean existsByTargetWarehouse_Id(Long warehouseId);

    List<StockOut> findByKindOrderByIdDesc(StockOutKind kind);

    List<StockOut> findByProject_IdIn(Collection<Long> projectIds);

    @Query("""
            select coalesce(sum(s.amount), 0)
            from StockOut s
            where s.project.id = :projectId and s.kind = com.projectmgmt.warehouse.StockOutKind.PROJECT
              and s.budgetDeducted = true
            """)
    BigDecimal deductedTotal(@Param("projectId") Long projectId);

    @Query("""
            select coalesce(sum(s.amount), 0)
            from StockOut s
            where s.project.id = :projectId and s.kind = com.projectmgmt.warehouse.StockOutKind.PROJECT
              and s.budgetDeducted = false
              and (:excludeId is null or s.id <> :excludeId)
            """)
    BigDecimal pendingAmount(@Param("projectId") Long projectId, @Param("excludeId") Long excludeId);

    @Query("""
            select coalesce(sum(s.qty), 0)
            from StockOut s
            where s.warehouse.id = :warehouseId and lower(s.itemName) = lower(:itemName)
              and s.ledgerPosted = false
              and (:excludeId is null or s.id <> :excludeId)
              and not exists (select 1 from StockOutLine l where l.stockOut = s)
            """)
    BigDecimal pendingQty(
            @Param("warehouseId") Long warehouseId,
            @Param("itemName") String itemName,
            @Param("excludeId") Long excludeId
    );

    @Query("""
            select coalesce(sum(l.qty), 0)
            from StockOutLine l
            where l.stockOut.warehouse.id = :warehouseId and lower(l.itemName) = lower(:itemName)
              and l.stockOut.ledgerPosted = false
              and (:excludeId is null or l.stockOut.id <> :excludeId)
            """)
    BigDecimal pendingLineQty(
            @Param("warehouseId") Long warehouseId,
            @Param("itemName") String itemName,
            @Param("excludeId") Long excludeId
    );
}
