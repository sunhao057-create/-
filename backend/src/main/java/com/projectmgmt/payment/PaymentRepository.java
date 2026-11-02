package com.projectmgmt.payment;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
    boolean existsByCode(String code);

    boolean existsByProject_Id(Long projectId);

    boolean existsByAccount_Id(Long accountId);

    List<Payment> findByProject_IdIn(Collection<Long> projectIds);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.project.id = :projectId and p.budgetDeducted = true
            """)
    BigDecimal paidTotal(@Param("projectId") Long projectId);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.project.id = :projectId and p.budgetDeducted = false
              and (:excludeId is null or p.id <> :excludeId)
            """)
    BigDecimal pendingTotal(@Param("projectId") Long projectId, @Param("excludeId") Long excludeId);
}
