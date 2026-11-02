package com.projectmgmt.tech;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TechRecordRepository extends JpaRepository<TechRecord, Long>, JpaSpecificationExecutor<TechRecord> {

    long countByKind(TechKind kind);

    java.util.List<TechRecord> findByKindAndExtraJsonContainingOrderByIdDesc(TechKind kind, String code);

    java.util.List<TechRecord> findByKindOrderByIdDesc(TechKind kind);

    Page<TechRecord> findAllByKindOrderByIdDesc(TechKind kind, Pageable pageable);

    @Query("select r from TechRecord r where r.kind = :kind and (:keyword is null or :keyword = '' or r.title like %:keyword% or r.code like %:keyword%) order by r.id desc")
    Page<TechRecord> search(@Param("kind") TechKind kind, @Param("keyword") String keyword, Pageable pageable);
}
