package com.examgrid.backend.catalog;

import com.examgrid.solver.model.Session;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "course")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 64)
    private String department = "";

    @Column(nullable = false)
    private boolean hard;

    @Convert(converter = SessionSetConverter.class)
    @Column(name = "allowed_sessions", nullable = false, length = 64)
    private Set<Session> allowedSessions = EnumSet.noneOf(Session.class);

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Course() {
    }

    public Course(String code, Instant now) {
        this.code = code;
        this.name = code;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String department, boolean hard, Set<Session> sessions, Instant now) {
        this.name = name;
        this.department = department == null ? "" : department;
        this.hard = hard;
        this.allowedSessions = sessions == null || sessions.isEmpty()
                ? EnumSet.noneOf(Session.class) : EnumSet.copyOf(sessions);
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public boolean isHard() { return hard; }
    public Set<Session> getAllowedSessions() { return allowedSessions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
