package com.examgrid.solver.cli;

import com.examgrid.solver.pipeline.ExamAssignment;
import com.examgrid.solver.pipeline.InvigilationAssignment;
import com.examgrid.solver.pipeline.SeatAssignment;
import com.examgrid.solver.pipeline.Solution;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a solution as three CSV files, the format exam cells import into Excel. */
final class SolutionCsvWriter {

    private SolutionCsvWriter() {
    }

    static void write(Solution solution, Path dir) throws IOException {
        Files.createDirectories(dir);
        try (Writer w = Files.newBufferedWriter(dir.resolve("timetable.csv"), StandardCharsets.UTF_8)) {
            w.write("course,slot\n");
            for (ExamAssignment e : solution.exams()) {
                w.write(e.courseCode() + "," + e.slotId() + "\n");
            }
        }
        try (Writer w = Files.newBufferedWriter(dir.resolve("seats.csv"), StandardCharsets.UTF_8)) {
            w.write("roll,course,slot,room,seat\n");
            for (SeatAssignment s : solution.seats()) {
                w.write(s.roll() + "," + s.courseCode() + "," + s.slotId() + "," + s.roomCode() + ","
                        + s.seatLabel() + "\n");
            }
        }
        try (Writer w = Files.newBufferedWriter(dir.resolve("invigilation.csv"), StandardCharsets.UTF_8)) {
            w.write("faculty,room,slot\n");
            for (InvigilationAssignment i : solution.invigilations()) {
                w.write(i.facultyId() + "," + i.roomCode() + "," + i.slotId() + "\n");
            }
        }
    }
}
