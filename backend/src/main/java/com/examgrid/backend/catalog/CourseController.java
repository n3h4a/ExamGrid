package com.examgrid.backend.catalog;

import com.examgrid.backend.catalog.CatalogDtos.CourseRequest;
import com.examgrid.backend.catalog.CatalogDtos.CourseView;
import com.examgrid.backend.catalog.CatalogDtos.StudentView;
import com.examgrid.backend.common.api.PageResponse;
import com.examgrid.backend.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Courses")
public class CourseController {

    private final CourseRepository courses;
    private final EnrolmentRepository enrolments;
    private final CatalogService service;

    public CourseController(CourseRepository courses, EnrolmentRepository enrolments, CatalogService service) {
        this.courses = courses;
        this.enrolments = enrolments;
        this.service = service;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List or search courses (code or name), optionally by department")
    public PageResponse<CourseView> list(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String department,
                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        Map<Long, Long> candidates = service.candidatesPerCourse();
        return PageResponse.of(courses.search(q, department, PageRequest.of(page, size, Sort.by("code"))),
                c -> CourseView.of(c, candidates.getOrDefault(c.getId(), 0L)));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public CourseView get(@PathVariable long id) {
        Course c = courses.findById(id).orElseThrow(() -> ApiException.notFound("Course", id));
        return CourseView.of(c, enrolments.countByCourseId(id));
    }

    @GetMapping("/{id}/students")
    @Transactional(readOnly = true)
    @Operation(summary = "Candidates enrolled in a course")
    public PageResponse<StudentView> students(@PathVariable long id,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int size) {
        Course c = courses.findById(id).orElseThrow(() -> ApiException.notFound("Course", id));
        return PageResponse.of(enrolments.findByCourseCode(c.getCode(),
                PageRequest.of(page, size, Sort.by("student.roll"))), e -> StudentView.of(e.getStudent()));
    }

    @PostMapping
    public ResponseEntity<CourseView> create(@Valid @RequestBody CourseRequest request) {
        Course c = service.createCourse(request);
        return ResponseEntity.created(URI.create("/api/courses/" + c.getId())).body(CourseView.of(c, 0));
    }

    @PutMapping("/{id}")
    public CourseView update(@PathVariable long id, @Valid @RequestBody CourseRequest request) {
        Course c = service.updateCourse(id, request);
        return CourseView.of(c, enrolments.countByCourseId(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a course and its enrolments (refused if a saved run uses it)")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteCourse(id);
        return ResponseEntity.noContent().build();
    }
}
