package com.examgrid.backend.imports;

import com.examgrid.backend.support.ApiClient;
import com.examgrid.backend.support.IntegrationTest;
import com.examgrid.backend.support.TestData;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ImportIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    ApiClient api;
    String admin;
    String dept;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, mapper);
        admin = api.adminToken();
        dept = TestData.letters();
    }

    @Test
    void dryRunReportsEveryProblemAndWritesNothing() throws Exception {
        importCourses(dept + "101," + dept + " one\n" + dept + "102," + dept + " two\n");
        String csv = "Roll No,Course Code,Name\n"
                + TestData.roll(dept, 1) + "," + dept + "101,Asha\n"
                + TestData.roll(dept, 1) + "," + dept + "101,Asha\n"
                + "bad-roll," + dept + "101,\n"
                + TestData.roll(dept, 2) + ",NOPE999,\n";
        JsonNode result = api.read(api.upload("/api/imports/enrolments", admin, "enrol.csv", csv)
                .andExpect(status().isOk()));
        assertThat(result.at("/batch/status").asString()).isEqualTo("REJECTED");
        assertThat(result.at("/report/errorCount").asInt()).isEqualTo(2);
        assertThat(result.at("/report/warningCount").asInt()).isEqualTo(1);
        assertThat(result.at("/report/acceptedRows").asInt()).isZero();
        JsonNode issues = result.at("/report/issues");
        assertThat(issues.get(0).get("row").asInt()).isEqualTo(4);
        assertThat(issues.get(0).get("message").asString()).contains("does not look like a roll number");
        assertThat(issues.get(1).get("message").asString()).contains("Unknown course NOPE999");
        assertThat(issues.get(2).get("severity").asString()).isEqualTo("WARNING");
        assertThat(count("select count(*) from student where roll like '21" + dept + "%'")).isZero();
    }

    @Test
    void commitWritesStudentsAndEnrolmentsAndReimportIsIdempotent() throws Exception {
        importCourses(dept + "201,A\n" + dept + "202,B\n");
        String csv = "roll,course,name,department\n"
                + TestData.roll(dept, 1) + "," + dept + "201,Asha Rao," + dept + "\n"
                + TestData.roll(dept, 1) + "," + dept + "202,Asha Rao," + dept + "\n"
                + TestData.roll(dept, 2) + "," + dept + "201,Ravi," + dept + "\n";
        JsonNode first = commit("enrolments", csv);
        assertThat(first.at("/batch/status").asString()).isEqualTo("COMMITTED");
        assertThat(first.at("/report/changes/enrolmentsAdded").asInt()).isEqualTo(3);
        assertThat(first.at("/report/changes/studentsCreated").asInt()).isEqualTo(2);
        assertThat(count("select count(*) from enrolment e join student s on s.id = e.student_id "
                + "where s.roll like '21" + dept + "%'")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select name from student where roll = ?", String.class,
                TestData.roll(dept, 1))).isEqualTo("Asha Rao");

        JsonNode again = commit("enrolments", csv);
        assertThat(again.at("/report/changes/enrolmentsAdded").asInt()).isZero();
        assertThat(again.at("/report/changes/alreadyEnrolled").asInt()).isEqualTo(3);
    }

    @Test
    void replaceModeMakesTheFileTheCompleteList() throws Exception {
        importCourses(dept + "301,A\n" + dept + "302,B\n");
        commit("enrolments", "roll,course\n" + TestData.roll(dept, 1) + "," + dept + "301\n"
                + TestData.roll(dept, 1) + "," + dept + "302\n");
        long totalBefore = count("select count(*) from enrolment");
        JsonNode replace = api.read(api.upload("/api/imports/enrolments?dryRun=false&mode=REPLACE", admin, "all.csv",
                "roll,course\n" + TestData.roll(dept, 1) + "," + dept + "301\n").andExpect(status().isOk()));
        assertThat(replace.at("/report/changes/enrolmentsRemoved").asLong()).isEqualTo(totalBefore - 1);
        assertThat(count("select count(*) from enrolment")).isEqualTo(1);
    }

    @Test
    void replaceIsOnlyForEnrolments() throws Exception {
        api.upload("/api/imports/courses?mode=REPLACE", admin, "c.csv", "code,name\nX1,X\n")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("unsupported_mode"));
    }

    @Test
    void courseImportUpsertsAndReportsChanges() throws Exception {
        JsonNode created = commit("courses", "code,name,hard,sessions\n" + dept + "401,Algorithms,yes,\n"
                + dept + "491,Lab,no,AFTERNOON\n");
        assertThat(created.at("/report/changes/created").asInt()).isEqualTo(2);
        JsonNode updated = commit("courses", "code,name,hard,sessions\n" + dept + "401,Algorithms II,yes,\n"
                + dept + "491,Lab,no,AFTERNOON\n");
        assertThat(updated.at("/report/changes/updated").asInt()).isEqualTo(1);
        assertThat(updated.at("/report/changes/unchanged").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select allowed_sessions from course where code = ?", String.class,
                dept + "491")).isEqualTo("AFTERNOON");
        JsonNode bad = api.read(api.upload("/api/imports/courses", admin, "c.csv",
                "code,name,hard,sessions\n" + dept + "402,X,maybe,NIGHT\n"));
        assertThat(bad.at("/report/errorCount").asInt()).isEqualTo(2);
    }

    @Test
    void missingColumnsAreReportedAgainstTheHeader() throws Exception {
        JsonNode r = api.read(api.upload("/api/imports/rooms", admin, "r.csv", "code,building\nR1,Main\n"));
        assertThat(r.at("/batch/status").asString()).isEqualTo("REJECTED");
        assertThat(r.at("/report/issues/0/row").asInt()).isEqualTo(1);
        assertThat(r.at("/report/issues/0/message").asString()).contains("Missing required column 'rows'");
    }

    @Test
    void roomImportValidatesLayouts() throws Exception {
        String csv = "code,building,rows,columns,blocked\n" + dept + "R1,Main,4,5,A1;D5\n" + dept + "R2,Main,4,5,Z9\n";
        JsonNode bad = api.read(api.upload("/api/imports/rooms", admin, "r.csv", csv));
        assertThat(bad.at("/report/issues/0/message").asString()).contains("outside the 4 × 5 grid");
        JsonNode ok = commit("rooms", "code,building,rows,columns,blocked\n" + dept + "R1,Main,4,5,A1;D5\n");
        assertThat(ok.at("/report/changes/seats").asInt()).isEqualTo(18);
        assertThat(jdbc.queryForObject("select capacity from room where code = ?", Integer.class, dept + "R1"))
                .isEqualTo(18);
    }

    @Test
    void facultyImportLinksCoursesAndUnavailability() throws Exception {
        importCourses(dept + "501,A\n");
        api.post("/api/slots", admin, java.util.Map.of("date", "2031-03-03", "session", "MORNING"));
        api.post("/api/slots", admin, java.util.Map.of("date", "2031-03-03", "session", "AFTERNOON"));
        JsonNode r = commit("faculty", "code,name,teaches,unavailable\n" + dept + "F1,Dr X," + dept + "501,2031-03-03\n");
        assertThat(r.at("/report/changes/unavailableSlots").asInt()).isEqualTo(2);
        assertThat(count("select count(*) from faculty_course fc join faculty f on f.id = fc.faculty_id "
                + "where f.code = '" + dept + "F1'")).isEqualTo(1);
        JsonNode bad = api.read(api.upload("/api/imports/faculty", admin, "f.csv",
                "code,name,teaches,unavailable\n" + dept + "F2,Dr Y,NOPE,2099-01-01\n"));
        assertThat(bad.at("/report/errorCount").asInt()).isEqualTo(2);
    }

    @Test
    void xlsxWorkbooksAreImported() throws Exception {
        importCourses(dept + "601,A\n");
        byte[] workbook;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Roll Number");
            sheet.getRow(0).createCell(1).setCellValue("Course");
            sheet.createRow(1).createCell(0).setCellValue(TestData.roll(dept, 7));
            sheet.getRow(1).createCell(1).setCellValue(dept + "601");
            wb.write(out);
            workbook = out.toByteArray();
        }
        JsonNode r = api.read(api.upload("/api/imports/enrolments?dryRun=false", admin, "enrol.xlsx", workbook));
        assertThat(r.at("/batch/status").asString()).isEqualTo("COMMITTED");
        assertThat(r.at("/report/acceptedRows").asInt()).isEqualTo(1);
    }

    @Test
    void originalFileIsArchivedWithItsHashAndAudited() throws Exception {
        String csv = "code,name\n" + dept + "701,Archived\n";
        JsonNode r = commit("courses", csv);
        long id = r.at("/batch/id").asLong();
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(r.at("/batch/sha256").asString()).isEqualTo(expected);
        api.get("/api/imports/" + id + "/file", admin)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("courses.csv")))
                .andExpect(content().bytes(csv.getBytes(StandardCharsets.UTF_8)));
        api.get("/api/imports/" + id, admin).andExpect(jsonPath("$.report.changes.created").value(1));
        assertThat(count("select count(*) from audit_log where action = 'import.committed' and entity_id = '" + id + "'"))
                .isEqualTo(1);
    }

    @Test
    void templatesAndRbac() throws Exception {
        api.get("/api/imports/templates/enrolments", admin)
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("roll,course")));
        api.get("/api/imports/templates/unknown", admin).andExpect(status().isNotFound());
        String username = "imp-" + dept.toLowerCase();
        api.post("/api/users", admin, java.util.Map.of("username", username, "displayName", "S", "password",
                "password123", "role", "STUDENT", "studentRoll", TestData.roll(dept, 99)));
        api.upload("/api/imports/courses", api.login(username, "password123"), "c.csv", "code,name\nX,Y\n")
                .andExpect(status().isForbidden());
    }

    private void importCourses(String rows) throws Exception {
        commit("courses", "code,name\n" + rows);
    }

    private JsonNode commit(String kind, String csv) throws Exception {
        JsonNode r = api.read(api.upload("/api/imports/" + kind + "?dryRun=false", admin, kind + ".csv", csv)
                .andExpect(status().isOk()));
        assertThat(r.at("/batch/status").asString()).as(r.toString()).isEqualTo("COMMITTED");
        return r;
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
