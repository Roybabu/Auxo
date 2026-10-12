"""Summarise build, unit-test, lint and APK results for a CI run.

Writes GitHub Actions annotations (visible on the run and pull request pages)
and a Markdown table to the job summary. Never fails the build itself.

With --device, summarises the emulator job instead: device test results and
problems from its build log.
"""

import collections
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

DEVICE = "--device" in sys.argv
UNIT_RESULTS = "app/build/test-results/testDebugUnitTest/*.xml"
DEVICE_RESULTS = "app/build/outputs/androidTest-results/connected/**/*.xml"
LINT_XML = "app/build/reports/lint-results-debug.xml"
APK_GLOB = "app/build/outputs/apk/debug/*.apk"
LOG_DIR = "build-logs"
MAX_ANNOTATION_CHARS = 20000


def escape(text):
    """Escape text for a GitHub Actions workflow command."""
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def short_path(text):
    return re.sub(r"file://|/home/runner/work/[^/]+/[^/]+/", "", text)


def read_log(name):
    path = os.path.join(LOG_DIR, name)
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read().splitlines()


def build_problems():
    """Compiler errors, compiler warnings and Gradle failure reasons from the captured logs."""
    errors, warnings = [], []
    for name in ("device.log",) if DEVICE else ("assemble.log", "test.log", "lint.log"):
        lines = read_log(name)
        for index, line in enumerate(lines):
            stripped = line.strip()
            if stripped.startswith("e: ") or "error:" in stripped.lower() and "AAPT" in stripped:
                errors.append(short_path(stripped))
            elif stripped.startswith("w: "):
                warnings.append(short_path(stripped))
            elif stripped == "* What went wrong:":
                block = [l.rstrip() for l in lines[index + 1:index + 12] if l.strip()]
                stop = next((i for i, l in enumerate(block) if l.startswith("* Try:")), len(block))
                errors.append("Gradle: " + " | ".join(short_path(l.strip()) for l in block[:stop]))
    return list(dict.fromkeys(errors)), list(dict.fromkeys(warnings))


def test_results(pattern):
    totals = collections.Counter()
    failures = []
    files = glob.glob(pattern, recursive=True)
    for path in files:
        suite = ET.parse(path).getroot()
        for key in ("tests", "failures", "errors", "skipped"):
            totals[key] += int(suite.get(key, 0))
        for case in suite.iter("testcase"):
            for problem in list(case.findall("failure")) + list(case.findall("error")):
                detail = (problem.text or problem.get("message") or "").strip().splitlines()[:8]
                failures.append(f"{case.get('classname')}.{case.get('name')}: " + " | ".join(detail))
    if not files:
        return "no results found", failures
    passed = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
    summary = (
        f"{totals['tests']} run, {passed} passed, {totals['failures']} failed, "
        f"{totals['errors']} errors, {totals['skipped']} skipped "
        f"({len(files)} {'devices' if DEVICE else 'classes'})"
    )
    return summary, failures


def lint():
    if not os.path.exists(LINT_XML):
        return "no report found"
    by_severity = collections.Counter()
    messages = collections.OrderedDict()
    for issue in ET.parse(LINT_XML).getroot().iter("issue"):
        by_severity[issue.get("severity", "Unknown")] += 1
        location = issue.find("location")
        where = ""
        if location is not None:
            where = f" ({short_path(location.get('file', ''))}:{location.get('line', '?')})"
        severity = issue.get("severity", "Unknown")
        label = ("ERROR " if severity in ("Error", "Fatal") else "") + issue.get("id", "?")
        messages.setdefault(label, issue.get("message", "").strip() + where)
    errors = by_severity["Error"] + by_severity["Fatal"]
    summary = f"{errors} errors, {by_severity['Warning']} warnings"
    # Errors first: they are what stops the build.
    ordered = sorted(messages.items(), key=lambda item: not item[0].startswith("ERROR "))
    for issue_id, message in ordered[:15]:
        summary += f" | {issue_id}: {message}"
    return summary


def apk():
    files = glob.glob(APK_GLOB)
    if not files:
        return "no APK found"
    return ", ".join(f"{os.path.basename(f)} ({os.path.getsize(f) / 1_048_576:.1f} MB)" for f in files)


def annotate(level, title, items):
    if not items:
        return
    text = "\n".join(items)
    if len(text) > MAX_ANNOTATION_CHARS:
        text = text[:MAX_ANNOTATION_CHARS] + "\n... (truncated)"
    print(f"::{level} title={title} ({len(items)})::{escape(text)}")


errors, warnings = build_problems()
test_summary, test_failures = test_results(DEVICE_RESULTS if DEVICE else UNIT_RESULTS)
annotate("error", "Build errors", errors)
# GitHub cuts an annotation at about 4 KB and shows only 10 per step, so failures are packed one short line each
# (test name and the first line of the reason) into a few annotations.
failure_title = "Device test failures" if DEVICE else "Test failures"
packed, chunk = [], ""
for failure in test_failures:
    name, _, reason = failure.partition(": ")
    parts = reason.split(' | ')
    frame = next((p.strip() for p in parts[1:] if 'com.auxo.pos' in p), '')
    line = f"{name.replace('com.auxo.pos.', '')}: {parts[0][:330]} {frame.replace('com.auxo.pos.', '')[:90]}\n"
    if len(chunk) + len(line) > 3800:
        packed.append(chunk)
        chunk = ""
    chunk += line
if chunk:
    packed.append(chunk)
for index, text in enumerate(packed[:9], 1):
    print(f"::error title={failure_title} {index}/{len(packed)}::{escape(text.rstrip())}")
annotate("warning", "Compiler warnings", warnings)

if DEVICE:
    rows = [("Device tests", test_summary)]
else:
    rows = [("Unit tests", test_summary), ("Lint", lint()), ("Debug APK", apk())]
for title, text in rows:
    print(f"::notice title={title}::{escape(text)}")

summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
if summary_path:
    with open(summary_path, "a", encoding="utf-8") as out:
        heading = "Auxo device tests" if DEVICE else "Auxo CI results"
        out.write(f"### {heading}\n\n| Check | Result |\n|---|---|\n")
        for title, text in rows:
            out.write(f"| {title} | {text.replace('|', '/')} |\n")
        if errors:
            out.write("\n**Build errors**\n\n```\n" + "\n".join(errors[:50]) + "\n```\n")
        if test_failures:
            out.write("\n**Test failures**\n\n```\n" + "\n".join(test_failures[:50]) + "\n```\n")
