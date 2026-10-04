package com.gymms.service;

import com.gymms.dto.ReportTable;

import java.util.List;

/** Minimal RFC 4180 CSV serialiser for {@link ReportTable}. */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static String toCsv(ReportTable table) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, table.columns());
        for (List<String> row : table.rows()) {
            appendRow(sb, row);
        }
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        sb.append("\r\n");
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        // Neutralise spreadsheet formula injection: text cells starting with = or @ are prefixed with an apostrophe.
        if (!v.isEmpty() && "=@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
