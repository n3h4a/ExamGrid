package com.examgrid.backend.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "faculty")
public class Faculty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 254)
    private String email;

    @Column(nullable = false, length = 64)
    private String department = "";

    @ManyToMany
    @JoinTable(name = "faculty_course", joinColumns = @JoinColumn(name = "faculty_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"))
    private Set<Course> teaches = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "faculty_unavailability", joinColumns = @JoinColumn(name = "faculty_id"),
            inverseJoinColumns = @JoinColumn(name = "slot_id"))
    private Set<ExamSlot> unavailable = new HashSet<>();

    protected Faculty() {
    }

    public Faculty(String code) {
        this.code = code;
        this.name = code;
    }

    public void update(String name, String email, String department, Set<Course> teaches, Set<ExamSlot> unavailable) {
        this.name = name;
        this.email = email == null || email.isBlank() ? null : email;
        this.department = department == null ? "" : department;
        this.teaches.clear();
        this.teaches.addAll(teaches);
        this.unavailable.clear();
        this.unavailable.addAll(unavailable);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public Set<Course> getTeaches() { return teaches; }
    public Set<ExamSlot> getUnavailable() { return unavailable; }
}
