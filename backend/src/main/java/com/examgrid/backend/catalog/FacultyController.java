package com.examgrid.backend.catalog;

import com.examgrid.backend.catalog.CatalogDtos.FacultyRequest;
import com.examgrid.backend.catalog.CatalogDtos.FacultyView;
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
@RequestMapping("/api/faculty")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Faculty")
public class FacultyController {

    private final FacultyRepository faculty;
    private final CatalogService service;

    public FacultyController(FacultyRepository faculty, CatalogService service) {
        this.faculty = faculty;
        this.service = service;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<FacultyView> list(@RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return PageResponse.of(faculty.search(q, PageRequest.of(page, size, Sort.by("code"))), FacultyView::of);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public FacultyView get(@PathVariable long id) {
        return FacultyView.of(faculty.findById(id).orElseThrow(() -> ApiException.notFound("Faculty", id)));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<FacultyView> create(@Valid @RequestBody FacultyRequest request) {
        Faculty f = service.createFaculty(request);
        return ResponseEntity.created(URI.create("/api/faculty/" + f.getId())).body(FacultyView.of(f));
    }

    @PutMapping("/{id}")
    @Transactional
    public FacultyView update(@PathVariable long id, @Valid @RequestBody FacultyRequest request) {
        return FacultyView.of(service.updateFaculty(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteFaculty(id);
        return ResponseEntity.noContent().build();
    }
}
