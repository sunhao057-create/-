package com.projectmgmt.cursor;

import com.projectmgmt.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cursor")
@RequiredArgsConstructor
public class CursorController {

    private final CursorBridgeService cursorBridgeService;

    @GetMapping("/status")
    public ApiResponse<CursorStatusView> status() {
        return ApiResponse.ok(cursorBridgeService.status());
    }

    @GetMapping("/tasks")
    public ApiResponse<List<CursorTaskView>> list() {
        return ApiResponse.ok(cursorBridgeService.list());
    }

    @PostMapping("/tasks")
    public ApiResponse<CursorTaskView> create(@Valid @RequestBody CursorPromptRequest request) {
        return ApiResponse.ok(cursorBridgeService.create(request.prompt()));
    }

    @PostMapping("/tasks/{id}/follow")
    public ApiResponse<CursorTaskView> follow(@PathVariable Long id, @Valid @RequestBody CursorPromptRequest request) {
        return ApiResponse.ok(cursorBridgeService.follow(id, request.prompt()));
    }
}
