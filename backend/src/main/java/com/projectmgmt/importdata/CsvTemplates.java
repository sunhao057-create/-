package com.projectmgmt.importdata;

import com.projectmgmt.common.ApiException;
import com.projectmgmt.role.FormModule;
import java.util.List;

public final class CsvTemplates {

    private CsvTemplates() {}

    public static Spec of(FormModule module) {
        return switch (module) {
            case PROJECT -> new Spec(
                    "项目导入模板.csv",
                    List.of("项目名称", "项目编码", "客户编码", "项目经理用户名", "区域名称", "项目概述"),
                    List.of(
                            "提示：必填，最多200字",
                            "提示：必填且唯一",
                            "提示：填写已有客户的编码，如 133",
                            "提示：填写系统账号的用户名，如 liuxiaolin",
                            "提示：填写已有区域名称，如 华东地区",
                            "提示：选填"
                    )
            );
            case PAYMENT -> new Spec(
                    "付款申请导入模板.csv",
                    List.of("项目编码", "供应商编码", "金额", "付款事由", "付款方式", "期望付款日", "银行账号", "付款账户账号"),
                    List.of(
                            "提示：选填；填写已有项目编码，关联后计入该项目成本",
                            "提示：必填，填写已有供应商编码",
                            "提示：必填，大于 0，最多两位小数",
                            "提示：必填",
                            "提示：必填，只能填 电汇 / 支票 / 现金 / 其他",
                            "提示：必填，格式 2026-08-26",
                            "提示：选填；不填则使用供应商默认账户",
                            "提示：必填，填写公司银行账户的账号"
                    )
            );
            case CUSTOMER -> new Spec(
                    "客户导入模板.csv",
                    List.of("客户简称", "公司名称", "客户编码", "等级", "联系人姓名", "联系人电话", "联系人邮箱", "账户名称", "开户银行", "银行账号"),
                    List.of(
                            "提示：必填，最多50字",
                            "提示：必填，最多120字",
                            "提示：必填且唯一，最多32字",
                            "提示：必填，只能填 一般重要 / 重要 / 非常重要",
                            "提示：选填；填写后将作为主联系人",
                            "提示：选填",
                            "提示：选填",
                            "提示：选填；与开户银行、银行账号同时填写",
                            "提示：选填",
                            "提示：选填"
                    )
            );
            case SUPPLIER -> new Spec(
                    "供应商导入模板.csv",
                    List.of("供应商简称", "公司名称", "供应商编码", "类型", "联系人姓名", "联系人电话", "联系人邮箱", "账户名称", "开户银行", "银行账号"),
                    List.of(
                            "提示：必填，最多50字",
                            "提示：必填，最多120字",
                            "提示：必填且唯一，最多32字",
                            "提示：必填，只能填 原材料 / 设备 / 外协 / 服务",
                            "提示：选填；填写后将作为主联系人",
                            "提示：选填",
                            "提示：选填",
                            "提示：选填；与开户银行、银行账号同时填写",
                            "提示：选填",
                            "提示：选填"
                    )
            );
            case USER -> new Spec(
                    "账号导入模板.csv",
                    List.of("用户名", "密码", "姓名", "角色名称"),
                    List.of(
                            "提示：必填，4-32位字母、数字或下划线，不可重复",
                            "提示：必填，至少8位且同时包含字母和数字；导入后首次登录需改密",
                            "提示：必填，最多50字",
                            "提示：必填，填写角色管理中的角色名称，如 系统管理员 / 普通用户"
                    )
            );
            case ROLE -> new Spec(
                    "角色导入模板.csv",
                    List.of("角色名称", "说明"),
                    List.of(
                            "提示：必填，最多50字；导入后编码自动生成，权限请到「编辑角色」勾选",
                            "提示：选填，最多200字"
                    )
            );
            default -> throw ApiException.badRequest(module.label() + "不支持导入");
        };
    }

    public record Spec(String filename, List<String> headers, List<String> hints) {
        public byte[] bytes() {
            return CsvFiles.template(headers, hints);
        }
    }
}
