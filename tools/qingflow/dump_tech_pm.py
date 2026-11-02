# -*- coding: utf-8 -*-
"""Dump Qingflow apps under 技术服务项目管理 into tmp/qingflow-tech."""
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(r"D:\项目管理")
CLI = r"C:\Users\15675\AppData\Roaming\npm\qingflow.CMD"
SRC = ROOT / "tmp" / "qingflow-tech"
KEYS = [
    "6ar55akg6k02",
    "d7kprf0g1c02",
    "0ddf6092",
    "dgbubsun4802",
    "6dlu39l9ro02",
    "5v9n82h7fk01",
]


def run(dest: Path, args: str) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    cmd = f'"{CLI}" --json {args}'
    print(f"  {dest.name}", flush=True)
    try:
        raw = subprocess.check_output(cmd, cwd=str(ROOT), shell=True, stderr=subprocess.STDOUT)
        dest.write_bytes(raw)
        print(f"    ok {len(raw)} bytes", flush=True)
    except subprocess.CalledProcessError as ex:
        dest.write_bytes(ex.output or b"{}")
        print(f"    FAILED {ex.returncode}", flush=True)


def main() -> None:
    SRC.mkdir(parents=True, exist_ok=True)
    for key in KEYS:
        print(f"dump {key}", flush=True)
        run(SRC / f"{key}.fields.json", f"builder app get --app-key {key} fields")
        run(SRC / "insert" / f"{key}.insert.json", f"record schema insert --app-key {key}")
        run(SRC / "browse" / f"{key}.browse.json", f"record schema browse --app-key {key} --view-id system:all")
        run(SRC / "layout" / f"{key}.layout.json", f"builder app get layout --app-key {key}")


if __name__ == "__main__":
    main()
