#!/usr/bin/env python3
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
UPDATE_JSON = ROOT / "update.json"
VERSION_PROPERTIES = ROOT / "version.properties"
REPORT = ROOT / "release-verification-report.txt"
EXPECTED_PACKAGE = "com.acceleratorer.wuwavn"
PLACEHOLDER_SHA = "put_real_apk_sha256_here"


def read_version_properties() -> dict[str, str]:
    result = {}
    for line in VERSION_PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_android_tool(name: str) -> str:
    found = shutil.which(name)
    if found:
        return found

    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if android_home:
        names = [name]
        if os.name == "nt" and not name.endswith(".exe"):
            names.append(f"{name}.exe")

        candidates = []
        for tool_name in names:
            candidates.extend(Path(android_home).glob(f"build-tools/*/{tool_name}"))
        if candidates:
            return str(sorted(candidates, reverse=True)[0])

    raise SystemExit(f"{name} not found. Install Android build-tools first.")


def run_command(command: list[str]) -> str:
    process = subprocess.run(
        command,
        cwd=ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if process.returncode != 0:
        raise SystemExit(f"Command failed: {' '.join(command)}\n{process.stdout}")
    return process.stdout


def parse_badging(text: str) -> dict[str, str]:
    package_line = next((line for line in text.splitlines() if line.startswith("package:")), "")
    if not package_line:
        raise SystemExit("aapt badging output does not contain package line.")

    def extract(name: str) -> str:
        match = re.search(rf"{name}='([^']+)'", package_line)
        if not match:
            raise SystemExit(f"Cannot extract {name} from package line: {package_line}")
        return match.group(1)

    return {
        "package": extract("name"),
        "versionCode": extract("versionCode"),
        "versionName": extract("versionName"),
    }


def manifest_debuggable(xmltree: str, badging: str) -> bool:
    if "application-debuggable" in badging:
        return True

    for line in xmltree.splitlines():
        if "android:debuggable" not in line:
            continue
        value = line.lower()
        if "0xffffffff" in value or "true" in value:
            return True
    return False


def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: tools/verify-release-apk.py path/to/release.apk")

    apk_path = Path(sys.argv[1]).resolve()
    if not apk_path.exists():
        raise SystemExit(f"APK not found: {apk_path}")

    version = read_version_properties()
    update = json.loads(UPDATE_JSON.read_text(encoding="utf-8"))
    app = update["app"]

    expected_name = version["VERSION_NAME"]
    expected_code = version["VERSION_CODE"]
    expected_sha = app["sha256"].lower().strip()
    actual_sha = sha256_file(apk_path)

    failures = []
    report = [
        "WUWA VN Release Verification",
        f"APK: {apk_path}",
        f"Expected package: {EXPECTED_PACKAGE}",
        f"Expected version: {expected_name} ({expected_code})",
        f"Actual SHA-256: {actual_sha}",
        f"update.json SHA-256: {expected_sha}",
    ]

    if expected_sha == PLACEHOLDER_SHA or not expected_sha:
        failures.append("update.json sha256 is still a placeholder.")
    elif actual_sha != expected_sha:
        failures.append(f"APK SHA-256 mismatch: update.json={expected_sha}, actual={actual_sha}")

    if app["version_name"] != expected_name:
        failures.append("update.json version_name does not match version.properties.")
    if str(app["version_code"]) != expected_code:
        failures.append("update.json version_code does not match version.properties.")

    aapt = find_android_tool("aapt")
    badging = run_command([aapt, "dump", "badging", str(apk_path)])
    badging_info = parse_badging(badging)
    xmltree = run_command([aapt, "dump", "xmltree", str(apk_path), "AndroidManifest.xml"])
    is_debuggable = manifest_debuggable(xmltree, badging)

    report.extend(
        [
            "",
            "APK badging:",
            f"Package: {badging_info['package']}",
            f"versionCode: {badging_info['versionCode']}",
            f"versionName: {badging_info['versionName']}",
            f"Debuggable: {is_debuggable}",
        ],
    )

    if badging_info["package"] != EXPECTED_PACKAGE:
        failures.append(f"APK package mismatch: expected {EXPECTED_PACKAGE}, got {badging_info['package']}")
    if badging_info["versionCode"] != expected_code:
        failures.append(f"APK versionCode mismatch: expected {expected_code}, got {badging_info['versionCode']}")
    if badging_info["versionName"] != expected_name:
        failures.append(f"APK versionName mismatch: expected {expected_name}, got {badging_info['versionName']}")
    if is_debuggable:
        failures.append("APK appears debuggable. Release APK must be debuggable=false.")

    permissions = [line for line in badging.splitlines() if line.startswith("uses-permission:")]
    report.append("")
    report.append("Permissions:")
    report.extend(permissions)

    if "android.permission.REQUEST_INSTALL_PACKAGES" in badging:
        failures.append("APK must not request REQUEST_INSTALL_PACKAGES.")

    report.append("")
    if failures:
        report.append("FAILED")
        report.extend(f"- {failure}" for failure in failures)
    else:
        report.append("PASSED")

    REPORT.write_text("\n".join(report) + "\n", encoding="utf-8")
    print(REPORT.read_text(encoding="utf-8"))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
