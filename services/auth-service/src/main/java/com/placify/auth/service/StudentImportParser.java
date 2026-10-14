package com.placify.auth.service;

import com.placify.common.web.ApiException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the student list uploaded by the TPO from CSV or Excel (.xlsx/.xls).
 * The first row must be a header; column names are matched flexibly, e.g.
 * "Reg No", "registration_number" and "regno" all mean the registration number.
 */
@Component
public class StudentImportParser {

    public record StudentRow(int rowNumber, String regNo, String name, String email, String branch,
                             String batch, String cgpa, String phone) {
    }

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        for (String a : List.of("regno", "registrationnumber", "registrationno", "regnumber", "rollno", "rollnumber")) {
            ALIASES.put(a, "regNo");
        }
        for (String a : List.of("name", "fullname", "studentname")) {
            ALIASES.put(a, "name");
        }
        for (String a : List.of("email", "emailid", "mail", "emailaddress")) {
            ALIASES.put(a, "email");
        }
        for (String a : List.of("branch", "department", "dept", "programme", "program")) {
            ALIASES.put(a, "branch");
        }
        for (String a : List.of("batch", "graduationyear", "passingyear", "year", "yearofpassing")) {
            ALIASES.put(a, "batch");
        }
        for (String a : List.of("cgpa", "gpa", "cgpi")) {
            ALIASES.put(a, "cgpa");
        }
        for (String a : List.of("phone", "mobile", "phonenumber", "mobilenumber", "contact")) {
            ALIASES.put(a, "phone");
        }
    }

    public List<StudentRow> parse(String filename, byte[] content) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            List<List<String>> table = (lower.endsWith(".xlsx") || lower.endsWith(".xls"))
                    ? readExcel(new ByteArrayInputStream(content))
                    : readCsv(new ByteArrayInputStream(content));
            return toRows(table);
        } catch (IOException ex) {
            throw ApiException.badRequest("Could not read the file: " + ex.getMessage());
        }
    }

    private List<StudentRow> toRows(List<List<String>> table) {
        if (table.isEmpty()) {
            throw ApiException.badRequest("The file is empty");
        }
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = table.get(0);
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            String field = ALIASES.get(key);
            if (field != null) {
                columns.putIfAbsent(field, i);
            }
        }
        for (String required : List.of("regNo", "name", "email", "branch", "batch")) {
            if (!columns.containsKey(required)) {
                throw ApiException.badRequest("Missing column: " + required
                        + ". Required columns: Reg No, Name, Email, Branch, Batch (optional: CGPA, Phone)");
            }
        }
        List<StudentRow> rows = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> row = table.get(r);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }
            rows.add(new StudentRow(r + 1,
                    cell(row, columns.get("regNo")),
                    cell(row, columns.get("name")),
                    cell(row, columns.get("email")),
                    cell(row, columns.get("branch")),
                    cell(row, columns.get("batch")),
                    cell(row, columns.get("cgpa")),
                    cell(row, columns.get("phone"))));
        }
        return rows;
    }

    private static String cell(List<String> row, Integer index) {
        if (index == null || index >= row.size()) {
            return "";
        }
        return row.get(index).trim();
    }

    private List<List<String>> readExcel(InputStream in) throws IOException {
        List<List<String>> table = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                int last = Math.max(row.getLastCellNum(), 0);
                for (int c = 0; c < last; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    values.add(cell == null ? "" : formatter.formatCellValue(cell));
                }
                table.add(values);
            }
        }
        return table;
    }

    private List<List<String>> readCsv(InputStream in) throws IOException {
        List<List<String>> table = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first && line.startsWith("﻿")) {
                    line = line.substring(1);
                }
                first = false;
                table.add(splitCsvLine(line));
            }
        }
        return table;
    }

    /** CSV split that respects double quotes ("Smith, John"). */
    static List<String> splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }
}
