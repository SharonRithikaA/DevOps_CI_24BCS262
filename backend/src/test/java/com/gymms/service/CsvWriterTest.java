package com.gymms.service;

import com.gymms.dto.ReportTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("CsvWriter")
class CsvWriterTest {

    @Test
    @DisplayName("writes a header row and data rows")
    void basicTable() {
        ReportTable t = new ReportTable("T", List.of("Name", "Plan"), List.of(List.of("Asha", "Basic")), Map.of());
        assertEquals("Name,Plan\r\nAsha,Basic\r\n", CsvWriter.toCsv(t));
    }

    @Test
    @DisplayName("quotes cells containing commas or quotes and doubles embedded quotes")
    void escaping() {
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""));
        assertEquals("", CsvWriter.escape(null));
    }

    @Test
    @DisplayName("neutralises spreadsheet formulas")
    void formulaInjection() {
        assertEquals("'=SUM(A1)", CsvWriter.escape("=SUM(A1)"));
        assertEquals("+919840012345", CsvWriter.escape("+919840012345")); // phone numbers stay intact
    }
}
