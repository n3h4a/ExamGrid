package com.examgrid.backend.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/** An exam hall: a seat grid with optional blocked seats (labels such as {@code A1}). */
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 120)
    private String building = "";

    @Column(name = "seat_rows", nullable = false)
    private int seatRows;

    @Column(name = "seat_columns", nullable = false)
    private int seatColumns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "blocked_seats", nullable = false, columnDefinition = "jsonb")
    private List<String> blockedSeats = new ArrayList<>();

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean active = true;

    protected Room() {
    }

    public Room(String code) {
        this.code = code;
    }

    public void update(String building, int rows, int columns, List<String> blocked, int priority, boolean active) {
        this.building = building == null ? "" : building;
        this.seatRows = rows;
        this.seatColumns = columns;
        this.blockedSeats = new ArrayList<>(blocked);
        this.capacity = rows * columns - blocked.size();
        this.priority = priority;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getBuilding() { return building; }
    public int getSeatRows() { return seatRows; }
    public int getSeatColumns() { return seatColumns; }
    public List<String> getBlockedSeats() { return List.copyOf(blockedSeats); }
    public int getCapacity() { return capacity; }
    public int getPriority() { return priority; }
    public boolean isActive() { return active; }
}
