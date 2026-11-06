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

/** Courses that must be examined in the same slot, e.g. a common paper across branches. */
@Entity
@Table(name = "same_slot_group")
public class SameSlotGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @ManyToMany
    @JoinTable(name = "same_slot_group_member", joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"))
    private Set<Course> members = new HashSet<>();

    protected SameSlotGroup() {
    }

    public SameSlotGroup(String name) {
        this.name = name;
    }

    public void update(String newName, Set<Course> courses) {
        this.name = newName;
        this.members.clear();
        this.members.addAll(courses);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Set<Course> getMembers() { return members; }
}
