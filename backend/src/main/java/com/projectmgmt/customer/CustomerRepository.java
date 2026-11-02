package com.projectmgmt.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByCode(String code);

    Optional<Customer> findFirstByCompanyName(String companyName);

    boolean existsByCode(String code);

    boolean existsByCompanyName(String companyName);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Customer> findAllByOrderByIdDesc();

    @Query("""
            select c from Customer c
            where lower(c.shortName) like :keyword
               or lower(c.companyName) like :keyword
               or lower(c.code) like :keyword
            order by c.id desc
            """)
    List<Customer> search(@Param("keyword") String keyword);
}
