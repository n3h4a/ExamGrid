package com.examgrid.backend.scheduling;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScheduleRunRepository extends JpaRepository<ScheduleRun, Long> {

    Page<ScheduleRun> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<ScheduleRun> findAllByStatusIn(Collection<RunStatus> statuses);

    Optional<ScheduleRun> findFirstByStatus(RunStatus status);

    long countByStatusIn(Collection<RunStatus> statuses);
}
