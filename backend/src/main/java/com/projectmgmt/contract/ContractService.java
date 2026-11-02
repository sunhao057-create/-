package com.projectmgmt.contract;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.customer.Customer;
import com.projectmgmt.customer.CustomerRepository;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import com.projectmgmt.workflow.WorkflowService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
public class ContractService {

    private final ContractRepository contractRepository;
    private final ContractCollectionRepository collectionRepository;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final PermissionService permissionService;
    private final WorkflowService workflowService;

    @Transactional(readOnly = true)
    public PageResult<ContractView> list(String keyword, Long customerId, Boolean linkable, Long keepId, int page, int size) {
        Specification<Contract> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("code")), like)
            ));
        }
        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        }
        if (Boolean.TRUE.equals(linkable)) {
            spec = spec.and((root, query, cb) -> {
                var ok = cb.notEqual(root.get("status"), ContractStatus.VOIDED);
                if (keepId != null) {
                    return cb.or(ok, cb.equal(root.get("id"), keepId));
                }
                return ok;
            });
        }
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.CONTRACT)));
        Page<Contract> result = contractRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id")));
        List<Long> ids = result.getContent().stream().map(Contract::getId).toList();
        Map<Long, BigDecimal> collected = collectedByContract(ids);
        List<ContractView> items = result.getContent().stream()
                .map(item -> toView(item, collected.getOrDefault(item.getId(), BigDecimal.ZERO)))
                .toList();
        return new PageResult<>(items, result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public List<ContractView> listByCustomer(Long customerId, Boolean linkable) {
        return contractRepository.findByCustomer_IdOrderByIdDesc(customerId).stream()
                .filter(item -> permissionService.ownFilterUserId(FormModule.CONTRACT) == null
                        || (item.getCreatedBy() != null
                        && item.getCreatedBy().getId().equals(permissionService.ownFilterUserId(FormModule.CONTRACT))))
                .filter(item -> !Boolean.TRUE.equals(linkable) || item.getStatus() != ContractStatus.VOIDED)
                .map(item -> toView(item, collectedOf(item.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractView get(Long id) {
        Contract contract = require(id);
        permissionService.requireOwnRecord(FormModule.CONTRACT, contract.getCreatedBy());
        contract.getCustomer().getShortName();
        return toView(contract, collectedOf(id));
    }

    @Transactional
    public ContractView create(UpsertContractRequest request, SysUser creator) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> ApiException.badRequest("请选择客户"));
        Contract contract = new Contract();
        contract.setCode(nextCode());
        apply(contract, request, customer);
        contract.setCreatedBy(creator);
        Contract saved = contractRepository.save(contract);
        workflowService.start(FormModule.CONTRACT, saved.getId());
        return toView(saved, BigDecimal.ZERO);
    }

    @Transactional
    public ContractView update(Long id, UpsertContractRequest request) {
        Contract contract = require(id);
        permissionService.requireOwnRecord(FormModule.CONTRACT, contract.getCreatedBy());
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> ApiException.badRequest("请选择客户"));
        apply(contract, request, customer);
        return toView(contract, collectedOf(id));
    }

    @Transactional
    public void delete(Long id) {
        Contract contract = require(id);
        permissionService.requireOwnRecord(FormModule.CONTRACT, contract.getCreatedBy());
        if (projectRepository.existsByContract_Id(id)) {
            throw ApiException.badRequest("该合同已关联项目立项，不能删除");
        }
        if (collectionRepository.existsByContract_Id(id)) {
            throw ApiException.badRequest("该合同已有回款记录，不能删除");
        }
        workflowService.deleteForBiz(FormModule.CONTRACT, id);
        contractRepository.delete(contract);
    }

    @Transactional
    public ContractView voidContract(Long id) {
        Contract contract = require(id);
        permissionService.requireOwnRecord(FormModule.CONTRACT, contract.getCreatedBy());
        contract.setStatus(ContractStatus.VOIDED);
        return toView(contract, collectedOf(id));
    }

    @Transactional
    public ContractView restore(Long id) {
        Contract contract = require(id);
        permissionService.requireOwnRecord(FormModule.CONTRACT, contract.getCreatedBy());
        if (contract.getStatus() == ContractStatus.VOIDED) {
            contract.setStatus(ContractStatus.EXECUTING);
        }
        return toView(contract, collectedOf(id));
    }

    public Contract require(Long id) {
        return contractRepository.findById(id).orElseThrow(() -> ApiException.notFound("合同不存在"));
    }

    private void apply(Contract contract, UpsertContractRequest request, Customer customer) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("合同金额必须大于 0");
        }
        contract.setName(request.name().trim());
        contract.setCustomer(customer);
        contract.setAmount(request.amount().setScale(2, java.math.RoundingMode.HALF_UP));
        contract.setSignDate(request.signDate());
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setStatus(ContractStatus.from(request.status()));
        contract.setRemark(request.remark() == null ? "" : request.remark().trim());
        if (request.isPublic() != null) {
            contract.setPublic(request.isPublic());
        }
    }

    private ContractView toView(Contract contract, BigDecimal collected) {
        int projects = (int) projectRepository.findByContract_IdOrderByIdDesc(contract.getId()).size();
        return ContractView.from(contract, projects, collected);
    }

    private BigDecimal collectedOf(Long contractId) {
        return collectedByContract(List.of(contractId)).getOrDefault(contractId, BigDecimal.ZERO);
    }

    private Map<Long, BigDecimal> collectedByContract(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return collectionRepository.findByContract_IdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getContract().getId(),
                        Collectors.reducing(BigDecimal.ZERO, ContractCollection::getAmount, BigDecimal::add)
                ));
    }

    private String nextCode() {
        String prefix = "HT" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!contractRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日合同申请过多，请稍后重试");
    }
}
