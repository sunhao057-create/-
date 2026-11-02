package com.projectmgmt.oa;

import java.util.List;

public final class OaFormOptions {

    private OaFormOptions() {}

    public static final List<String> COMPANIES = List.of(
            "西安赛仕智能科技有限公司",
            "陕西规圆技术工程股份有限公司",
            "西安赛仕实验室仪器技术有限公司",
            "陕西易卓实验科技有限公司",
            "西安曲江新区启蝶技术服务部(个体工商户)",
            "矩圆方升检测（陕西）有限公司"
    );

    public static final List<String> SEAL_TYPES = List.of("公章", "法人章", "财务章");

    public static final List<String> QUALIFY_ITEMS = List.of(
            "营业执照正本",
            "营业执照副本",
            "公章",
            "私章",
            "财务章",
            "合同原件",
            "合同章",
            "其他"
    );

    public static final List<String> TRAVEL_MODES = List.of("PUBLIC", "COMPANY", "SELF");

    public static final List<String> LEAVE_TYPES = List.of(
            "PERSONAL",
            "ANNUAL",
            "SICK",
            "MARRIAGE",
            "MATERNITY",
            "PATERNITY",
            "PRENATAL",
            "BEREAVEMENT"
    );

    public static final List<String> KINSHIPS = List.of("PARENT", "SPOUSE", "CHILD");

    public static String travelModeLabel(String mode) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case "PUBLIC" -> "公共交通（飞机/火车/汽车/轮船）";
            case "COMPANY" -> "公司车辆";
            case "SELF" -> "自驾车辆";
            default -> mode;
        };
    }

    public static String leaveTypeLabel(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "PERSONAL" -> "事假";
            case "ANNUAL" -> "年假";
            case "SICK" -> "病假";
            case "MARRIAGE" -> "婚假";
            case "MATERNITY" -> "产假";
            case "PATERNITY" -> "陪产假";
            case "PRENATAL" -> "产检假";
            case "BEREAVEMENT" -> "丧假";
            default -> type;
        };
    }

    public static String kinshipLabel(String kinship) {
        if (kinship == null) {
            return null;
        }
        return switch (kinship) {
            case "PARENT" -> "父母";
            case "SPOUSE" -> "配偶";
            case "CHILD" -> "子女";
            default -> kinship;
        };
    }
}
