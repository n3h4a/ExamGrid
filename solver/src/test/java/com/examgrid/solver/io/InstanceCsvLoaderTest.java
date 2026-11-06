package com.examgrid.solver.io;

import com.examgrid.solver.TestInstances;
import com.examgrid.solver.model.InvalidInstanceException;
import com.examgrid.solver.model.ProblemInstance;
import com.examgrid.solver.model.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceCsvLoaderTest {

    @TempDir
    Path dir;

    @Test
    void loadsAllFileTypes() throws IOException {
        Path courses = write("courses.csv", "code,name,hard,sessions\nCS301,Algorithms,true,\nCS391,Lab,false,AFTERNOON\n");
        Path enrol = write("enrol.csv", "roll,course\n21CS045,CS301\n21CS045,CS391\n21CS046,MA201\n");
        Path rooms = write("rooms.csv", "code,building,rows,columns,blocked\nR101,Main,4,5,A1;D5\n");
        Path faculty = write("faculty.csv", "id,name,teaches,unavailable\nF1,Dr Rao,CS301;MA201,"
                + TestInstances.slots(1, 1).getFirst().id() + "\n");

        ProblemInstance.Builder b = ProblemInstance.builder().slots(TestInstances.slots(3, 2));
        Set<String> known = new HashSet<>(InstanceCsvLoader.loadCourses(courses, b));
        InstanceCsvLoader.loadEnrolments(enrol, b, known);
        InstanceCsvLoader.loadRooms(rooms, b);
        InstanceCsvLoader.loadFaculty(faculty, b);
        ProblemInstance instance = b.build();

        assertThat(instance.courseCount()).isEqualTo(3);
        assertThat(instance.course(instance.courseIndex("CS301")).hard()).isTrue();
        assertThat(instance.course(instance.courseIndex("CS391")).allowedSessions()).containsExactly(Session.AFTERNOON);
        assertThat(instance.enrolmentCount()).isEqualTo(3);
        assertThat(instance.room(0).capacity()).isEqualTo(18);
        assertThat(instance.faculty().getFirst().teaches()).containsExactlyInAnyOrder("CS301", "MA201");
    }

    @Test
    void reportsLineNumbersForBadValues() throws IOException {
        Path rooms = write("rooms.csv", "code,building,rows,columns\nR1,Main,four,5\n");
        assertThatThrownBy(() -> InstanceCsvLoader.loadRooms(rooms, ProblemInstance.builder()))
                .isInstanceOf(InvalidInstanceException.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("rows");
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }
}
