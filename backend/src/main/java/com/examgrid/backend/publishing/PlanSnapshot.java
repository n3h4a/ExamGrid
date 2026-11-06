package com.examgrid.backend.publishing;

import com.examgrid.backend.scheduling.RunViews.InvigilationEntry;
import com.examgrid.backend.scheduling.RunViews.RoomPlan;
import com.examgrid.backend.scheduling.RunViews.TimetableEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Everything needed to publish a run, loaded once in bulk. */
public record PlanSnapshot(long runId, String label, List<TimetableEntry> timetable,
                           Map<String, StudentTicket> students, Map<String, List<RoomPlan>> roomsBySlot,
                           List<InvigilationEntry> invigilation) {

    /** One exam on a hall ticket or in a seat lookup. Contains no personal data. */
    public record SeatInfo(String courseCode, String courseName, String slot, LocalDate date, String session,
                           String roomCode, String building, String seat) {
    }

    public record StudentTicket(String roll, String name, String department, List<SeatInfo> exams) {
    }
}
