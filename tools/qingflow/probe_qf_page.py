# -*- coding: utf-8 -*-
"""Log in with stdin password and save the shape of one sales-order page. No secrets in the output."""
from __future__ import annotations

import base64
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

from Crypto.Cipher import PKCS1_v1_5
from Crypto.PublicKey import RSA

BASE = "https://qingflow.com/api"
UA = "Mozilla/5.0"
OUT = Path(r"D:\项目管理\tmp\qf_page_shape.json")


def request(method: str, path: str, body: dict | None = None, token: str = "", ws_id: str = "", qf_version: str = "") -> dict:
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("User-Agent", UA)
    req.add_header("Accept", "application/json")
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("token", token)
    if ws_id:
        req.add_header("wsId", ws_id)
    if qf_version:
        req.add_header("Cookie", f"qfVersion={qf_version}")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as ex:
        raw = ex.read().decode("utf-8", errors="replace")
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"code": "bad", "message": raw[:180]}


def redact(value, depth: int = 0):
    if depth > 6:
        return "..."
    if isinstance(value, dict):
        return {str(key): redact(item, depth + 1) for key, item in list(value.items())[:40]}
    if isinstance(value, list):
        if not value:
            return []
        return [redact(value[0], depth + 1), f"...{len(value)}"]
    if isinstance(value, str):
        return f"str:{len(value)}"
    if value is None or isinstance(value, (int, float, bool)):
        return value
    return type(value).__name__


def main() -> None:
    password = sys.stdin.read().strip()
    if not password:
        raise SystemExit("missing password")
    pub = request("GET", "/user/pubkey")
    pubkey = ((pub.get("data") or {}).get("pubkey")) or ""
    key = RSA.import_key("-----BEGIN PUBLIC KEY-----\n" + pubkey + "\n-----END PUBLIC KEY-----\n")

    def encrypted_password() -> str:
        return base64.b64encode(PKCS1_v1_5.new(key).encrypt(password.encode("utf-8"))).decode("ascii")

    email_login = request("POST", "/user/login", {"email": "17717961385", "password": encrypted_password()})
    mobile_login = request(
        "POST",
        "/user/login",
        {"mobile": "17717961385", "areaCode": "86", "password": encrypted_password()},
    )
    data = mobile_login if isinstance(mobile_login, dict) and mobile_login.get("token") else {}
    if not data and isinstance(mobile_login.get("data"), dict):
        data = mobile_login["data"]
    token = str(data.get("token") or "")
    user = data.get("userInfo") if isinstance(data.get("userInfo"), dict) else {}
    last_ws = user.get("lastWsInfo") if isinstance(user.get("lastWsInfo"), dict) else {}
    ws_id = str(last_ws.get("wsId") or last_ws.get("id") or user.get("lastWsId") or user.get("wsId") or "")
    summary = {
        "email_code": email_login.get("code"),
        "email_message": email_login.get("message"),
        "mobile_code": None if not isinstance(mobile_login, dict) else mobile_login.get("code"),
        "mobile_message": None if not isinstance(mobile_login, dict) else mobile_login.get("message"),
        "mobile_keys": sorted(mobile_login.keys()) if isinstance(mobile_login, dict) else type(mobile_login).__name__,
        "login_code": email_login.get("code"),
        "login_message": email_login.get("message"),
        "has_token": bool(token),
        "login_keys": sorted(data.keys()),
        "user_keys": sorted(user.keys()),
        "ws_id_present": bool(ws_id),
        "ws_info_keys": sorted(last_ws.keys()),
    }
    if token:
        totals = {}
        sample = None
        for kind in range(1, 13):
            page = request(
                "POST",
                "/app/8rlbmv813s02/apply/filter",
                {"pageSize": 1, "pageNum": 1, "type": kind, "sorts": [], "queries": []},
                token=token,
                ws_id=ws_id,
            )
            pdata = page.get("data") if isinstance(page.get("data"), dict) else page
            total = pdata.get("total") if isinstance(pdata, dict) else None
            rows = pdata.get("list") if isinstance(pdata, dict) else None
            totals[str(kind)] = {"code": page.get("code"), "total": total, "len": len(rows) if isinstance(rows, list) else None}
            if sample is None and isinstance(rows, list) and rows:
                sample = redact(rows[0])
        summary["totals"] = totals
        summary["row_shape"] = sample
        probes = {}
        for path in ("/ws/list", "/user/wsList", "/ws/getList", "/app/8rlbmv813s02"):
            payload = request("GET", path, token=token, ws_id=ws_id)
            if isinstance(payload, dict):
                probes[path] = {
                    "code": payload.get("code"),
                    "message": payload.get("message"),
                    "keys": sorted(payload.keys())[:20],
                }
            else:
                probes[path] = type(payload).__name__
        summary["probes"] = probes
        ws_page = request(
            "POST",
            "/user/workspaceList/pageQuery",
            {"pageNum": 1, "pageSize": 50, "authList": [0, 1, 2]},
            token=token,
            ws_id=ws_id,
        )
        ws_data = ws_page.get("data") if isinstance(ws_page.get("data"), dict) else ws_page
        ws_list = ws_data.get("list") if isinstance(ws_data, dict) else []
        workspaces = []
        if isinstance(ws_list, list):
            for item in ws_list:
                if not isinstance(item, dict):
                    continue
                item_ws = str(item.get("wsId") or "")
                name = str(item.get("workspaceName") or item.get("wsName") or "")
                page = request(
                    "POST",
                    "/app/8rlbmv813s02/apply/filter",
                    {"pageSize": 1, "pageNum": 1, "type": 8, "sorts": [], "queries": []},
                    token=token,
                    ws_id=item_ws,
                )
                pdata = page.get("data") if isinstance(page.get("data"), dict) else page
                workspaces.append({
                    "wsId": item_ws,
                    "name": name,
                    "code": page.get("code"),
                    "total": pdata.get("total") if isinstance(pdata, dict) else None,
                })
        summary["workspaces"] = workspaces
        summary["ws_auth"] = last_ws.get("auth")
        extra = {}
        for kind in (14, 15, 16):
            page = request(
                "POST",
                "/app/8rlbmv813s02/apply/filter",
                {"pageSize": 1, "pageNum": 1, "type": kind, "sorts": [], "queries": []},
                token=token,
                ws_id="385437",
            )
            pdata = page.get("data") if isinstance(page.get("data"), dict) else {}
            extra[str(kind)] = {"code": page.get("code"), "message": page.get("message"), "total": pdata.get("total") if isinstance(pdata, dict) else None}
        app = request("GET", "/app/8rlbmv813s02", token=token, ws_id="385437")
        extra["app"] = {
            "errCode": app.get("errCode") if isinstance(app, dict) else None,
            "errMsg": app.get("errMsg") if isinstance(app, dict) else None,
            "code": app.get("code") if isinstance(app, dict) else None,
            "message": app.get("message") if isinstance(app, dict) else None,
        }
        summary["extra"] = extra
        end_page = request(
            "POST",
            "/app/9ifculf7rk01/apply/filter",
            {"type": 8, "pageNum": 1, "pageSize": 5},
            token=token,
            ws_id="385437",
        )
        summary["project_end"] = redact(end_page)
        base = request("GET", "/app/9ifculf7rk01/apply/baseInfo", token=token, ws_id="385437")
        summary["project_end_base"] = redact(base)
        qf_version = str(last_ws.get("qfVersion") or "")
        summary["qf_version"] = qf_version
        login_token = str(data.get("loginToken") or "")
        if login_token:
            by_login = request(
                "POST",
                "/app/9ifculf7rk01/apply/filter",
                {"type": 8, "pageNum": 1, "pageSize": 5},
                token=login_token,
                ws_id="385437",
                qf_version=qf_version,
            )
            summary["project_end_login_token"] = redact(by_login)
        if qf_version:
            versioned = request(
                "POST",
                "/app/9ifculf7rk01/apply/filter",
                {"type": 8, "pageNum": 1, "pageSize": 5},
                token=token,
                ws_id="385437",
                qf_version=qf_version,
            )
            summary["project_end_versioned"] = redact(versioned)
    OUT.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
