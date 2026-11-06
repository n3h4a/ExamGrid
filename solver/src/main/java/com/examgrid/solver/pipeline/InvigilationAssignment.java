package com.examgrid.solver.pipeline;

/** One faculty member supervising one room in one slot. */
public record InvigilationAssignment(String facultyId, String roomCode, String slotId) {
}
