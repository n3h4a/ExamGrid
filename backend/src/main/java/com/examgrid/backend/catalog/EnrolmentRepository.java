package com.examgrid.backend.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EnrolmentRepository extends JpaRepository<Enrolment, Long> {

    @Query(value = "select e from Enrolment e join fetch e.student s join fetch e.course c where c.code = :code",
            countQuery = "select count(e) from Enrolment e where e.course.code = :code")
    Page<Enrolment> findByCourseCode(@Param("code") String code, Pageable pageable);

    @Query("select e from Enrolment e join fetch e.course c where e.student.roll = :roll order by c.code")
    List<Enrolment> findByStudentRoll(@Param("roll") String roll);

    long countByCourseId(long courseId);

    /** (courseId, candidates) for every course with at least one enrolment. */
    @Query("select e.course.id, count(e) from Enrolment e group by e.course.id")
    List<Object[]> countPerCourse();
}
