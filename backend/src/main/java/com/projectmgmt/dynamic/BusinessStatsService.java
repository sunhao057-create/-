package com.projectmgmt.dynamic;

import tools.jackson.databind.json.JsonMapper;
import com.projectmgmt.role.PermissionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 经营统计：从销售/采购/项目源单汇总收支，不读轻流辅助表。
 */
@Service
@RequiredArgsConstructor
public class BusinessStatsService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_DT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final FormDefinitionRepository definitionRepository;
    private final FormDataRecordRepository recordRepository;
    private final PermissionService permissionService;
    private final JsonMapper objectMapper = JsonMapper.shared();

    private record Spec(
            String code,
            String title,
            String domain,
            String bucket,
            String amountTitle,
            String dateTitle,
            String lineAmountTitle
    ) {}

    private record Hit(
            String domain,
            String bucket,
            String project,
            String company,
            String bank,
            String account,
            LocalDate date,
            double amount
    ) {}

    private static final List<Spec> SPECS = List.of(
            new Spec("8rlbmv813s02", "销售订单", "sales", "contractIn", "最终合同额", "合同签订时间", null),
            new Spec("8ut8e3p5ug02", "技术服务结算", "sales", "contractIn", "最终合同额", "合同签订时间", null),
            new Spec("8skchjldgk01", "回款登记", "sales", "cashIn", "本次回款金额", "收款日期", "回款金额"),
            new Spec("8ukiskb5ug02", "开票明细", "sales", "invoice", "实际开票金额", "开票日期", "实际开票金额"),
            new Spec("dht7opjbrg02", "发票作废", "sales", "invoiceVoid", "本次作废金额", "开票日期", null),
            new Spec("8tv6a7p1m402", "采购订单", "purchase", "contractOut", "订单总金额", "采购日期", null),
            new Spec("8tvjmj3llg01", "采购付款", "purchase", "cashOut", "本次付款总额", "实际付款时间", "实际付款金额"),
            new Spec("dndcbd60dc01", "采购预充值", "purchase", "prepaid", "本次付款金额", "支付日期", null),
            new Spec("8u9o017pjk02", "其它收款", "project", "otherIn", "收款金额", "收款时间", "收款金额"),
            new Spec("93aa2lsjro02", "其它付款", "project", "otherOut", "付款金额", "收款时间", "付款金额"),
            new Spec("dhtbb673t802", "保证金申请", "project", "depositOut", "申请保证金额", "日期", null),
            new Spec("dhtdvvu3pk02", "保证金退回", "project", "depositIn", "申请保证金额", "日期", null)
    );

    @Transactional(readOnly = true)
    public Map<String, Object> businessStats(Integer year, String company, String project) {
        String companyQ = normalize(company);
        String projectQ = normalize(project);
        List<Hit> hits = new ArrayList<>();
        List<Map<String, Object>> streams = new ArrayList<>();
        TreeSet<Integer> years = new TreeSet<>();

        for (Spec spec : SPECS) {
            FormDefinition def = definitionRepository.findByCode(spec.code).orElse(null);
            if (def == null || !def.isEnabled() || !permissionService.currentAllowed(spec.code, "VIEW")) {
                continue;
            }
            List<Map<String, Object>> fields = fieldsOf(def);
            String amountKey = findKey(fields, spec.amountTitle);
            String dateKey = findKey(fields, spec.dateTitle, "实际付款时间", "付款时间", "收款日期", "收款时间",
                    "支付日期", "开票日期", "采购日期", "合同签订时间", "日期");
            String projectKey = findKey(fields, "所属项目", "项目名称", "选择项目", "项目");
            String companyKey = findKey(fields, "收款公司", "付款公司", "申请付款公司", "归属公司", "项目归属公司", "公司");
            String bankKey = findKey(fields, "开户行信息", "开户行", "银行名称");
            String accountKey = findKey(fields, "账号");
            String lineAmountTitle = spec.lineAmountTitle;

            double streamSum = 0;
            int counted = 0;
            int docs = 0;
            for (FormDataRecord record : recordRepository.findAllByDefinitionIdOrderByIdDesc(def.getId())) {
                if ("CANCELLED".equals(record.getStatus())) {
                    continue;
                }
                docs++;
                Map<String, Object> values = readJson(record.getValuesJson());
                double amount = headerOrLines(values, amountKey, fields, spec.lineAmountTitle);
                if (amount == 0) {
                    continue;
                }
                LocalDate date = asDate(values.get(dateKey), record.getCreatedAt());
                if (date != null) {
                    years.add(date.getYear());
                }
                if (year != null && (date == null || date.getYear() != year)) {
                    continue;
                }
                String companyName = labelOf(values.get(companyKey));
                if (!companyQ.isEmpty() && !normalize(companyName).contains(companyQ)) {
                    continue;
                }
                String bankName = labelOf(values.get(bankKey));
                String accountNo = labelOf(values.get(accountKey));
                List<NamedAmt> splits = projectSplits(values, fields, lineAmountTitle, projectKey, amount);
                boolean any = false;
                for (NamedAmt split : splits) {
                    if (!projectQ.isEmpty() && !normalize(split.name).contains(projectQ)) {
                        continue;
                    }
                    hits.add(new Hit(spec.domain, spec.bucket, blankTo(split.name, "未归属项目"),
                            blankTo(companyName, "未归属公司"), blankTo(bankName, "未填开户行"),
                            blankTo(accountNo, "未填账号"), date, split.amount));
                    streamSum += split.amount;
                    any = true;
                }
                if (any) {
                    counted++;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", spec.code);
            row.put("title", spec.title);
            row.put("metric", spec.amountTitle);
            row.put("domain", spec.domain);
            row.put("bucket", spec.bucket);
            row.put("count", docs);
            row.put("amountCount", counted);
            row.put("amount", round2(streamSum));
            streams.add(row);
        }

        Map<String, double[]> months = new TreeMap<>();
        Map<String, double[]> projects = new LinkedHashMap<>();
        Map<String, double[]> companies = new LinkedHashMap<>();
        double[] k = new double[12];
        for (Hit hit : hits) {
            addBucket(k, hit.bucket, hit.amount);
            String month = hit.date == null ? "未知" : hit.date.format(YM);
            addBucket(months.computeIfAbsent(month, key -> new double[12]), hit.bucket, hit.amount);
            addBucket(projects.computeIfAbsent(hit.project, key -> new double[12]), hit.bucket, hit.amount);
            addBucket(companies.computeIfAbsent(hit.company, key -> new double[12]), hit.bucket, hit.amount);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("years", new ArrayList<>(years));
        result.put("kpis", kpisOf(k));
        result.put("streams", streams);
        result.put("monthly", rowsOf(months, true));
        result.put("projects", namedRows(projects, true));
        result.put("companies", namedRows(companies, false));
        result.put("banks", bankRows(hits, companyQ));
        return result;
    }

    private static final int CASH_IN = 0;
    private static final int CASH_OUT = 1;
    private static final int CONTRACT_IN = 2;
    private static final int CONTRACT_OUT = 3;
    private static final int RECEIVED = 4;
    private static final int PAID = 5;
    private static final int INVOICE = 6;
    private static final int INVOICE_VOID = 7;
    private static final int OTHER_IN = 8;
    private static final int OTHER_OUT = 9;
    private static final int DEPOSIT_IN = 10;
    private static final int DEPOSIT_OUT = 11;

    private void addBucket(double[] acc, String bucket, double amount) {
        switch (bucket) {
            case "cashIn" -> {
                acc[CASH_IN] += amount;
                acc[RECEIVED] += amount;
            }
            case "cashOut" -> {
                acc[CASH_OUT] += amount;
                acc[PAID] += amount;
            }
            case "contractIn" -> acc[CONTRACT_IN] += amount;
            case "contractOut" -> acc[CONTRACT_OUT] += amount;
            case "invoice" -> acc[INVOICE] += amount;
            case "invoiceVoid" -> acc[INVOICE_VOID] += amount;
            case "otherIn" -> {
                acc[CASH_IN] += amount;
                acc[OTHER_IN] += amount;
            }
            case "otherOut" -> {
                acc[CASH_OUT] += amount;
                acc[OTHER_OUT] += amount;
            }
            case "depositIn" -> {
                acc[CASH_IN] += amount;
                acc[DEPOSIT_IN] += amount;
            }
            case "depositOut" -> {
                acc[CASH_OUT] += amount;
                acc[DEPOSIT_OUT] += amount;
            }
            case "prepaid" -> acc[CASH_OUT] += amount;
            default -> {
            }
        }
    }

    private Map<String, Object> kpisOf(double[] k) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cashIn", round2(k[CASH_IN]));
        row.put("cashOut", round2(k[CASH_OUT]));
        row.put("cashNet", round2(k[CASH_IN] - k[CASH_OUT]));
        row.put("contractIn", round2(k[CONTRACT_IN]));
        row.put("contractOut", round2(k[CONTRACT_OUT]));
        row.put("contractProfit", round2(k[CONTRACT_IN] - k[CONTRACT_OUT] + k[OTHER_IN] - k[OTHER_OUT]));
        row.put("received", round2(k[RECEIVED]));
        row.put("receivable", round2(Math.max(0, k[CONTRACT_IN] - k[RECEIVED])));
        row.put("paid", round2(k[PAID]));
        row.put("payable", round2(Math.max(0, k[CONTRACT_OUT] - k[PAID])));
        row.put("invoice", round2(k[INVOICE]));
        row.put("invoiceVoid", round2(k[INVOICE_VOID]));
        row.put("invoiceNet", round2(k[INVOICE] - k[INVOICE_VOID]));
        row.put("otherIn", round2(k[OTHER_IN]));
        row.put("otherOut", round2(k[OTHER_OUT]));
        return row;
    }

    private List<Map<String, Object>> rowsOf(Map<String, double[]> source, boolean skipUnknown) {
        List<Map<String, Object>> rows = new ArrayList<>();
        source.forEach((key, acc) -> {
            if (skipUnknown && "未知".equals(key)) {
                return;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("month", key);
            putMoney(row, acc);
            rows.add(row);
        });
        return rows;
    }

    private List<Map<String, Object>> namedRows(Map<String, double[]> source, boolean asProject) {
        List<Map<String, Object>> rows = new ArrayList<>();
        source.forEach((name, acc) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", name);
            putMoney(row, acc);
            if (asProject) {
                row.put("sales", round2(acc[CONTRACT_IN]));
                row.put("purchase", round2(acc[CONTRACT_OUT]));
                row.put("profit", round2(acc[CONTRACT_IN] - acc[CONTRACT_OUT] + acc[OTHER_IN] - acc[OTHER_OUT]));
                row.put("cashNet", round2(acc[CASH_IN] - acc[CASH_OUT]));
            }
            rows.add(row);
        });
        rows.sort(Comparator.comparingDouble((Map<String, Object> row) ->
                Math.abs(((Number) row.get(asProject ? "profit" : "cashIn")).doubleValue())
                        + Math.abs(((Number) row.get(asProject ? "cashNet" : "cashOut")).doubleValue())).reversed());
        return rows.size() > 40 ? rows.subList(0, 40) : rows;
    }

    /** 轻流「公司账户-使用」：按公司+开户行+账号汇总本期现金，不读辅助流水。 */
    private List<Map<String, Object>> bankRows(List<Hit> hits, String companyQ) {
        Map<String, double[]> accs = new LinkedHashMap<>();
        Map<String, String[]> keys = new LinkedHashMap<>();
        seedCompanyBanks(accs, keys, companyQ);
        for (Hit hit : hits) {
            if (!isCashBucket(hit.bucket)) {
                continue;
            }
            String id = hit.company + "\u0001" + hit.bank + "\u0001" + hit.account;
            keys.putIfAbsent(id, new String[] {hit.company, hit.bank, hit.account});
            addBucket(accs.computeIfAbsent(id, key -> new double[12]), hit.bucket, hit.amount);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        keys.forEach((id, parts) -> {
            double[] acc = accs.getOrDefault(id, new double[12]);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("company", parts[0]);
            row.put("bank", parts[1]);
            row.put("account", parts[2]);
            row.put("cashIn", round2(acc[CASH_IN]));
            row.put("cashOut", round2(acc[CASH_OUT]));
            row.put("balance", round2(acc[CASH_IN] - acc[CASH_OUT]));
            rows.add(row);
        });
        rows.sort(Comparator.comparingDouble((Map<String, Object> row) ->
                Math.abs(((Number) row.get("balance")).doubleValue())).reversed());
        return rows;
    }

    private void seedCompanyBanks(Map<String, double[]> accs, Map<String, String[]> keys, String companyQ) {
        FormDefinition def = definitionRepository.findByCode("8ts5ue7tks01").orElse(null);
        if (def == null || !def.isEnabled() || !permissionService.currentAllowed("8ts5ue7tks01", "VIEW")) {
            return;
        }
        List<Map<String, Object>> fields = fieldsOf(def);
        String nameKey = findKey(fields, "公司名称");
        String tableKey = findKey(fields, "银行账户");
        for (FormDataRecord record : recordRepository.findAllByDefinitionIdOrderByIdDesc(def.getId())) {
            if ("CANCELLED".equals(record.getStatus())) {
                continue;
            }
            Map<String, Object> values = readJson(record.getValuesJson());
            String company = blankTo(labelOf(values.get(nameKey)), record.getTitle());
            if (!companyQ.isEmpty() && !normalize(company).contains(companyQ)) {
                continue;
            }
            Object raw = tableKey == null ? null : values.get(tableKey);
            if (!(raw instanceof List<?> list) || list.isEmpty()) {
                continue;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                String bank = blankTo(labelOf(row.get("q800000003")), "未填开户行");
                String account = blankTo(labelOf(row.get("q800000004")), "未填账号");
                String id = company + "\u0001" + bank + "\u0001" + account;
                keys.putIfAbsent(id, new String[] {company, bank, account});
                accs.putIfAbsent(id, new double[12]);
            }
        }
    }

    private static boolean isCashBucket(String bucket) {
        return switch (bucket) {
            case "cashIn", "cashOut", "otherIn", "otherOut", "depositIn", "depositOut", "prepaid" -> true;
            default -> false;
        };
    }

    private void putMoney(Map<String, Object> row, double[] acc) {
        row.put("cashIn", round2(acc[CASH_IN]));
        row.put("cashOut", round2(acc[CASH_OUT]));
        row.put("contractIn", round2(acc[CONTRACT_IN]));
        row.put("contractOut", round2(acc[CONTRACT_OUT]));
        row.put("invoice", round2(acc[INVOICE] - acc[INVOICE_VOID]));
        row.put("received", round2(acc[RECEIVED]));
        row.put("paid", round2(acc[PAID]));
        row.put("otherIn", round2(acc[OTHER_IN]));
        row.put("otherOut", round2(acc[OTHER_OUT]));
    }

    private record NamedAmt(String name, double amount) {}

    private List<NamedAmt> projectSplits(
            Map<String, Object> values,
            List<Map<String, Object>> fields,
            String lineAmountTitle,
            String projectKey,
            double headerAmount
    ) {
        List<NamedAmt> rows = new ArrayList<>();
        if (lineAmountTitle != null) {
            for (Map<String, Object> field : fields) {
                if (!"subtable".equals(String.valueOf(field.get("type")))) {
                    continue;
                }
                String amountCol = columnKey(field, lineAmountTitle);
                String projectCol = columnKey(field, "项目名称", "所属项目", "项目");
                Object raw = values.get(String.valueOf(field.get("key")));
                if (amountCol == null || !(raw instanceof List<?> list)) {
                    continue;
                }
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> row)) {
                        continue;
                    }
                    Double amt = asNumber(row.get(amountCol));
                    if (amt == null || amt == 0) {
                        continue;
                    }
                    String name = projectCol == null ? "" : labelOf(row.get(projectCol));
                    if (name.isBlank()) {
                        name = labelOf(values.get(projectKey));
                    }
                    rows.add(new NamedAmt(name, amt));
                }
            }
        }
        if (!rows.isEmpty()) {
            return rows;
        }
        rows.add(new NamedAmt(labelOf(values.get(projectKey)), headerAmount));
        return rows;
    }

    private double headerOrLines(Map<String, Object> values, String amountKey, List<Map<String, Object>> fields, String lineAmountTitle) {
        Double header = amountKey == null ? null : asNumber(values.get(amountKey));
        if (header != null && header != 0) {
            return header;
        }
        if (lineAmountTitle == null) {
            return header == null ? 0 : header;
        }
        double sum = 0;
        for (Map<String, Object> field : fields) {
            if (!"subtable".equals(String.valueOf(field.get("type")))) {
                continue;
            }
            String col = columnKey(field, lineAmountTitle);
            Object raw = values.get(String.valueOf(field.get("key")));
            if (col == null || !(raw instanceof List<?> list)) {
                continue;
            }
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    Double n = asNumber(row.get(col));
                    if (n != null) {
                        sum += n;
                    }
                }
            }
        }
        return sum;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fieldsOf(FormDefinition def) {
        Map<String, Object> design = readJson(def.getDesignJson());
        Object fields = design.get("fields");
        if (!(fields instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> field) {
                out.add((Map<String, Object>) field);
            }
        }
        return out;
    }

    private String findKey(List<Map<String, Object>> fields, String... titles) {
        if (titles == null) {
            return null;
        }
        for (String want : titles) {
            if (!StringUtils.hasText(want)) {
                continue;
            }
            for (Map<String, Object> field : fields) {
                if (want.equals(String.valueOf(field.getOrDefault("title", "")))) {
                    return String.valueOf(field.get("key"));
                }
            }
        }
        for (String want : titles) {
            if (!StringUtils.hasText(want)) {
                continue;
            }
            for (Map<String, Object> field : fields) {
                String title = String.valueOf(field.getOrDefault("title", ""));
                if (title.contains(want) && usableTitle(title)) {
                    return String.valueOf(field.get("key"));
                }
            }
        }
        return null;
    }

    private String columnKey(Map<String, Object> field, String... titles) {
        Object columns = field.get("columns");
        if (!(columns instanceof List<?> cols) || titles == null) {
            return null;
        }
        for (String want : titles) {
            if (!StringUtils.hasText(want)) {
                continue;
            }
            for (Object col : cols) {
                if (!(col instanceof Map<?, ?> column)) {
                    continue;
                }
                String title = column.get("title") == null ? "" : String.valueOf(column.get("title"));
                if (title.equals(want) || (title.contains(want) && usableTitle(title))) {
                    return String.valueOf(column.get("key"));
                }
            }
        }
        return null;
    }

    private boolean usableTitle(String title) {
        return !title.contains("历史") && !title.contains("剩余") && !title.contains("申请时间")
                && !title.contains("更新时间") && !title.contains("跟进");
    }

    private LocalDate asDate(Object value, LocalDateTime fallback) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return asDate(list.get(0), fallback);
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("value", "date", "label", "text")) {
                if (map.get(key) != null) {
                    LocalDate parsed = asDate(map.get(key), null);
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            if (raw > 10_000_000_000L) {
                return LocalDateTime.ofEpochSecond(raw / 1000, 0, java.time.ZoneOffset.ofHours(8)).toLocalDate();
            }
            if (raw > 10_000_000L) {
                return LocalDateTime.ofEpochSecond(raw, 0, java.time.ZoneOffset.ofHours(8)).toLocalDate();
            }
        }
        if (value != null) {
            String text = String.valueOf(value).trim();
            if (text.length() >= 10) {
                try {
                    return LocalDate.parse(text.substring(0, 10), ISO_DATE);
                } catch (DateTimeParseException ignored) {
                }
            }
            if (text.length() >= 19) {
                try {
                    return LocalDateTime.parse(text.substring(0, 19), ISO_DT).toLocalDate();
                } catch (DateTimeParseException ignored) {
                }
            }
        }
        return fallback == null ? null : fallback.toLocalDate();
    }

    private Double asNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            return asNumber(list.get(0));
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("value", "amount", "number")) {
                if (map.get(key) != null) {
                    return asNumber(map.get(key));
                }
            }
        }
        String text = String.valueOf(value).replace(",", "").replace("￥", "").replace("¥", "").trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String labelOf(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String label = labelOf(item);
                if (!label.isBlank()) {
                    return label;
                }
            }
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("label", "title", "name", "value", "text", "display")) {
                Object raw = map.get(key);
                if (raw != null && !(raw instanceof Map<?, ?>) && !(raw instanceof List<?>)) {
                    String label = String.valueOf(raw).trim();
                    if (!label.isEmpty() && !"null".equalsIgnoreCase(label)) {
                        return label;
                    }
                }
            }
        }
        String text = String.valueOf(value).trim();
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankTo(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
