#!/usr/bin/env python3
"""Small failure report without re-running builds or dumping environment/secrets."""
import glob
import os
import pathlib
import xml.etree.ElementTree as ET

print("### Virtual Volume — verification diagnostics\n")
print("```text")
for pattern in ("android/app/build/test-results/**/*.xml", "android/app/build/outputs/androidTest-results/**/*.xml"):
    for filename in sorted(glob.glob(pattern, recursive=True)):
        try:
            root = ET.parse(filename).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            for node in list(case.findall("failure")) + list(case.findall("error")):
                print(f"{case.get('classname')} > {case.get('name')}")
                print((node.get("message") or node.text or "")[:6000])
for report in glob.glob("android/app/build/reports/lint-results-*.txt"):
    lines = pathlib.Path(report).read_text().splitlines()
    for i, line in enumerate(lines):
        if ": Error:" in line:
            print("\n".join(lines[i:i + 7]))
log = pathlib.Path(os.getenv("RUNNER_TEMP", "/tmp")) / "android-checks.log"
if log.exists():
    lines = log.read_text(errors="replace").splitlines()
    errors = [line for line in lines if "e: " in line or "error:" in line or "FAILED" in line]
    print("\n".join(errors[:70]))
    print("\n".join(lines[-45:]))
print("```")
print("\nFull test, lint and emulator evidence is attached to this Actions run.")
