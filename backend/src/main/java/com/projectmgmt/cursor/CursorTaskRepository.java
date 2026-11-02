package com.projectmgmt.cursor;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CursorTaskRepository extends JpaRepository<CursorTask, Long> {
    List<CursorTask> findAllByOrderByIdDesc();
}
