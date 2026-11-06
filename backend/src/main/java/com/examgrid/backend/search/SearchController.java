package com.examgrid.backend.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * One search box for the exam cell: students, courses, faculty and rooms in a single call.
 * Matches are case-insensitive substrings ranked by trigram similarity, backed by GIN
 * trigram indexes (V2 migration).
 */
@RestController
@RequestMapping("/api/search")
@PreAuthorize("hasRole('EXAM_CELL')")
@Tag(name = "Search")
public class SearchController {

    private final JdbcTemplate jdbc;

    public SearchController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Hit(String type, long id, String code, String label) {
    }

    public record SearchResult(String query, List<Hit> students, List<Hit> courses, List<Hit> faculty,
                               List<Hit> rooms) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Search students, courses, faculty and rooms")
    public SearchResult search(@RequestParam @NotBlank @Size(min = 2, max = 64) String q,
                               @RequestParam(defaultValue = "8") @Min(1) @Max(50) int limit) {
        String text = q.strip();
        String like = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return new SearchResult(text,
                query("student", "roll", "name", text, like, limit),
                query("course", "code", "name", text, like, limit),
                query("faculty", "code", "name", text, like, limit),
                query("room", "code", "building", text, like, limit));
    }

    private List<Hit> query(String table, String codeColumn, String labelColumn, String text, String like, int limit) {
        // Table and column names are constants from this class, never user input.
        String sql = "select id, " + codeColumn + " as code, " + labelColumn + " as label from " + table
                + " where " + codeColumn + " ilike ? or " + labelColumn + " ilike ?"
                + " order by greatest(similarity(" + codeColumn + ", ?), similarity(" + labelColumn + ", ?)) desc, "
                + codeColumn + " limit ?";
        return jdbc.query(sql, (rs, i) -> new Hit(table, rs.getLong("id"), rs.getString("code"), rs.getString("label")),
                like, like, text, text, limit);
    }
}
