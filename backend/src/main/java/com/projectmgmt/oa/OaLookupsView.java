package com.projectmgmt.oa;

import com.projectmgmt.org.OrgDepartmentView;
import com.projectmgmt.user.StaffView;
import java.util.List;

public record OaLookupsView(List<OrgDepartmentView> departments, List<StaffView> staff) {
}
