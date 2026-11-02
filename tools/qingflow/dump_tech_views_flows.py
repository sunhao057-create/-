# -*- coding: utf-8 -*-
"""Dump Qingflow views/flows for tech catalog UI and workflow replica."""
from __future__ import annotations

import json
import subprocess
from pathlib import Path

from gen_tech_catalog import KINDS

ROOT = Path(r"D:\项目管理")
OUT_VIEWS = ROOT / "tmp" / "qingflow-tech" / "views"
OUT_FLOWS = ROOT / "tmp" / "qingflow-tech" / "flows"
CLI = Path(r"C:\Users\15675\AppData\Roaming\npm\qingflow.CMD")


def run(app_key: str, kind: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    cmd = ["qingflow", "--json", "builder"]
    if kind == "views":
        cmd += ["app", "get", "--app-key", app_key, "views"]
    else:
        cmd += ["flow", "get", "--app-key", app_key]
    if CLI.exists():
        cmd[0] = str(CLI)
    with dest.open("w", encoding="utf-8") as handle:
        subprocess.run(cmd, check=False, stdout=handle, stderr=subprocess.DEVNULL)


def main() -> None:
    for app_key, *_rest in KINDS:
        print(app_key, flush=True)
        run(app_key, "views", OUT_VIEWS / f"{app_key}.json")
        run(app_key, "flow", OUT_FLOWS / f"{app_key}.json")


if __name__ == "__main__":
    main()
