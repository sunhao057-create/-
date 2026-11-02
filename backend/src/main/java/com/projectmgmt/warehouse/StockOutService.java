package com.projectmgmt.warehouse;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.purchase.Purchase;
import com.projectmgmt.purchase.PurchaseService;
import com.projectmgmt.purchase.PurchaseSettleService;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.workflow.WorkflowInstance;
import com.projectmgmt.workflow.WorkflowInstanceRepository;
import com.projectmgmt.workflow.WorkflowService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StockOutService {

    private final StockOutRepository stockOutRepository;
    private final WarehouseService warehouseService;
    private final StockLedgerService stockLedgerService;
    private final PurchaseService purchaseService;
    private final ProjectRepository projectRepository;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final PurchaseSettleService settleService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<StockOutView> list(StockOutKind kind, String keyword, int page, int size) {
        FormModule module = moduleOf(kind);
        Specification<StockOut> spec = (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            Predicate kindPred = cb.equal(root.get("kind"), kind);
            if (!StringUtils.hasText(keyword)) {
                return kindPred;
            }
            String like = "%" + keyword.trim().toLowerCase() + "%";
            Join<StockOut, Warehouse> warehouse = root.join("warehouse", JoinType.INNER);
            Join<StockOut, Warehouse> target = root.join("targetWarehouse", JoinType.LEFT);
            Join<StockOut, Project> project = root.join("project", JoinType.LEFT);
            List<Predicate> ors = new ArrayList<>();
            ors.add(cb.like(cb.lower(root.get("code")), like));
            ors.add(cb.like(cb.lower(root.get("itemName")), like));
            ors.add(cb.like(cb.lower(root.get("reason")), like));
            ors.add(cb.like(cb.lower(warehouse.get("name")), like));
            ors.add(cb.like(cb.lower(target.get("name")), like));
            ors.add(cb.like(cb.lower(project.get("name")), like));
            return cb.and(kindPred, cb.or(ors.toArray(Predicate[]::new)));
        };
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(module)));
        Page<StockOut> result = stockOutRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResult<>(result.map(item -> toView(item, kind)).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public StockOutView get(StockOutKind kind, Long id) {
        StockOut out = require(kind, id);
        permissionService.requireOwnRecord(moduleOf(kind), out.getCreatedBy());
        return toView(out, kind);
    }

    @Transactional
    public StockOutView create(StockOutKind kind, UpsertStockOutRequest request, SysUser creator) {
        FormModule module = moduleOf(kind);
        if (!workflowService.hasPublished(module)) {
            throw ApiException.badRequest("请先在流程引擎发布" + module.label() + "流程");
        }
        StockOut out = new StockOut();
        out.setCode(nextCode(kind));
        out.setKind(kind);
        out.setCreatedBy(creator);
        apply(out, kind, request, Map.of(), false);
        assertStock(out, null);
        StockOut saved = stockOutRepository.save(out);
        workflowService.start(module, saved.getId());
        return toView(saved, kind);
    }

    @Transactional
    public StockOutView update(StockOutKind kind, Long id, UpsertStockOutRequest request) {
        StockOut out = require(kind, id);
        permissionService.requireOwnRecord(moduleOf(kind), out.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(moduleOf(kind), id);
        apply(out, kind, request, modes, out.isLedgerPosted());
        assertStock(out, out.getId());
        return toView(out, kind);
    }

    @Transactional
    public void delete(StockOutKind kind, Long id) {
        StockOut out = require(kind, id);
        permissionService.requireOwnRecord(moduleOf(kind), out.getCreatedBy());
        settleService.restoreStockOut(out);
        workflowService.deleteForBiz(moduleOf(kind), id);
        stockOutRepository.delete(out);
    }

    private void apply(StockOut out, StockOutKind kind, UpsertStockOutRequest request, Map<String, String> modes, boolean posted) {
        if (writable(modes, "warehouseId") || out.getWarehouse() == null) {
            out.setWarehouse(warehouseService.requireEnabled(request == null ? null : request.warehouseId()));
        }
        if (kind == StockOutKind.TRANSFER && (writable(modes, "targetWarehouseId") || out.getTargetWarehouse() == null)) {
            out.setTargetWarehouse(warehouseService.requireEnabled(request == null ? null : request.targetWarehouseId()));
        }
        if (kind == StockOutKind.TRANSFER && out.getWarehouse() != null && out.getTargetWarehouse() != null
                && out.getWarehouse().getId().equals(out.getTargetWarehouse().getId())) {
            throw ApiException.badRequest("调出仓和调入仓不能相同");
        }
        if (kind == StockOutKind.PURCHASE && (writable(modes, "purchaseId") || out.getId() == null)) {
            out.setPurchase(optionalPurchase(request == null ? null : request.purchaseId()));
            if (out.getPurchase() != null) {
                Project purchaseProject = out.getPurchase().getProject();
                LinkGuard.assertNewProjectLink(purchaseProject, previousProjectId(out));
                out.setProject(purchaseProject);
            }
        }
        if (kind == StockOutKind.PROJECT && (writable(modes, "projectId") || out.getProject() == null)) {
            out.setProject(requireProject(request == null ? null : request.projectId(), previousProjectId(out)));
        }
        if (kind == StockOutKind.TRANSFER) {
            applyLines(out, request, modes, posted);
        } else {
            if (writable(modes, "itemName") || out.getItemName() == null) {
                String item = BizParse.required(request == null ? null : request.itemName(), "请填写物料名称");
                if (kind == StockOutKind.PURCHASE && out.getPurchase() != null && (request == null || !StringUtils.hasText(request.itemName()))) {
                    item = out.getPurchase().getItemName();
                }
                out.setItemName(item);
            }
            if (writable(modes, "qty") || out.getQty() == null) {
                out.setQty(BizParse.qty(request == null ? null : request.qty()));
            }
            if (writable(modes, "unit") || out.getUnit() == null) {
                out.setUnit(BizParse.unit(request == null ? null : request.unit()));
            }
            if (kind == StockOutKind.PROJECT && (writable(modes, "amount") || out.getAmount() == null)) {
                out.setAmount(BizParse.amount(request == null ? null : request.amount()));
            } else if (kind == StockOutKind.PURCHASE) {
                out.setAmount(BizParse.amountOrZero(request == null ? null : request.amount()));
            }
        }
        if (writable(modes, "reason") || out.getReason() == null) {
            out.setReason(BizParse.required(request == null ? null : request.reason(),
                    kind == StockOutKind.TRANSFER ? "请填写调拨事由" : "请填写出库事由"));
        }
        if (writable(modes, "outDate") || out.getOutDate() == null) {
            out.setOutDate(BizParse.date(request == null ? null : request.outDate(),
                    kind == StockOutKind.TRANSFER ? "请选择调拨日期" : "请选择出库日期"));
        }
    }

    private void applyLines(StockOut out, UpsertStockOutRequest request, Map<String, String> modes, boolean posted) {
        boolean canEdit = writable(modes, "lines")
                || writable(modes, "itemName")
                || writable(modes, "qty")
                || writable(modes, "unit");
        if (!canEdit && out.getItemName() != null && !out.getLines().isEmpty()) {
            return;
        }
        List<UpsertStockOutLineRequest> incoming = normalizeLines(request);
        if (incoming.isEmpty()) {
            throw ApiException.badRequest("请至少填写一行调拨明细");
        }
        out.getLines().clear();
        Set<String> names = new HashSet<>();
        List<String> nameList = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        String unit = null;
        boolean mixedUnit = false;
        int order = 0;
        for (UpsertStockOutLineRequest row : incoming) {
            String name = BizParse.required(row.itemName(), "请填写物料名称");
            String key = name.toLowerCase(Locale.ROOT);
            if (!names.add(key)) {
                throw ApiException.badRequest("调拨明细中物料名称不能重复");
            }
            BigDecimal qty = BizParse.qty(row.qty());
            String lineUnit = BizParse.unit(row.unit());
            StockOutLine line = new StockOutLine();
            line.setStockOut(out);
            line.setItemName(name);
            line.setQty(qty);
            line.setUnit(lineUnit);
            line.setAmount(BizParse.amountOrZero(row.amount()));
            line.setSortOrder(order++);
            out.getLines().add(line);
            nameList.add(name);
            totalQty = totalQty.add(qty);
            if (unit == null) {
                unit = lineUnit;
            } else if (!unit.equals(lineUnit)) {
                mixedUnit = true;
            }
        }
        out.setItemName(summarizeNames(nameList));
        out.setQty(totalQty);
        out.setUnit(mixedUnit ? "项" : unit);
        out.setAmount(BigDecimal.ZERO);
    }

    private void assertStock(StockOut out, Long excludeId) {
        List<StockOutLine> lines = out.getLines();
        if (lines != null && !lines.isEmpty()) {
            for (StockOutLine line : lines) {
                assertItemStock(out.getWarehouse().getId(), line.getItemName(), line.getQty(), excludeId);
            }
            return;
        }
        assertItemStock(out.getWarehouse().getId(), out.getItemName(), out.getQty(), excludeId);
    }

    private void assertItemStock(Long warehouseId, String itemName, BigDecimal qty, Long excludeId) {
        BigDecimal onHand = stockLedgerService.onHand(warehouseId, itemName);
        BigDecimal pending = BizParse.nz(stockOutRepository.pendingQty(warehouseId, itemName, excludeId))
                .add(BizParse.nz(stockOutRepository.pendingLineQty(warehouseId, itemName, excludeId)));
        BigDecimal room = onHand.subtract(pending);
        if (qty.compareTo(room) > 0) {
            throw ApiException.badRequest("库存不足，不能出库（可用 "
                    + room.stripTrailingZeros().toPlainString()
                    + "，本次 "
                    + qty.stripTrailingZeros().toPlainString()
                    + "）");
        }
    }

    private Purchase optionalPurchase(Long purchaseId) {
        if (purchaseId == null) {
            return null;
        }
        return purchaseService.require(purchaseId);
    }

    private Project requireProject(Long projectId, Long previousId) {
        if (projectId == null) {
            throw ApiException.badRequest("请选择关联项目");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.badRequest("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        LinkGuard.assertNewProjectLink(project, previousId);
        return project;
    }

    private static Long previousProjectId(StockOut out) {
        return out.getProject() == null ? null : out.getProject().getId();
    }

    private StockOut require(StockOutKind kind, Long id) {
        StockOut out = stockOutRepository.findById(id).orElseThrow(() -> ApiException.notFound("出库单不存在"));
        if (out.getKind() != kind) {
            throw ApiException.notFound("出库单不存在");
        }
        return out;
    }

    private StockOutView toView(StockOut out, StockOutKind kind) {
        BigDecimal onHand = stockLedgerService.onHand(out.getWarehouse().getId(), firstItemName(out));
        BigDecimal available = BigDecimal.ZERO;
        WorkflowInstance instance = instanceRepository
                .findByFormModuleAndBizId(moduleOf(kind), out.getId())
                .orElse(null);
        String status = instance == null ? null : instance.getStatus();
        List<StockOutLineView> lineViews = out.getLines() == null
                ? List.of()
                : out.getLines().stream().map(StockOutLineView::from).toList();
        return new StockOutView(
                out.getId(),
                out.getCode(),
                out.getKind().name(),
                out.getWarehouse().getId(),
                out.getWarehouse().getName(),
                out.getTargetWarehouse() == null ? null : out.getTargetWarehouse().getId(),
                out.getTargetWarehouse() == null ? null : out.getTargetWarehouse().getName(),
                out.getPurchase() == null ? null : out.getPurchase().getId(),
                out.getPurchase() == null ? null : out.getPurchase().getCode(),
                out.getProject() == null ? null : out.getProject().getId(),
                out.getProject() == null ? null : out.getProject().getName(),
                out.getProject() == null ? null : out.getProject().getCode(),
                out.getItemName(),
                out.getQty(),
                out.getUnit(),
                out.getAmount(),
                out.getReason(),
                out.getOutDate(),
                out.isLedgerPosted(),
                out.isBudgetDeducted(),
                onHand,
                available == null ? BigDecimal.ZERO : available,
                status,
                workflowService.statusLabel(status),
                lineViews,
                out.getCreatedAt()
        );
    }

    private static String firstItemName(StockOut out) {
        if (out.getLines() != null && !out.getLines().isEmpty()) {
            return out.getLines().get(0).getItemName();
        }
        return out.getItemName();
    }

    private String nextCode(StockOutKind kind) {
        String prefix = switch (kind) {
            case PROJECT -> "CX";
            case TRANSFER -> "DB";
            default -> "CC";
        } + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!stockOutRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest(kind == StockOutKind.TRANSFER ? "当日调拨单过多，请稍后重试" : "当日出库单过多，请稍后重试");
    }

    private static FormModule moduleOf(StockOutKind kind) {
        return switch (kind) {
            case PROJECT -> FormModule.STOCK_OUT_PROJECT;
            case TRANSFER -> FormModule.STOCK_TRANSFER;
            default -> FormModule.STOCK_OUT_PURCHASE;
        };
    }

    private static List<UpsertStockOutLineRequest> normalizeLines(UpsertStockOutRequest request) {
        if (request == null) {
            return List.of();
        }
        List<UpsertStockOutLineRequest> incoming = new ArrayList<>();
        if (request.lines() != null) {
            for (UpsertStockOutLineRequest row : request.lines()) {
                if (row != null && StringUtils.hasText(row.itemName())) {
                    incoming.add(row);
                }
            }
        }
        if (!incoming.isEmpty()) {
            return incoming;
        }
        if (StringUtils.hasText(request.itemName())) {
            return List.of(new UpsertStockOutLineRequest(request.itemName(), request.qty(), request.unit(), request.amount()));
        }
        return List.of();
    }

    private static String summarizeNames(List<String> names) {
        if (names.size() == 1) {
            return clip(names.get(0), 120);
        }
        return clip(names.get(0) + " 等" + names.size() + "项", 120);
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static BigDecimal nzAmount(BigDecimal amount) {
        BigDecimal value = BizParse.nz(amount);
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("请填写金额");
        }
        return value;
    }

    private static boolean writable(Map<String, String> modes, String field) {
        if (modes == null || modes.isEmpty()) {
            return true;
        }
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }
}
