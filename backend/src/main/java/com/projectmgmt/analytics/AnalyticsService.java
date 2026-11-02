package com.projectmgmt.analytics;

import com.projectmgmt.contract.Contract;
import com.projectmgmt.contract.ContractCollection;
import com.projectmgmt.contract.ContractStatus;
import com.projectmgmt.contract.ContractCollectionRepository;
import com.projectmgmt.contract.ContractRepository;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.finance.Expense;
import com.projectmgmt.finance.ExpenseRepository;
import com.projectmgmt.payment.Payment;
import com.projectmgmt.payment.PaymentRepository;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.purchase.Purchase;
import com.projectmgmt.purchase.PurchaseRepository;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.warehouse.StockOut;
import com.projectmgmt.warehouse.StockOutKind;
import com.projectmgmt.warehouse.StockOutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ContractRepository contractRepository;
    private final ContractCollectionRepository collectionRepository;
    private final ProjectRepository projectRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final PurchaseRepository purchaseRepository;
    private final StockOutRepository stockOutRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public AnalyticsView customerView(Long customerId, String contractCode, String projectName, LocalDate from, LocalDate to) {
        permissionService.require(FormModule.CUSTOMER, "VIEW");
        return build(customerId, contractCode, projectName, from, to, FormModule.CUSTOMER);
    }

    @Transactional(readOnly = true)
    public AnalyticsView projectView(Long customerId, String contractCode, String projectName, LocalDate from, LocalDate to) {
        permissionService.require(FormModule.PROJECT_ANALYTICS, "VIEW");
        return build(customerId, contractCode, projectName, from, to, FormModule.PROJECT_ANALYTICS);
    }

    private AnalyticsView build(
            Long customerId,
            String contractCode,
            String projectName,
            LocalDate from,
            LocalDate to,
            FormModule scopeModule
    ) {
        Long ownId = permissionService.ownFilterUserId(scopeModule);
        List<Contract> contracts = contractRepository.findAll().stream()
                .filter(item -> customerId == null || (item.getCustomer() != null && customerId.equals(item.getCustomer().getId())))
                .filter(item -> !StringUtils.hasText(contractCode)
                        || (item.getCode() != null && item.getCode().toLowerCase().contains(contractCode.trim().toLowerCase()))
                        || (item.getName() != null && item.getName().toLowerCase().contains(contractCode.trim().toLowerCase())))
                .filter(item -> inRange(item.getSignDate() != null ? item.getSignDate() : item.getCreatedAt().toLocalDate(), from, to))
                .filter(item -> ownedBy(item.getCreatedBy(), ownId))
                .toList();
        Set<Long> contractIds = contracts.stream().map(Contract::getId).collect(Collectors.toSet());
        Set<Long> linkedIds = contractIds;
        List<Project> projects = projectRepository.findAll().stream()
                .filter(item -> customerId == null || (item.getCustomer() != null && customerId.equals(item.getCustomer().getId())))
                .filter(item -> !StringUtils.hasText(projectName)
                        || contains(item.getName(), projectName)
                        || contains(item.getCode(), projectName))
                .filter(item -> {
                    if (StringUtils.hasText(contractCode)) {
                        return item.getContract() != null && linkedIds.contains(item.getContract().getId());
                    }
                    return true;
                })
                .filter(item -> inRange(item.getCreatedAt().toLocalDate(), from, to)
                        || (item.getContract() != null && linkedIds.contains(item.getContract().getId())))
                .filter(item -> ownedBy(item.getCreatedBy(), ownId))
                .toList();

        if (StringUtils.hasText(projectName)) {
            Set<Long> keepContracts = projects.stream()
                    .map(Project::getContract)
                    .filter(Objects::nonNull)
                    .map(Contract::getId)
                    .collect(Collectors.toSet());
            contracts = contracts.stream().filter(item -> keepContracts.contains(item.getId())).toList();
            contractIds = keepContracts;
        }

        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        List<ContractCollection> collections = collectionsOf(contractIds);
        Map<Long, BigDecimal> collectedByContract = collections.stream()
                .filter(item -> inRange(item.getReceivedDate(), from, to))
                .collect(Collectors.groupingBy(
                        item -> item.getContract().getId(),
                        Collectors.reducing(BigDecimal.ZERO, ContractCollection::getAmount, BigDecimal::add)
                ));
        Map<Long, BigDecimal> collectedByProject = collections.stream()
                .filter(item -> item.getProject() != null && inRange(item.getReceivedDate(), from, to))
                .collect(Collectors.groupingBy(
                        item -> item.getProject().getId(),
                        Collectors.reducing(BigDecimal.ZERO, ContractCollection::getAmount, BigDecimal::add)
                ));
        Map<Long, BigDecimal> paidByProject = new java.util.HashMap<>();
        if (!projectIds.isEmpty()) {
            paymentRepository.findByProject_IdIn(projectIds).stream()
                    .filter(Payment::isBudgetDeducted)
                    .filter(item -> inRange(item.getCreatedAt().toLocalDate(), from, to))
                    .forEach(item -> paidByProject.merge(item.getProject().getId(), nz(item.getAmount()), BigDecimal::add));
            expenseRepository.findByProject_IdIn(projectIds).stream()
                    .filter(Expense::isBudgetDeducted)
                    .filter(item -> inRange(item.getExpenseDate(), from, to))
                    .forEach(item -> paidByProject.merge(item.getProject().getId(), nz(item.getAmount()), BigDecimal::add));
            purchaseRepository.findByProject_IdIn(projectIds).stream()
                    .filter(Purchase::isBudgetDeducted)
                    .filter(item -> inRange(item.getPurchaseDate(), from, to))
                    .forEach(item -> paidByProject.merge(item.getProject().getId(), nz(item.getAmount()), BigDecimal::add));
            stockOutRepository.findByProject_IdIn(projectIds).stream()
                    .filter(item -> item.getKind() == StockOutKind.PROJECT)
                    .filter(StockOut::isBudgetDeducted)
                    .filter(item -> inRange(item.getOutDate(), from, to))
                    .forEach(item -> paidByProject.merge(item.getProject().getId(), nz(item.getAmount()), BigDecimal::add));
        }
        BigDecimal contractAmount = contracts.stream().map(item -> nz(item.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collectedAmount = collectedByContract.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paymentAmount = paidByProject.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Double avgProgress = projects.isEmpty()
                ? null
                : projects.stream().mapToInt(item -> item.getProgressPercent() == null ? 0 : item.getProgressPercent()).average().orElse(0);

        Set<Long> customerIds = contracts.stream().map(item -> item.getCustomer().getId()).collect(Collectors.toSet());
        customerIds.addAll(projects.stream().map(item -> item.getCustomer().getId()).toList());

        Double collectionRate = contractAmount.compareTo(BigDecimal.ZERO) <= 0
                ? null
                : collectedAmount.multiply(BigDecimal.valueOf(100))
                .divide(contractAmount, 1, java.math.RoundingMode.HALF_UP)
                .doubleValue();
        long executing = contracts.stream().filter(item -> item.getStatus() == ContractStatus.EXECUTING).count();
        LocalDate today = LocalDate.now();
        long overdue = contracts.stream()
                .filter(item -> item.getEndDate() != null)
                .filter(item -> item.getStatus() != ContractStatus.CLOSED && item.getStatus() != ContractStatus.CANCELLED)
                .filter(item -> item.getEndDate().isBefore(today))
                .count();
        AnalyticsView.Kpi kpis = new AnalyticsView.Kpi(
                customerIds.size(),
                contracts.size(),
                contractAmount,
                collectedAmount,
                contractAmount.subtract(collectedAmount),
                projects.size(),
                paymentAmount,
                avgProgress,
                collectionRate,
                executing,
                overdue
        );

        Map<Long, List<Contract>> contractsByCustomer = contracts.stream()
                .collect(Collectors.groupingBy(item -> item.getCustomer().getId()));
        Map<Long, List<Project>> projectsByCustomer = projects.stream()
                .collect(Collectors.groupingBy(item -> item.getCustomer().getId()));
        List<AnalyticsView.CustomerRow> customerRows = customerIds.stream()
                .map(id -> {
                    List<Contract> rows = contractsByCustomer.getOrDefault(id, List.of());
                    List<Project> pRows = projectsByCustomer.getOrDefault(id, List.of());
                    Customer customer = rows.isEmpty() ? pRows.get(0).getCustomer() : rows.get(0).getCustomer();
                    BigDecimal amount = rows.stream().map(item -> nz(item.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal collected = rows.stream()
                            .map(item -> collectedByContract.getOrDefault(item.getId(), BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Double progress = pRows.isEmpty()
                            ? null
                            : pRows.stream().mapToInt(item -> item.getProgressPercent() == null ? 0 : item.getProgressPercent()).average().orElse(0);
                    return new AnalyticsView.CustomerRow(
                            id,
                            customer.getShortName(),
                            rows.size(),
                            amount,
                            collected,
                            amount.subtract(collected),
                            pRows.size(),
                            progress
                    );
                })
                .sorted(Comparator.comparing(AnalyticsView.CustomerRow::contractAmount).reversed())
                .toList();

        Map<Long, Long> projectCountByContract = projects.stream()
                .filter(item -> item.getContract() != null)
                .collect(Collectors.groupingBy(item -> item.getContract().getId(), Collectors.counting()));
        List<AnalyticsView.ContractRow> contractRows = contracts.stream()
                .map(item -> {
                    BigDecimal collected = collectedByContract.getOrDefault(item.getId(), BigDecimal.ZERO);
                    return new AnalyticsView.ContractRow(
                            item.getId(),
                            item.getCode(),
                            item.getName(),
                            item.getCustomer().getId(),
                            item.getCustomer().getShortName(),
                            item.getStatus().label(),
                            nz(item.getAmount()),
                            collected,
                            nz(item.getAmount()).subtract(collected),
                            projectCountByContract.getOrDefault(item.getId(), 0L),
                            item.getSignDate() == null ? null : item.getSignDate().toString()
                    );
                })
                .toList();

        List<AnalyticsView.ProjectRow> projectRows = projects.stream()
                .map(item -> {
                    BigDecimal linked = item.getContract() == null ? BigDecimal.ZERO : nz(item.getContract().getAmount());
                    return new AnalyticsView.ProjectRow(
                            item.getId(),
                            item.getName(),
                            item.getCode(),
                            item.getCustomer().getId(),
                            item.getCustomer().getShortName(),
                            item.getContract() == null ? null : item.getContract().getId(),
                            item.getContract() == null ? null : item.getContract().getCode(),
                            item.getContract() == null ? null : item.getContract().getName(),
                            item.getProgressPercent(),
                            linked,
                            collectedByProject.getOrDefault(item.getId(), BigDecimal.ZERO),
                            paidByProject.getOrDefault(item.getId(), BigDecimal.ZERO)
                    );
                })
                .toList();

        Map<String, AnalyticsView.MonthRow> months = new LinkedHashMap<>();
        for (Contract item : contracts) {
            String month = YearMonth.from(item.getSignDate() != null ? item.getSignDate() : item.getCreatedAt()).toString();
            AnalyticsView.MonthRow current = months.getOrDefault(month, new AnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            months.put(month, new AnalyticsView.MonthRow(
                    month,
                    current.contractAmount().add(nz(item.getAmount())),
                    current.collectedAmount(),
                    current.paymentAmount()
            ));
        }
        for (ContractCollection item : collections) {
            if (!inRange(item.getReceivedDate(), from, to)) {
                continue;
            }
            String month = YearMonth.from(item.getReceivedDate()).toString();
            AnalyticsView.MonthRow current = months.getOrDefault(month, new AnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            months.put(month, new AnalyticsView.MonthRow(
                    month,
                    current.contractAmount(),
                    current.collectedAmount().add(nz(item.getAmount())),
                    current.paymentAmount()
            ));
        }
        for (Payment item : projectIds.isEmpty() ? List.<Payment>of() : paymentRepository.findByProject_IdIn(projectIds)) {
            if (!item.isBudgetDeducted() || !inRange(item.getCreatedAt().toLocalDate(), from, to)) {
                continue;
            }
            String month = YearMonth.from(item.getCreatedAt()).toString();
            AnalyticsView.MonthRow current = months.getOrDefault(month, new AnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            months.put(month, new AnalyticsView.MonthRow(
                    month,
                    current.contractAmount(),
                    current.collectedAmount(),
                    current.paymentAmount().add(nz(item.getAmount()))
            ));
        }
        for (Expense item : projectIds.isEmpty() ? List.<Expense>of() : expenseRepository.findByProject_IdIn(projectIds)) {
            if (!item.isBudgetDeducted() || !inRange(item.getExpenseDate(), from, to)) {
                continue;
            }
            String month = YearMonth.from(item.getExpenseDate()).toString();
            AnalyticsView.MonthRow current = months.getOrDefault(month, new AnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            months.put(month, new AnalyticsView.MonthRow(
                    month,
                    current.contractAmount(),
                    current.collectedAmount(),
                    current.paymentAmount().add(nz(item.getAmount()))
            ));
        }
        for (Purchase item : projectIds.isEmpty() ? List.<Purchase>of() : purchaseRepository.findByProject_IdIn(projectIds)) {
            if (!item.isBudgetDeducted() || !inRange(item.getPurchaseDate(), from, to)) {
                continue;
            }
            String month = YearMonth.from(item.getPurchaseDate()).toString();
            AnalyticsView.MonthRow current = months.getOrDefault(month, new AnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            months.put(month, new AnalyticsView.MonthRow(
                    month,
                    current.contractAmount(),
                    current.collectedAmount(),
                    current.paymentAmount().add(nz(item.getAmount()))
            ));
        }
        for (StockOut item : projectIds.isEmpty() ? List.<StockOut>of() : stockOutRepository.findByProject_IdIn(projectIds)) {
            if (item.getKind() != StockOutKind.PROJECT || !item.isBudgetDeducted() || !inRange(item.getOutDate(), from, to)) {
                continue;
            }
            String month = YearMonth.from(item.getOutDate()).toString();
            AnalyticsView.MonthRow current = months.getOrDefault(month, new AnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
            months.put(month, new AnalyticsView.MonthRow(
                    month,
                    current.contractAmount(),
                    current.collectedAmount(),
                    current.paymentAmount().add(nz(item.getAmount()))
            ));
        }
        List<AnalyticsView.MonthRow> monthly = new ArrayList<>(months.values());
        monthly.sort(Comparator.comparing(AnalyticsView.MonthRow::month));
        return new AnalyticsView(kpis, customerRows, contractRows, projectRows, monthly);
    }

    private List<ContractCollection> collectionsOf(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return collectionRepository.findByContract_IdIn(ids);
    }

    private static boolean ownedBy(SysUser user, Long ownId) {
        return ownId == null || (user != null && ownId.equals(user.getId()));
    }

    private static boolean contains(String value, String keyword) {
        return value != null && keyword != null && value.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        if (date == null) {
            return from == null && to == null;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
