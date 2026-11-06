package com.examgrid.backend.catalog;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SameSlotGroupRepository extends JpaRepository<SameSlotGroup, Long> {

    @EntityGraph(attributePaths = "members")
    List<SameSlotGroup> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
