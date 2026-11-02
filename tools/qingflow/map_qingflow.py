# -*- coding: utf-8 -*-
"""Map Qingflow record_get / access values onto local tech catalog extra keys."""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

ROOT = Path(r"D:\项目管理")
CATALOG_PATH = ROOT / "backend" / "src" / "main" / "resources" / "tech" / "catalog.json"

APPROVED_STATUSES = {"已通过", "已完成", "通过", "完成"}


def load_catalog() -> list[dict]:
    return json.loads(CATALOG_PATH.read_text(encoding="utf-8"))


def kind_by_app_key(app_key: str, catalog: list[dict] | None = None) -> dict | None:
    items = catalog if catalog is not None else load_catalog()
    for item in items:
        if item.get("appKey") == app_key:
            return item
    return None


def field_key(field_id: Any) -> str:
    text = str(field_id).strip()
    if text.startswith("field_"):
        return text
    return f"field_{text}"


def clean_asset_name(name: str) -> str:
    return re.sub(r"@\d+w_[^.]*(\.[A-Za-z0-9]+)$", r"\1", name or "")


def approved(status: Any) -> bool:
    if status is None:
        return False
    if isinstance(status, list):
        return any(approved(item) for item in status)
    text = str(status).strip().strip("[]\"'")
    if text.startswith("[") and text.endswith("]"):
        try:
            parsed = json.loads(text.replace("'", '"'))
            return approved(parsed)
        except Exception:
            return any(token in APPROVED_STATUSES for token in re.findall(r"[\u4e00-\u9fff]+", text))
    return text in APPROVED_STATUSES


def flatten_fields(fields: list[dict]) -> list[dict]:
    out: list[dict] = []
    for field in fields:
        out.append(field)
        out.extend(flatten_fields(field.get("subfields") or []))
    return out


def staff_id(name: str, staff: list[dict]) -> int | None:
    raw = (name or "").strip()
    if not raw:
        return None
    for item in staff:
        display = str(item.get("displayName") or "").strip()
        if display == raw:
            return item.get("id")
    for item in staff:
        display = str(item.get("displayName") or "").strip()
        if display and (raw in display or display in raw):
            return item.get("id")
    return None


def coerce_member(value: Any, staff: list[dict]) -> Any:
    names: list[str] = []
    if isinstance(value, list):
        for item in value:
            if isinstance(item, dict):
                names.append(str(item.get("name") or item.get("nickName") or item.get("displayName") or "").strip())
            else:
                names.append(str(item).strip())
    elif isinstance(value, dict):
        names.append(str(value.get("name") or value.get("nickName") or value.get("displayName") or "").strip())
    elif value not in (None, ""):
        names.extend(part.strip() for part in str(value).replace("；", ";").split(";") if part.strip())
    for name in names:
        matched = staff_id(name, staff)
        if matched is not None:
            return matched
    return None


def coerce_boolean(value: Any) -> Any:
    if isinstance(value, bool):
        return value
    text = str(value).strip().lower()
    if text in {"是", "true", "1", "y", "yes", "勾选"}:
        return True
    if text in {"否", "false", "0", "n", "no", "未勾选", ""}:
        return False
    return value


def coerce_number(value: Any) -> Any:
    if value in (None, ""):
        return None
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        return value
    text = str(value).strip().replace(",", "")
    try:
        if "." in text:
            return float(text)
        return int(text)
    except ValueError:
        return value


def coerce_ref(value: Any) -> Any:
    if value is None or value == "":
        return None
    if isinstance(value, str):
        text = value.strip()
        if text.startswith("{") or text.startswith("["):
            try:
                parsed = json.loads(text)
            except json.JSONDecodeError:
                return value
            shown = coerce_ref(parsed)
            if shown is not None:
                return shown
            if isinstance(parsed, dict) and ("apply_id" in parsed or "applyId" in parsed):
                return None
        return value
    if isinstance(value, list):
        parts = [coerce_ref(item) for item in value]
        parts = [part for part in parts if part not in (None, "")]
        if not parts:
            return None
        if len(parts) == 1:
            return parts[0]
        return "、".join(str(part) for part in parts)
    if isinstance(value, dict):
        if value.get("url") and (value.get("name") or value.get("originalName") or value.get("id") is not None):
            return value
        for key in ("label", "name", "title", "display_value", "displayName", "apply_title", "value"):
            nested = value.get(key)
            if nested in (None, ""):
                continue
            shown = coerce_ref(nested) if isinstance(nested, (dict, list)) else str(nested).strip()
            if shown not in (None, ""):
                return shown
        if "apply_id" in value or "applyId" in value:
            return None
    return value


def coerce_scalar(field_type: str, value: Any, staff: list[dict]) -> Any:
    if value is None:
        return None
    if field_type == "member":
        return coerce_member(value, staff)
    if field_type == "boolean":
        return coerce_boolean(value)
    if field_type == "number":
        return coerce_number(value)
    if field_type == "multiselect":
        if isinstance(value, list):
            return [str(item) for item in value if item not in (None, "")]
        if isinstance(value, str) and value.strip():
            return [part.strip() for part in re.split(r"[;；,，]", value) if part.strip()]
        return []
    if field_type == "select":
        shown = coerce_ref(value)
        if isinstance(shown, list):
            return str(shown[0]) if shown else None
        return None if shown in (None, "") else shown
    return coerce_ref(value)


def map_subtable(field: dict, value: Any, staff: list[dict]) -> list[dict]:
    rows = value
    if isinstance(value, dict):
        rows = value.get("rows") or value.get("value") or []
    if not isinstance(rows, list):
        return []
    by_name = {str(sub.get("name") or "").strip(): sub for sub in field.get("subfields") or []}
    mapped: list[dict] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        out: dict[str, Any] = {}
        for title, raw in row.items():
            if str(title).startswith("__"):
                continue
            sub = by_name.get(str(title).strip())
            if sub is None:
                continue
            if sub.get("type") == "attachment":
                continue
            out[sub["key"]] = coerce_scalar(sub.get("type") or "text", raw, staff)
        name_to_key = {str(sub.get("name") or "").strip(): sub["key"] for sub in field.get("subfields") or []}
        contact_key = name_to_key.get("联系人")
        name_key = name_to_key.get("姓名")
        if contact_key and name_key and not out.get(contact_key) and out.get(name_key):
            out[contact_key] = out[name_key]
        mapped.append(out)
    return mapped


def extra_from_fields(kind: dict, fields: list[dict], staff: list[dict]) -> dict[str, Any]:
    by_id = {str(item.get("field_id")): item for item in fields if item.get("field_id") is not None}
    extra: dict[str, Any] = {}
    for field in kind.get("fields") or []:
        key = field.get("key") or ""
        fid = key.replace("field_", "", 1) if key.startswith("field_") else key
        source = by_id.get(str(fid))
        if source is None:
            continue
        value = source.get("value")
        if value is None:
            continue
        ftype = field.get("type") or "text"
        if ftype == "attachment":
            continue
        if ftype == "subtable":
            extra[key] = map_subtable(field, value, staff)
        else:
            extra[key] = coerce_scalar(ftype, value, staff)
    by_name = {str(field.get("name") or ""): field.get("key") for field in kind.get("fields") or []}
    instrument = by_name.get("选择仪器")
    device_no = by_name.get("设备编号")
    if instrument and device_no and not extra.get(instrument) and extra.get(device_no):
        extra[instrument] = extra[device_no]
    return extra


def files_from_assets(kind: dict, payload: dict) -> list[tuple[str, Path, str]]:
    """Return (field_key, local_path, original_name) for downloaded Qingflow assets."""
    catalog_ids = {field["key"]: field["key"].replace("field_", "", 1) for field in flatten_fields(kind.get("fields") or []) if field.get("type") == "attachment"}
    id_to_key = {fid: key for key, fid in catalog_ids.items()}
    out: list[tuple[str, Path, str]] = []
    seen: set[tuple[str, str, str]] = set()
    for bucket in ("media_assets", "file_assets"):
        items = ((payload.get(bucket) or {}).get("items") or [])
        for item in items:
            if not isinstance(item, dict):
                continue
            local = item.get("local_path") or item.get("path")
            if not local:
                continue
            path = Path(str(local))
            if not path.is_file():
                continue
            name = clean_asset_name(str(item.get("name") or item.get("original_name") or item.get("filename") or path.name))
            fid = item.get("field_id")
            key = id_to_key.get(str(fid), "") if fid is not None else ""
            if not key:
                title = str(item.get("field_title") or item.get("title") or "").strip()
                for field in kind.get("fields") or []:
                    field_name = str(field.get("name") or "")
                    if field.get("type") == "attachment" and (field_name == title or field_name.startswith(title)):
                        key = field["key"]
                        break
            sig = (key, str(path.resolve()), name)
            if sig in seen:
                continue
            seen.add(sig)
            out.append((key, path, name))
    return out
