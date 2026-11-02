package com.projectmgmt.purchase;

import com.projectmgmt.common.BizParse;
import com.projectmgmt.finance.BankTxnDirection;
import com.projectmgmt.finance.BankTxnSource;
import com.projectmgmt.finance.LedgerService;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.warehouse.StockLedgerService;
import com.projectmgmt.warehouse.StockOut;
import com.projectmgmt.warehouse.StockOutKind;
import com.projectmgmt.warehouse.StockOutLine;
import com.projectmgmt.warehouse.StockOutRepository;
import com.projectmgmt.warehouse.StockTxnDirection;
import com.projectmgmt.warehouse.StockTxnSource;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseSettleService {

    private final PurchaseRepository purchaseRepository;
    private final PurchasePayRepository purchasePayRepository;
    private final StockOutRepository stockOutRepository;
    private final StockLedgerService stockLedgerService;
    private final LedgerService ledgerService;

    @Transactional
    public void onWorkflowEnded(String moduleCode, Long bizId) {
        FormModule module = FormModule.fromNameOrNull(moduleCode);
        if (module == null) {
            return;
        }
        if (module == FormModule.PURCHASE) {
            completePurchase(bizId);
        } else if (module == FormModule.PURCHASE_PAY) {
            completePay(bizId);
        } else if (module == FormModule.STOCK_OUT_PURCHASE || module == FormModule.STOCK_OUT_PROJECT
                || module == FormModule.STOCK_TRANSFER) {
            completeStockOut(bizId);
        }
    }

    @Transactional
    public void restorePurchase(Purchase purchase) {
        if (purchase == null) {
            return;
        }
        purchase.setBudgetDeducted(false);
        stockLedgerService.reverse(StockTxnSource.PURCHASE_IN, purchase.getId());
    }

    @Transactional
    public void restorePay(PurchasePay pay) {
        if (pay == null) {
            return;
        }
        ledgerService.reverse(BankTxnSource.PURCHASE_PAY, pay.getId());
        pay.setLedgerPosted(false);
    }

    @Transactional
    public void restoreStockOut(StockOut out) {
        if (out == null) {
            return;
        }
        out.setBudgetDeducted(false);
        if (out.getKind() == StockOutKind.TRANSFER) {
            stockLedgerService.reverse(StockTxnSource.TRANSFER_OUT, out.getId());
            stockLedgerService.reverse(StockTxnSource.TRANSFER_IN, out.getId());
        } else {
            StockTxnSource source = out.getKind() == StockOutKind.PROJECT ? StockTxnSource.PROJECT_OUT : StockTxnSource.PURCHASE_OUT;
            stockLedgerService.reverse(source, out.getId());
        }
        out.setLedgerPosted(false);
    }

    private void completePurchase(Long bizId) {
        Purchase purchase = purchaseRepository.findById(bizId).orElse(null);
        if (purchase == null) {
            return;
        }
        if (purchase.getProject() != null) {
            purchase.setBudgetDeducted(true);
        }
        List<PurchaseLine> lines = purchase.getLines();
        if (lines == null || lines.isEmpty()) {
            postPurchaseIn(purchase, purchase.getItemName(), purchase.getQty(), purchase.getUnit(), purchase.getAmount());
            return;
        }
        for (PurchaseLine line : lines) {
            postPurchaseIn(purchase, line.getItemName(), line.getQty(), line.getUnit(), line.getAmount());
        }
    }

    private void postPurchaseIn(Purchase purchase, String itemName, BigDecimal qty, String unit, BigDecimal amount) {
        stockLedgerService.post(
                purchase.getWarehouse(),
                purchase.getPurchaseDate(),
                StockTxnDirection.IN,
                itemName,
                qty,
                unit,
                amount,
                "采购入库 " + purchase.getCode() + " " + itemName,
                StockTxnSource.PURCHASE_IN,
                purchase.getId(),
                purchase.getProject(),
                purchase,
                purchase.getCreatedBy()
        );
    }

    private void completePay(Long bizId) {
        PurchasePay pay = purchasePayRepository.findById(bizId).orElse(null);
        if (pay == null || pay.isLedgerPosted()) {
            return;
        }
        Purchase purchase = pay.getPurchase();
        ledgerService.post(
                pay.getAccount(),
                pay.getPayDate(),
                BankTxnDirection.OUT,
                pay.getAmount(),
                "采购付款 " + pay.getCode() + " " + pay.getReason(),
                BankTxnSource.PURCHASE_PAY,
                pay.getId(),
                purchase == null ? null : purchase.getProject(),
                pay.getCreatedBy()
        );
        pay.setLedgerPosted(true);
    }

    private void completeStockOut(Long bizId) {
        StockOut out = stockOutRepository.findById(bizId).orElse(null);
        if (out == null) {
            return;
        }
        if (out.getKind() == StockOutKind.PROJECT && out.getProject() != null
                && BizParse.nz(out.getAmount()).compareTo(BigDecimal.ZERO) > 0) {
            out.setBudgetDeducted(true);
        }
        if (out.getKind() == StockOutKind.TRANSFER) {
            postTransfer(out);
            out.setLedgerPosted(true);
            return;
        }
        StockTxnSource source = out.getKind() == StockOutKind.PROJECT ? StockTxnSource.PROJECT_OUT : StockTxnSource.PURCHASE_OUT;
        stockLedgerService.post(
                out.getWarehouse(),
                out.getOutDate(),
                StockTxnDirection.OUT,
                out.getItemName(),
                out.getQty(),
                out.getUnit(),
                out.getAmount(),
                (out.getKind() == StockOutKind.PROJECT ? "项目出库 " : "采购出库 ") + out.getCode() + " " + out.getItemName(),
                source,
                out.getId(),
                out.getProject(),
                out.getPurchase(),
                out.getCreatedBy()
        );
        out.setLedgerPosted(true);
    }

    private void postTransfer(StockOut out) {
        List<StockOutLine> lines = out.getLines();
        if (lines == null || lines.isEmpty()) {
            postTransferItem(out, out.getItemName(), out.getQty(), out.getUnit());
            return;
        }
        for (StockOutLine line : lines) {
            postTransferItem(out, line.getItemName(), line.getQty(), line.getUnit());
        }
    }

    private void postTransferItem(StockOut out, String itemName, BigDecimal qty, String unit) {
        stockLedgerService.post(
                out.getWarehouse(),
                out.getOutDate(),
                StockTxnDirection.OUT,
                itemName,
                qty,
                unit,
                null,
                "仓库调拨出库 " + out.getCode() + " " + itemName,
                StockTxnSource.TRANSFER_OUT,
                out.getId(),
                null,
                null,
                out.getCreatedBy()
        );
        stockLedgerService.post(
                out.getTargetWarehouse(),
                out.getOutDate(),
                StockTxnDirection.IN,
                itemName,
                qty,
                unit,
                null,
                "仓库调拨入库 " + out.getCode() + " " + itemName,
                StockTxnSource.TRANSFER_IN,
                out.getId(),
                null,
                null,
                out.getCreatedBy()
        );
    }
}

