package com.examgrid.backend.catalog;

import com.examgrid.backend.catalog.CatalogDtos.StudentDetail;
import com.examgrid.backend.catalog.CatalogDtos.StudentRequest;
import com.examgrid.backend.catalog.CatalogDtos.StudentView;
import com.examgrid.backend.common.api.PageResponse;
import com.examgrid.backend.common.error.ApiException;
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

@RestController
@RequestMapping("/api/students")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Students")
public class StudentController {

    private final StudentRepository students;
    private final EnrolmentRepository enrolments;
    private final CatalogService service;

    public StudentController(StudentRepository students, EnrolmentRepository enrolments, CatalogService service) {
        this.students = students;
        this.enrolments = enrolments;
        this.service = service;
    }

    @GetMapping
    public PageResponse<StudentView> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String department,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return PageResponse.of(students.search(q, department, PageRequest.of(page, size, Sort.by("roll"))),
                StudentView::of);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public StudentDetail get(@PathVariable long id) {
        Student s = students.findById(id).orElseThrow(() -> ApiException.notFound("Student", id));
        return detail(s);
    }

    @PostMapping
    public ResponseEntity<StudentDetail> create(@Valid @RequestBody StudentRequest request) {
        Student s = service.createStudent(request);
        return ResponseEntity.created(URI.create("/api/students/" + s.getId())).body(detail(s));
    }

    @PutMapping("/{id}")
    public StudentDetail update(@PathVariable long id, @Valid @RequestBody StudentRequest request) {
        return detail(service.updateStudent(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }

    private StudentDetail detail(Student s) {
        return new StudentDetail(StudentView.of(s),
                enrolments.findByStudentRoll(s.getRoll()).stream().map(e -> e.getCourse().getCode()).toList());
    }
}
