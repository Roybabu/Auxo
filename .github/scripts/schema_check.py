"""Fail the build when Room's generated schema differs from the committed one.

When it differs, each changed schema file is also published as gzip+base64
annotations, so the exact file can be recovered and committed without a local
Android build. Annotations are cut at 4 KB, so the text is split into numbered
parts: join them in order, then  base64 -d | gunzip > app/schemas/<path>
"""

import base64
import gzip
import subprocess
import sys

# GitHub keeps the first 4096 characters of an annotation.
PART_SIZE = 4000

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
    parts = [packed[i:i + PART_SIZE] for i in range(0, len(packed), PART_SIZE)]
    for number, part in enumerate(parts, start=1):
        print(f"::notice title=schema-file {path} part {number}/{len(parts)}::{part}")
sys.exit(1)
