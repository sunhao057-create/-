# -*- coding: utf-8 -*-
from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(r"D:\项目管理")
OUT = ROOT / "tmp" / "qingflow-tech" / "layout"
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
]


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for key in KEYS:
        dest = OUT / f"{key}.layout.json"
        print(f"layout {key}", flush=True)
        cmd = (
            f'"C:\\Users\\15675\\AppData\\Roaming\\npm\\qingflow.CMD" --json builder app get layout '
            f"--app-key {key}"
        )
        try:
            raw = subprocess.check_output(cmd, cwd=str(ROOT), shell=True, stderr=subprocess.STDOUT)
            dest.write_bytes(raw)
            print(f"  ok {len(raw)} bytes", flush=True)
        except subprocess.CalledProcessError as ex:
            dest.write_bytes(ex.output or b"{}")
            print(f"  FAILED {ex.returncode}", flush=True)


if __name__ == "__main__":
    main()
