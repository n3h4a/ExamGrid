package com.examgrid.backend.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

    Optional<Course> findByCode(String code);

    List<Course> findAllByCodeIn(Collection<String> codes);

    default Page<Course> search(String text, String department, Pageable pageable) {
        Specification<Course> spec = Specification.unrestricted();
        if (department != null && !department.isBlank()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("department"), department));
        }
        if (text != null && !text.isBlank()) {
            String p = "%" + text.strip().toLowerCase(Locale.ROOT).replace("%", "") + "%";
            spec = spec.and((r, q, cb) -> cb.or(cb.like(cb.lower(r.get("code")), p), cb.like(cb.lower(r.get("name")), p)));
        }
        return findAll(spec, pageable);
    }
}
