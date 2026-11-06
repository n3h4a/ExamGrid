package com.examgrid.backend.schema;

import com.examgrid.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Database-level guarantees that do not depend on application code. */
@IntegrationTest
class SchemaIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void onlyOneRunCanBePublishedAtATime() {
        jdbc.update("insert into schedule_run (status, created_by) values ('PUBLISHED', 'test')");
        assertThatThrownBy(() -> jdbc.update("insert into schedule_run (status, created_by) values ('PUBLISHED', 'test')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("delete from schedule_run where created_by = 'test'");
    }

    @Test
    void studentAccountsMustHaveARollNumber() {
        assertThatThrownBy(() -> jdbc.update("insert into app_user (username, password_hash, display_name, role) "
                + "values ('norole', 'x', 'x', 'STUDENT')")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enrolmentsAreUniquePerStudentAndCourse() {
        long student = jdbc.queryForObject("insert into student (roll) values ('ZZ999') returning id", Long.class);
        long course = jdbc.queryForObject("insert into course (code, name) values ('ZZ101', 'Z') returning id", Long.class);
        jdbc.update("insert into enrolment (student_id, course_id) values (?, ?)", student, course);
        assertThatThrownBy(() -> jdbc.update("insert into enrolment (student_id, course_id) values (?, ?)", student, course))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("delete from student where id = ?", student);
        jdbc.update("delete from course where id = ?", course);
        assertThat(jdbc.queryForObject("select count(*) from enrolment where student_id = ?", Long.class, student)).isZero();
    }
}
