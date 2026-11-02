package com.projectmgmt.export;

import com.projectmgmt.common.ApiException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ExcelSheets {

    private ExcelSheets() {}

    public static ResponseEntity<byte[]> workbook(String filenamePrefix, String sheetName, String[] headers, List<String[]> rows) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
                sheet.setColumnWidth(i, 18 * 256);
            }
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] values = rows.get(r);
                for (int c = 0; c < headers.length; c++) {
                    row.createCell(c).setCellValue(c < values.length && values[c] != null ? values[c] : "");
                }
            }
            workbook.write(output);
        } catch (Exception ex) {
            throw ApiException.badRequest("导出失败");
        }
        String filename = filenamePrefix + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString()
                )
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(output.toByteArray());
    }

    public static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
