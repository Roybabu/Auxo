#!/usr/bin/env python3
"""Repository security checks run by CI. Prints file and line only, never the matched text."""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
files = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.splitlines()
problems = []


def fail(path, line, message):
    problems.append(f"{path}:{line}: {message}")
    print(f"::error file={path},line={line}::{message}")


SECRET_PATTERNS = {
    "private key block": re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    "AWS access key id": re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    "Google API key": re.compile(r"\bAIza[0-9A-Za-z_\-]{35}\b"),
    "GitHub token": re.compile(r"\b(?:ghp|gho|ghu|ghs|ghr|github_pat)_[0-9A-Za-z_]{20,}\b"),
    "Slack token": re.compile(r"\bxox[abprs]-[0-9A-Za-z\-]{10,}\b"),
    "hard-coded credential": re.compile(
        r"(?i)\b(?:password|passwd|secret|api[_-]?key|storePassword|keyPassword)\b\s*[=:]\s*[\"'][^\"'$\s]{6,}[\"']"
    ),
}
SECRET_FILES = re.compile(r"(?i)(\.jks|\.keystore|\.p12|\.pfx|\.pem|keystore\.properties|secrets\.properties|local\.properties|google-services\.json|\.env)$")
# Prohibited integration: the name is assembled here so this file does not contain it.
BLOCKED = re.compile("invoice" + "shelf", re.IGNORECASE)
SKIP_SELF = {".github/scripts/security_check.py"}

for path in files:
    if SECRET_FILES.search(path):
        fail(path, 1, "File type that normally holds secrets is tracked")
    if BLOCKED.search(path):
        fail(path, 1, "Prohibited integration name in a file name")
    full = ROOT / path
    if path in SKIP_SELF or not full.is_file():
        continue
    try:
        text = full.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    for number, line in enumerate(text.splitlines(), 1):
        for label, pattern in SECRET_PATTERNS.items():
            if pattern.search(line):
                fail(path, number, f"Possible {label}")
        if BLOCKED.search(line):
            fail(path, number, "Prohibited integration name")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
if re.search(r'android:debuggable\s*=\s*"true"', manifest):
    fail("app/src/main/AndroidManifest.xml", 1, "Manifest sets debuggable")
if re.search(r'android:usesCleartextTraffic\s*=\s*"true"', manifest):
    fail("app/src/main/AndroidManifest.xml", 1, "Manifest allows cleartext traffic")
if re.search(r'android:allowBackup\s*=\s*"true"', manifest):
    fail("app/src/main/AndroidManifest.xml", 1, "Manifest allows backup of the store database")
# Permissions that were reviewed and are needed. Anything else still fails the check.
#  - BLUETOOTH, BLUETOOTH_CONNECT: print receipts on the shop's paired Bluetooth printer. No scan, no location.
REVIEWED_PERMISSIONS = {"android.permission.BLUETOOTH", "android.permission.BLUETOOTH_CONNECT"}
for permission in re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', manifest):
    if permission in REVIEWED_PERMISSIONS:
        continue
    fail("app/src/main/AndroidManifest.xml", 1, f"Source manifest requests {permission}; review before adding permissions")

for workflow in sorted((ROOT / ".github/workflows").glob("*.y*ml")):
    name = workflow.relative_to(ROOT).as_posix()
    body = workflow.read_text(encoding="utf-8")
    if "pull_request_target" in body:
        fail(name, 1, "pull_request_target runs with write access; do not use it here")
    if not re.search(r"^permissions:", body, re.MULTILINE):
        fail(name, 1, "Workflow has no top-level permissions block")
    if re.search(r"permissions:\s*write-all", body):
        fail(name, 1, "write-all permissions")
    for number, line in enumerate(body.splitlines(), 1):
        if re.search(r"\$\{\{\s*github\.event\.(?:pull_request|issue|comment|head_commit)[^}]*\}\}", line) and "run:" in line:
            fail(name, number, "Untrusted event text used inside a run command")

if problems:
    print(f"{len(problems)} problem(s) found")
    sys.exit(1)
print(f"Checked {len(files)} tracked files: no secrets, prohibited names or unsafe settings found")
