package com.examgrid.backend.imports;

import com.examgrid.backend.common.error.ApiException;
import com.examgrid.backend.imports.TabularFileReader.Table;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabularFileReaderTest {

    @Test
    void readsCsvWithBomAliasesAndRowNumbers() {
        Table t = TabularFileReader.read(utf8("\uFEFFRoll No,Course Code\n21CS045,CS301\n\n21CS046,\"MA 201\"\n"), "e.csv");
        assertThat(t.headers()).containsExactly("roll", "course");
        assertThat(t.rows()).hasSize(2);
        assertThat(t.rows().get(1).get("course")).isEqualTo("MA 201");
        assertThat(t.rows().get(1).rowNumber()).isEqualTo(4);
    }

    @Test
    void detectsSemicolonDelimitedExports() {
        Table t = TabularFileReader.read(utf8("roll;course\n21CS045;CS301\n"), "e.csv");
        assertThat(t.rows().getFirst().get("course")).isEqualTo("CS301");
    }

    @Test
    void fallsBackToWindows1252() {
        byte[] content = "code,name\nCS301,Café algorithms\n".getBytes(Charset.forName("windows-1252"));
        assertThat(TabularFileReader.read(content, "c.csv").rows().getFirst().get("name")).isEqualTo("Café algorithms");
    }

    @Test
    void readsXlsxAsDisplayedIncludingNumericCells() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Enrolments");
            XSSFRow header = sheet.createRow(0);
            header.createCell(0).setCellValue("Registration Number");
            header.createCell(1).setCellValue("Subject Code");
            header.createCell(2).setCellValue("rows");
            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue("21CS045");
            row.createCell(1).setCellValue("CS301");
            row.createCell(2).setCellValue(8);
            sheet.createRow(2);
            wb.write(out);
            Table t = TabularFileReader.read(out.toByteArray(), "upload.bin");
            assertThat(t.headers()).containsExactly("roll", "course", "rows");
            assertThat(t.rows()).hasSize(1);
            assertThat(t.rows().getFirst().get("rows")).isEqualTo("8");
            assertThat(t.rows().getFirst().rowNumber()).isEqualTo(2);
        }
    }

    @Test
    void rejectsLegacyXlsAndEmptyFiles() {
        byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0};
        assertThatThrownBy(() -> TabularFileReader.read(ole2, "old.xls"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("unsupported_format"));
        assertThatThrownBy(() -> TabularFileReader.read(new byte[0], "x.csv"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("empty_file"));
    }

    @Test
    void sanitisesFilenamesForStorageKeys() {
        assertThat(ImportService.sanitise("../../etc/passwd")).isEqualTo("passwd");
        assertThat(ImportService.sanitise("C:\\Users\\exam cell\\Enrolments Nov 2026.xlsx"))
                .isEqualTo("Enrolments_Nov_2026.xlsx");
        assertThat(ImportService.sanitise("...")).isEqualTo("upload");
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
