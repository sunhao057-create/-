# -*- coding: utf-8 -*-
"""Password-login to Qingflow and copy business records into the system. No captcha."""
from __future__ import annotations

import base64
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from Crypto.Cipher import PKCS1_v1_5
from Crypto.PublicKey import RSA

ROOT = Path(r"D:\项目管理")
FORMS = ROOT / "backend" / "src" / "main" / "resources" / "dynamic-seed" / "forms.json"
TECH = ROOT / "backend" / "src" / "main" / "resources" / "dynamic-seed" / "tech-forms.json"
PROGRESS = ROOT / "tmp" / "qingflow-crm" / "progress.json"
SUMMARY = ROOT / "tmp" / "qingflow-crm" / "summary.json"
TOKEN_FILE = ROOT / "tmp" / "import.token"
QF = "https://qingflow.com/api"
APP = "http://101.132.181.18:8098"
WS = "385437"
QF_VERSION = "mongo2"
PAGE_SIZE = 100
BATCH = 30
AUXILIARY = {
    "8tv4okp5hk02", "8u953rsdlg02", "8uq56lhpv401", "8usc6bvtvc01", "8uqer79ht001",
    "8u9eh78tlg01", "8uk4a1kdvk02", "8ukifob5rc01", "8ukcrr6ht002", "8uk8l0llv401",
    "8uks7am1v402", "8ssblh6hlg02", "8tukus31hk02", "8tum65splg01", "8tv5ucftm402",
    "9acej0ae5801", "dhtgrk97t801", "bsghrfm5fg01", "4a0fdb7b", "f236e3f6",
    "8ts7r6h9jk02", "8uhgs195rc02", "8uqg2bilvk02", "8uha80ftug01", "ecbvu7jifk02",
    "ecbuuh1qfk02", "dv4ibgcvjk01", "6ih1gug8ik01", "70o53qveoo02",
}
ALREADY = {
    "689f58d1", "6fba7ahqqk02", "6tth2ob1lg01", "97hor9l95802", "97hs18o56o02",
    "97hujbgd6o01", "97hvvik16o02", "97i29pmh5801", "97i2ne695801", "97i2pmoh6o02",
    "97i2s1d55802", "97i2udrl5801", "a4dc7e7c", "akbtu9cdsg02", "beb22af6",
}


def qf(method: str, path: str, body: dict | None = None, token: str = "") -> dict:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    last = None
    for attempt in range(1, 4):
        req = urllib.request.Request(QF + path, data=data, method=method)
        req.add_header("User-Agent", "Mozilla/5.0")
        req.add_header("Accept", "application/json")
        req.add_header("Cookie", f"qfVersion={QF_VERSION}")
        if body is not None:
            req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("token", token)
            req.add_header("wsId", WS)
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as ex:
            raw = ex.read().decode("utf-8", errors="replace")
            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                last = RuntimeError(raw[:180])
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as ex:
            last = ex
        time.sleep(attempt)
    raise last if last else RuntimeError("qingflow request failed")


def login(password: str) -> str:
    pub = qf("GET", "/user/pubkey")
    pubkey = ((pub.get("data") or {}).get("pubkey")) or ""
    key = RSA.import_key("-----BEGIN PUBLIC KEY-----\n" + pubkey + "\n-----END PUBLIC KEY-----\n")
    encrypted = base64.b64encode(PKCS1_v1_5.new(key).encrypt(password.encode("utf-8"))).decode("ascii")
    result = qf("POST", "/user/login", {"mobile": "17717961385", "areaCode": "86", "password": encrypted})
    token = ""
    if isinstance(result, dict):
        token = str(result.get("token") or "")
        if not token and isinstance(result.get("data"), dict):
            token = str(result["data"].get("token") or "")
    if not token:
        raise SystemExit("password login did not return a session")
    return token


def piece(item: dict, que_type: int):
    if que_type == 5:
        return item.get("otherInfo") or item.get("value") or ""
    if item.get("dataValue") not in (None, ""):
        return item.get("dataValue")
    return "" if item.get("value") is None else item.get("value")


def scalar(answer: dict):
    que_type = int(answer.get("queType") or 0)
    if que_type == 18:
        rows = []
        for row in answer.get("tableValues") or []:
            cells = row if isinstance(row, list) else row.get("values") if isinstance(row, dict) else None
            if not isinstance(cells, list):
                continue
            mapped = {}
            for cell in cells:
                if isinstance(cell, dict) and cell.get("queId") is not None:
                    mapped[f"q{cell['queId']}"] = scalar(cell)
            if any(value not in ("", None, []) for value in mapped.values()):
                rows.append(mapped)
        return rows
    parts = []
    for item in answer.get("values") or []:
        if isinstance(item, dict):
            text = piece(item, que_type)
        else:
            text = item
        if text not in (None, ""):
            parts.append(text)
    if que_type == 11:
        return [str(part) for part in parts]
    if que_type == 8 and len(parts) == 1:
        try:
            number = float(parts[0])
            return int(number) if number.is_integer() else number
        except (TypeError, ValueError):
            return parts[0]
    if len(parts) <= 1:
        return parts[0] if parts else ""
    return "、".join(str(part) for part in parts)


def map_row(row: dict) -> dict:
    values = {}
    title = ""
    for answer in row.get("answers") or []:
        if not isinstance(answer, dict) or answer.get("queId") is None:
            continue
        value = scalar(answer)
        values[f"q{answer['queId']}"] = value
        if not title and isinstance(value, str) and value.strip() and int(answer.get("queType") or 0) in {2, 3, 19}:
            title = value.strip()
    base = row.get("applyBaseInfo") if isinstance(row.get("applyBaseInfo"), dict) else {}
    serial = str(row.get("customApplyNum") or base.get("applyNum") or "")
    status_code = row.get("applyStatus")
    status = {1: "DRAFT", 4: "CANCELLED", 5: "DRAFT"}.get(status_code, "DONE")
    created = str(base.get("applyTime") or "")
    if len(created) >= 19:
        created = created[:19]
    return {
        "sourceKey": str(row.get("applyId") or ""),
        "title": title or serial or "未命名",
        "serialNo": serial,
        "status": status,
        "createdAt": created,
        "values": values,
    }


def post(token: str, code: str, items: list) -> dict:
    body = json.dumps({"items": items}, ensure_ascii=False).encode("utf-8")
    last = None
    for attempt in range(1, 4):
        req = urllib.request.Request(
            f"{APP}/api/dynamic/definitions/{code}/import-batch",
            data=body,
            headers={"Content-Type": "application/json", "X-Pm-Import-Token": token},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                payload = json.loads(resp.read().decode("utf-8"))
            return payload.get("data") or payload
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as ex:
            last = ex
            time.sleep(attempt)
    raise last if last else RuntimeError("import failed")


def codes() -> list[str]:
    found = []
    for path in (FORMS, TECH):
        if not path.exists():
            continue
        for item in json.loads(path.read_text(encoding="utf-8-sig")).get("items") or []:
            code = str(item.get("code") or "")
            if code and code not in AUXILIARY and code not in ALREADY and code not in found:
                found.append(code)
    # 项目结束先导，方便马上在页面上看到。
    if "9ifculf7rk01" in found:
        found.remove("9ifculf7rk01")
        found.insert(0, "9ifculf7rk01")
    return found


def load_progress() -> dict:
    if PROGRESS.exists():
        return json.loads(PROGRESS.read_text(encoding="utf-8"))
    return {}


def save_progress(progress: dict) -> None:
    PROGRESS.parent.mkdir(parents=True, exist_ok=True)
    PROGRESS.write_text(json.dumps(progress, ensure_ascii=False, indent=2), encoding="utf-8")


def import_form(session: str, app_token: str, code: str, start_page: int) -> dict:
    created = existed = failed = 0
    page = start_page
    total = None
    while True:
        payload = qf(
            "POST",
            f"/app/{code}/apply/filter",
            {"type": 8, "pageNum": page, "pageSize": PAGE_SIZE},
            token=session,
        )
        if payload.get("code") not in (0, None) and "list" not in (payload.get("data") or {}):
            print(f"SKIP {code} code={payload.get('code')}", flush=True)
            break
        data = payload.get("data") if isinstance(payload.get("data"), dict) else {}
        rows = data.get("list") or []
        if total is None:
            try:
                total = int(data.get("total") or 0)
            except (TypeError, ValueError):
                total = 0
            print(f"FORM {code} total {total} from page {page}", flush=True)
        if not rows:
            break
        items = [item for item in (map_row(row) for row in rows) if item["sourceKey"]]
        for offset in range(0, len(items), BATCH):
            result = post(app_token, code, items[offset : offset + BATCH])
            created += int(result.get("created") or 0)
            existed += int(result.get("existed") or 0)
            failed += int(result.get("failed") or 0)
            if result.get("errors"):
                print(code, result["errors"][:2], flush=True)
        page += 1
        progress = load_progress()
        progress[code] = page
        save_progress(progress)
        print(f"{code} page {page - 1} created {created} existed {existed} failed {failed}", flush=True)
        if total and (page - 1) * PAGE_SIZE >= total:
            break
    return {"total": total or 0, "created": created, "existed": existed, "failed": failed}


def main() -> None:
    password = sys.stdin.read().strip()
    if not password:
        raise SystemExit("missing password")
    session = login(password)
    app_token = TOKEN_FILE.read_text(encoding="utf-8").strip()
    progress = load_progress()
    summary = {}
    if SUMMARY.exists():
        summary = json.loads(SUMMARY.read_text(encoding="utf-8"))
    for code in codes():
        if progress.get(code) == "done":
            continue
        start = int(progress.get(code) or 1)
        summary[code] = import_form(session, app_token, code, start)
        progress[code] = "done"
        save_progress(progress)
        SUMMARY.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
        print("DONE", code, summary[code], flush=True)


if __name__ == "__main__":
    main()
