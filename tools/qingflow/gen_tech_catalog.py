# -*- coding: utf-8 -*-
"""Build tech service form catalog from Qingflow field dumps."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(r"D:\项目管理")
SRC = ROOT / "tmp" / "qingflow-tech"
OUT_JSON = ROOT / "backend" / "src" / "main" / "resources" / "tech" / "catalog.json"
OUT_TS = ROOT / "frontend" / "src" / "tech" / "catalog.ts"
OUT_KIND = ROOT / "backend" / "src" / "main" / "java" / "com" / "projectmgmt" / "tech" / "TechKind.java"

SKIP_TYPES = {
    "static",
    "static_text",
    "description",
    "html",
    "section",
    "divider",
    "quote",
    "button",
}

TYPE_MAP = {
    "text": "text",
    "textarea": "textarea",
    "long_text": "textarea",
    "number": "number",
    "amount": "number",
    "phone": "text",
    "email": "text",
    "url": "text",
    "id_card": "text",
    "datetime": "datetime",
    "date": "datetime",
    "time": "text",
    "boolean": "boolean",
    "attachment": "attachment",
    "image": "attachment",
    "signature": "text",
    "member": "member",
    "department": "text",
    "single_select": "select",
    "multi_select": "multiselect",
    "select": "select",
    "subtable": "subtable",
    "relation": "text",
    "address": "text",
    "location": "text",
    "formula": "text",
    "lookup": "text",
    "serial_number": "text",
}

KINDS = [
    ("beb22af6", "CUSTOMER_INSTRUMENT", "customer-instruments", "客户仪器信息表", "CI", "customer", "客户信息"),
    ("a4dc7e7c", "WECHAT_CONTACTS", "wechat-contacts", "客户微信资料库", "WC", "customer", "客户信息"),
    ("dgkte87b9g01", "REPAIR_TRACK", "repair-track", "客户仪器送修信息追踪卡", "RT", "repair", "维修安装培训记录"),
    ("akbtu9cdsg02", "TICKET_POOL", "ticket-pool", "客户报修工单池", "TP", "repair", "维修安装培训记录"),
    ("689f58d1", "SERVICE_REPORT", "service-report", "技术服务报告单", "SR", "repair", "维修安装培训记录"),
    ("6fba7ahqqk02", "MULTI_REPORT", "multi-report", "多次技术服务报告单", "MR", "repair", "维修安装培训记录"),
    ("fe5aqq67uk01", "MOVE_CARD", "move-card", "设备搬迁记录卡", "MV", "move", "搬迁服务记录"),
    ("6tth2ob1lg01", "MAINT_BASIC", "maint-basic", "基础设备维保记录卡", "MB", "maint", "维保服务记录"),
    ("97hor9l95802", "MAINT_LCMS", "maint-lcms", "液质联用仪-设备维保记录卡", "ML", "maint", "维保服务记录"),
    ("97hs18o56o02", "MAINT_ICPMS", "maint-icpms", "ICP-MS-设备维保记录卡", "MM", "maint", "维保服务记录"),
    ("97hujbgd6o01", "MAINT_ICP", "maint-icp", "ICP-设备维保记录卡", "MI", "maint", "维保服务记录"),
    ("97hvvik16o02", "MAINT_AAS", "maint-aas", "原子吸收光谱仪-设备维保记录卡", "MA", "maint", "维保服务记录"),
    ("97i29pmh5801", "MAINT_LC", "maint-lc", "液相色谱仪-设备维保记录卡", "LC", "maint", "维保服务记录"),
    ("97i2ne695801", "MAINT_GC", "maint-gc", "气相色谱仪-设备维保记录卡", "GC", "maint", "维保服务记录"),
    ("97i2pmoh6o02", "MAINT_GCMS", "maint-gcms", "气相色谱质谱仪-设备维保记录卡", "GM", "maint", "维保服务记录"),
    ("97i2s1d55802", "MAINT_AFS", "maint-afs", "原子荧光光度计-设备维保记录卡", "AF", "maint", "维保服务记录"),
    ("97i2udrl5801", "MAINT_UV", "maint-uv", "紫外可见分光光度计-设备维保记录卡", "UV", "maint", "维保服务记录"),
    ("f236e3f6", "MAINT_LEGACY", "maint-legacy", "设备维保记录卡(隐藏旧版）", "LG", "maint", "维保服务记录"),
    ("1102d97d", "SPECIAL_CONFIRM", "special-confirm", "特殊仪器确认表", "SC", "other", "其他资料"),
    ("b49de9a7", "REPAIR_CARD", "repair-card", "设备维修记录卡", "RC", "other", "其他资料"),
    ("4a0fdb7b", "INSTRUMENT_LIB", "instrument-lib", "仪器信息资料库", "IL", "other", "其他资料"),
    ("62a78914", "WEIGHT_CALIB", "weight-calib", "承重类维护后校准记录表", "WZ", "other", "其他资料"),
    ("6ar55akg6k02", "PM_PROJECT", "pm-projects", "蜜蜂e工-搬迁/维保项目管理", "PP", "pm-project", "项目管理"),
    ("d7kprf0g1c02", "PM_STATUS", "pm-status", "蜜蜂e工-搬迁/维保项目状态变更", "ST", "pm-project", "项目管理"),
    ("0ddf6092", "PM_CHECKIN", "pm-checkin", "搬迁/维保签到表", "CK", "pm-site", "现场记录"),
    ("dgbubsun4802", "PM_MAINT_DAILY", "pm-maint-daily", "维保项目日报", "MD", "pm-site", "现场记录"),
    ("6dlu39l9ro02", "PM_MOVE_DAILY", "pm-move-daily", "搬迁项目日报", "VD", "pm-site", "现场记录"),
    ("5v9n82h7fk01", "PM_MAKEUP", "pm-makeup", "搬迁补签表（隔日补签）", "MU", "pm-site", "现场记录"),
]


def map_type(raw: str) -> str:
    return TYPE_MAP.get((raw or "").strip().lower(), "text")


def options_of(item: dict) -> list[str]:
    raw = item.get("options") or item.get("choices") or []
    out: list[str] = []
    if not isinstance(raw, list):
        return out
    for opt in raw:
        if isinstance(opt, str) and opt.strip():
            out.append(opt.strip())
        elif isinstance(opt, dict):
            name = opt.get("name") or opt.get("label") or opt.get("value")
            if name:
                out.append(str(name).strip())
    return out


def que_id_of(item: dict) -> str | None:
    if item.get("que_id") is not None:
        return str(item.get("que_id"))
    fid = item.get("field_id")
    if fid is None:
        return None
    text = str(fid)
    return text.replace("field_", "") if text.startswith("field_") else text


def load_json(path: Path) -> dict:
    if not path.exists():
        return {}
    raw = path.read_bytes()
    data = None
    for encoding in ("utf-8", "utf-8-sig", "gb18030"):
        try:
            data = json.loads(raw.decode(encoding))
            break
        except (UnicodeDecodeError, json.JSONDecodeError):
            continue
    return data if isinstance(data, dict) else {}


def overlay_maps(app_key: str) -> tuple[dict[str, dict], dict[str, dict], bool]:
    browse = load_json(SRC / "browse" / f"{app_key}.browse.json")
    insert = load_json(SRC / "insert" / f"{app_key}.insert.json")
    by_browse: dict[str, dict] = {}
    for item in browse.get("fields") or []:
        qid = que_id_of(item)
        if qid:
            by_browse[qid] = item
    by_insert: dict[str, dict] = {}
    has_insert = insert.get("status") == "success" or bool(insert.get("required_fields") or insert.get("optional_fields"))
    if has_insert:
        for item in (insert.get("required_fields") or []) + (insert.get("optional_fields") or []):
            qid = que_id_of(item)
            if qid:
                by_insert[qid] = item
    return by_browse, by_insert, bool(by_insert)


def convert_field(item: dict, browse: dict[str, dict], insert: dict[str, dict], has_insert: bool) -> dict | None:
    name = (item.get("name") or "").strip()
    ftype = (item.get("type") or "").strip().lower()
    if not name or ftype in SKIP_TYPES:
        return None
    qid = que_id_of(item)
    key = item.get("field_id") or (f"q{qid}" if qid else None)
    if not key:
        return None
    mapped = map_type(ftype)
    extra = {}
    if qid and qid in browse:
        extra = browse[qid]
    ins = insert.get(qid) if qid else None
    if ins:
        mapped = map_type(ins.get("kind") or mapped)
        required = bool(ins.get("required"))
        applicant = True
        opts = options_of(ins) or options_of(extra) or options_of(item)
    else:
        required = bool(item.get("required"))
        applicant = not has_insert
        opts = options_of(extra) or options_of(item)
        kind = (extra.get("kind") or "").lower()
        if kind in TYPE_MAP and mapped in {"boolean", "text"}:
            mapped = map_type(kind)
        if kind == "attachment":
            mapped = "attachment"
        if kind == "member":
            mapped = "member"
    if mapped == "boolean" and (opts or name.startswith("是否")):
        mapped = "select"
        if not opts:
            opts = ["是", "否"]
    if mapped == "select" and extra.get("kind") == "select" and ftype == "multi_select":
        mapped = "multiselect"
    field: dict = {
        "key": str(key),
        "name": name,
        "type": mapped,
        "required": required,
        "applicant": applicant,
    }
    if opts:
        field["options"] = opts
    if ins and ins.get("may_become_required"):
        field["requiredWhenVisible"] = True
    if mapped == "subtable":
        sub = []
        row_by_title = {}
        source = ins or extra
        for child in source.get("row_fields") or []:
            title = (child.get("title") or child.get("name") or "").strip()
            if title:
                row_by_title[title] = child
        for child in item.get("subfields") or []:
            converted = convert_field(child, {}, {}, False)
            if not converted:
                continue
            hint = row_by_title.get(converted["name"])
            if hint:
                converted["type"] = map_type(hint.get("kind") or converted["type"])
                converted["required"] = bool(hint.get("required", converted["required"]))
                child_opts = options_of(hint)
                if child_opts:
                    converted["options"] = child_opts
            converted["applicant"] = True
            sub.append(converted)
        field["subfields"] = sub
    if "section" not in field:
        field["section"] = ""
    if "span" not in field:
        field["span"] = 24
    return field


def que_id_from_key(key: str) -> str | None:
    text = str(key or "")
    if text.startswith("field_"):
        return text[6:]
    if text.startswith("q"):
        return text[1:]
    return text or None


def load_option_links(app_key: str) -> dict[str, dict[str, list[str]]]:
    data = load_json(SRC / "visibility" / f"{app_key}.visibility.json")
    by_target: dict[str, dict[str, list[str]]] = {}
    for link in data.get("option_links") or []:
        target = str(link.get("target") or "").strip()
        source = str(link.get("source") or "").strip()
        value = str(link.get("value") or "").strip()
        if not target or not source or not value:
            continue
        by_source = by_target.setdefault(target, {})
        values = by_source.setdefault(source, [])
        if value not in values:
            values.append(value)
    return by_target


def walk_fields(fields: list[dict]):
    for field in fields:
        yield field
        yield from walk_fields(field.get("subfields") or [])


def apply_visibility(app_key: str, fields: list[dict]) -> list[dict]:
    links = load_option_links(app_key)
    if not links:
        return fields
    known = {que_id_from_key(field["key"]) for field in walk_fields(fields)}
    known.discard(None)
    for field in walk_fields(fields):
        qid = que_id_from_key(field["key"])
        by_source = links.get(qid or "")
        if not by_source:
            continue
        groups = []
        for source, values in by_source.items():
            if source not in known:
                continue
            groups.append({"all": [{"field": f"field_{source}", "op": "in", "values": values}]})
        if groups:
            field["visibleWhen"] = {"any": groups}
    return fields


def apply_layout(app_key: str, fields: list[dict]) -> list[dict]:
    wide = {"subtable", "attachment", "textarea", "multiselect"}
    layout = load_json(SRC / "layout" / f"{app_key}.layout.json")
    queues: dict[str, list[dict]] = {}
    for field in fields:
        queues.setdefault(field["name"], []).append(field)
    ordered: list[dict] = []
    seen: set[str] = set()

    def take(name: str, section: str, span: int) -> None:
        queue = queues.get(name) or []
        if not queue:
            return
        field = queue.pop(0)
        if field["key"] in seen:
            return
        seen.add(field["key"])
        field["section"] = section
        field["span"] = 24 if field["type"] in wide else span
        ordered.append(field)

    for section in layout.get("sections") or []:
        title = (section.get("title") or "").strip() or "表单信息"
        for row in section.get("rows") or []:
            names = [str(name) for name in row if name]
            count = max(len(names), 1)
            span = 8 if count >= 3 else (12 if count == 2 else 24)
            for name in names:
                take(name, title, span)

    leftover = [field for field in fields if field["key"] not in seen]
    packed: list[dict] = []
    index = 0
    lead = "其他信息" if ordered else "表单信息"
    while index < len(leftover):
        field = leftover[index]
        field["section"] = lead
        if field["type"] in wide:
            field["span"] = 24
            packed.append(field)
            index += 1
            continue
        nxt = leftover[index + 1] if index + 1 < len(leftover) else None
        if nxt and nxt["type"] not in wide:
            field["span"] = 12
            nxt["section"] = lead
            nxt["span"] = 12
            packed.append(field)
            packed.append(nxt)
            index += 2
        else:
            field["span"] = 12
            packed.append(field)
            index += 1
    return ordered + packed


def disambiguate_field_names(fields: list[dict]) -> list[dict]:
    for field in fields:
        if field.get("subfields"):
            disambiguate_field_names(field["subfields"])
        if field.get("type") != "attachment":
            continue
        name = str(field.get("name") or "")
        if re.search(r"照片|附件|图片", name):
            continue
        clash = False
        for other in fields:
            if other.get("key") == field.get("key") or other.get("type") == "attachment":
                continue
            other_name = str(other.get("name") or "")
            if other_name == name or other_name.endswith(name) or name.endswith(other_name):
                clash = True
                break
        if clash:
            field["name"] = f"{name}（照片）"
    return fields


def load_fields(app_key: str) -> list[dict]:
    path = SRC / f"{app_key}.fields.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    browse, insert, has_insert = overlay_maps(app_key)
    fields = []
    for item in data.get("fields") or []:
        converted = convert_field(item, browse, insert, has_insert)
        if converted:
            fields.append(converted)
    return disambiguate_field_names(apply_visibility(app_key, apply_layout(app_key, fields)))


SYSTEM_LIST_COLUMNS = {
    "编号": {"key": "code", "name": "编号", "source": "record"},
    "当前流程状态": {"key": "workflowStatusLabel", "name": "流程状态", "source": "record"},
    "申请人": {"key": "creatorName", "name": "填报人", "source": "record"},
    "申请时间": {"key": "createdAt", "name": "申请时间", "source": "record"},
}


def attach_list_columns(app_key: str, fields: list[dict]) -> list[dict]:
    views = load_json(SRC / "views" / f"{app_key}.json")
    titles = None
    for view in views.get("views") or []:
        cols = view.get("display_columns") or view.get("columns")
        if not cols:
            continue
        name = str(view.get("name") or "")
        if titles is None or "全部" in name:
            titles = cols
            if "全部" in name:
                break
    if not titles:
        return []
    by_name = {}
    for field in fields:
        by_name.setdefault(field["name"], field)
    out = []
    seen = set()
    for title in titles:
        name = str(title).strip()
        if not name or name in {"更新时间", "当前节点"}:
            continue
        if name in SYSTEM_LIST_COLUMNS:
            col = SYSTEM_LIST_COLUMNS[name]
        elif name in by_name and by_name[name]["type"] not in {"attachment", "subtable"}:
            col = {"key": by_name[name]["key"], "name": name, "source": "extra"}
        else:
            continue
        if col["key"] in seen:
            continue
        seen.add(col["key"])
        out.append(col)
        if len(out) >= 20:
            break
    return out


def repair_text(text: str) -> str:
    raw = (text or "").strip()
    if not raw:
        return raw
    try:
        fixed = raw.encode("latin-1").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return raw
    if any("\u4e00" <= ch <= "\u9fff" for ch in fixed):
        return fixed.strip()
    return raw


def compact_flow(app_key: str, fields: list[dict]) -> list[dict]:
    data = load_json(SRC / "flows" / f"{app_key}.json")
    spec = data.get("spec") or {}
    nodes = spec.get("nodes") or []
    edges = ((spec.get("edges") or {}).get("edges")) or []
    by_id = {str(node.get("id")): node for node in nodes if node.get("id")}
    incoming = {str(edge.get("to")) for edge in edges}
    start_ids = [nid for nid, node in by_id.items() if nid not in incoming]
    if not start_ids:
        start_ids = [str(node.get("id")) for node in nodes[:1] if node.get("id")]
    nxt = {}
    for edge in edges:
        nxt.setdefault(str(edge.get("from")), []).append(str(edge.get("to")))
    field_by_qid = {field["key"].replace("field_", ""): field["key"] for field in fields if str(field.get("key", "")).startswith("field_")}
    walked = []
    seen = set()
    queue = list(start_ids)
    while queue:
        nid = queue.pop(0)
        if nid in seen:
            continue
        seen.add(nid)
        node = by_id.get(nid) or {}
        ntype = str(node.get("type") or "").lower()
        if ntype in {"applicant", "approval", "cc"}:
            step = {
                "key": "fill" if ntype == "applicant" and not any(item["type"] == "applicant" for item in walked) else f"n{len(walked)+1}",
                "name": repair_text(node.get("name") or ("申请人" if ntype == "applicant" else "审批")),
                "type": ntype,
            }
            people = (node.get("attrs") or {}).get("responsible") or (node.get("attrs") or {}).get("receivers") or []
            for person in people:
                qid = person.get("queId") or person.get("que_id")
                if qid is not None and str(qid) in field_by_qid:
                    step["assigneeField"] = field_by_qid[str(qid)]
                    break
            walked.append(step)
        for child in nxt.get(nid) or []:
            queue.append(child)
    return walked


def main() -> None:
    catalog = []
    flows = {}
    for app_key, enum_name, slug, label, prefix, group, group_label in KINDS:
        fields = load_fields(app_key)
        item = {
            "appKey": app_key,
            "enumName": enum_name,
            "module": f"TECH_{enum_name}",
            "slug": slug,
            "label": label,
            "prefix": prefix,
            "group": group,
            "groupLabel": group_label,
            "fields": fields,
        }
        columns = attach_list_columns(app_key, fields)
        if columns:
            item["listColumns"] = columns
        catalog.append(item)
        steps = compact_flow(app_key, fields)
        if steps:
            flows[enum_name] = steps
            if all(step.get("type") == "applicant" for step in steps):
                def mark_applicant(rows: list[dict]) -> None:
                    for field in rows:
                        field["applicant"] = True
                        mark_applicant(field.get("subfields") or [])

                mark_applicant(fields)

    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT_JSON.parent / "flows.json").write_text(json.dumps(flows, ensure_ascii=False, indent=2), encoding="utf-8")

    OUT_TS.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(catalog, ensure_ascii=False, indent=2)
    OUT_TS.write_text(
        "import type { FormModule } from '../types'\n\n"
        "export type TechFieldType =\n"
        "  | 'text'\n"
        "  | 'textarea'\n"
        "  | 'number'\n"
        "  | 'datetime'\n"
        "  | 'boolean'\n"
        "  | 'select'\n"
        "  | 'multiselect'\n"
        "  | 'member'\n"
        "  | 'attachment'\n"
        "  | 'subtable'\n\n"
        "export type TechVisibleRule = {\n"
        "  field: string\n"
        "  op: string\n"
        "  values?: string[]\n"
        "}\n\n"
        "export type TechVisibleWhen = {\n"
        "  any: Array<{ all: TechVisibleRule[] }>\n"
        "}\n\n"
        "export type TechField = {\n"
        "  key: string\n"
        "  name: string\n"
        "  type: TechFieldType | string\n"
        "  required: boolean\n"
        "  applicant?: boolean\n"
        "  section?: string\n"
        "  span?: number\n"
        "  options?: string[]\n"
        "  visibleWhen?: TechVisibleWhen\n"
        "  requiredWhenVisible?: boolean\n"
        "  subfields?: TechField[]\n"
        "}\n\n"
        "export type TechListColumn = {\n"
        "  key: string\n"
        "  name: string\n"
        "  source?: 'record' | 'extra'\n"
        "}\n\n"
        "export type TechKindMeta = {\n"
        "  appKey: string\n"
        "  enumName: string\n"
        "  module: FormModule\n"
        "  slug: string\n"
        "  label: string\n"
        "  prefix: string\n"
        "  group: string\n"
        "  groupLabel: string\n"
        "  listColumns?: TechListColumn[]\n"
        "  fields: TechField[]\n"
        "}\n\n"
        f"export const TECH_KINDS: TechKindMeta[] = {payload}\n\n"
        "export const TECH_GROUPS = [\n"
        "  { key: 'customer', label: '客户信息' },\n"
        "  { key: 'repair', label: '维修安装培训记录' },\n"
        "  { key: 'move', label: '搬迁服务记录' },\n"
        "  { key: 'maint', label: '维保服务记录' },\n"
        "  { key: 'other', label: '其他资料' },\n"
        "] as const\n\n"
        "export const TECH_PM_GROUPS = [\n"
        "  { key: 'pm-project', label: '项目管理' },\n"
        "  { key: 'pm-site', label: '现场记录' },\n"
        "] as const\n\n"
        "export function techKindBySlug(slug?: string | null) {\n"
        "  if (!slug) return undefined\n"
        "  return TECH_KINDS.find((item) => item.slug === slug)\n"
        "}\n\n"
        "export function techKindByModule(module?: string | null) {\n"
        "  if (!module) return undefined\n"
        "  return TECH_KINDS.find((item) => item.module === module)\n"
        "}\n\n"
        "export function techKindsInGroup(group: string) {\n"
        "  return TECH_KINDS.filter((item) => item.group === group)\n"
        "}\n",
        encoding="utf-8",
    )

    lines = [
        "package com.projectmgmt.tech;",
        "",
        "import com.projectmgmt.common.ApiException;",
        "import com.projectmgmt.role.FormModule;",
        "",
        "public enum TechKind {",
    ]
    for i, item in enumerate(catalog):
        comma = "," if i < len(catalog) - 1 else ";"
        lines.append(
            f'    {item["enumName"]}("{item["slug"]}", "{item["label"]}", FormModule.{item["module"]}, "{item["prefix"]}"){comma}'
        )
    lines += [
        "",
        "    private final String slug;",
        "    private final String label;",
        "    private final FormModule module;",
        "    private final String codePrefix;",
        "",
        "    TechKind(String slug, String label, FormModule module, String codePrefix) {",
        "        this.slug = slug;",
        "        this.label = label;",
        "        this.module = module;",
        "        this.codePrefix = codePrefix;",
        "    }",
        "",
        "    public String slug() { return slug; }",
        "    public String label() { return label; }",
        "    public FormModule module() { return module; }",
        "    public String codePrefix() { return codePrefix; }",
        "    public String adminPath() { return \"/tech/\" + slug; }",
        "    public String publicPath() { return \"/public/tech/\" + slug; }",
        "",
        "    public static TechKind fromSlug(String slug) {",
        "        if (slug == null || slug.isBlank()) {",
        "            throw ApiException.badRequest(\"请选择技术服务表单\");",
        "        }",
        "        for (TechKind kind : values()) {",
        "            if (kind.slug.equalsIgnoreCase(slug.trim())) {",
        "                return kind;",
        "            }",
        "        }",
        "        throw ApiException.notFound(\"技术服务表单不存在\");",
        "    }",
        "",
        "    public static TechKind of(FormModule module) {",
        "        TechKind kind = fromModule(module);",
        "        if (kind == null) {",
        "            throw ApiException.badRequest(\"不是技术服务模块\");",
        "        }",
        "        return kind;",
        "    }",
        "",
        "    public static TechKind fromModule(FormModule module) {",
        "        if (module == null) {",
        "            return null;",
        "        }",
        "        for (TechKind kind : values()) {",
        "            if (kind.module == module) {",
        "                return kind;",
        "            }",
        "        }",
        "        return null;",
        "    }",
        "}",
        "",
    ]
    OUT_KIND.parent.mkdir(parents=True, exist_ok=True)
    OUT_KIND.write_text("\n".join(lines), encoding="utf-8")
    print(f"kinds={len(catalog)} json={OUT_JSON} ts={OUT_TS}")


if __name__ == "__main__":
    main()
