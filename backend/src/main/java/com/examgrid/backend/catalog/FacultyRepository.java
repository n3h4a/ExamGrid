package com.examgrid.backend.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long>, JpaSpecificationExecutor<Faculty> {

    Optional<Faculty> findByCode(String code);

    @EntityGraph(attributePaths = {"teaches", "unavailable"})
    List<Faculty> findAllByOrderByCodeAsc();

    default Page<Faculty> search(String text, Pageable pageable) {
        Specification<Faculty> spec = Specification.unrestricted();
        if (text != null && !text.isBlank()) {
            String p = "%" + text.strip().toLowerCase(Locale.ROOT).replace("%", "") + "%";
            spec = spec.and((r, q, cb) -> cb.or(cb.like(cb.lower(r.get("code")), p), cb.like(cb.lower(r.get("name")), p)));
        }
        return findAll(spec, pageable);
    }
}
