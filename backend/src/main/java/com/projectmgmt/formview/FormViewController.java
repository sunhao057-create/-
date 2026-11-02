package com.projectmgmt.formview;

import com.projectmgmt.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/form-views")
@RequiredArgsConstructor
public class FormViewController {

    private final FormViewService formViewService;

    @GetMapping
    public ApiResponse<List<FormViewDto>> list(@RequestParam String module) {
        return ApiResponse.ok(formViewService.list(module));
    }

    @PostMapping
    public ApiResponse<FormViewDto> create(@RequestParam String module, @Valid @RequestBody UpsertFormViewRequest request) {
        return ApiResponse.ok(formViewService.create(module, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FormViewDto> update(
            @RequestParam String module,
            @PathVariable Long id,
            @Valid @RequestBody UpsertFormViewRequest request
    ) {
        return ApiResponse.ok(formViewService.update(module, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestParam String module, @PathVariable Long id) {
        formViewService.delete(module, id);
        return ApiResponse.ok(null);
    }
}
