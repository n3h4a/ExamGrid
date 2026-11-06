package com.examgrid.backend.catalog;

import com.examgrid.solver.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExamSlotRepository extends JpaRepository<ExamSlot, Long> {

    Optional<ExamSlot> findByDateAndSession(LocalDate date, Session session);

    List<ExamSlot> findAllByOrderByDateAscSessionAsc();

    default Optional<ExamSlot> findByKey(String key) {
        return SlotKeys.parse(key).flatMap(k -> findByDateAndSession(k.date(), k.session()));
    }
}
