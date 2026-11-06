package com.examgrid.backend.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseLockRepository extends JpaRepository<CourseLock, Long> {

    @EntityGraph(attributePaths = {"course", "slot"})
    List<CourseLock> findAllByOrderByLockedAtAsc();
}
