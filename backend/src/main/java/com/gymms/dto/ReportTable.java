package com.gymms.dto;

import java.util.List;
import java.util.Map;

/** A generic report: header columns, string rows and a small summary. Used for both JSON and CSV output. */
public record ReportTable(String title, List<String> columns, List<List<String>> rows, Map<String, String> summary) {
}
