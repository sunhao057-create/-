package com.projectmgmt.finance;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {
    boolean existsByCode(String code);

    boolean existsByAccount_Id(Long accountId);

    List<Expense> findByProject_IdIn(Collection<Long> projectIds);

    @Query("""
            select coalesce(sum(e.amount), 0)
            from Expense e
            where e.project.id = :projectId and e.budgetDeducted = true
            """)
    BigDecimal deductedTotal(@Param("projectId") Long projectId);

    @Query("""
            select coalesce(sum(e.amount), 0)
            from Expense e
            where e.project.id = :projectId and e.budgetDeducted = false
              and (:excludeId is null or e.id <> :excludeId)
            """)
    BigDecimal pendingTotal(@Param("projectId") Long projectId, @Param("excludeId") Long excludeId);
}
