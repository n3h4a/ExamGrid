package com.examgrid.backend.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "student")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String roll;

    @Column(nullable = false, length = 120)
    private String name = "";

    @Column(nullable = false, length = 64)
    private String department = "";

    @Column(length = 254)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Student() {
    }

    public Student(String roll, Instant now) {
        this.roll = roll;
        this.createdAt = now;
    }

    public void update(String name, String department, String email) {
        this.name = name == null ? "" : name;
        this.department = department == null ? "" : department;
        this.email = email == null || email.isBlank() ? null : email;
    }

    public Long getId() { return id; }
    public String getRoll() { return roll; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
}
