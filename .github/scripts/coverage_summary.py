"""Prints unit test line coverage from the JaCoCo XML report as CI annotations and a job summary."""
import glob
import os
import sys
import xml.etree.ElementTree as ET

# Packages that hold money, stock and ledger rules; shown on their own.
KEY_PACKAGES = [
    "com/auxo/pos/domain/billing",
    "com/auxo/pos/domain/inventory",
    "com/auxo/pos/domain/customers",
    "com/auxo/pos/domain/purchasing",
    "com/auxo/pos/domain/reports",
    "com/auxo/pos/domain/usecase",
    "com/auxo/pos/data/repository",
    "com/auxo/pos/data/local",
    "com/auxo/pos/viewmodel",
]


def counter(element, kind):
    for c in element.findall("counter"):
        if c.get("type") == kind:
            return int(c.get("missed")), int(c.get("covered"))
    return 0, 0


def percent(missed, covered):
    total = missed + covered
    return 100.0 * covered / total if total else 0.0


def main():
    reports = glob.glob("app/build/reports/coverage/**/report.xml", recursive=True)
    if not reports:
        print("::error title=Coverage::No JaCoCo report.xml was produced")
        return 1
    root = ET.parse(reports[0]).getroot()
    rows = []
    for package in root.findall("package"):
        name = package.get("name")
        if name in KEY_PACKAGES:
            missed, covered = counter(package, "LINE")
            b_missed, b_covered = counter(package, "BRANCH")
            rows.append((name.replace("com/auxo/pos/", ""), missed, covered, b_missed, b_covered))
    all_missed, all_covered = counter(root, "LINE")
    all_b_missed, all_b_covered = counter(root, "BRANCH")

    lines = ["### Unit test coverage", "", "| Package | Lines | Line coverage | Branch coverage |", "|---|---|---|---|"]
    for name, missed, covered, b_missed, b_covered in sorted(rows):
        lines.append(f"| {name} | {covered}/{missed + covered} | {percent(missed, covered):.1f}% | {percent(b_missed, b_covered):.1f}% |")
    lines.append(
        f"| **whole app** | {all_covered}/{all_missed + all_covered} | {percent(all_missed, all_covered):.1f}% | {percent(all_b_missed, all_b_covered):.1f}% |"
    )
    text = "\n".join(lines)
    print(text)
    print(f"::notice title=Unit test coverage::{percent(all_missed, all_covered):.1f}% of lines, {percent(all_b_missed, all_b_covered):.1f}% of branches")
    for name, missed, covered, b_missed, b_covered in sorted(rows):
        print(f"::notice title=Coverage {name}::{percent(missed, covered):.1f}% of lines ({covered}/{missed + covered}), {percent(b_missed, b_covered):.1f}% of branches")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(text + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
