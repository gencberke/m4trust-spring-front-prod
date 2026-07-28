#!/usr/bin/env python3
"""Scan Markdown files for unresolved relative references.

Checks:
1. Inline links: [text](path)
2. Repo-root-style paths in backticks: docs/..., services/..., scripts/..., etc.

Known non-defect plaintext references (intentionally extensionless or historical)
are reported separately and do not fail the run.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP_FILES = {"FIX-PLAN.md", "HANDOFF.md", "TEMP-REPO-CLEANUP-REPORT.md"}
SKIP_DIRS = {
    ".git",
    "node_modules",
    ".venv",
    "target",
    "dist",
    "build",
    "__pycache__",
    ".pytest_cache",
}

MARKDOWN_LINK = re.compile(r"(?<!!)\[([^\]]*)\]\(([^)]+)\)")
BACKTICK = re.compile(r"`([^`]+)`")

REPO_PATH_PREFIXES = (
    "docs/",
    "services/",
    "scripts/",
    "tools/",
    "contracts/",
    "frontend/",
    "infra/",
    "architecture-decisions/",
)

KNOWN_NON_DEFECTS: frozenset[tuple[str, int, str]] = frozenset(
    {
        (
            "docs/history/hackathon-2026-07/done/00-platform-foundation.md",
            73,
            "services/core-api/README",
        ),
        (
            "docs/history/hackathon-2026-07/done/00-platform-foundation.md",
            82,
            "scripts/dev-reset",
        ),
        (
            "docs/history/hackathon-2026-07/done/00-platform-foundation.md",
            82,
            "scripts/dev-seed",
        ),
        (
            "docs/history/hackathon-2026-07/done/15-railway-demo-reconciliation-and-deployment.md",
            4,
            "docs/plan/ready/15-production-reconciliation-and-readiness.md",
        ),
    }
)


@dataclass(frozen=True)
class Reference:
    file: str
    line: int
    target: str
    kind: str


def is_external(url: str) -> bool:
    return url.startswith(("http://", "https://", "mailto:", "#", "tel:"))


def normalize_path(raw: str) -> str:
    return raw.split("#", 1)[0].strip().rstrip("/")


def resolve_candidates(from_file: Path, path_part: str) -> list[Path]:
    relative = (from_file.parent / path_part).resolve()
    repo_rooted = (ROOT / path_part).resolve()
    candidates = [relative, repo_rooted]
    for base in (relative, repo_rooted):
        candidates.extend(
            Path(str(base) + ext)
            for ext in (".md", ".sh", ".ps1", ".json", ".yaml", ".yml")
        )
    return candidates


def exists_target(
    from_file: Path, raw: str, *, allow_extensions: bool = True
) -> bool:
    path_part = normalize_path(raw)
    if not path_part or is_external(path_part):
        return True
    if any(char in path_part for char in "*?|"):
        return True
    candidates = resolve_candidates(from_file, path_part)
    if not allow_extensions:
        candidates = candidates[:2]
    return any(candidate.exists() for candidate in candidates)


def is_repo_backtick_path(value: str) -> bool:
    value = value.strip()
    if not value or " " in value or "\t" in value:
        return False
    if value.startswith(("/", "npm ", "mvn ", "POST ", "GET ", "PATCH ")):
        return False
    if value.endswith(("/**", "/*")):
        return False
    return value.startswith(REPO_PATH_PREFIXES)


def iter_markdown_files() -> list[Path]:
    files: list[Path] = []
    for path in sorted(ROOT.rglob("*.md")):
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.name in SKIP_FILES:
            continue
        files.append(path)
    return files


def collect_references() -> list[Reference]:
    references: list[Reference] = []
    for md in iter_markdown_files():
        rel = str(md.relative_to(ROOT))
        text = md.read_text(encoding="utf-8")
        for lineno, line in enumerate(text.splitlines(), start=1):
            for match in MARKDOWN_LINK.finditer(line):
                url = match.group(2).strip().split()[0]
                references.append(Reference(rel, lineno, url, "markdown"))
            for match in BACKTICK.finditer(line):
                value = match.group(1).strip()
                if is_repo_backtick_path(value):
                    references.append(Reference(rel, lineno, value, "plaintext"))
    return references


def is_known_non_defect(ref: Reference) -> bool:
    return (ref.file, ref.line, ref.target) in KNOWN_NON_DEFECTS


def main() -> int:
    broken: list[Reference] = []
    known: list[Reference] = []

    for ref in collect_references():
        if is_known_non_defect(ref):
            known.append(ref)
            continue
        allow_extensions = ref.kind == "markdown"
        if not exists_target(ROOT / ref.file, ref.target, allow_extensions=allow_extensions):
            broken.append(ref)

    exit_code = 0

    print("=== Broken references ===")
    if broken:
        exit_code = 1
        for ref in broken:
            print(f"{ref.file}:{ref.line} [{ref.kind}] {ref.target}")
    else:
        print("(none)")

    print("\n=== Known non-defect references ===")
    missing_known = KNOWN_NON_DEFECTS - {(r.file, r.line, r.target) for r in known}
    if missing_known:
        exit_code = 1
        for item in sorted(missing_known):
            print(f"ERROR: documented non-defect not found: {item[0]}:{item[1]} {item[2]}")
    for ref in sorted(known, key=lambda r: (r.file, r.line, r.target)):
        print(f"{ref.file}:{ref.line} [{ref.kind}] {ref.target}")

    print(f"\nBroken references: {len(broken)}")
    print(f"Known non-defect references: {len(known)}")
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
