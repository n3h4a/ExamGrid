package com.examgrid.backend.catalog;

import com.examgrid.solver.model.Session;
import com.examgrid.solver.model.Slot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** One exam sitting. Its stable public id is {@code <date>-<SESSION>}, e.g. {@code 2026-12-01-MORNING}. */
@Entity
@Table(name = "exam_slot")
public class ExamSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Session session;

    protected ExamSlot() {
    }

    public ExamSlot(LocalDate date, Session session) {
        this.date = date;
        this.session = session;
    }

    public Long getId() { return id; }
    public LocalDate getDate() { return date; }
    public Session getSession() { return session; }

    /** Same key the solver uses for this slot. */
    public String key() {
        return Slot.of(date, session).id();
    }
}
