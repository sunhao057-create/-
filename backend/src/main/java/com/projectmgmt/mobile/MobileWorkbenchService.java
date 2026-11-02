package com.projectmgmt.mobile;

import com.projectmgmt.mobile.dto.MobileAppGroup;
import com.projectmgmt.mobile.dto.MobileAppItem;
import com.projectmgmt.role.FormModule;
import com.projectmgmt.role.PermissionService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MobileWorkbenchService {

    private record Spec(FormModule module, String label, String path, String createPath, String tone) {
    }

    /** 与网页应用目录、角色权限树同一批模块。 */
    private static final List<Spec> DEVICE = List.of(
            new Spec(FormModule.DEVICE, "设备信息", "/devices", "/devices/new", "slate"),
            new Spec(FormModule.DEVICE_REPAIR, "设备维修", "/devices/repairs", "/devices/repairs/new", "amber"),
            new Spec(FormModule.DEVICE_MAINT, "维保服务记录", "/devices/maint-records", "/devices/maint-records/new", "emerald"),
            new Spec(FormModule.DEVICE_MOVE, "设备搬迁", "/devices/moves", "/devices/moves/new", "cyan"),
            new Spec(FormModule.DEVICE_SCRAP, "设备报废", "/devices/scraps", "/devices/scraps/new", "rose")
    );

    private static final List<Spec> CUSTOMER = List.of(
            new Spec(FormModule.CUSTOMER_APPLY, "新建客户", "/customer-opens", "/customer-opens/new", "blue"),
            new Spec(FormModule.CUSTOMER_ACCOUNT, "新建账号", "/customer-accounts", "/customer-accounts/new", "indigo")
    );

    private static final List<Spec> MINI = List.of(
            new Spec(FormModule.MINI_BANNER, "轮播图", "/portal/banners", "/portal/banners/new", "blue"),
            new Spec(FormModule.MINI_KNOWLEDGE, "知识库", "/portal/knowledge", "/portal/knowledge/new", "indigo"),
            new Spec(FormModule.MINI_COMPANY, "公司介绍", "/portal/company", "/portal/company/new", "cyan"),
            new Spec(FormModule.MINI_SERVICE, "服务内容", "/portal/service", "/portal/service/new", "amber")
    );

    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public List<MobileAppGroup> groups() {
        List<MobileAppGroup> groups = new ArrayList<>();
        addGroup(groups, "device", "设备管理", DEVICE);
        addGroup(groups, "customer", "客户", CUSTOMER);
        addGroup(groups, "mini", "客户小程序", MINI);
        return groups;
    }

    private void addGroup(List<MobileAppGroup> groups, String key, String title, List<Spec> specs) {
        List<MobileAppItem> items = new ArrayList<>();
        for (Spec spec : specs) {
            if (spec.module().retired() || !permissionService.currentAllowed(spec.module(), "VIEW")) {
                continue;
            }
            boolean create = spec.createPath() != null
                    && spec.module().hasCreate()
                    && permissionService.currentAllowed(spec.module(), "CREATE");
            items.add(new MobileAppItem(
                    spec.module().name(),
                    spec.label(),
                    spec.path(),
                    create ? spec.createPath() : null,
                    spec.tone(),
                    create
            ));
        }
        if (!items.isEmpty()) {
            groups.add(new MobileAppGroup(key, title, List.copyOf(items)));
        }
    }

}
