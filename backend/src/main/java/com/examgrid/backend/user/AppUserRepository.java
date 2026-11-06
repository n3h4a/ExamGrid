package com.examgrid.backend.user;

import com.examgrid.backend.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Locale;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndEnabledTrue(Role role);

    /** Optional role filter plus case-insensitive substring match on username or display name. */
    default Page<AppUser> search(Role role, String text, Pageable pageable) {
        Specification<AppUser> spec = Specification.unrestricted();
        if (role != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("role"), role));
        }
        if (text != null && !text.isBlank()) {
            String pattern = "%" + text.strip().toLowerCase(Locale.ROOT).replace("%", "") + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("displayName")), pattern)));
        }
        return findAll(spec, pageable);
    }
}
