# -*- coding: utf-8 -*-
"""Dump Qingflow option-link / questionRelation visibility into slim JSON."""
from __future__ import annotations

import json
import sys
from pathlib import Path

SITE = Path(r"C:\Users\15675\AppData\Roaming\npm\node_modules\@qingflow-tech\qingflow-cli\.npm-python\Lib\site-packages")
sys.path.insert(0, str(SITE))

from qingflow_mcp.cli.context import build_cli_context  # noqa: E402
from qingflow_mcp.config import DEFAULT_PROFILE  # noqa: E402

ROOT = Path(r"D:\项目管理")
OUT = ROOT / "tmp" / "qingflow-tech" / "visibility"
KEYS = [
    "beb22af6",
    "a4dc7e7c",
    "dgkte87b9g01",
    "akbtu9cdsg02",
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
    "f236e3f6",
    "1102d97d",
    "b49de9a7",
    "4a0fdb7b",
    "62a78914",
    "6ar55akg6k02",
    "d7kprf0g1c02",
    "0ddf6092",
    "dgbubsun4802",
    "6dlu39l9ro02",
    "5v9n82h7fk01",
]


def walk_questions(node, acc: list) -> None:
    if isinstance(node, list):
        for item in node:
            walk_questions(item, acc)
        return
    if not isinstance(node, dict):
        return
    if node.get("queId"):
        acc.append(node)
    for key in ("formQues", "baseQues", "innerQuestions", "subQuestions", "questions"):
        walk_questions(node.get(key), acc)


def option_text(option: dict) -> str:
    for key in ("optValue", "value", "name", "label"):
        text = option.get(key)
        if text is not None and str(text).strip():
            return str(text).strip()
    return ""


def extract(raw: dict) -> dict:
    questions: list = []
    walk_questions(raw, questions)
    option_links = []
    seen = set()
    for question in questions:
        source_id = question.get("queId")
        options = question.get("options")
        if not isinstance(options, list):
            continue
        for option in options:
            if not isinstance(option, dict):
                continue
            links = option.get("linkQueIds")
            if not isinstance(links, list) or not links:
                continue
            text = option_text(option)
            for target in links:
                row = (source_id, text, target)
                if row in seen:
                    continue
                seen.add(row)
                option_links.append({"source": source_id, "value": text, "target": target})
    relations = raw.get("questionRelations") if isinstance(raw.get("questionRelations"), list) else []
    return {
        "option_links": option_links,
        "question_relations": relations,
        "question_count": len(questions),
        "relation_count": len(relations),
        "option_link_count": len(option_links),
    }


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    ctx = build_cli_context()
    try:
        for key in KEYS:
            dest = OUT / f"{key}.visibility.json"
            print(f"dump {key}", flush=True)
            try:
                payload = ctx.app.app_get_form_schema(
                    profile=DEFAULT_PROFILE,
                    app_key=key,
                    form_type=2,
                    being_draft=None,
                    being_apply=True,
                    audit_node_id=None,
                    include_raw=True,
                )
            except Exception as ex:
                dest.write_text(json.dumps({"status": "error", "error": str(ex)}, ensure_ascii=False, indent=2), encoding="utf-8")
                print(f"  FAILED {ex}", flush=True)
                continue
            raw = payload.get("result") if isinstance(payload, dict) else {}
            if not isinstance(raw, dict):
                raw = {}
            slim = extract(raw)
            slim["status"] = "success"
            slim["app_key"] = key
            dest.write_text(json.dumps(slim, ensure_ascii=False, indent=2), encoding="utf-8")
            print(
                f"  questions={slim['question_count']} relations={slim['relation_count']} option_links={slim['option_link_count']}",
                flush=True,
            )
    finally:
        ctx.close()


if __name__ == "__main__":
    main()
