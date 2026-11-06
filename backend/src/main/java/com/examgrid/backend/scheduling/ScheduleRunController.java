package com.examgrid.backend.scheduling;

import com.examgrid.backend.common.api.PageResponse;
import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.scheduling.RunViews.InvigilationEntry;
import com.examgrid.backend.scheduling.RunViews.RoomPlan;
import com.examgrid.backend.scheduling.RunViews.RunDetail;
import com.examgrid.backend.scheduling.RunViews.RunSummary;
import com.examgrid.backend.scheduling.RunViews.StudentPlan;
import com.examgrid.backend.scheduling.RunViews.TimetableEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/schedule-runs")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Schedule runs")
public class ScheduleRunController {

    private final ScheduleRunService service;
    private final ScheduleRunRepository runs;
    private final PlanQueries plans;
    private final RunProgressBroadcaster broadcaster;
    private final InstanceLoader loader;
    private final ObjectMapper mapper;

    public ScheduleRunController(ScheduleRunService service, ScheduleRunRepository runs, PlanQueries plans,
                                 RunProgressBroadcaster broadcaster, InstanceLoader loader, ObjectMapper mapper) {
        this.service = service;
        this.runs = runs;
        this.plans = plans;
        this.broadcaster = broadcaster;
        this.loader = loader;
        this.mapper = mapper;
    }

    @PostMapping
    @Operation(summary = "Start a solve. Returns 202 at once; follow progress on /events")
    public ResponseEntity<RunSummary> create(@Valid @RequestBody(required = false) RunParams params) {
        ScheduleRun run = service.create(params == null ? new RunParams(null, null, null, null, null, null, null, null,
                null) : params);
        return ResponseEntity.accepted().location(URI.create("/api/schedule-runs/" + run.getId()))
                .body(RunSummary.of(run));
    }

    @GetMapping
    public PageResponse<RunSummary> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(runs.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size)), RunSummary::of);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Run detail: parameters, metrics, infeasibility report, approvals")
    public RunDetail get(@PathVariable long id) {
        return detail(service.get(id), true);
    }

    @GetMapping(path = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-Sent Events: 'progress' events while solving, then one 'status' event")
    public SseEmitter events(@PathVariable long id) {
        ScheduleRun run = service.get(id);
        return broadcaster.subscribe(id, service.currentEvent(run));
    }

    @PostMapping("/{id}/cancel")
    public RunSummary cancel(@PathVariable long id) {
        return RunSummary.of(service.cancel(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/timetable")
    @Operation(summary = "Every exam with its slot, candidates and rooms")
    public List<TimetableEntry> timetable(@PathVariable long id) {
        service.requirePlan(id);
        return plans.timetable(id);
    }

    @GetMapping("/{id}/rooms")
    @Operation(summary = "Seating charts of every room used in a slot")
    public List<RoomPlan> rooms(@PathVariable long id, @RequestParam @Size(max = 32) String slot) {
        service.requirePlan(id);
        return plans.rooms(id, slot);
    }

    @GetMapping("/{id}/invigilation")
    public List<InvigilationEntry> invigilation(@PathVariable long id,
                                                @RequestParam(required = false) String faculty) {
        service.requirePlan(id);
        return plans.invigilation(id, faculty);
    }

    @GetMapping("/{id}/students/{roll}")
    @Operation(summary = "One student's exams and seats in this run")
    public StudentPlan student(@PathVariable long id, @PathVariable String roll) {
        service.requirePlan(id);
        return plans.student(id, roll.toUpperCase()).orElseThrow(() -> ApiException.notFound("Student", roll));
    }

    @GetMapping("/compare")
    @Operation(summary = "Side-by-side parameters and metrics of up to five runs")
    public List<RunDetail> compare(@RequestParam @Size(min = 2, max = 5) List<Long> ids) {
        return ids.stream().map(id -> detail(service.get(id), false)).toList();
    }

    private RunDetail detail(ScheduleRun run, boolean checkFreshness) {
        JsonNode summary = json(run.getInputSummary());
        Boolean changed = null;
        if (checkFreshness && summary != null && summary.has("fingerprint") && run.getStatus().hasPlan()) {
            changed = !summary.get("fingerprint").asString().equals(loader.currentFingerprint());
        }
        return new RunDetail(RunSummary.of(run), json(run.getParams()), json(run.getMetrics()),
                json(run.getInfeasibility()), summary, json(run.getDisruption()), run.getErrorMessage(), changed,
                plans.approvals(run.getId()));
    }

    private JsonNode json(String value) {
        return value == null ? null : mapper.readTree(value);
    }
}
