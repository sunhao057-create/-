package com.projectmgmt.export;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lists")
@RequiredArgsConstructor
public class ListExportController {

    private final ListExportService listExportService;

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody(required = false) ListExportRequest request) {
        return listExportService.export(request);
    }
}
