package com.examgrid.backend.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** A manual override: this course's exam must be held in this slot. */
@Entity
@Table(name = "course_lock")
public class CourseLock {

    @Id
    @Column(name = "course_id")
    private Long courseId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(optional = false)
    @JoinColumn(name = "slot_id")
    private ExamSlot slot;

    @Column(name = "locked_by", nullable = false, length = 64)
    private String lockedBy;

    @Column(name = "locked_at", nullable = false)
    private Instant lockedAt;

    protected CourseLock() {
    }

    public CourseLock(Course course, ExamSlot slot, String lockedBy, Instant lockedAt) {
        this.course = course;
        this.slot = slot;
        this.lockedBy = lockedBy;
        this.lockedAt = lockedAt;
    }

    public void relock(ExamSlot newSlot, String by, Instant at) {
        this.slot = newSlot;
        this.lockedBy = by;
        this.lockedAt = at;
    }

    public Long getCourseId() { return courseId; }
    public Course getCourse() { return course; }
    public ExamSlot getSlot() { return slot; }
    public String getLockedBy() { return lockedBy; }
    public Instant getLockedAt() { return lockedAt; }
}
