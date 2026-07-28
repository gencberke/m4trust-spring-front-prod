#!/usr/bin/env python3
"""Regenerate docs/agent/repo-map.md from a Git-aware, reproducible file set.

Freshness rule: committed output is current only when running this generator
produces no diff. The map intentionally omits wall-clock time, branch name,
HEAD SHA, and absolute machine paths.
"""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "agent" / "repo-map.md"

SKIP_DIR_NAMES = {
    ".git",
    ".worktrees",
    "node_modules",
    "target",
    ".venv",
    "dist",
    "build",
    ".idea",
    ".vscode",
    ".claude",
    "__pycache__",
    ".pytest_cache",
}

# Temporary root handoff artifacts that must not pollute the committed map.
SKIP_FILE_NAMES = {
    "FIX-PLAN.md",
    "HANDOFF.md",
    "TEMP-REPO-CLEANUP-REPORT.md",
}

LANG_EXTS = {
    ".java": "Java",
    ".ts": "TypeScript",
    ".tsx": "TSX",
    ".py": "Python",
}

BUILD_PATTERNS = [
    ("Python", "tools/mock-ai-worker/requirements.txt"),
    ("Docker", "tools/mock-ai-worker/Dockerfile"),
    ("Docker", "tools/moka-emulator/Dockerfile"),
    ("Docker", "frontend/Dockerfile"),
    ("Node/JS", "frontend/package.json"),
    ("Python", "contracts/requirements.txt"),
    ("Docker", "services/core-api/Dockerfile"),
    ("Java/Maven", "services/core-api/pom.xml"),
]

ENTRY_POINTS = [
    "frontend/src/main.tsx",
    "tools/mock-ai-worker/src/m4trust_mock_worker/__main__.py",
    "tools/moka-emulator/src/m4trust_moka_emulator/__main__.py",
    "tools/moka-emulator/src/m4trust_moka_emulator/server.py",
]


def git_ls_candidate_files() -> list[Path]:
    """Tracked files plus non-ignored untracked candidates (Git-aware set)."""
    listed = subprocess.check_output(
        ["git", "ls-files", "-co", "--exclude-standard", "-z"],
        cwd=ROOT,
        text=True,
    )
    files: list[Path] = []
    for raw in listed.split("\0"):
        if not raw:
            continue
        rel = Path(raw)
        if any(part in SKIP_DIR_NAMES for part in rel.parts):
            continue
        if rel.name in SKIP_FILE_NAMES:
            continue
        if not (ROOT / rel).is_file():
            continue
        files.append(rel)
    files.sort(key=lambda path: path.as_posix())
    return files


def rel_posix(path: Path) -> str:
    return path.as_posix()


def count_languages(files: list[Path]) -> list[tuple[str, int]]:
    counts = Counter(path.suffix for path in files)
    return [(ext, counts[ext]) for ext in (".java", ".ts", ".tsx", ".py") if counts[ext]]


def existing_build_entries() -> list[tuple[str, str]]:
    return [(label, rel) for label, rel in BUILD_PATTERNS if (ROOT / rel).is_file()]


def import_fan_in(files: list[Path]) -> list[tuple[int, str]]:
    frontend_ts = [
        rel
        for rel in files
        if rel.parts
        and rel.parts[0] == "frontend"
        and rel.suffix in {".ts", ".tsx"}
        and "generated" not in rel.parts
    ]
    fan_in: Counter[str] = Counter()
    import_re = re.compile(r"""from\s+['"]([^'"]+)['"]|import\s+['"]([^'"]+)['"]""")

    def resolve(base: Path, specifier: str) -> str | None:
        if specifier.startswith("@/"):
            candidate = Path("frontend/src") / specifier.removeprefix("@/")
        elif specifier.startswith("."):
            candidate = Path(os.path.normpath(base / specifier))
        else:
            return None
        for suffix in ("", ".ts", ".tsx", "/index.ts", "/index.tsx"):
            probe = candidate.as_posix() + suffix
            if (ROOT / probe).is_file():
                return probe
        return None

    for rel in frontend_ts:
        text = (ROOT / rel).read_text(encoding="utf-8", errors="ignore")
        for match in import_re.finditer(text):
            specifier = match.group(1) or match.group(2)
            if not specifier:
                continue
            target = resolve(rel.parent, specifier)
            if target is not None:
                fan_in[target] += 1

    ranked = [(count, module) for module, count in fan_in.items()]
    ranked.sort(key=lambda item: (-item[0], item[1]))
    return ranked[:6]


def largest_files(files: list[Path], limit: int = 12) -> list[tuple[int, str]]:
    sized = []
    for rel in files:
        try:
            text = (ROOT / rel).read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        lines = text.count("\n") + (0 if text.endswith("\n") or not text else 1)
        if not text:
            lines = 0
        sized.append((lines, rel_posix(rel)))
    sized.sort(key=lambda item: (-item[0], item[1]))
    return sized[:limit]


def has_test_signal(module: str, tests: list[Path]) -> bool:
    stem = Path(module).stem
    for test in tests:
        if stem.lower() in test.name.lower():
            return True
    return False


def top_level_tree(files: list[Path]) -> str:
    """Deterministic top-level listing derived from the Git-aware file set."""
    top_entries: set[str] = set()
    children: dict[str, set[str]] = {}
    for rel in files:
        parts = rel.parts
        if not parts:
            continue
        top = parts[0]
        if (ROOT / top).is_dir():
            top_entries.add(f"{top}/")
            if len(parts) > 1:
                child = parts[1]
                child_path = ROOT / top / child
                children.setdefault(f"{top}/", set()).add(
                    f"{child}/" if child_path.is_dir() else child
                )
        else:
            top_entries.add(top)

    lines: list[str] = []
    for entry in sorted(top_entries):
        lines.append(entry)
        if entry.endswith("/"):
            for child in sorted(children.get(entry, set()))[:8]:
                lines.append(f"  {child}")
    return "\n".join(lines)


def render() -> str:
    files = git_ls_candidate_files()
    langs = count_languages(files)
    build = existing_build_entries()
    fan_in = import_fan_in(files)
    largest = largest_files(files)
    tests = [
        rel
        for rel in files
        if "test" in rel.parts
        or rel.name.endswith("Test.java")
        or rel.name.endswith(".test.ts")
        or rel.name.endswith(".test.tsx")
        or rel.name.endswith(".spec.ts")
        or rel.name.endswith(".spec.tsx")
    ]

    lines = [
        "# Repository Map (deterministic)",
        "",
        "- Freshness: regenerate with `python3 scripts/generate-repo-map.py`;",
        "  committed output is current only when that command produces no diff.",
        "- Source set: Git-tracked files plus non-ignored untracked candidates",
        "  (`git ls-files -co --exclude-standard`), excluding build caches and",
        "  virtual environments.",
        "",
        "## Languages",
    ]
    for ext, count in langs:
        lines.append(f"- `{ext}`: {count} files")

    lines.extend(["", "## Build / tooling"])
    for label, rel in build:
        lines.append(f"- {label}: `{rel}`")

    lines.extend(["", "## Entry points (heuristic)"])
    for rel in ENTRY_POINTS:
        if (ROOT / rel).is_file():
            lines.append(f"- `{rel}`")

    lines.extend(
        [
            "",
            "## Load-bearing modules — by import fan-in",
            "_How many internal files import each module. High fan-in = high blast radius =",
            "prime test candidate (feeds test-driven-development)._",
            "",
        ]
    )
    for count, module in fan_in:
        lines.append(f"- {count}× `{module}`")

    lines.extend(["", "## Largest files (complexity hotspots)"])
    for line_count, rel in largest:
        lines.append(f"- {line_count} lines · `{rel}`")

    lines.extend(
        [
            "",
            "## Tests",
            f"- Test files found: {len(tests)}",
            "- Coverage signal for top modules:",
        ]
    )
    for _, module in fan_in:
        signal = "test signal" if has_test_signal(module, tests) else "NO test signal"
        lines.append(f"  - `{module}` — {signal}")

    lines.extend(["", "## Top-level structure", "```", top_level_tree(files), "```", ""])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check",
        action="store_true",
        help="compare the generated map with the committed file without writing it",
    )
    args = parser.parse_args()
    rendered = render()
    if args.check:
        committed = OUT.read_text(encoding="utf-8") if OUT.exists() else ""
        if committed != rendered:
            print(
                "Repository map is stale; run "
                "`python3 scripts/generate-repo-map.py` and commit the result.",
                file=sys.stderr,
            )
            return 1
        print("Repository map is current")
        return 0

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(rendered, encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
