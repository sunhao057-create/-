# -*- coding: utf-8 -*-
"""把本地轻流单据写入动态表单。已通过的记为办结，不启动流程。"""
from __future__ import annotations

import json
import ssl
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(r"D:\项目管理")
GETS = ROOT / "tmp" / "qingflow-tech" / "migrate" / "gets"
DESIGN = ROOT / "backend" / "src" / "main" / "resources" / "dynamic-seed" / "tech-forms.json"
PROGRESS = ROOT / "tmp" / "qingflow-tech" / "migrate" / "dynamic-progress.json"
BASE = "http://101.132.181.18:8098"
BATCH = 30

KEEP = {
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
}


def load_designs() -> dict:
    doc = json.loads(DESIGN.read_text(encoding="utf-8"))
    designs = {}
    for item in doc["items"]:
        by_key = {}
        for field in item["design"]["fields"]:
            by_key[field["key"]] = field
        designs[item["code"]] = by_key
    return designs


def scalar(value, kind: str):
    if value is None:
        return "" if kind != "checkbox" else []
    if isinstance(value, list):
        parts = []
        for item in value:
            if isinstance(item, dict):
                text = item.get("display") or item.get("name") or item.get("fileName") or item.get("value") or ""
                if text:
                    parts.append(str(text))
            elif item not in (None, ""):
                parts.append(item if kind == "checkbox" else str(item))
        if kind == "checkbox":
            return parts
        if len(parts) <= 1:
            return parts[0] if parts else ""
        return "、".join(str(part) for part in parts)
    if isinstance(value, dict):
        return value.get("display_value") or value.get("value") or value.get("name") or ""
    if kind == "number":
        try:
            return float(value) if "." in str(value) else int(value)
        except (TypeError, ValueError):
            return value
    return value


def map_record(doc: dict, fields: dict) -> dict:
    record = doc.get("record") or {}
    values = {}
    for field in doc.get("fields") or []:
        key = f"q{field.get('field_id')}"
        design = fields.get(key)
        if not design:
            continue
        kind = design.get("type")
        raw = field.get("value")
        if kind == "subtable":
            columns = {col["title"]: col for col in design.get("columns") or []}
            rows = []
            if isinstance(raw, list):
                for line in raw:
                    if not isinstance(line, dict):
                        continue
                    row = {}
                    for title, cell in line.items():
                        column = columns.get(title)
                        if not column:
                            continue
                        row[column["key"]] = scalar(cell, column.get("type") or "text")
                    if any(cell not in ("", None, []) for cell in row.values()):
                        rows.append(row)
            values[key] = rows
        else:
            values[key] = scalar(raw, kind or "text")
    status_text = str(record.get("workflow_status") or "")
    if any(word in status_text for word in ("拒绝", "取消", "撤回")):
        status = "CANCELLED"
    elif "草稿" in status_text:
        status = "DRAFT"
    else:
        status = "DONE"
    return {
        "sourceKey": str(record.get("record_id") or record.get("apply_id") or ""),
        "title": record.get("title") or "",
        "serialNo": record.get("serial_number") or "",
        "status": status,
        "createdAt": record.get("apply_time") or "",
        "values": values,
    }


def post(token: str, code: str, items: list) -> dict:
    body = json.dumps({"items": items}, ensure_ascii=False).encode("utf-8")
    last = None
    for attempt in range(1, 4):
        req = urllib.request.Request(
            f"{BASE}/api/dynamic/definitions/{code}/import-batch",
            data=body,
            headers={"Content-Type": "application/json", "X-Pm-Import-Token": token},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=180, context=ssl.create_default_context()) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            return payload.get("data") or payload
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as ex:
            last = ex
            print(f"retry {attempt} {code} {ex}", flush=True)
    raise last


def main() -> None:
    token_file = Path(r"D:\项目管理\tmp\import.token")
    token = token_file.read_text(encoding="utf-8").strip()
    designs = load_designs()
    done = set()
    if PROGRESS.exists():
        done = set(json.loads(PROGRESS.read_text(encoding="utf-8")))
    summary = {}
    for code in sorted(KEEP):
        folder = GETS / code
        if not folder.is_dir() or code not in designs:
            continue
        pending = []
        paths = sorted(folder.glob("*.json"))
        for path in paths:
            marker = f"{code}/{path.stem}"
            if marker in done:
                continue
            pending.append((marker, path))
        created = existed = failed = 0
        batch = []
        markers = []

        def flush() -> None:
            nonlocal created, existed, failed, batch, markers
            if not batch:
                return
            result = post(token, code, batch)
            created += int(result.get("created") or 0)
            existed += int(result.get("existed") or 0)
            failed += int(result.get("failed") or 0)
            if result.get("errors"):
                print(code, result["errors"][:2], flush=True)
            done.update(markers)
            PROGRESS.write_text(json.dumps(sorted(done), ensure_ascii=False), encoding="utf-8")
            batch = []
            markers = []

        for marker, path in pending:
            doc = json.loads(path.read_text(encoding="utf-8"))
            item = map_record(doc, designs[code])
            if not item["sourceKey"]:
                done.add(marker)
                continue
            batch.append(item)
            markers.append(marker)
            if len(batch) >= BATCH:
                flush()
                print(code, "created", created, "existed", existed, "failed", failed, flush=True)
        flush()
        summary[code] = {"files": len(paths), "created": created, "existed": existed, "failed": failed}
        print("DONE", code, summary[code], flush=True)
    Path(ROOT / "tmp" / "dynamic-migrate-summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )


if __name__ == "__main__":
    main()
