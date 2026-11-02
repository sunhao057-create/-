# -*- coding: utf-8 -*-
"""Import Qingflow-approved tech records (with files) into project-mgmt."""
from __future__ import annotations

import argparse
import csv
import json
import mimetypes
import os
import subprocess
import sys
import time
import uuid
from collections import Counter
from http.cookiejar import CookieJar
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import HTTPCookieProcessor, Request, build_opener

ROOT = Path(r"D:\项目管理")
sys.path.insert(0, str(ROOT / "tools" / "qingflow"))

from map_qingflow import (  # noqa: E402
    approved,
    extra_from_fields,
    files_from_assets,
    kind_by_app_key,
    load_catalog,
)

QINGFLOW = Path(r"C:\Users\15675\AppData\Roaming\npm\qingflow.CMD")
TMP = ROOT / "tmp" / "qingflow-tech" / "migrate"
PROGRESS = TMP / "progress.json"
WHERE_FILE = TMP / "where_all_time.json"
ORDER_FILE = TMP / "order_id.json"
DEFAULT_BASE = "http://101.132.181.18:8094"


def run_qingflow(args: list[str], dest: Path | None = None, attempts: int = 4) -> dict:
    cmd = [str(QINGFLOW), "--json", *args]
    last: Exception | None = None
    for round_no in range(1, attempts + 1):
        try:
            raw = subprocess.check_output(cmd, cwd=str(ROOT), stderr=subprocess.STDOUT, timeout=420)
            if dest is not None:
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(raw)
            text = raw.decode("utf-8", errors="replace")
            if not text.strip():
                raise RuntimeError("qingflow empty output")
            return json.loads(text)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError, json.JSONDecodeError, RuntimeError) as ex:
            last = ex
            print(f"qingflow retry {round_no}/{attempts}: {args[:6]} {ex}", flush=True)
            time.sleep(min(40, 4 * round_no))
    raise last if last else RuntimeError("qingflow failed")


def ensure_filter_files() -> None:
    TMP.mkdir(parents=True, exist_ok=True)
    if not WHERE_FILE.exists():
        WHERE_FILE.write_text(
            json.dumps([{"field_id": 2, "op": "between", "value": ["2015-01-01", "2027-12-31"]}], ensure_ascii=False),
            encoding="utf-8",
        )
    if not ORDER_FILE.exists():
        ORDER_FILE.write_text(json.dumps([{"field_id": 2, "direction": "asc"}]), encoding="utf-8")


def access_status(app_key: str) -> tuple[Path, dict]:
    ensure_filter_files()
    columns = TMP / f"columns_status_{app_key}.json"
    columns.write_text(json.dumps([{"field_id": 0}, {"field_id": 4}, {"field_id": 2}], ensure_ascii=False), encoding="utf-8")
    col = str(columns.relative_to(ROOT)).replace("\\", "/")
    order = str(ORDER_FILE.relative_to(ROOT)).replace("\\", "/")
    where = str(WHERE_FILE.relative_to(ROOT)).replace("\\", "/")
    common = ["record", "access", "--app-key", app_key, "--view-id", "system:all", "--columns-file", col, "--order-by-file", order]
    last: Exception | None = None
    for extra in ([], ["--where-file", where]):
        try:
            payload = run_qingflow(common + extra, TMP / f"{app_key}.access.json", attempts=2)
            files = payload.get("files") or []
            csv_path = Path(files[0]["local_path"]) if files else Path()
            if csv_path.is_file():
                return csv_path, payload
        except Exception as ex:
            last = ex
            print(f"access fallback {app_key} extra={extra[:1]}: {ex}", flush=True)
    raise last if last else RuntimeError(f"access failed {app_key}")


def approved_ids(csv_path: Path) -> list[tuple[str, str]]:
    if not csv_path.is_file():
        return []
    out: list[tuple[str, str]] = []
    with csv_path.open(encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            status = row.get("当前流程状态__field_4") or row.get("当前流程状态") or ""
            if not approved(status):
                continue
            rid = str(row.get("record_id") or "").strip()
            if not rid:
                continue
            apply_time = str(row.get("申请时间__field_2") or "").strip()
            out.append((rid, apply_time))
    return out


def record_get(app_key: str, record_id: str) -> dict:
    dest = TMP / "gets" / app_key / f"{record_id}.json"
    if dest.is_file() and dest.stat().st_size > 80:
        try:
            cached = json.loads(dest.read_text(encoding="utf-8"))
            if cached.get("status") in {"ok", "success"} and (cached.get("fields") or cached.get("record")):
                return cached
        except json.JSONDecodeError:
            pass
    return run_qingflow(
        ["record", "get", "--app-key", app_key, "--record-id", record_id, "--view-id", "system:all"],
        dest,
    )


def load_progress() -> dict:
    if PROGRESS.is_file():
        try:
            return json.loads(PROGRESS.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            return {"done": []}
    return {"done": []}


def save_progress(data: dict) -> None:
    PROGRESS.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def api_opener() -> Any:
    return build_opener(HTTPCookieProcessor(CookieJar()))


def api_json(opener, method: str, url: str, payload: dict | None = None, token: str = "") -> dict:
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = Request(url, data=data, method=method)
    req.add_header("Accept", "application/json")
    if payload is not None:
        req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("X-Pm-Import-Token", token)
    try:
        with opener.open(req, timeout=180) as resp:
            body = resp.read().decode("utf-8")
    except HTTPError as ex:
        body = ex.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} -> {ex.code} {body[:500]}") from ex
    except (URLError, TimeoutError, OSError) as ex:
        raise RuntimeError(f"{method} {url} -> {ex}") from ex
    parsed = json.loads(body) if body else {}
    if parsed.get("code") not in (0, None) and "data" not in parsed:
        raise RuntimeError(f"{method} {url} -> {parsed}")
    return parsed


def login(opener, base: str, username: str, password: str) -> None:
    parsed = api_json(opener, "POST", base.rstrip("/") + "/api/auth/login", {"username": username, "password": password})
    if parsed.get("code") not in (0, None):
        raise RuntimeError(f"登录失败：{parsed.get('message') or parsed}")


def upload_files(opener, url: str, field_key: str, files: list[tuple[Path, str]], token: str = "") -> dict:
    boundary = "----PmMigrate" + uuid.uuid4().hex
    chunks: list[bytes] = []
    if field_key:
        chunks.append(
            (
                f"--{boundary}\r\nContent-Disposition: form-data; name=\"fieldKey\"\r\n\r\n{field_key}\r\n"
            ).encode("utf-8")
        )
    for path, name in files:
        ctype = mimetypes.guess_type(name)[0] or "application/octet-stream"
        header = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="files"; filename="{name}"\r\n'
            f"Content-Type: {ctype}\r\n\r\n"
        ).encode("utf-8")
        chunks.append(header + path.read_bytes() + b"\r\n")
    chunks.append(f"--{boundary}--\r\n".encode("utf-8"))
    body = b"".join(chunks)
    req = Request(url, data=body, method="POST")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    req.add_header("Accept", "application/json")
    if token:
        req.add_header("X-Pm-Import-Token", token)
    try:
        with opener.open(req, timeout=180) as resp:
            parsed = json.loads(resp.read().decode("utf-8"))
    except HTTPError as ex:
        raise RuntimeError(f"upload {url} -> {ex.code} {ex.read()[:400]!r}") from ex
    if parsed.get("code") not in (0, None):
        raise RuntimeError(f"upload failed: {parsed}")
    return parsed


def import_one(
        opener,
        base: str,
        slug: str,
        payload: dict,
        assets: list[tuple[str, Path, str]],
        skip_files: bool,
        token: str = "",
) -> str:
    url = f"{base.rstrip('/')}/api/tech/{slug}/import"
    created = None
    last: Exception | None = None
    for round_no in range(1, 4):
        try:
            created = api_json(opener, "POST", url, payload, token)
            break
        except Exception as ex:
            last = ex
            print(f"import retry {round_no}/3 {slug}: {ex}", flush=True)
            time.sleep(5 * round_no)
    if created is None:
        raise last if last else RuntimeError("import failed")
    data = created.get("data") or {}
    rid = data.get("id")
    if rid is None:
        raise RuntimeError(f"import missing id: {created}")
    if skip_files or not assets:
        return "imported" if not data.get("files") else "exists"
    existing_names = {str(item.get("name")) for item in (data.get("files") or [])}
    grouped: dict[str, list[tuple[Path, str]]] = {}
    for field_key, path, name in assets:
        if name in existing_names:
            continue
        grouped.setdefault(field_key or "", []).append((path, name))
    for field_key, files in grouped.items():
        upload_files(
            opener,
            f"{base.rstrip('/')}/api/tech/{slug}/{rid}/files",
            field_key,
            files,
            token,
        )
    return "imported"


def count_apps(catalog: list[dict], only: str | None) -> list[dict]:
    rows = []
    for kind in catalog:
        app_key = kind.get("appKey")
        if not app_key:
            continue
        if only and app_key != only and kind.get("slug") != only:
            continue
        try:
            csv_path, payload = access_status(app_key)
            ids = approved_ids(csv_path)
            err = None
        except subprocess.CalledProcessError as ex:
            ids = []
            payload = {}
            err = (ex.output or b"")[:300].decode("utf-8", errors="replace")
        rows.append(
            {
                "appKey": app_key,
                "slug": kind.get("slug"),
                "label": kind.get("label"),
                "approved": len(ids),
                "reported": (payload.get("scope") or payload.get("verification") or {}).get("reported_total"),
                "error": err,
            }
        )
        print(f"{kind.get('label')} {app_key}: approved={len(ids)} err={err}", flush=True)
    return rows


def migrate(args: argparse.Namespace) -> None:
    catalog = load_catalog()
    progress = load_progress()
    done = set(progress.get("done") or [])
    imported_by_app = Counter(item.split(":")[0] for item in done)
    catalog = sorted(catalog, key=lambda kind: imported_by_app.get(kind.get("appKey") or "", 0))
    opener = api_opener()
    auth_token = args.token or os.environ.get("PM_IMPORT_TOKEN", "")
    if auth_token:
        print("using import token", flush=True)
    else:
        login(opener, args.base_url, args.username, args.password)
    lookups = api_json(opener, "GET", args.base_url.rstrip("/") + "/api/tech/lookups", token=auth_token)
    staff = ((lookups.get("data") or {}).get("staff")) or []
    summary = []
    for kind in catalog:
        app_key = kind.get("appKey")
        if not app_key:
            continue
        if args.app_key and app_key != args.app_key and kind.get("slug") != args.app_key:
            continue
        slug = kind.get("slug")
        try:
            csv_path, _payload = access_status(app_key)
            rows = approved_ids(csv_path)
        except Exception as ex:
            print(f"SKIP list {kind.get('label')}: {ex}", flush=True)
            summary.append({"label": kind.get("label"), "error": str(ex)})
            continue
        if args.limit:
            rows = rows[: args.limit]
        imported = skipped = failed = 0
        for record_id, apply_time in rows:
            mark = f"{app_key}:{record_id}"
            if mark in done:
                skipped += 1
                continue
            try:
                detail = record_get(app_key, record_id)
                record = detail.get("record") or {}
                extra = extra_from_fields(kind, detail.get("fields") or [], staff)
                assets = [] if args.skip_files else files_from_assets(kind, detail)
                payload = {
                    "qingflowRecordId": str(record.get("record_id") or record_id),
                    "applicantName": record.get("applicant"),
                    "applyTime": record.get("apply_time") or apply_time,
                    "extra": extra,
                }
                import_one(opener, args.base_url, slug, payload, assets, args.skip_files, auth_token)
                done.add(mark)
                progress["done"] = sorted(done)
                save_progress(progress)
                imported += 1
                print(f"OK {kind.get('label')} {record_id} files={len(assets)}", flush=True)
            except Exception as ex:
                failed += 1
                print(f"FAIL {kind.get('label')} {record_id}: {ex}", flush=True)
                time.sleep(2)
        summary.append({"label": kind.get("label"), "approved": len(rows), "imported": imported, "skipped": skipped, "failed": failed})
    (TMP / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2), flush=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=os.environ.get("PM_BASE_URL", DEFAULT_BASE))
    parser.add_argument("--username", default=os.environ.get("PM_ADMIN_USER", "admin"))
    parser.add_argument("--password", default=os.environ.get("PM_ADMIN_PASSWORD", "Admin123!"))
    parser.add_argument("--token", default=os.environ.get("PM_IMPORT_TOKEN", ""))
    parser.add_argument("--app-key")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--count-only", action="store_true")
    parser.add_argument("--skip-files", action="store_true")
    args = parser.parse_args()
    if args.count_only:
        rows = count_apps(load_catalog(), args.app_key)
        (TMP / "counts.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(rows, ensure_ascii=False, indent=2))
        return
    migrate(args)


if __name__ == "__main__":
    try:
        main()
    except URLError as ex:
        raise SystemExit(f"无法连接系统：{ex}") from ex
    except subprocess.CalledProcessError as ex:
        raise SystemExit((ex.output or b"").decode("utf-8", errors="replace")[:1000]) from ex
