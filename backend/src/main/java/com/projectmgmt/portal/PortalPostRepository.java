package com.projectmgmt.portal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalPostRepository extends JpaRepository<PortalPost, Long> {

    List<PortalPost> findByKindOrderBySortOrderAscIdDesc(String kind);

    List<PortalPost> findByKindAndEnabledTrueOrderBySortOrderAscIdDesc(String kind);
}
