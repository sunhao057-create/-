package com.projectmgmt.contract;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.common.LinkGuard;
import com.projectmgmt.common.PageResult;
import com.projectmgmt.finance.BankAccount;
import com.projectmgmt.finance.BankAccountRepository;
import com.projectmgmt.finance.BankTxnDirection;
import com.projectmgmt.finance.BankTxnSource;
import com.projectmgmt.finance.LedgerService;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectRepository;
import com.projectmgmt.role.DataScopeQuery;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import com.projectmgmt.user.SysUser;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
public class CollectionService {

    private static final Set<String> METHODS = Set.of("电汇", "支票", "现金", "其他");

    private final ContractCollectionRepository collectionRepository;
    private final ContractRepository contractRepository;
    private final ProjectRepository projectRepository;
    private final BankAccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public PageResult<CollectionView> list(String keyword, int page, int size) {
        Specification<ContractCollection> spec = (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String like = "%" + keyword.trim().toLowerCase() + "%";
            Join<ContractCollection, Contract> contract = root.join("contract", JoinType.INNER);
            Join<Contract, ?> customer = contract.join("customer", JoinType.INNER);
            Join<ContractCollection, Project> project = root.join("project", JoinType.LEFT);
            List<Predicate> ors = new ArrayList<>();
            ors.add(cb.like(cb.lower(root.get("code")), like));
            ors.add(cb.like(cb.lower(contract.get("code")), like));
            ors.add(cb.like(cb.lower(contract.get("name")), like));
            ors.add(cb.like(cb.lower(customer.get("shortName")), like));
            ors.add(cb.like(cb.lower(project.get("name")), like));
            ors.add(cb.like(cb.lower(project.get("code")), like));
            return cb.or(ors.toArray(Predicate[]::new));
        };
        spec = spec.and(DataScopeQuery.createdBy(permissionService.ownFilterUserId(FormModule.CONTRACT)));
        Page<ContractCollection> result = collectionRepository.findAll(
                spec, PageRequest.of(Math.max(page - 1, 0), size, Sort.by(Sort.Direction.DESC, "id")));
        List<CollectionView> items = result.getContent().stream().map(this::toView).toList();
        return new PageResult<>(items, result.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public CollectionView get(Long id) {
        ContractCollection item = require(id);
        permissionService.requireOwnRecord(FormModule.CONTRACT, item.getCreatedBy());
        return toView(item);
    }

    @Transactional(readOnly = true)
    public List<CollectionView> listByContract(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> ApiException.notFound("合同不存在"));
        permissionService.requireOwnRecord(FormModule.CONTRACT, contract.getCreatedBy());
        BigDecimal collected = collectedOf(contractId);
        return collectionRepository.findByContract_IdOrderByReceivedDateDescIdDesc(contractId).stream()
                .map(item -> {
                    if (item.getAccount() != null) {
                        item.getAccount().getName();
                    }
                    return CollectionView.from(item, collected);
                })
                .toList();
    }

    @Transactional
    public CollectionView createForProject(UpsertCollectionRequest request, SysUser creator) {
        Project project = requireLinkedProject(request.projectId());
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        LinkGuard.assertProjectLinkable(project);
        ContractCollection item = new ContractCollection();
        item.setCode(nextCode());
        item.setContract(project.getContract());
        item.setProject(project);
        apply(item, request, project.getContract(), true);
        assertWithinContract(project.getContract(), item.getAmount(), null);
        item.setCreatedBy(creator);
        ContractCollection saved = collectionRepository.save(item);
        syncLedger(saved);
        return toView(saved);
    }

    @Transactional
    public CollectionView update(Long id, UpsertCollectionRequest request) {
        ContractCollection item = require(id);
        permissionService.requireOwnRecord(FormModule.CONTRACT, item.getCreatedBy());
        Project project = requireLinkedProject(request.projectId());
        permissionService.requireOwnRecord(FormModule.PROJECT, project.getCreatedBy());
        if (!project.getContract().getId().equals(item.getContract().getId())) {
            throw ApiException.badRequest("不能把回款改到其他合同");
        }
        LinkGuard.assertNewProjectLink(project, item.getProject() == null ? null : item.getProject().getId());
        item.setProject(project);
        apply(item, request, project.getContract(), true);
        assertWithinContract(project.getContract(), item.getAmount(), item.getId());
        syncLedger(item);
        return toView(item);
    }

    @Transactional
    public void deleteById(Long id) {
        ContractCollection item = require(id);
        permissionService.requireOwnRecord(FormModule.CONTRACT, item.getCreatedBy());
        ledgerService.reverse(BankTxnSource.COLLECTION, item.getId());
        collectionRepository.delete(item);
    }

    @Transactional
    public CollectionView create(Long contractId, UpsertCollectionRequest request, SysUser creator) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> ApiException.notFound("合同不存在"));
        permissionService.requireOwnRecord(FormModule.CONTRACT, contract.getCreatedBy());
        LinkGuard.assertContractLinkable(contract);
        ContractCollection item = new ContractCollection();
        item.setCode(nextCode());
        item.setContract(contract);
        apply(item, request, contract, false);
        assertWithinContract(contract, item.getAmount(), null);
        item.setCreatedBy(creator);
        ContractCollection saved = collectionRepository.save(item);
        syncLedger(saved);
        return toView(saved);
    }

    @Transactional
    public void delete(Long contractId, Long id) {
        ContractCollection item = require(id);
        if (!item.getContract().getId().equals(contractId)) {
            throw ApiException.badRequest("回款不属于该合同");
        }
        permissionService.requireOwnRecord(FormModule.CONTRACT, item.getCreatedBy());
        ledgerService.reverse(BankTxnSource.COLLECTION, item.getId());
        collectionRepository.delete(item);
    }

    private ContractCollection require(Long id) {
        return collectionRepository.findById(id).orElseThrow(() -> ApiException.notFound("回款记录不存在"));
    }

    private CollectionView toView(ContractCollection item) {
        item.getContract().getCustomer().getShortName();
        if (item.getProject() != null) {
            item.getProject().getName();
        }
        if (item.getAccount() != null) {
            item.getAccount().getName();
        }
        return CollectionView.from(item, collectedOf(item.getContract().getId()));
    }

    private Project requireLinkedProject(Long projectId) {
        if (projectId == null) {
            throw ApiException.badRequest("请选择立项");
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.badRequest("项目不存在"));
        if (project.getContract() == null) {
            throw ApiException.badRequest("该立项未关联合同，无法登记回款");
        }
        project.getContract().getCustomer().getShortName();
        return project;
    }

    private void apply(ContractCollection item, UpsertCollectionRequest request, Contract contract, boolean requireProject) {
        item.setAccount(requireAccount(request.accountId()));
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("回款金额必须大于 0");
        }
        item.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        item.setReceivedDate(request.receivedDate());
        String method = StringUtils.hasText(request.method()) ? request.method().trim() : "电汇";
        if (!METHODS.contains(method)) {
            throw ApiException.badRequest("回款方式只能是 电汇 / 支票 / 现金 / 其他");
        }
        item.setMethod(method);
        item.setRemark(request.remark() == null ? "" : request.remark().trim());
        if (request.projectId() == null) {
            if (requireProject) {
                throw ApiException.badRequest("请选择立项");
            }
            item.setProject(null);
            return;
        }
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> ApiException.badRequest("项目不存在"));
        if (project.getContract() == null || !project.getContract().getId().equals(contract.getId())) {
            throw ApiException.badRequest("只能把回款分到本合同下的项目");
        }
        Long previousId = item.getId() == null || item.getProject() == null ? null : item.getProject().getId();
        LinkGuard.assertNewProjectLink(project, previousId);
        item.setProject(project);
    }

    private BankAccount requireAccount(Long accountId) {
        if (accountId == null) {
            throw ApiException.badRequest("请选择收款账户");
        }
        BankAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.badRequest("银行账户不存在"));
        if (!account.isEnabled()) {
            throw ApiException.badRequest("该银行账户已停用");
        }
        return account;
    }

    private void syncLedger(ContractCollection item) {
        if (item.getId() == null || item.getAccount() == null) {
            return;
        }
        String customer = item.getContract() == null || item.getContract().getCustomer() == null
                ? ""
                : item.getContract().getCustomer().getShortName();
        ledgerService.replace(
                item.getAccount(),
                item.getReceivedDate(),
                BankTxnDirection.IN,
                item.getAmount(),
                "项目回款 " + item.getCode() + " " + customer,
                BankTxnSource.COLLECTION,
                item.getId(),
                item.getProject(),
                item.getCreatedBy()
        );
    }

    private void assertWithinContract(Contract contract, BigDecimal amount, Long excludeId) {
        BigDecimal collected = collectionRepository.sumAmountByContractId(contract.getId(), excludeId);
        BigDecimal room = contract.getAmount().subtract(collected == null ? BigDecimal.ZERO : collected);
        if (amount.compareTo(room) > 0) {
            throw ApiException.badRequest("回款金额不能超过合同待回款");
        }
    }

    private BigDecimal collectedOf(Long contractId) {
        BigDecimal collected = collectionRepository.sumAmountByContractId(contractId, null);
        return collected == null ? BigDecimal.ZERO : collected;
    }

    private String nextCode() {
        String prefix = "HK" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i < 1000; i++) {
            String code = prefix + String.format("%03d", i);
            if (!collectionRepository.existsByCode(code)) {
                return code;
            }
        }
        throw ApiException.badRequest("当日回款登记过多，请稍后重试");
    }
}
