package com.examgrid.backend.catalog;

import com.examgrid.backend.catalog.CatalogDtos.RoomRequest;
import com.examgrid.backend.catalog.CatalogDtos.RoomView;
import com.examgrid.backend.common.error.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Rooms")
public class RoomController {

    private final RoomRepository rooms;
    private final CatalogService service;

    public RoomController(RoomRepository rooms, CatalogService service) {
        this.rooms = rooms;
        this.service = service;
    }

    @GetMapping
    public List<RoomView> list() {
        return rooms.findAllByOrderByPriorityAscCodeAsc().stream().map(RoomView::of).toList();
    }

    @GetMapping("/{id}")
    public RoomView get(@PathVariable long id) {
        return RoomView.of(rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room", id)));
    }

    @PostMapping
    public ResponseEntity<RoomView> create(@Valid @RequestBody RoomRequest request) {
        Room r = service.createRoom(request);
        return ResponseEntity.created(URI.create("/api/rooms/" + r.getId())).body(RoomView.of(r));
    }

    @PutMapping("/{id}")
    public RoomView update(@PathVariable long id, @Valid @RequestBody RoomRequest request) {
        return RoomView.of(service.updateRoom(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }
}
