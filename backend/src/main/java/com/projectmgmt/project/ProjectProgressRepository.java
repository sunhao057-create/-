package com.projectmgmt.project;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectProgressRepository extends JpaRepository<ProjectProgress, Long> {
    List<ProjectProgress> findByProjectOrderByIdDesc(Project project);

    void deleteByProject(Project project);
}
