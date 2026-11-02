package com.projectmgmt.purchase;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.supplier.Supplier;
import com.projectmgmt.supplier.SupplierRepository;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.warehouse.Warehouse;
import com.projectmgmt.warehouse.WarehouseService;
import com.projectmgmt.workflow.WorkflowInstance;
import com.projectmgmt.workflow.WorkflowInstanceRepository;
import com.projectmgmt.workflow.WorkflowService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchasePayRepository purchasePayRepository;
    private final ProjectRepository projectRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseService warehouseService;
    private final WorkflowService workflowService;
    private final WorkflowInstanceRepository instanceRepository;
    private final PurchaseSettleService settleService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<PurchaseView> list(String keyword, int page, int size) {
        Specification<Purchase> spec = (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase() + "%";
            Join<Purchase, Project> project = root.join("project", JoinType.LEFT);
            Join<Purchase, Supplier> supplier = root.join("supplier", JoinType.INNER);
            List<Predicate> ors = new ArrayList<>();
            ors.add(cb.like(cb.lower(root.get("code")), like));
            ors.add(cb.like(cb.lower(root.get("reason")), like));
            ors.add(cb.like(cb.lower(root.get("itemName")), like));
            ors.add(cb.like(cb.lower(supplier.get("shortName")), like));
            ors.add(cb.like(cb.lower(project.get("name")), like));
            ors.add(cb.like(cb.lower(project.get("code")), like));
            if (query != null) {
                Subquery<Long> lineExists = query.subquery(Long.class);
                Root<PurchaseLine> lineRoot = lineExists.from(PurchaseLine.class);
                lineExists.select(lineRoot.get("id"));
                lineExists.where(
                        cb.equal(lineRoot.get("purchase"), root),
                        cb.like(cb.lower(lineRoot.get("itemName")), like)
                );
                ors.add(cb.exists(lineExists));
            }
            return cb.or(ors.toArray(Predicate[]::new));
        };
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.PURCHASE)));
        Page<Purchase> result = purchaseRepository.findAll(
                spec,
                PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id"))
        );
        return new PageResult<>(result.map(this::toView).getContent(), result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public PurchaseView get(Long id) {
        Purchase purchase = require(id);
        permissionService.requireOwnRecord(FormModule.PURCHASE, purchase.getCreatedBy());
        return toView(purchase);
    }

    @Transactional
    public PurchaseView create(UpsertPurchaseRequest request, SysUser creator) {
        if (!workflowService.hasPublished(FormModule.PURCHASE)) {
            throw ApiException.badRequest("请先在流程引擎发布采购申请流程");
        }
        Purchase purchase = new Purchase();
        purchase.setCode(nextCode());
        purchase.setCreatedBy(creator);
        apply(purchase, request, Map.of(), false);
        Purchase saved = purchaseRepository.save(purchase);
        workflowService.start(FormModule.PURCHASE, saved.getId());
        return toView(saved);
    }

    @Transactional
    public PurchaseView update(Long id, UpsertPurchaseRequest request) {
        Purchase purchase = require(id);
        permissionService.requireOwnRecord(FormModule.PURCHASE, purchase.getCreatedBy());
        Map<String, String> modes = workflowService.writeModes(FormModule.PURCHASE, id);
        apply(purchase, request, modes, purchase.isBudgetDeducted());
        return toView(purchase);
    }

    @Transactional
    public void delete(Long id) {
        Purchase purchase = require(id);
        permissionService.requireOwnRecord(FormModule.PURCHASE, purchase.getCreatedBy());
        if (purchasePayRepository.existsByPurchase_Id(id)) {
            throw ApiException.badRequest("该采购申请已有付款，请先删除付款");
        }
        settleService.restorePurchase(purchase);
        workflowService.deleteForBiz(FormModule.PURCHASE, id);
        purchaseRepository.delete(purchase);
    }

    public Purchase require(Long id) {
        if (id == null) {
            throw ApiException.badRequest("请选择采购申请");
        }
        return purchaseRepository.findById(id).orElseThrow(() -> ApiException.notFound("采购申请不存在"));
    }

    private void apply(Purchase purchase, UpsertPurchaseRequest request, Map<String, String> modes, boolean deducted) {
        if (writable(modes, "projectId")) {
            purchase.setProject(optionalProject(request == null ? null : request.projectId(), previousProjectId(purchase)));
        } else if (purchase.getId() == null) {
            purchase.setProject(optionalProject(request == null ? null : request.projectId(), null));
        }
        if (writable(modes, "supplierId") || purchase.getSupplier() == null) {
            purchase.setSupplier(requireSupplier(request == null ? null : request.supplierId()));
        }
        if (writable(modes, "warehouseId") || purchase.getWarehouse() == null) {
            purchase.setWarehouse(warehouseService.requireEnabled(request == null ? null : request.warehouseId()));
        }
        applyLines(purchase, request, modes, deducted);
        if (writable(modes, "reason") || purchase.getReason() == null) {
            purchase.setReason(BizParse.required(request == null ? null : request.reason(), "请填写采购事由"));
        }
        if (writable(modes, "purchaseDate") || purchase.getPurchaseDate() == null) {
            purchase.setPurchaseDate(BizParse.date(request == null ? null : request.purchaseDate(), "请选择采购日期"));
        }
    }

    private Project optionalProject(Long projectId, Long previousId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.badRequest("项目不存在"));
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        LinkGuard.assertNewProjectLink(project, previousId);
        return project;
    }

    private static Long previousProjectId(Purchase purchase) {
        return purchase.getProject() == null ? null : purchase.getProject().getId();
    }

    private Supplier requireSupplier(Long supplierId) {
        if (supplierId == null) {
            throw ApiException.badRequest("请选择供应商");
        }
        return supplierRepository.findById(supplierId).orElseThrow(() -> ApiException.badRequest("供应商不存在"));
    }

    PurchaseView toView(Purchase purchase) {
        BigDecimal available = BigDecimal.ZERO;
        BigDecimal occupied = BizParse.nz(purchasePayRepository.paidOrPending(purchase.getId(), null));
        BigDecimal unpaid = BizParse.nz(purchase.getAmount()).subtract(occupied);
        if (unpaid.compareTo(BigDecimal.ZERO) < 0) {
            unpaid = BigDecimal.ZERO;
        }
        WorkflowInstance instance = instanceRepository
                .findByFormModuleAndBizId(FormModule.PURCHASE, purchase.getId())
                .orElse(null);
        String status = instance == null ? null : instance.getStatus();
        Warehouse warehouse = purchase.getWarehouse();
        return new PurchaseView(
                purchase.getId(),
                purchase.getCode(),
                purchase.getProject() == null ? null : purchase.getProject().getId(),
                purchase.getProject() == null ? null : purchase.getProject().getName(),
                purchase.getProject() == null ? null : purchase.getProject().getCode(),
                purchase.getSupplier().getId(),
                purchase.getSupplier().getShortName(),
                warehouse.getId(),
                warehouse.getName(),
                purchase.getItemName(),
                purchase.getQty(),
                purchase.getUnit(),
                purchase.getAmount(),
                purchase.getReason(),
                purchase.getPurchaseDate(),
                purchase.isBudgetDeducted(),
                available == null ? BigDecimal.ZERO : available,
                unpaid,
                status,
                workflowService.statusLabel(status),
                purchase.getLines().stream().map(PurchaseLineView::from).toList(),
                purchase.getCreatedAt()
        );
    }

    private void applyLines(Purchase purchase, UpsertPurchaseRequest request, Map<String, String> modes, boolean deducted) {
        boolean canEdit = writable(modes, "lines")
                        || writable(modes, "itemName")
                        || writable(modes, "qty")
                        || writable(modes, "unit")
                        || writable(modes, "amount");
        if (!canEdit && purchase.getItemName() != null && !purchase.getLines().isEmpty()) {
            return;
        }
        List<UpsertPurchaseLineRequest> incoming = normalizeLines(request);
        if (incoming.isEmpty()) {
            throw ApiException.badRequest("请至少填写一行采购明细");
        }
        purchase.getLines().clear();
        Set<String> names = new HashSet<>();
        List<String> nameList = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        String unit = null;
        boolean mixedUnit = false;
        int order = 0;
        for (UpsertPurchaseLineRequest row : incoming) {
            String name = BizParse.required(row.itemName(), "请填写物料名称");
            String key = name.toLowerCase(Locale.ROOT);
            if (!names.add(key)) {
                throw ApiException.badRequest("采购明细中物料名称不能重复");
            }
            BigDecimal qty = BizParse.qty(row.qty());
            String lineUnit = BizParse.unit(row.unit());
            BigDecimal amount = BizParse.amount(row.amount());
            PurchaseLine line = new PurchaseLine();
            line.setPurchase(purchase);
            line.setItemName(name);
            line.setQty(qty);
            line.setUnit(lineUnit);
            line.setAmount(amount);
            line.setSortOrder(order++);
            purchase.getLines().add(line);
            nameList.add(name);
            totalQty = totalQty.add(qty);
            totalAmount = totalAmount.add(amount);
            if (unit == null) {
                unit = lineUnit;
            } else if (!unit.equals(lineUnit)) {
                mixedUnit = true;
            }
        }
        purchase.setItemName(summarizeNames(nameList));
        purchase.setQty(totalQty);
        purchase.setUnit(mixedUnit ? "项" : unit);
        purchase.setAmount(totalAmount);
    }

    private static List<UpsertPurchaseLineRequest> normalizeLines(UpsertPurchaseRequest request) {
        if (request == null) {
            return List.of();
        }
        List<UpsertPurchaseLineRequest> incoming = new ArrayList<>();
        if (request.lines() != null) {
            for (UpsertPurchaseLineRequest row : request.lines()) {
                if (row != null && StringUtils.hasText(row.itemName())) {
                    incoming.add(row);
                }
            }
        }
        if (!incoming.isEmpty()) {
            return incoming;
        }
        if (StringUtils.hasText(request.itemName())) {
            return List.of(new UpsertPurchaseLineRequest(
                    request.itemName(), request.qty(), request.unit(), request.amount()));
        }
        return List.of();
    }

    private static String summarizeNames(List<String> names) {
        if (names.size() == 1) {
            return clip(names.get(0), 120);
        }
        String summary = names.get(0) + " 等" + names.size() + "项";
        return clip(summary, 120);
    }

    private static String clip(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private String nextCode() {
        String prefix = "CG" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!purchaseRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日采购申请过多，请稍后重试");
    }

    private static boolean writable(Map<String, String> modes, String field) {
        if (modes == null || modes.isEmpty()) {
            return true;
        }
        return WorkflowService.EDITABLE.equals(modes.getOrDefault(field, WorkflowService.EDITABLE));
    }
}
