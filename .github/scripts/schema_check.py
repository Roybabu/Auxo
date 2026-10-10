"""Fail the build when Room's generated schema differs from the committed one.

When it differs, each changed schema file is also published as a gzip+base64
annotation, so the exact file can be recovered and committed without a local
Android build:  base64 -d <text> | gunzip > app/schemas/<path>
"""

import base64
import gzip
import subprocess
import sys

changed = subprocess.run(
    ["git", "status", "--porcelain", "--untracked-files=all", "--", "app/schemas"],
    capture_output=True, text=True, check=True,
).stdout.splitlines()

if not changed:
    print("Room schema matches the committed files.")
    sys.exit(0)

print("::error title=Room schema::The database schema changed. Commit the files in app/schemas (and add a migration).")
for line in changed:
    path = line[3:].strip()
    if not path.endswith(".json"):
        continue
    with open(path, "rb") as handle:
        packed = base64.b64encode(gzip.compress(handle.read(), mtime=0)).decode("ascii")
    print(f"::notice title=schema-file {path}::{packed}")
sys.exit(1)
