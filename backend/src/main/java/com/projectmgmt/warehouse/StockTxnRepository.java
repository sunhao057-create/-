package com.projectmgmt.warehouse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockTxnRepository extends JpaRepository<StockTxn, Long>, JpaSpecificationExecutor<StockTxn> {
    List<StockTxn> findAllByOrderByTxnDateDescIdDesc();

    Optional<StockTxn> findBySourceTypeAndSourceIdAndItemNameIgnoreCase(
            StockTxnSource sourceType, Long sourceId, String itemName);

    void deleteBySourceTypeAndSourceId(StockTxnSource sourceType, Long sourceId);

    boolean existsByWarehouse_Id(Long warehouseId);

    @Query("""
            select coalesce(sum(case when t.direction = com.projectmgmt.warehouse.StockTxnDirection.IN then t.qty else -t.qty end), 0)
            from StockTxn t
            where t.warehouse.id = :warehouseId and lower(t.itemName) = lower(:itemName)
            """)
    BigDecimal onHand(@Param("warehouseId") Long warehouseId, @Param("itemName") String itemName);

    @Query("""
            select t.itemName as itemName, max(t.unit) as unit,
                   coalesce(sum(case when t.direction = com.projectmgmt.warehouse.StockTxnDirection.IN then t.qty else -t.qty end), 0) as stockQty
            from StockTxn t
            where t.warehouse.id = :warehouseId
            group by t.itemName
            order by t.itemName
            """)
    List<Object[]> stockBalances(@Param("warehouseId") Long warehouseId);
}
