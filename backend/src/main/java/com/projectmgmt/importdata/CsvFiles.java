package com.projectmgmt.importdata;

import com.projectmgmt.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvFiles {

    public static final int MAX_ROWS = 500;

    private CsvFiles() {}

    public static byte[] template(List<String> headers, List<String> hints) {
        List<String> lines = new ArrayList<>();
        lines.add(join(hints));
        lines.add(join(headers));
        return ("\uFEFF" + String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public static CsvTable parse(byte[] bytes, List<String> expectedHeaders) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        if (text.isBlank()) {
            throw ApiException.badRequest("文件为空，请使用下载的模板填写后上传");
        }
        List<LocatedRow> raw = new ArrayList<>();
        int lineNo = 0;
        for (String line : text.split("\\r?\\n", -1)) {
            lineNo++;
            List<String> cells = parseLine(line);
            if (isBlankRow(cells)) {
                continue;
            }
            String first = cells.get(0).trim();
            if (first.startsWith("#") || isHintRow(cells)) {
                continue;
            }
            raw.add(new LocatedRow(lineNo, cells));
        }
        if (raw.isEmpty()) {
            throw ApiException.badRequest("未读到表头，请使用下载的模板");
        }
        List<String> headers = raw.get(0).cells().stream().map(String::trim).toList();
        for (String expected : expectedHeaders) {
            if (!headers.contains(expected)) {
                throw ApiException.badRequest("表头与模板不一致，缺少「" + expected + "」。请重新下载模板，勿修改表头名称。");
            }
        }
        List<CsvRow> rows = new ArrayList<>();
        for (int i = 1; i < raw.size(); i++) {
            LocatedRow located = raw.get(i);
            if (rows.size() >= MAX_ROWS) {
                throw ApiException.badRequest("单次最多导入 " + MAX_ROWS + " 行");
            }
            Map<String, String> cells = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String value = c < located.cells().size() ? located.cells().get(c).trim() : "";
                cells.put(headers.get(c), value);
            }
            if (isBlankRow(cells.values().stream().toList())) {
                continue;
            }
            rows.add(new CsvRow(located.lineNo(), cells));
        }
        if (rows.isEmpty()) {
            throw ApiException.badRequest("模板中没有可导入的数据。请从表头的下一行开始填写。");
        }
        return new CsvTable(headers, rows);
    }

    public static String cell(CsvRow row, String header) {
        return row.cells().getOrDefault(header, "").trim();
    }

    public static String require(CsvRow row, String header) {
        String value = cell(row, header);
        if (value.isBlank()) {
            throw ApiException.badRequest("第" + row.lineNo() + "行「" + header + "」不能为空");
        }
        return value;
    }

    static List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    private static boolean isHintRow(List<String> cells) {
        return cells.stream().anyMatch(cell -> cell.trim().startsWith("提示"));
    }

    private static boolean isBlankRow(List<String> cells) {
        return cells.stream().allMatch(cell -> cell == null || cell.trim().isEmpty());
    }

    private static String join(List<String> cells) {
        List<String> escaped = new ArrayList<>();
        for (String cell : cells) {
            String value = cell == null ? "" : cell;
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                escaped.add("\"" + value.replace("\"", "\"\"") + "\"");
            } else {
                escaped.add(value);
            }
        }
        return String.join(",", escaped);
    }

    public record CsvTable(List<String> headers, List<CsvRow> rows) {}

    public record CsvRow(int lineNo, Map<String, String> cells) {}

    private record LocatedRow(int lineNo, List<String> cells) {}
}
