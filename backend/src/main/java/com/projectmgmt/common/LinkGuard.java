package com.projectmgmt.common;

import com.projectmgmt.contract.Contract;
import com.projectmgmt.contract.ContractStatus;
import com.projectmgmt.project.Project;
import com.projectmgmt.project.ProjectStatus;

public final class LinkGuard {

    private LinkGuard() {
    }

    public static void assertContractLinkable(Contract contract) {
        if (contract != null && contract.getStatus() == ContractStatus.VOIDED) {
            throw ApiException.badRequest("该合同已作废，不能再关联");
        }
    }

    public static void assertProjectLinkable(Project project) {
        if (project == null) {
            return;
        }
        if (project.getStatus() == ProjectStatus.VOIDED) {
            throw ApiException.badRequest("该项目已作废，不能再关联");
        }
        if (project.getContract() != null) {
            assertContractLinkable(project.getContract());
        }
    }

    public static void assertNewProjectLink(Project project, Long previousProjectId) {
        if (project == null) {
            return;
        }
        if (previousProjectId != null && previousProjectId.equals(project.getId())) {
            return;
        }
        assertProjectLinkable(project);
    }

    public static void assertNewContractLink(Contract contract, Long previousContractId) {
        if (contract == null) {
            return;
        }
        if (previousContractId != null && previousContractId.equals(contract.getId())) {
            return;
        }
        assertContractLinkable(contract);
    }
}
