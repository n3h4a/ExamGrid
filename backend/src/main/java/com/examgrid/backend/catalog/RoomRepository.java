package com.examgrid.backend.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByCode(String code);

    List<Room> findAllByOrderByPriorityAscCodeAsc();

    List<Room> findAllByActiveTrueOrderByPriorityAscCodeAsc();
}
