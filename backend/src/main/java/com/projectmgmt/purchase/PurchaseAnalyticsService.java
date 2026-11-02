package com.projectmgmt.purchase;

import com.projectmgmt.common.BizParse;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseAnalyticsService {

    private final PurchaseRepository purchaseRepository;
    private final PurchasePayRepository purchasePayRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public PurchaseAnalyticsView view() {
        permissionService.require(FormModule.PURCHASE_ANALYTICS, "VIEW");
        Long own = permissionService.ownFilterUserId(FormModule.PURCHASE_ANALYTICS);
        List<Purchase> purchases = purchaseRepository.findAll().stream()
                .filter(item -> ownedBy(item.getCreatedBy() == null ? null : item.getCreatedBy().getId(), own))
                .toList();
        Map<Long, BigDecimal> paidByPurchase = new HashMap<>();
        Map<String, BigDecimal> paidByMonth = new HashMap<>();
        for (PurchasePay pay : purchasePayRepository.findAll()) {
            Long purchaseId = pay.getPurchase().getId();
            BigDecimal amount = BizParse.nz(pay.getAmount());
            paidByPurchase.merge(purchaseId, amount, BigDecimal::add);
            String month = YearMonth.from(pay.getPayDate()).toString();
            paidByMonth.merge(month, amount, BigDecimal::add);
        }

        BigDecimal purchaseAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        BigDecimal unpaidAmount = BigDecimal.ZERO;
        BigDecimal linkedAmount = BigDecimal.ZERO;
        BigDecimal unlinkedAmount = BigDecimal.ZERO;
        Map<Long, PurchaseAnalyticsView.SupplierRow> suppliers = new LinkedHashMap<>();
        Map<String, PurchaseAnalyticsView.MonthRow> months = new LinkedHashMap<>();
        List<PurchaseAnalyticsView.RecentRow> recent = new ArrayList<>();

        for (Purchase purchase : purchases) {
            BigDecimal amount = BizParse.nz(purchase.getAmount());
            BigDecimal paid = paidByPurchase.getOrDefault(purchase.getId(), BigDecimal.ZERO);
            BigDecimal unpaid = amount.subtract(paid);
            if (unpaid.compareTo(BigDecimal.ZERO) < 0) {
                unpaid = BigDecimal.ZERO;
            }
            purchaseAmount = purchaseAmount.add(amount);
            paidAmount = paidAmount.add(paid);
            unpaidAmount = unpaidAmount.add(unpaid);
            if (purchase.getProject() == null) {
                unlinkedAmount = unlinkedAmount.add(amount);
            } else {
                linkedAmount = linkedAmount.add(amount);
            }
            Long supplierId = purchase.getSupplier().getId();
            PurchaseAnalyticsView.SupplierRow supplier = suppliers.getOrDefault(
                    supplierId,
                    new PurchaseAnalyticsView.SupplierRow(supplierId, purchase.getSupplier().getShortName(), 0, BigDecimal.ZERO, BigDecimal.ZERO));
            suppliers.put(supplierId, new PurchaseAnalyticsView.SupplierRow(
                    supplierId,
                    supplier.supplierName(),
                    supplier.purchaseCount() + 1,
                    supplier.purchaseAmount().add(amount),
                    supplier.unpaidAmount().add(unpaid)));
            String month = YearMonth.from(purchase.getPurchaseDate()).toString();
            PurchaseAnalyticsView.MonthRow current = months.getOrDefault(
                    month, new PurchaseAnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO));
            months.put(month, new PurchaseAnalyticsView.MonthRow(month, current.purchaseAmount().add(amount), current.paidAmount()));
            recent.add(new PurchaseAnalyticsView.RecentRow(
                    purchase.getId(),
                    purchase.getCode(),
                    purchase.getItemName(),
                    purchase.getSupplier().getShortName(),
                    purchase.getPurchaseDate(),
                    amount,
                    unpaid,
                    purchase.getProject() != null));
        }
        for (Map.Entry<String, BigDecimal> entry : paidByMonth.entrySet()) {
            PurchaseAnalyticsView.MonthRow current = months.getOrDefault(
                    entry.getKey(), new PurchaseAnalyticsView.MonthRow(entry.getKey(), BigDecimal.ZERO, BigDecimal.ZERO));
            months.put(entry.getKey(), new PurchaseAnalyticsView.MonthRow(current.month(), current.purchaseAmount(), entry.getValue()));
        }
        List<PurchaseAnalyticsView.SupplierRow> supplierRows = new ArrayList<>(suppliers.values());
        supplierRows.sort(Comparator.comparing(PurchaseAnalyticsView.SupplierRow::purchaseAmount).reversed());
        List<PurchaseAnalyticsView.MonthRow> monthly = months.values().stream()
                .sorted(Comparator.comparing(PurchaseAnalyticsView.MonthRow::month))
                .toList();
        recent.sort(Comparator.comparing(PurchaseAnalyticsView.RecentRow::purchaseDate)
                .thenComparing(PurchaseAnalyticsView.RecentRow::id)
                .reversed());
        if (recent.size() > 8) {
            recent = new ArrayList<>(recent.subList(0, 8));
        }
        return new PurchaseAnalyticsView(
                new PurchaseAnalyticsView.Kpi(
                        purchases.size(),
                        purchaseAmount,
                        paidAmount,
                        unpaidAmount,
                        linkedAmount,
                        unlinkedAmount),
                supplierRows,
                monthly,
                recent);
    }

    private static boolean ownedBy(Long ownerId, Long filter) {
        return filter == null || Objects.equals(ownerId, filter);
    }
}
