# OA / 业务模块 SOP

## 角色权限（默认，无需用户每次强调）

新增 OA 表单、业务单据、门户、列表时，**默认用角色控制权限**：

1. 增加独立 `FormModule`（及 OA 的 `OaKind`）。
2. 前后端权限树、侧栏、路由、`PermissionService.require` 全部挂该模块。
3. 查看 / 新增 / 编辑 / 删除 / 数据范围按模块能力开放勾选。
4. 可公开填报的模块同步公开访客权限（`canBePublic`）；不可公开的不要硬开。

用户说「特殊情况」时才取消或收窄权限。不要再问「要不要角色控权」。

## OA 申请单落地清单

- `FormModule` + `isOa()`、`OaKind` slug/前缀
- `FormFieldCatalog`、流程种子 `seedSimpleFlow`
- 列表 / 表单 / 公开页 / 导出 / 角色树 / 侧栏 / 路由
- 测试：USER 可见、PUBLIC_GUEST 的 `hasPublicAccess` 符合 `canBePublic`
- 日期/时间字段：整框可点选，用 `DateInput`，见 `docs/sop/forms.md`
- 表单联动用 `Form.useWatch`，不要把字段名当未声明变量，见 `docs/sop/forms.md`
