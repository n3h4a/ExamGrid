package com.examgrid.backend.user;

import com.examgrid.backend.common.api.PageResponse;
import com.examgrid.backend.security.Role;
import com.examgrid.backend.user.UserDtos.CreateUserRequest;
import com.examgrid.backend.user.UserDtos.UpdateUserRequest;
import com.examgrid.backend.user.UserDtos.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List or search users")
    public PageResponse<UserView> list(@RequestParam(required = false) Role role,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return PageResponse.of(service.search(role, q, PageRequest.of(page, size, Sort.by("username"))),
                UserView::of);
    }

    @GetMapping("/{id}")
    public UserView get(@PathVariable long id) {
        return UserView.of(service.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a user")
    public ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        AppUser user = service.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + user.getId())).body(UserView.of(user));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update name, role, enabled flag or reset the password")
    public UserView update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request) {
        return UserView.of(service.update(id, request));
    }
}
