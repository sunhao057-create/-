package com.projectmgmt.supplier;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {
    Optional<Supplier> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<Supplier> findAllByOrderByIdDesc();

    @Query("""
            select s from Supplier s
            where lower(s.shortName) like :keyword
               or lower(s.companyName) like :keyword
               or lower(s.code) like :keyword
            order by s.id desc
            """)
    List<Supplier> search(@Param("keyword") String keyword);
}
