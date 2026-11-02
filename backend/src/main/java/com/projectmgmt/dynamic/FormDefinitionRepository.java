package com.projectmgmt.dynamic;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {

    Optional<FormDefinition> findByCode(String code);

    List<FormDefinition> findAllByEnabledTrueOrderByPackageCodeAscIdAsc();

    List<FormDefinition> findAllByPackageCodeOrderByIdAsc(String packageCode);
}
