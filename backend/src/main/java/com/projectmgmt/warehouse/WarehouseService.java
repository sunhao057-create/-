package com.projectmgmt.warehouse;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.BizParse;
import com.projectmgmt.purchase.PurchaseRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final StockTxnRepository txnRepository;
    private final StockOutRepository stockOutRepository;
    private final PurchaseRepository purchaseRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public List<WarehouseView> list(String keyword) {
        Specification<Warehouse> spec = DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.WAREHOUSE));
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("address")), like),
                    cb.like(cb.lower(root.get("keeper")), like)
            ));
        }
        return warehouseRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "id")).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseView get(Long id) {
        Warehouse warehouse = require(id);
        permissionService.requireOwnRecord(FormModule.WAREHOUSE, warehouse.getCreatedBy());
        return toView(warehouse);
    }

    @Transactional
    public WarehouseView create(UpsertWarehouseRequest request, SysUser actor) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCreatedBy(actor);
        apply(warehouse, request, true);
        return toView(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseView update(Long id, UpsertWarehouseRequest request) {
        Warehouse warehouse = require(id);
        permissionService.requireOwnRecord(FormModule.WAREHOUSE, warehouse.getCreatedBy());
        apply(warehouse, request, false);
        return toView(warehouse);
    }

    @Transactional
    public void delete(Long id) {
        Warehouse warehouse = require(id);
        permissionService.requireOwnRecord(FormModule.WAREHOUSE, warehouse.getCreatedBy());
        if (txnRepository.existsByWarehouse_Id(id)
                || stockOutRepository.existsByWarehouse_Id(id)
                || stockOutRepository.existsByTargetWarehouse_Id(id)
                || purchaseRepository.existsByWarehouse_Id(id)) {
            throw ApiException.badRequest("该仓库已有流水或单据，不能删除");
        }
        warehouseRepository.delete(warehouse);
    }

    public Warehouse requireEnabled(Long id) {
        Warehouse warehouse = require(id);
        if (!warehouse.isEnabled()) {
            throw ApiException.badRequest("该仓库已停用");
        }
        return warehouse;
    }

    public Warehouse require(Long id) {
        if (id == null) {
            throw ApiException.badRequest("请选择仓库");
        }
        return warehouseRepository.findById(id).orElseThrow(() -> ApiException.notFound("仓库不存在"));
    }

    @Transactional(readOnly = true)
    public List<StockItemView> stockItems(Long warehouseId) {
        Warehouse warehouse = require(warehouseId);
        permissionService.requireOwnRecord(FormModule.WAREHOUSE, warehouse.getCreatedBy());
        List<StockItemView> items = new ArrayList<>();
        for (Object[] row : txnRepository.stockBalances(warehouseId)) {
            String name = (String) row[0];
            String unit = row[1] == null ? "件" : String.valueOf(row[1]);
            BigDecimal qty = BizParse.nz((BigDecimal) row[2]);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            items.add(new StockItemView(name, unit, qty));
        }
        return items;
    }

    private void apply(Warehouse warehouse, UpsertWarehouseRequest request, boolean creating) {
        String name = BizParse.required(request == null ? null : request.name(), "请填写仓库名称");
        if (creating ? warehouseRepository.existsByName(name)
                : warehouseRepository.existsByNameAndIdNot(name, warehouse.getId())) {
            throw ApiException.badRequest("仓库名称已存在");
        }
        warehouse.setName(name);
        warehouse.setAddress(BizParse.blankToNull(request == null ? null : request.address()));
        warehouse.setKeeper(BizParse.blankToNull(request == null ? null : request.keeper()));
        warehouse.setRemark(BizParse.blankToNull(request == null ? null : request.remark()));
        warehouse.setEnabled(request == null || request.enabled() == null || Boolean.TRUE.equals(request.enabled()));
    }

    private WarehouseView toView(Warehouse warehouse) {
        BigDecimal stock = txnRepository.findAll().stream()
                .filter(item -> warehouse.getId().equals(item.getWarehouse().getId()))
                .map(item -> item.getDirection() == StockTxnDirection.IN ? BizParse.nz(item.getQty()) : BizParse.nz(item.getQty()).negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return WarehouseView.from(warehouse, stock);
    }
}
