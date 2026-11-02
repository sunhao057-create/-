package com.projectmgmt.finance;

import com.projectmgmt.payment.Payment;
import com.projectmgmt.payment.PaymentRepository;
import com.projectmgmt.project.Project;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
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
public class FinanceAnalyticsService {

    private final BankAccountRepository accountRepository;
    private final BankTxnRepository txnRepository;
    private final ExpenseRepository expenseRepository;
    private final PaymentRepository paymentRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public FinanceAnalyticsView view() {
        permissionService.require(FormModule.BANK_ANALYTICS, "VIEW");
        Long ownAccount = permissionService.ownFilterUserId(FormModule.BANK_ANALYTICS);
        List<BankAccount> accounts = accountRepository.findAll().stream()
                .filter(item -> ownedBy(item.getCreatedBy() == null ? null : item.getCreatedBy().getId(), ownAccount))
                .toList();
        Set<Long> accountIds = accounts.stream().map(BankAccount::getId).collect(Collectors.toSet());
        List<BankTxn> txns = txnRepository.findAllByOrderByTxnDateDescIdDesc().stream()
                .filter(item -> accountIds.contains(item.getAccount().getId()))
                .toList();
        YearMonth thisMonth = YearMonth.now();
        BigDecimal monthIn = BigDecimal.ZERO;
        BigDecimal monthOut = BigDecimal.ZERO;
        Map<String, FinanceAnalyticsView.MonthRow> months = new LinkedHashMap<>();
        List<FinanceAnalyticsView.AccountRow> accountRows = new ArrayList<>();
        BigDecimal totalBalance = BigDecimal.ZERO;
        for (BankAccount account : accounts) {
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;
            for (BankTxn txn : txns) {
                if (!account.getId().equals(txn.getAccount().getId())) {
                    continue;
                }
                if (txn.getDirection() == BankTxnDirection.IN) {
                    income = income.add(nz(txn.getAmount()));
                } else {
                    expense = expense.add(nz(txn.getAmount()));
                }
            }
            BigDecimal opening = nz(account.getOpeningBalance());
            BigDecimal balance = opening.add(income).subtract(expense);
            totalBalance = totalBalance.add(balance);
            accountRows.add(new FinanceAnalyticsView.AccountRow(
                    account.getId(),
                    account.getName(),
                    account.getBankName(),
                    opening,
                    income,
                    expense,
                    balance
            ));
        }
        accountRows.sort(Comparator.comparing(FinanceAnalyticsView.AccountRow::balance).reversed());
        for (BankTxn txn : txns) {
            String month = YearMonth.from(txn.getTxnDate()).toString();
            FinanceAnalyticsView.MonthRow current = months.getOrDefault(
                    month, new FinanceAnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO));
            if (txn.getDirection() == BankTxnDirection.IN) {
                current = new FinanceAnalyticsView.MonthRow(month, current.incomeAmount().add(nz(txn.getAmount())), current.expenseAmount());
                if (YearMonth.from(txn.getTxnDate()).equals(thisMonth)) {
                    monthIn = monthIn.add(nz(txn.getAmount()));
                }
            } else {
                current = new FinanceAnalyticsView.MonthRow(month, current.incomeAmount(), current.expenseAmount().add(nz(txn.getAmount())));
                if (YearMonth.from(txn.getTxnDate()).equals(thisMonth)) {
                    monthOut = monthOut.add(nz(txn.getAmount()));
                }
            }
            months.put(month, current);
        }
        List<FinanceAnalyticsView.MonthRow> monthly = new ArrayList<>(months.values());
        monthly.sort(Comparator.comparing(FinanceAnalyticsView.MonthRow::month));

        Long ownExpense = permissionService.ownFilterUserId(FormModule.EXPENSE);
        List<Expense> expenses = expenseRepository.findAll().stream()
                .filter(item -> ownedBy(item.getCreatedBy() == null ? null : item.getCreatedBy().getId(), ownExpense))
                .toList();
        BigDecimal expenseAmount = expenses.stream().map(item -> nz(item.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal projectLinked = expenses.stream()
                .filter(item -> item.getProject() != null)
                .map(item -> nz(item.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<ExpenseCategory, List<Expense>> byCategory = expenses.stream().collect(Collectors.groupingBy(Expense::getCategory));
        List<FinanceAnalyticsView.CategoryRow> categories = byCategory.entrySet().stream()
                .map(entry -> new FinanceAnalyticsView.CategoryRow(
                        entry.getKey().name(),
                        entry.getKey().label(),
                        entry.getValue().stream().map(item -> nz(item.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add),
                        entry.getValue().size()
                ))
                .sorted(Comparator.comparing(FinanceAnalyticsView.CategoryRow::amount).reversed())
                .toList();

        Map<Long, BigDecimal> expenseByProject = expenses.stream()
                .filter(item -> item.getProject() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getProject().getId(),
                        Collectors.reducing(BigDecimal.ZERO, item -> nz(item.getAmount()), BigDecimal::add)
                ));
        Set<Long> projectIds = expenseByProject.keySet();
        List<Payment> payments = projectIds.isEmpty() ? List.of() : paymentRepository.findByProject_IdIn(projectIds);
        Map<Long, BigDecimal> paymentByProject = payments.stream()
                .filter(Payment::isBudgetDeducted)
                .collect(Collectors.groupingBy(
                        item -> item.getProject().getId(),
                        Collectors.reducing(BigDecimal.ZERO, item -> nz(item.getAmount()), BigDecimal::add)
                ));
        List<FinanceAnalyticsView.ProjectSpendRow> projectRows = expenseByProject.entrySet().stream()
                .map(entry -> {
                    Expense sample = expenses.stream()
                            .filter(item -> item.getProject() != null && entry.getKey().equals(item.getProject().getId()))
                            .findFirst()
                            .orElse(null);
                    Project project = sample == null ? null : sample.getProject();
                    BigDecimal pay = paymentByProject.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                    return new FinanceAnalyticsView.ProjectSpendRow(
                            entry.getKey(),
                            project == null ? "" : project.getName(),
                            project == null ? "" : project.getCode(),
                            BigDecimal.ZERO,
                            pay,
                            entry.getValue(),
                            pay.add(entry.getValue())
                    );
                })
                .sorted(Comparator.comparing(FinanceAnalyticsView.ProjectSpendRow::spendAmount).reversed())
                .toList();

        List<BankTxnView> recent = txns.stream().limit(12).map(BankTxnView::from).toList();
        FinanceAnalyticsView.Kpi kpis = new FinanceAnalyticsView.Kpi(
                accounts.size(),
                totalBalance,
                monthIn,
                monthOut,
                expenseAmount,
                projectLinked,
                expenseAmount.subtract(projectLinked)
        );
        return new FinanceAnalyticsView(kpis, accountRows, monthly, categories, projectRows, recent);
    }

    @Transactional(readOnly = true)
    public FinanceStatementView statement(Long accountId, String keyword) {
        permissionService.require(FormModule.BANK_ANALYTICS, "VIEW");
        Long ownAccount = permissionService.ownFilterUserId(FormModule.BANK_ANALYTICS);
        List<BankAccount> accounts = accountRepository.findAll().stream()
                .filter(item -> ownedBy(item.getCreatedBy() == null ? null : item.getCreatedBy().getId(), ownAccount))
                .sorted(Comparator.comparing(BankAccount::getId))
                .toList();
        List<FinanceAnalyticsView.AccountRow> accountRows = accounts.stream()
                .map(this::toAccountRow)
                .toList();
        if (accounts.isEmpty()) {
            return new FinanceStatementView(
                    accountRows, null, null, null, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
        }
        BankAccount selected = accounts.stream()
                .filter(item -> accountId != null && accountId.equals(item.getId()))
                .findFirst()
                .orElse(accounts.get(0));
        List<BankTxn> chronological = new ArrayList<>(txnRepository.findByAccount_IdOrderByTxnDateDescIdDesc(selected.getId()));
        chronological.sort(Comparator.comparing(BankTxn::getTxnDate).thenComparing(BankTxn::getId));
        BigDecimal opening = nz(selected.getOpeningBalance());
        BigDecimal running = opening;
        YearMonth thisMonth = YearMonth.now();
        BigDecimal monthIn = BigDecimal.ZERO;
        BigDecimal monthOut = BigDecimal.ZERO;
        Map<String, FinanceAnalyticsView.MonthRow> months = new LinkedHashMap<>();
        List<FinanceStatementView.Line> built = new ArrayList<>();
        for (BankTxn txn : chronological) {
            boolean inflow = txn.getDirection() == BankTxnDirection.IN;
            BigDecimal amount = nz(txn.getAmount());
            running = inflow ? running.add(amount) : running.subtract(amount);
            String month = YearMonth.from(txn.getTxnDate()).toString();
            FinanceAnalyticsView.MonthRow current = months.getOrDefault(
                    month, new FinanceAnalyticsView.MonthRow(month, BigDecimal.ZERO, BigDecimal.ZERO));
            if (inflow) {
                current = new FinanceAnalyticsView.MonthRow(month, current.incomeAmount().add(amount), current.expenseAmount());
                if (YearMonth.from(txn.getTxnDate()).equals(thisMonth)) {
                    monthIn = monthIn.add(amount);
                }
            } else {
                current = new FinanceAnalyticsView.MonthRow(month, current.incomeAmount(), current.expenseAmount().add(amount));
                if (YearMonth.from(txn.getTxnDate()).equals(thisMonth)) {
                    monthOut = monthOut.add(amount);
                }
            }
            months.put(month, current);
            built.add(new FinanceStatementView.Line(
                    txn.getId(),
                    txn.getTxnDate(),
                    txn.getSummary(),
                    txn.getProject() == null ? null : txn.getProject().getName(),
                    inflow ? amount : BigDecimal.ZERO,
                    inflow ? BigDecimal.ZERO : amount,
                    running,
                    txn.getDirection().name(),
                    txn.getSourceType() == null ? null : txn.getSourceType().name(),
                    txn.getSourceId()
            ));
        }
        Collections.reverse(built);
        List<FinanceAnalyticsView.MonthRow> monthly = new ArrayList<>(months.values());
        monthly.sort(Comparator.comparing(FinanceAnalyticsView.MonthRow::month));
        String like = keyword == null ? "" : keyword.trim().toLowerCase();
        List<FinanceStatementView.Line> lines = like.isBlank()
                ? built
                : built.stream().filter(line -> matches(line, like)).toList();
        return new FinanceStatementView(
                accountRows,
                selected.getId(),
                selected.getName(),
                selected.getBankName(),
                selected.getAccountNo(),
                opening,
                running,
                monthIn,
                monthOut,
                monthly,
                lines
        );
    }

    private FinanceAnalyticsView.AccountRow toAccountRow(BankAccount account) {
        List<BankTxn> txns = txnRepository.findByAccount_IdOrderByTxnDateDescIdDesc(account.getId());
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (BankTxn txn : txns) {
            if (txn.getDirection() == BankTxnDirection.IN) {
                income = income.add(nz(txn.getAmount()));
            } else {
                expense = expense.add(nz(txn.getAmount()));
            }
        }
        BigDecimal opening = nz(account.getOpeningBalance());
        return new FinanceAnalyticsView.AccountRow(
                account.getId(),
                account.getName(),
                account.getBankName(),
                opening,
                income,
                expense,
                opening.add(income).subtract(expense)
        );
    }

    private static boolean matches(FinanceStatementView.Line line, String like) {
        String summary = line.summary() == null ? "" : line.summary().toLowerCase();
        String project = line.projectName() == null ? "" : line.projectName().toLowerCase();
        return summary.contains(like) || project.contains(like);
    }

    private static boolean ownedBy(Long ownerId, Long filter) {
        return filter == null || Objects.equals(filter, ownerId);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
