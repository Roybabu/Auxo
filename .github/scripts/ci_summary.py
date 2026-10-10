"""Summarise unit-test, lint and APK results for a CI run.

Writes GitHub Actions annotations (visible on the run page and via the API)
and a Markdown table to the job summary. Never fails the build itself.
"""

import collections
import glob
import os
import xml.etree.ElementTree as ET

TEST_RESULTS = "app/build/test-results/testDebugUnitTest/*.xml"
LINT_XML = "app/build/reports/lint-results-debug.xml"
APK_GLOB = "app/build/outputs/apk/debug/*.apk"


def unit_tests():
    totals = collections.Counter()
    files = glob.glob(TEST_RESULTS)
    for path in files:
        suite = ET.parse(path).getroot()
        for key in ("tests", "failures", "errors", "skipped"):
            totals[key] += int(suite.get(key, 0))
    if not files:
        return "no results found"
    passed = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    return (
        f"{totals['tests']} run, {passed} passed, {totals['failures']} failed, "
        f"{totals['errors']} errors, {totals['skipped']} skipped ({len(files)} classes)"
    )


def lint():
    if not os.path.exists(LINT_XML):
        return "no report found"
    by_severity = collections.Counter()
    ids = collections.Counter()
    for issue in ET.parse(LINT_XML).getroot().iter("issue"):
        by_severity[issue.get("severity", "Unknown")] += 1
        ids[issue.get("id", "?")] += 1
    errors = by_severity["Error"] + by_severity["Fatal"]
    summary = f"{errors} errors, {by_severity['Warning']} warnings"
    if ids:
        summary += " (" + ", ".join(f"{k} x{v}" for k, v in ids.most_common(10)) + ")"
    return summary


def apk():
    files = glob.glob(APK_GLOB)
    if not files:
        return "no APK found"
    return ", ".join(f"{os.path.basename(f)} ({os.path.getsize(f) / 1_048_576:.1f} MB)" for f in files)


rows = [("Unit tests", unit_tests()), ("Lint", lint()), ("Debug APK", apk())]

for title, text in rows:
    print(f"::notice title={title}::{text}")

summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
if summary_path:
    with open(summary_path, "a", encoding="utf-8") as out:
        out.write("### Invision CI results\n\n| Check | Result |\n|---|---|\n")
        for title, text in rows:
            out.write(f"| {title} | {text} |\n")
