package com.projectmgmt.oa;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OaRequestRepository extends JpaRepository<OaRequest, Long>, JpaSpecificationExecutor<OaRequest> {
    boolean existsByCode(String code);

    boolean existsByLoginUsernameAndAppliedFalse(String loginUsername);

    boolean existsByLoginUsernameAndAppliedFalseAndIdNot(String loginUsername, Long id);

    boolean existsByLinkRequestId(Long linkRequestId);

    List<OaRequest> findByKindAndLinkRequestId(OaKind kind, Long linkRequestId);
}
