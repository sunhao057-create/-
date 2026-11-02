# -*- coding: utf-8 -*-
"""把技术服务轻流表单转成动态表单种子，跳过辅助/隐藏字段。"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(r"D:\项目管理")
EXTRACT = ROOT / "tmp" / "qingflow-extract"
OUT = ROOT / "backend" / "src" / "main" / "resources" / "dynamic-seed" / "tech-forms.json"

CODES = [
    "beb22af6",
    "a4dc7e7c",
    "akbtu9cdsg02",
    "dgkte87b9g01",
    "689f58d1",
    "6fba7ahqqk02",
    "fe5aqq67uk01",
    "6tth2ob1lg01",
    "97hor9l95802",
    "97hs18o56o02",
    "97hujbgd6o01",
    "97hvvik16o02",
    "97i29pmh5801",
    "97i2ne695801",
    "97i2pmoh6o02",
    "97i2s1d55802",
    "97i2udrl5801",
]


def skipped(title: str, hidden: bool, que_id) -> bool:
    if hidden:
        return True
    text = title or ""
    if "辅助" in text or "隐藏" in text:
        return True
    try:
        return int(que_id) < 10
    except (TypeError, ValueError):
        return True


def field_type(que_type) -> str:
    return {
        2: "text",
        3: "textarea",
        4: "date",
        5: "member",
        6: "text",
        7: "text",
        8: "number",
        9: "text",
        10: "select",
        11: "checkbox",
        12: "select",
        13: "image",
        15: "text",
        16: "text",
        18: "subtable",
        19: "relation",
        24: "section",
        25: "relation",
    }.get(que_type, "text")


def apply_relation(field: dict, q: dict, relations: dict) -> None:
    rel = relations.get(q.get("queId")) or {}
    info = rel.get("displayedQueInfo") or {}
    if info.get("appKey"):
        field["refTarget"] = info.get("appKey")
    if info.get("queId") is not None:
        field["displayKey"] = f"q{info.get('queId')}"
    if info.get("queTitle"):
        field["displayTitle"] = info.get("queTitle")
    ref = q.get("referenceConfig") or {}
    if not field.get("refTarget") and ref.get("referAppKey"):
        field["refTarget"] = ref.get("referAppKey")
        field["displayTitle"] = field.get("displayTitle") or title_of_ref(ref)


def title_of_ref(ref: dict) -> str | None:
    ques = ref.get("referQuestions") or []
    if ques and isinstance(ques[0], dict):
        return ques[0].get("queTitle")
    return None


def convert_question(q: dict, relations: dict) -> dict | None:
    title = q.get("queTitle") or ""
    que_type = q.get("queType")
    if que_type != 24 and skipped(title, bool(q.get("beingHide")), q.get("queId")):
        return None
    kind = field_type(que_type)
    field = {
        "key": f"q{q.get('queId')}",
        "title": title,
        "type": kind,
        "required": bool(q.get("required")),
    }
    hint = q.get("queHint")
    if hint:
        field["hint"] = hint
    options = q.get("options") or []
    if kind in ("select", "checkbox", "radio") and options:
        field["options"] = [{"label": opt.get("optValue") or ""} for opt in options if opt.get("optValue")]
    if kind == "relation":
        apply_relation(field, q, relations)
    if kind == "subtable":
        columns = []
        for col in q.get("subQuestions") or []:
            converted = convert_question(col, relations)
            if converted and converted["type"] not in ("subtable", "section"):
                columns.append(converted)
        field["columns"] = columns
    return field


def collect_fields(nodes, relations: dict, fields: list) -> None:
    for node in nodes or []:
        if isinstance(node, list):
            collect_fields(node, relations, fields)
            continue
        if not isinstance(node, dict):
            continue
        converted = convert_question(node, relations)
        if converted:
            fields.append(converted)
        if node.get("queType") == 24:
            collect_fields(node.get("innerQuestions"), relations, fields)


def convert_form(code: str) -> dict | None:
    path = EXTRACT / f"form_{code}.json"
    if not path.exists():
        return None
    raw = json.loads(path.read_text(encoding="utf-8"))
    data = raw.get("data") or raw
    relations = {}
    for rel in data.get("questionRelations") or []:
        relations[rel.get("queId")] = rel
    fields: list[dict] = []
    collect_fields(data.get("formQues") or [], relations, fields)
    title = data.get("formTitle") or code
    return {
        "code": code,
        "title": title,
        "packageCode": "TECH",
        "design": {"fields": fields},
    }


def main() -> None:
    items = []
    for code in CODES:
        item = convert_form(code)
        if item:
            items.append(item)
            print(code, item["title"], len(item["design"]["fields"]))
    OUT.write_text(json.dumps({"items": items}, ensure_ascii=False), encoding="utf-8")
    print("wrote", OUT, "forms", len(items))


if __name__ == "__main__":
    main()
