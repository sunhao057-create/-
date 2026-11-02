package com.projectmgmt.device;

import com.projectmgmt.common.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 小程序首页「搬迁服务」「维保服务」查看电脑端已提交的数据。 */
@RestController
@RequestMapping("/api/mini/services")
@RequiredArgsConstructor
public class MiniServiceController {

    private final DeviceWorkOrderService workOrderService;
    private final DeviceMaintService deviceMaintService;

    @GetMapping("/moves")
    public ApiResponse<List<DeviceWorkOrderView>> moves() {
        return ApiResponse.ok(workOrderService.listForCompany(DeviceWorkOrderKind.MOVE));
    }

    @GetMapping("/maint")
    public ApiResponse<List<DeviceMaintService.DeviceBrief>> maint() {
        return ApiResponse.ok(deviceMaintService.briefsForCompany());
    }
}
