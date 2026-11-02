package com.projectmgmt.export;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.contract.CollectionService;
import com.projectmgmt.contract.CollectionView;
import com.projectmgmt.contract.ContractService;
import com.projectmgmt.contract.ContractView;
import com.projectmgmt.customer.CustomerService;
import com.projectmgmt.customer.CustomerView;
import com.projectmgmt.deposit.DepositKind;
import com.projectmgmt.deposit.DepositService;
import com.projectmgmt.deposit.DepositView;
import com.projectmgmt.bid.BidService;
import com.projectmgmt.bid.BidView;
import com.projectmgmt.device.DeviceWorkOrderKind;
import com.projectmgmt.device.DeviceWorkOrderService;
import com.projectmgmt.device.DeviceWorkOrderView;
import com.projectmgmt.finance.BankAccountService;
import com.projectmgmt.oa.OaKind;
import com.projectmgmt.oa.OaRequestView;
import com.projectmgmt.oa.OaService;
import com.projectmgmt.oa.StaffPortalService;
import com.projectmgmt.oa.StaffPortalView;
import com.projectmgmt.finance.BankAccountView;
import com.projectmgmt.finance.ExpenseService;
import com.projectmgmt.finance.ExpenseView;
import com.projectmgmt.payment.PaymentService;
import com.projectmgmt.payment.PaymentView;
import com.projectmgmt.project.ProjectService;
import com.projectmgmt.project.ProjectView;
import com.projectmgmt.purchase.PurchasePayService;
import com.projectmgmt.purchase.PurchasePayView;
import com.projectmgmt.purchase.PurchaseService;
import com.projectmgmt.purchase.PurchaseView;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.role.RoleService;
import com.projectmgmt.role.dto.RoleView;
import com.projectmgmt.tech.TechKind;
import com.projectmgmt.tech.TechRecordView;
import com.projectmgmt.tech.TechService;
import com.projectmgmt.supplier.SupplierService;
import com.projectmgmt.supplier.SupplierView;
import com.projectmgmt.user.UserService;
import com.projectmgmt.user.UserStatus;
import com.projectmgmt.user.dto.UserView;
import com.projectmgmt.warehouse.StockOutKind;
import com.projectmgmt.warehouse.StockOutService;
import com.projectmgmt.warehouse.StockOutView;
import com.projectmgmt.warehouse.WarehouseService;
import com.projectmgmt.warehouse.WarehouseView;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListExportService {

    private static final int ALL = 50_000;

    private final PermissionService permissionService;
    private final ProjectService projectService;
    private final CustomerService customerService;
    private final SupplierService supplierService;
    private final ContractService contractService;
    private final CollectionService collectionService;
    private final DepositService depositService;
    private final BidService bidService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final RoleService roleService;
    private final ExpenseService expenseService;
    private final BankAccountService bankAccountService;
    private final PurchaseService purchaseService;
    private final PurchasePayService purchasePayService;
    private final WarehouseService warehouseService;
    private final StockOutService stockOutService;
    private final DeviceWorkOrderService deviceWorkOrderService;
    private final OaService oaService;
    private final StaffPortalService staffPortalService;
    private final TechService techService;

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(ListExportRequest request) {
        FormModule module = parse(request == null ? null : request.module());
        if (module == FormModule.DEVICE) {
            throw ApiException.badRequest("请使用设备台账导出");
        }
        FormModule permModule = module == FormModule.COLLECTION ? FormModule.CONTRACT : module;
        permissionService.require(permModule, "IMPORT_EXPORT");
        List<Long> ids = request == null ? null : request.ids();
        String keyword = request == null ? null : request.keyword();
        return switch (module) {
            case PROJECT -> projects(request, ids, keyword);
            case CUSTOMER -> customers(request, ids, keyword);
            case SUPPLIER -> suppliers(request, ids, keyword);
            case CONTRACT -> contracts(request, ids, keyword);
            case COLLECTION -> collections(ids, keyword);
            case DEPOSIT_APPLY -> deposits(DepositKind.APPLY, "项目保证金申请", ids, keyword);
            case DEPOSIT_RETURN -> deposits(DepositKind.RETURN, "项目保证金退回", ids, keyword);
            case BIDDING -> bids(ids, keyword);
            case PAYMENT -> payments(ids, keyword);
            case USER -> users(request, ids, keyword);
            case ROLE -> roles(ids);
            case EXPENSE -> expenses(ids, keyword);
            case BANK_ACCOUNT -> accounts(ids, keyword);
            case PURCHASE -> purchases(ids, keyword);
            case PURCHASE_PAY -> purchasePays(ids, keyword);
            case WAREHOUSE -> warehouses(ids, keyword);
            case STOCK_OUT_PURCHASE -> stockOuts(StockOutKind.PURCHASE, "采购出库", ids, keyword);
            case STOCK_OUT_PROJECT -> stockOuts(StockOutKind.PROJECT, "项目出库", ids, keyword);
            case STOCK_TRANSFER -> stockOuts(StockOutKind.TRANSFER, "仓库调拨", ids, keyword);
            case DEVICE_REPAIR -> workOrders(DeviceWorkOrderKind.REPAIR, "设备维修工单", ids, keyword);
            case DEVICE_MOVE -> workOrders(DeviceWorkOrderKind.MOVE, "设备搬迁工单", ids, keyword);
            case DEVICE_SCRAP -> workOrders(DeviceWorkOrderKind.SCRAP, "设备报废工单", ids, keyword);
            case OA_ONBOARD -> oaOrders(OaKind.ONBOARD, ids, keyword);
            case OA_TRANSFER -> oaOrders(OaKind.TRANSFER, ids, keyword);
            case OA_PROMOTION -> oaOrders(OaKind.PROMOTION, ids, keyword);
            case OA_RESIGN -> oaOrders(OaKind.RESIGN, ids, keyword);
            case OA_SALARY -> oaOrders(OaKind.SALARY, ids, keyword);
            case OA_INTERVIEW -> oaOrders(OaKind.INTERVIEW, ids, keyword);
            case OA_SEAL -> oaOrders(OaKind.SEAL, ids, keyword);
            case OA_QUALIFY -> oaOrders(OaKind.QUALIFY, ids, keyword);
            case OA_TRIP -> oaOrders(OaKind.TRIP, ids, keyword);
            case OA_LEAVE -> oaOrders(OaKind.LEAVE, ids, keyword);
            case OA_LOAN -> oaOrders(OaKind.LOAN, ids, keyword);
            case OA_LOAN_PAY -> oaOrders(OaKind.LOAN_PAY, ids, keyword);
            case OA_LOAN_REPAY -> oaOrders(OaKind.LOAN_REPAY, ids, keyword);
            case OA_STAFF -> personnel(ids, keyword, request == null ? null : request.status());
            default -> {
                if (module.isTech()) {
                    yield techOrders(TechKind.of(module), ids, keyword);
                }
                throw ApiException.badRequest("该模块暂不支持导出");
            }
        };
    }

    private ResponseEntity<byte[]> projects(ListExportRequest request, List<Long> ids, String keyword) {
        String publicFilter = request == null ? null : request.publicFilter();
        List<ProjectView> rows = pick(
                projectService.list(keyword, publicFilter, null, null, null, null, 1, ALL).items(),
                ids,
                ProjectView::id
        );
        return ExcelSheets.workbook("项目列表", "项目列表",
                new String[] {"项目名称", "编码", "客户", "合同", "项目经理", "区域", "状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.name()),
                        ExcelSheets.text(row.code()),
                        row.customer() == null ? "" : ExcelSheets.text(row.customer().shortName()),
                        ExcelSheets.text(row.contractCode()),
                        row.manager() == null ? "" : ExcelSheets.text(row.manager().displayName()),
                        ExcelSheets.text(row.regionName()),
                        ExcelSheets.text(row.statusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> customers(ListExportRequest request, List<Long> ids, String keyword) {
        String publicFilter = request == null ? null : request.publicFilter();
        List<CustomerView> rows = pick(customerService.list(keyword, publicFilter), ids, CustomerView::id);
        return ExcelSheets.workbook("客户列表", "客户列表",
                new String[] {"简称", "公司", "编码", "等级"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.shortName()),
                        ExcelSheets.text(row.companyName()),
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.level())
                }).toList());
    }

    private ResponseEntity<byte[]> suppliers(ListExportRequest request, List<Long> ids, String keyword) {
        String publicFilter = request == null ? null : request.publicFilter();
        List<SupplierView> rows = pick(supplierService.list(keyword, publicFilter), ids, SupplierView::id);
        return ExcelSheets.workbook("供应商列表", "供应商列表",
                new String[] {"简称", "公司", "编码", "类型"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.shortName()),
                        ExcelSheets.text(row.companyName()),
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.category())
                }).toList());
    }

    private ResponseEntity<byte[]> contracts(ListExportRequest request, List<Long> ids, String keyword) {
        Long customerId = request == null ? null : request.customerId();
        List<ContractView> rows = pick(
                contractService.list(keyword, customerId, null, null, 1, ALL).items(),
                ids,
                ContractView::id
        );
        return ExcelSheets.workbook("合同列表", "合同列表",
                new String[] {"合同编号", "合同名称", "客户", "金额", "签订日期", "状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.name()),
                        ExcelSheets.text(row.customerShortName()),
                        ExcelSheets.text(row.amount()),
                        ExcelSheets.text(row.signDate()),
                        ExcelSheets.text(row.statusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> collections(List<Long> ids, String keyword) {
        List<CollectionView> rows = pick(collectionService.list(keyword, 1, ALL).items(), ids, CollectionView::id);
        return ExcelSheets.workbook("回款列表", "回款列表",
                new String[] {"回款编号", "回款日期", "金额", "立项", "合同", "客户", "收款账户", "方式"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.receivedDate()),
                        ExcelSheets.text(row.amount()),
                        ExcelSheets.text(row.projectName()),
                        ExcelSheets.text(row.contractName()),
                        ExcelSheets.text(row.customerShortName()),
                        ExcelSheets.text(row.accountName()),
                        ExcelSheets.text(row.method())
                }).toList());
    }

    private ResponseEntity<byte[]> deposits(DepositKind kind, String title, List<Long> ids, String keyword) {
        List<DepositView> rows = pick(depositService.list(kind, keyword, null, null, 1, ALL).items(), ids, DepositView::id);
        return ExcelSheets.workbook(title, title,
                new String[] {"单据编码", "项目", "客户", "金额", "日期", "银行账户", "流程状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.projectName()) + " " + ExcelSheets.text(row.projectCode()),
                        ExcelSheets.text(row.customerShortName()),
                        ExcelSheets.text(row.amount()),
                        ExcelSheets.text(row.bizDate()),
                        ExcelSheets.text(row.accountName()),
                        ExcelSheets.text(row.workflowStatusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> bids(List<Long> ids, String keyword) {
        List<BidView> rows = pick(bidService.list(keyword, 1, ALL).items(), ids, BidView::id);
        return ExcelSheets.workbook("招标信息", "招标信息",
                new String[] {"单据编码", "客户", "项目", "招标编号", "最终结果", "中标金额", "流程状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.customerShortName()),
                        ExcelSheets.text(row.projectName()) + " " + ExcelSheets.text(row.projectCode()),
                        ExcelSheets.text(row.tenderCode()),
                        ExcelSheets.text(row.resultLabel()),
                        ExcelSheets.text(row.winAmount()),
                        ExcelSheets.text(row.workflowStatusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> payments(List<Long> ids, String keyword) {
        List<PaymentView> rows = pick(paymentService.list(keyword, 1, ALL).items(), ids, PaymentView::id);
        return ExcelSheets.workbook("付款申请", "付款申请",
                new String[] {"申请编码", "关联项目", "供应商", "付款账户", "金额", "期望付款日", "流程状态", "已扣预算"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        row.projectId() == null ? "未关联" : ExcelSheets.text(row.projectName()) + " " + ExcelSheets.text(row.projectCode()),
                        ExcelSheets.text(row.supplierShortName()),
                        ExcelSheets.text(row.accountName()),
                        ExcelSheets.text(row.amount()),
                        ExcelSheets.text(row.expectedPayDate()),
                        ExcelSheets.text(row.workflowStatusLabel()),
                        row.budgetDeducted() ? "已扣" : "未扣"
                }).toList());
    }

    private ResponseEntity<byte[]> users(ListExportRequest request, List<Long> ids, String keyword) {
        UserStatus status = null;
        if (request != null && request.status() != null && !request.status().isBlank()) {
            status = UserStatus.valueOf(request.status());
        }
        List<UserView> rows = pick(userService.list(keyword, status, 1, ALL).items(), ids, UserView::id);
        return ExcelSheets.workbook("账号列表", "账号列表",
                new String[] {"用户名", "密码", "姓名", "部门", "主管", "角色", "状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.username()),
                        ExcelSheets.text(row.password()),
                        ExcelSheets.text(row.displayName()),
                        ExcelSheets.text(row.departmentName()),
                        row.deptLeader() ? "是" : "否",
                        ExcelSheets.text(row.roleName()),
                        row.status() == UserStatus.ENABLED ? "启用" : "停用"
                }).toList());
    }

    private ResponseEntity<byte[]> personnel(List<Long> ids, String keyword, String status) {
        UserStatus parsed = null;
        if (status != null && !status.isBlank()) {
            try {
                parsed = UserStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("员工状态无效");
            }
        }
        List<StaffPortalView> rows = pick(staffPortalService.listAll(keyword, parsed), ids, StaffPortalView::id);
        return ExcelSheets.workbook("员工信息", "员工信息",
                new String[] {"入职日期", "姓名", "性别", "身份证号码", "出生日期", "联系电话", "岗位", "部门", "员工状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.hireDate()),
                        ExcelSheets.text(row.displayName()),
                        ExcelSheets.text(row.genderLabel()),
                        ExcelSheets.text(row.idNumber()),
                        ExcelSheets.text(row.birthDate()),
                        ExcelSheets.text(row.mobile()),
                        ExcelSheets.text(row.position()),
                        ExcelSheets.text(row.departmentName()),
                        ExcelSheets.text(row.statusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> roles(List<Long> ids) {
        List<RoleView> rows = pick(roleService.list(), ids, RoleView::id);
        return ExcelSheets.workbook("角色列表", "角色列表",
                new String[] {"名称", "说明", "类型"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.name()),
                        ExcelSheets.text(row.description()),
                        row.builtin() ? "内置" : "自定义"
                }).toList());
    }

    private ResponseEntity<byte[]> expenses(List<Long> ids, String keyword) {
        List<ExpenseView> rows = pick(expenseService.list(keyword, 1, ALL).items(), ids, ExpenseView::id);
        return ExcelSheets.workbook("报销申请", "报销申请",
                new String[] {"申请编码", "关联项目", "付款账户", "类别", "金额", "报销日期", "流程状态", "已扣预算"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        row.projectId() == null ? "未关联" : ExcelSheets.text(row.projectName()) + " " + ExcelSheets.text(row.projectCode()),
                        ExcelSheets.text(row.accountName()),
                        ExcelSheets.text(row.categoryLabel()),
                        ExcelSheets.text(row.amount()),
                        ExcelSheets.text(row.expenseDate()),
                        ExcelSheets.text(row.workflowStatusLabel()),
                        row.budgetDeducted() ? "已扣" : "未扣"
                }).toList());
    }

    private ResponseEntity<byte[]> accounts(List<Long> ids, String keyword) {
        List<BankAccountView> rows = pick(bankAccountService.list(keyword), ids, BankAccountView::id);
        return ExcelSheets.workbook("银行账户", "银行账户",
                new String[] {"账户名称", "开户银行", "户名", "账号", "余额", "状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.name()),
                        ExcelSheets.text(row.bankName()),
                        ExcelSheets.text(row.accountName()),
                        ExcelSheets.text(row.accountNo()),
                        ExcelSheets.text(row.balance()),
                        row.enabled() ? "启用" : "停用"
                }).toList());
    }

    private ResponseEntity<byte[]> purchases(List<Long> ids, String keyword) {
        List<PurchaseView> rows = pick(purchaseService.list(keyword, 1, ALL).items(), ids, PurchaseView::id);
        return ExcelSheets.workbook("采购申请", "采购申请",
                new String[] {"申请编码", "关联项目", "供应商", "物料", "金额", "流程状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        row.projectId() == null ? "未关联" : ExcelSheets.text(row.projectName()) + " " + ExcelSheets.text(row.projectCode()),
                        ExcelSheets.text(row.supplierName()),
                        ExcelSheets.text(row.itemName()),
                        ExcelSheets.text(row.amount()),
                        ExcelSheets.text(row.workflowStatusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> purchasePays(List<Long> ids, String keyword) {
        List<PurchasePayView> rows = pick(purchasePayService.list(keyword, 1, ALL).items(), ids, PurchasePayView::id);
        return ExcelSheets.workbook("采购付款", "采购付款",
                new String[] {"付款编码", "采购申请", "付款账户", "金额", "付款日期", "流程状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.purchaseCode()),
                        ExcelSheets.text(row.accountName()),
                        ExcelSheets.text(row.amount()),
                        ExcelSheets.text(row.payDate()),
                        ExcelSheets.text(row.workflowStatusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> warehouses(List<Long> ids, String keyword) {
        List<WarehouseView> rows = pick(warehouseService.list(keyword), ids, WarehouseView::id);
        return ExcelSheets.workbook("仓库信息", "仓库信息",
                new String[] {"仓库名称", "地址", "仓管", "库存数量", "状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.name()),
                        ExcelSheets.text(row.address()),
                        ExcelSheets.text(row.keeper()),
                        ExcelSheets.text(row.stockQty()),
                        row.enabled() ? "启用" : "停用"
                }).toList());
    }

    private ResponseEntity<byte[]> stockOuts(StockOutKind kind, String title, List<Long> ids, String keyword) {
        List<StockOutView> rows = pick(stockOutService.list(kind, keyword, 1, ALL).items(), ids, StockOutView::id);
        return ExcelSheets.workbook(title, title,
                new String[] {"编码", "仓库", "关联", "物料", "数量", "日期", "流程状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.warehouseName()),
                        kind == StockOutKind.TRANSFER
                                ? ExcelSheets.text(row.targetWarehouseName())
                                : kind == StockOutKind.PURCHASE
                                ? ExcelSheets.text(row.purchaseCode())
                                : ExcelSheets.text(row.projectName()),
                        ExcelSheets.text(row.itemName()),
                        ExcelSheets.text(row.qty()) + " " + ExcelSheets.text(row.unit()),
                        ExcelSheets.text(row.outDate()),
                        ExcelSheets.text(row.workflowStatusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> workOrders(DeviceWorkOrderKind kind, String title, List<Long> ids, String keyword) {
        List<DeviceWorkOrderView> rows = pick(deviceWorkOrderService.list(kind, keyword, 1, ALL).items(), ids, DeviceWorkOrderView::id);
        return ExcelSheets.workbook(title, title,
                new String[] {"工单编码", "设备", "事由", "申请日期", "流程状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.deviceName()) + "（" + ExcelSheets.text(row.deviceCode()) + "）",
                        ExcelSheets.text(row.reason()),
                        ExcelSheets.text(row.applyDate()),
                        ExcelSheets.text(row.workflowStatusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> oaOrders(OaKind kind, List<Long> ids, String keyword) {
        List<OaRequestView> rows = pick(oaService.list(kind, keyword, 1, ALL).items(), ids, OaRequestView::id);
        return ExcelSheets.workbook(kind.label(), kind.label(),
                new String[] {"申请编码", "申请人", "部门", "岗位", "日期", "流程状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.applicantName()),
                        ExcelSheets.text(row.departmentName()),
                        ExcelSheets.text(row.position()),
                        ExcelSheets.text(row.expectedDate()),
                        ExcelSheets.text(row.workflowStatusLabel())
                }).toList());
    }

    private ResponseEntity<byte[]> techOrders(TechKind kind, List<Long> ids, String keyword) {
        List<TechRecordView> rows = pick(techService.list(kind, keyword, 1, ALL).items(), ids, TechRecordView::id);
        return ExcelSheets.workbook(kind.label(), kind.label(),
                new String[] {"编码", "标题", "填报人", "公开", "流程状态"},
                rows.stream().map(row -> new String[] {
                        ExcelSheets.text(row.code()),
                        ExcelSheets.text(row.title()),
                        ExcelSheets.text(row.creatorName()),
                        row.isPublic() ? "是" : "否",
                        ExcelSheets.text(row.workflowStatusLabel())
                }).toList());
    }

    private FormModule parse(String module) {
        if (module == null || module.isBlank()) {
            throw ApiException.badRequest("请选择导出模块");
        }
        try {
            return FormModule.valueOf(module.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("不支持的导出模块");
        }
    }

    private static <T> List<T> pick(List<T> rows, List<Long> ids, ToLongFunction<T> idFn) {
        if (ids == null || ids.isEmpty()) {
            return rows;
        }
        Set<Long> wanted = new LinkedHashSet<>(ids);
        return rows.stream().filter(row -> wanted.contains(idFn.applyAsLong(row))).toList();
    }
}
