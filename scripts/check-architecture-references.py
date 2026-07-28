#!/usr/bin/env python3
"""Reject live references to the deleted numbered ADR authority."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NUMBERED_ADR = re.compile(r"\bADR-[0-9]{3}\b")
FORBIDDEN_FILE = re.compile(r"\bFORBIDDEN\.md\b")
SKIP_PARTS = {".git", "node_modules", "target", ".venv", "dist", "__pycache__"}
PLAN_RECORD = Path("docs/plan/ready/phase-5-compact-adr-and-critical-validation.md")
TRANSITIONAL_CURRENT = Path("docs/plan/CURRENT.md")
CONTRACT_CHANGELOG = Path("contracts/CHANGELOG.md")


def candidates() -> list[Path]:
    output = subprocess.check_output(
        ["git", "ls-files", "-co", "--exclude-standard", "-z"],
        cwd=ROOT,
    )
    return [
        Path(raw.decode("utf-8"))
        for raw in output.split(b"\0")
        if raw and not any(part in SKIP_PARTS for part in Path(raw.decode()).parts)
    ]


def allowed(relative: Path) -> bool:
    return (
        relative.parts[:2] == ("docs", "history")
        or relative.parts[:3] == ("docs", "plan", "done")
        or relative
        in {
            PLAN_RECORD,
            TRANSITIONAL_CURRENT,
            CONTRACT_CHANGELOG,
            Path("scripts/check-architecture-references.py"),
        }
        or relative.parts[:7]
        == (
            "services",
            "core-api",
            "src",
            "main",
            "resources",
            "db",
            "migration",
        )
    )


def main() -> int:
    findings: list[str] = []
    for relative in candidates():
        path = ROOT / relative
        if not path.is_file() or allowed(relative):
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue
        for line_number, line in enumerate(text.splitlines(), start=1):
            if NUMBERED_ADR.search(line) or FORBIDDEN_FILE.search(line):
                findings.append(f"{relative}:{line_number}: {line.strip()}")
    if findings:
        print("FAIL live legacy architecture references:", file=sys.stderr)
        print("\n".join(findings), file=sys.stderr)
        return 1
    print("PASS no live numbered ADR or FORBIDDEN.md references")
    return 0


if __name__ == "__main__":
    sys.exit(main())
