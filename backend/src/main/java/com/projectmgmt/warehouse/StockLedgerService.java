package com.projectmgmt.warehouse;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.project.Project;
import com.projectmgmt.purchase.Purchase;
import com.projectmgmt.user.SysUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockLedgerService {

    private final StockTxnRepository txnRepository;

    @Transactional
    public StockTxn post(
            Warehouse warehouse,
            LocalDate txnDate,
            StockTxnDirection direction,
            String itemName,
            BigDecimal qty,
            String unit,
            BigDecimal amount,
            String summary,
            StockTxnSource sourceType,
            Long sourceId,
            Project project,
            Purchase purchase,
            SysUser actor
    ) {
        if (warehouse == null) {
            throw ApiException.badRequest("请选择仓库");
        }
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("数量必须大于 0");
        }
        if (sourceType != null && sourceId != null) {
            StockTxn existing = txnRepository
                    .findBySourceTypeAndSourceIdAndItemNameIgnoreCase(sourceType, sourceId, itemName.trim())
                    .orElse(null);
            if (existing != null) {
                return existing;
            }
        }
        StockTxn txn = new StockTxn();
        txn.setWarehouse(warehouse);
        txn.setTxnDate(txnDate == null ? LocalDate.now() : txnDate);
        txn.setDirection(direction);
        txn.setItemName(itemName.trim());
        txn.setQty(qty);
        txn.setUnit(BizParse.unit(unit));
        txn.setAmount(amount);
        txn.setSummary(summary == null || summary.isBlank() ? direction.label() + " " + itemName : summary.trim());
        txn.setSourceType(sourceType);
        txn.setSourceId(sourceId);
        txn.setProject(project);
        txn.setPurchase(purchase);
        txn.setCreatedBy(actor);
        return txnRepository.save(txn);
    }

    @Transactional
    public void reverse(StockTxnSource sourceType, Long sourceId) {
        if (sourceType == null || sourceId == null) {
            return;
        }
        txnRepository.deleteBySourceTypeAndSourceId(sourceType, sourceId);
    }

    @Transactional(readOnly = true)
    public BigDecimal onHand(Long warehouseId, String itemName) {
        if (warehouseId == null || itemName == null || itemName.isBlank()) {
            return BigDecimal.ZERO;
        }
        return BizParse.nz(txnRepository.onHand(warehouseId, itemName.trim()));
    }
}
