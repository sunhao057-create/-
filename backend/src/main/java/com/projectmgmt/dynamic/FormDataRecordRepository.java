package com.projectmgmt.dynamic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormDataRecordRepository extends JpaRepository<FormDataRecord, Long> {

    Page<FormDataRecord> findAllByDefinitionIdOrderByIdDesc(Long definitionId, Pageable pageable);

    Page<FormDataRecord> findAllByDefinitionIdAndCreatedByIdOrderByIdDesc(Long definitionId, Long userId, Pageable pageable);

    @Query("select r from FormDataRecord r where r.definition.id = :defId and (:userId is null or r.createdBy.id = :userId) and (:keyword is null or :keyword = '' or r.title like %:keyword% or r.valuesJson like %:keyword%) order by r.id desc")
    Page<FormDataRecord> search(@Param("defId") Long defId, @Param("userId") Long userId, @Param("keyword") String keyword, @Param("status") String status, Pageable pageable);

    List<FormDataRecord> findAllByDefinitionIdOrderByIdDesc(Long definitionId);

    Optional<FormDataRecord> findByDefinitionIdAndSourceKey(Long definitionId, String sourceKey);

    long countByDefinitionIdAndStatus(Long definitionId, String status);
}
