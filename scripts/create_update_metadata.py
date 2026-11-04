#!/usr/bin/env python3
"""Create signed-release update metadata consumed by UpdateManager in the Android app."""

import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def properties(path: Path) -> dict[str, str]:
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            key, value = line.split("=", 1)
            result[key] = value
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository", default="zigorminsk-debug/pdd")
    args = parser.parse_args()

    if not args.tag.startswith("v"):
        raise ValueError("Release tag must start with 'v'")
    version = properties(ROOT / "version.properties")
    apk_hash = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    metadata = {
        "packageName": "by.pdd.study",
        "versionCode": int(version["versionCode"]),
        "versionName": version["versionName"],
        "apkUrl": f"https://github.com/{args.repository}/releases/download/{args.tag}/app-release.apk",
        "sha256": apk_hash,
        "notes": (ROOT / "RELEASE_NOTES.md").read_text(encoding="utf-8").strip(),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Update metadata: {version['versionName']} ({version['versionCode']}), SHA-256 {apk_hash}")


if __name__ == "__main__":
    main()
