package com.examgrid.backend.catalog;

import com.examgrid.backend.catalog.CatalogDtos.CatalogSummary;
import com.examgrid.backend.catalog.CatalogDtos.GenerateSlotsRequest;
import com.examgrid.backend.catalog.CatalogDtos.GroupRequest;
import com.examgrid.backend.catalog.CatalogDtos.GroupView;
import com.examgrid.backend.catalog.CatalogDtos.LockRequest;
import com.examgrid.backend.catalog.CatalogDtos.LockView;
import com.examgrid.backend.catalog.CatalogDtos.SlotRequest;
import com.examgrid.backend.catalog.CatalogDtos.SlotView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Slots, locks and same-slot groups: the rules a run is solved under. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Schedule setup")
public class ScheduleSetupController {

    private final ExamSlotRepository slots;
    private final CourseLockRepository locks;
    private final SameSlotGroupRepository groups;
    private final CatalogService service;

    public ScheduleSetupController(ExamSlotRepository slots, CourseLockRepository locks,
                                   SameSlotGroupRepository groups, CatalogService service) {
        this.slots = slots;
        this.locks = locks;
        this.groups = groups;
        this.service = service;
    }

    @GetMapping("/catalog/summary")
    @Operation(summary = "Counts of every kind of master data, for the dashboard")
    public CatalogSummary summary() {
        return service.summary();
    }

    @GetMapping("/slots")
    public List<SlotView> slots() {
        return slots.findAllByOrderByDateAscSessionAsc().stream().map(SlotView::of).toList();
    }

    @PostMapping("/slots")
    public ResponseEntity<SlotView> createSlot(@Valid @RequestBody SlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SlotView.of(service.createSlot(request.date(), request.session())));
    }

    @PostMapping("/slots/generate")
    @Operation(summary = "Create all slots in a date range, skipping weekdays and holidays")
    public ResponseEntity<List<SlotView>> generate(@Valid @RequestBody GenerateSlotsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.generateSlots(request).stream().map(SlotView::of).toList());
    }

    @DeleteMapping("/slots/{id}")
    public ResponseEntity<Void> deleteSlot(@PathVariable long id) {
        service.deleteSlot(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/locks")
    @Transactional(readOnly = true)
    public List<LockView> locks() {
        return locks.findAllByOrderByLockedAtAsc().stream().map(LockView::of).toList();
    }

    @PutMapping("/locks/{courseCode}")
    @Transactional
    @Operation(summary = "Pin a course's exam to a slot")
    public LockView lock(@PathVariable String courseCode, @Valid @RequestBody LockRequest request) {
        return LockView.of(service.lock(courseCode, request.slotKey()));
    }

    @DeleteMapping("/locks/{courseCode}")
    public ResponseEntity<Void> unlock(@PathVariable String courseCode) {
        service.unlock(courseCode);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/same-slot-groups")
    @Transactional(readOnly = true)
    public List<GroupView> groups() {
        return groups.findAllByOrderByNameAsc().stream().map(GroupView::of).toList();
    }

    @PostMapping("/same-slot-groups")
    @Transactional
    public ResponseEntity<GroupView> createGroup(@Valid @RequestBody GroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupView.of(service.createGroup(request)));
    }

    @PutMapping("/same-slot-groups/{id}")
    @Transactional
    public GroupView updateGroup(@PathVariable long id, @Valid @RequestBody GroupRequest request) {
        return GroupView.of(service.updateGroup(id, request));
    }

    @DeleteMapping("/same-slot-groups/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable long id) {
        service.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}
