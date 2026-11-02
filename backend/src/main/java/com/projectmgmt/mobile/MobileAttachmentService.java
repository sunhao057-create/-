package com.projectmgmt.mobile;

import com.projectmgmt.bid.BidFileCategory;
import com.projectmgmt.bid.BidService;
import com.projectmgmt.common.ApiException;
import com.projectmgmt.deposit.DepositKind;
import com.projectmgmt.deposit.DepositService;
import com.projectmgmt.device.DeviceWorkOrderKind;
import com.projectmgmt.device.DeviceWorkOrderService;
import com.projectmgmt.finance.ExpenseService;
import com.projectmgmt.oa.OaKind;
import com.projectmgmt.oa.OaService;
import com.projectmgmt.payment.PaymentService;
import com.projectmgmt.project.ProjectService;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.security.CurrentUser;
import com.projectmgmt.tech.TechKind;
import com.projectmgmt.tech.TechService;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MobileAttachmentService {

    private final PermissionService permissionService;
    private final CurrentUser currentUser;
    private final OaService oaService;
    private final TechService techService;
    private final BidService bidService;
    private final DeviceWorkOrderService deviceWorkOrderService;
    private final ProjectService projectService;
    private final PaymentService paymentService;
    private final ExpenseService expenseService;
    private final DepositService depositService;

    public void add(FormModule module, Long bizId, String fieldKey, MultipartFile[] files) {
        requireWrite(module);
        List<MultipartFile> list = files == null ? List.of() : Arrays.asList(files);
        if (module.isOa() && module != FormModule.OA_STAFF) {
            oaService.addFiles(OaKind.of(module), bizId, list);
            return;
        }
        if (module.isTech()) {
            techService.addFiles(TechKind.of(module), bizId, list, fieldKey);
            return;
        }
        switch (module) {
            case PROJECT -> projectService.addFiles(bizId, list);
            case PAYMENT -> paymentService.addFiles(bizId, list);
            case EXPENSE -> expenseService.addFiles(bizId, list);
            case DEPOSIT_APPLY -> depositService.addFiles(DepositKind.APPLY, bizId, list);
            case DEPOSIT_RETURN -> depositService.addFiles(DepositKind.RETURN, bizId, list);
            case BIDDING -> bidService.addFiles(bizId, bidCategory(fieldKey), list);
            case DEVICE_REPAIR -> deviceWorkOrderService.addFiles(DeviceWorkOrderKind.REPAIR, bizId, list);
            case DEVICE_MOVE -> deviceWorkOrderService.addFiles(DeviceWorkOrderKind.MOVE, bizId, list);
            case DEVICE_SCRAP -> deviceWorkOrderService.addFiles(DeviceWorkOrderKind.SCRAP, bizId, list);
            default -> throw ApiException.badRequest("该模块不支持附件");
        }
    }

    public void remove(FormModule module, Long bizId, Long fileId) {
        requireWrite(module);
        if (module.isOa() && module != FormModule.OA_STAFF) {
            oaService.removeFile(OaKind.of(module), bizId, fileId);
            return;
        }
        if (module.isTech()) {
            techService.removeFile(TechKind.of(module), bizId, fileId);
            return;
        }
        switch (module) {
            case PROJECT -> projectService.removeFile(bizId, fileId);
            case PAYMENT -> paymentService.removeFile(bizId, fileId);
            case EXPENSE -> expenseService.removeFile(bizId, fileId);
            case DEPOSIT_APPLY -> depositService.removeFile(DepositKind.APPLY, bizId, fileId);
            case DEPOSIT_RETURN -> depositService.removeFile(DepositKind.RETURN, bizId, fileId);
            case BIDDING -> bidService.removeFile(bizId, fileId);
            case DEVICE_REPAIR -> deviceWorkOrderService.removeFile(DeviceWorkOrderKind.REPAIR, bizId, fileId);
            case DEVICE_MOVE -> deviceWorkOrderService.removeFile(DeviceWorkOrderKind.MOVE, bizId, fileId);
            case DEVICE_SCRAP -> deviceWorkOrderService.removeFile(DeviceWorkOrderKind.SCRAP, bizId, fileId);
            default -> throw ApiException.badRequest("该模块不支持附件");
        }
    }

    private void requireWrite(FormModule module) {
        String role = currentUser.entity().getRole();
        if (!permissionService.allowed(role, module, "EDIT")) {
            permissionService.require(module, "CREATE");
        }
    }

    private static BidFileCategory bidCategory(String fieldKey) {
        if (fieldKey == null || fieldKey.isBlank()) {
            return BidFileCategory.SCREENSHOT;
        }
        try {
            return BidFileCategory.valueOf(fieldKey.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BidFileCategory.SCREENSHOT;
        }
    }
}
