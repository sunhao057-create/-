package com.projectmgmt.warehouse;

import com.projectmgmt.common.BizParse;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarehouseAnalyticsService {

    private final WarehouseRepository warehouseRepository;
    private final StockTxnRepository txnRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public WarehouseAnalyticsView view() {
        permissionService.require(FormModule.WAREHOUSE_ANALYTICS, "VIEW");
        Long own = permissionService.ownFilterUserId(FormModule.WAREHOUSE_ANALYTICS);
        List<Warehouse> warehouses = warehouseRepository.findAll().stream()
                .filter(item -> ownedBy(item.getCreatedBy() == null ? null : item.getCreatedBy().getId(), own))
                .toList();
        Set<Long> warehouseIds = warehouses.stream().map(Warehouse::getId).collect(Collectors.toSet());
        List<StockTxn> txns = txnRepository.findAllByOrderByTxnDateDescIdDesc().stream()
                .filter(item -> warehouseIds.contains(item.getWarehouse().getId()))
                .toList();
        YearMonth thisMonth = YearMonth.now();
        BigDecimal monthIn = BigDecimal.ZERO;
        BigDecimal monthOut = BigDecimal.ZERO;
        BigDecimal purchaseIn = BigDecimal.ZERO;
        BigDecimal purchaseOut = BigDecimal.ZERO;
        BigDecimal projectOut = BigDecimal.ZERO;
        Map<String, WarehouseAnalyticsView.MonthRow> months = new LinkedHashMap<>();
        Map<String, WarehouseAnalyticsView.ItemStockRow> items = new LinkedHashMap<>();
        List<WarehouseAnalyticsView.WarehouseStockRow> warehouseRows = new ArrayList<>();
        BigDecimal totalStock = BigDecimal.ZERO;
        for (Warehouse warehouse : warehouses) {
            BigDecimal inQty = BigDecimal.ZERO;
            BigDecimal outQty = BigDecimal.ZERO;
            for (StockTxn txn : txns) {
                if (!warehouse.getId().equals(txn.getWarehouse().getId())) {
                    continue;
                }
                if (txn.getDirection() == StockTxnDirection.IN) {
                    inQty = inQty.add(BizParse.nz(txn.getQty()));
                } else {
                    outQty = outQty.add(BizParse.nz(txn.getQty()));
                }
            }
            BigDecimal stock = inQty.subtract(outQty);
            totalStock = totalStock.add(stock);
            warehouseRows.add(new WarehouseAnalyticsView.WarehouseStockRow(
                    warehouse.getId(), warehouse.getName(), inQty, outQty, stock));
        }
        warehouseRows.sort(Comparator.comparing(WarehouseAnalyticsView.WarehouseStockRow::stockQty).reversed());
        for (StockTxn txn : txns) {
            String month = YearMonth.from(txn.getTxnDate()).toString();
            WarehouseAnalyticsView.MonthRow current = months.getOrDefault(
                    month, new WarehouseAnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO));
            String itemKey = txn.getItemName() + "|" + txn.getUnit();
            WarehouseAnalyticsView.ItemStockRow item = items.getOrDefault(
                    itemKey, new WarehouseAnalyticsView.ItemStockRow(txn.getItemName(), txn.getUnit(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            if (txn.getDirection() == StockTxnDirection.IN) {
                current = new WarehouseAnalyticsView.MonthRow(month, current.inQty().add(BizParse.nz(txn.getQty())), current.outQty());
                item = new WarehouseAnalyticsView.ItemStockRow(item.itemName(), item.unit(), item.inQty().add(BizParse.nz(txn.getQty())), item.outQty(), BigDecimal.ZERO);
                if (YearMonth.from(txn.getTxnDate()).equals(thisMonth)) {
                    monthIn = monthIn.add(BizParse.nz(txn.getQty()));
                }
                if (txn.getSourceType() == StockTxnSource.PURCHASE_IN) {
                    purchaseIn = purchaseIn.add(BizParse.nz(txn.getQty()));
                }
            } else {
                current = new WarehouseAnalyticsView.MonthRow(month, current.inQty(), current.outQty().add(BizParse.nz(txn.getQty())));
                item = new WarehouseAnalyticsView.ItemStockRow(item.itemName(), item.unit(), item.inQty(), item.outQty().add(BizParse.nz(txn.getQty())), BigDecimal.ZERO);
                if (YearMonth.from(txn.getTxnDate()).equals(thisMonth)) {
                    monthOut = monthOut.add(BizParse.nz(txn.getQty()));
                }
                if (txn.getSourceType() == StockTxnSource.PURCHASE_OUT) {
                    purchaseOut = purchaseOut.add(BizParse.nz(txn.getQty()));
                }
                if (txn.getSourceType() == StockTxnSource.PROJECT_OUT) {
                    projectOut = projectOut.add(BizParse.nz(txn.getQty()));
                }
            }
            months.put(month, current);
            items.put(itemKey, item);
        }
        List<WarehouseAnalyticsView.ItemStockRow> itemRows = items.values().stream()
                .map(item -> new WarehouseAnalyticsView.ItemStockRow(
                        item.itemName(), item.unit(), item.inQty(), item.outQty(), item.inQty().subtract(item.outQty())))
                .sorted(Comparator.comparing(WarehouseAnalyticsView.ItemStockRow::stockQty).reversed())
                .toList();
        List<WarehouseAnalyticsView.MonthRow> monthly = new ArrayList<>(months.values());
        monthly.sort(Comparator.comparing(WarehouseAnalyticsView.MonthRow::month));
        List<StockTxnView> recent = txns.stream().limit(12).map(StockTxnView::from).toList();
        return new WarehouseAnalyticsView(
                new WarehouseAnalyticsView.Kpi(
                        warehouses.size(),
                        totalStock,
                        monthIn,
                        monthOut,
                        purchaseIn,
                        purchaseOut,
                        projectOut
                ),
                warehouseRows,
                itemRows,
                monthly,
                recent
        );
    }

    private static boolean ownedBy(Long ownerId, Long filter) {
        return filter == null || Objects.equals(filter, ownerId);
    }
}
