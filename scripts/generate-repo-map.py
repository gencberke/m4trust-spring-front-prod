#!/usr/bin/env python3
"""Regenerate docs/agent/repo-map.md from the current working tree."""

from __future__ import annotations

import re
import subprocess
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "agent" / "repo-map.md"

SKIP_DIRS = {
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


def git_head() -> tuple[str, str]:
    commit = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
    branch = subprocess.check_output(
        ["git", "branch", "--show-current"], cwd=ROOT, text=True
    ).strip()
    return commit, branch or "detached"


def iter_files() -> list[Path]:
    files: list[Path] = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT)
        if any(part in SKIP_DIRS for part in rel.parts):
            continue
        files.append(rel)
    return files


def rel_posix(path: Path) -> str:
    return path.as_posix()


def count_languages(files: list[Path]) -> list[tuple[str, int]]:
    counts = Counter(path.suffix for path in files)
    return [
        (ext, counts[ext])
        for ext in (".java", ".ts", ".tsx", ".py")
        if counts[ext]
    ]


def existing_build_entries() -> list[tuple[str, str]]:
    return [(label, rel) for label, rel in BUILD_PATTERNS if (ROOT / rel).is_file()]


def import_fan_in(files: list[Path]) -> list[tuple[int, str]]:
    frontend_ts = [
        rel
        for rel in files
        if rel.parts[0] == "frontend"
        and rel.suffix in {".ts", ".tsx"}
        and "generated" not in rel.parts
    ]
    fan_in: Counter[str] = Counter()
    import_re = re.compile(
        r"""from\s+['"]([^'"]+)['"]|import\s+['"]([^'"]+)['"]"""
    )

    for rel in frontend_ts:
        text = (ROOT / rel).read_text(encoding="utf-8", errors="ignore")
        for match in import_re.finditer(text):
            target = match.group(1) or match.group(2)
            if not target or target.startswith("."):
                continue
            if target.startswith("@/"):
                target = "frontend/src/" + target.removeprefix("@/")
            elif not target.startswith("frontend/"):
                target = f"frontend/src/{target}"
            if not target.endswith((".ts", ".tsx")):
                if (ROOT / f"{target}.ts").exists():
                    target = f"{target}.ts"
                elif (ROOT / f"{target}.tsx").exists():
                    target = f"{target}.tsx"
                else:
                    target = f"{target}.ts"
            fan_in[target] += 1

    ranked = [(count, module) for module, count in fan_in.items()]
    ranked.sort(key=lambda item: (-item[0], item[1]))
    return ranked[:6]


def largest_files(files: list[Path], limit: int = 12) -> list[tuple[int, str]]:
    sized = []
    for rel in files:
        try:
            size = (ROOT / rel).stat().st_size
        except OSError:
            continue
        lines = (ROOT / rel).read_text(encoding="utf-8", errors="ignore").count("\n") + 1
        sized.append((lines, rel_posix(rel)))
    sized.sort(key=lambda item: (-item[0], item[1]))
    return sized[:limit]


def has_test_signal(module: str, tests: list[Path]) -> bool:
    stem = Path(module).stem
    for test in tests:
        name = test.name.lower()
        if stem.lower() in name:
            return True
    return False


def top_level_tree() -> str:
    lines = []
    for entry in sorted(ROOT.iterdir(), key=lambda p: p.name):
        name = entry.name
        if name.startswith(".") and name not in {".claude"}:
            if name == ".DS_Store":
                lines.append(name)
            continue
        if name in SKIP_DIRS and name != ".claude":
            continue
        if entry.is_dir():
            lines.append(f"{name}/")
            children = sorted(entry.iterdir(), key=lambda p: p.name)[:8]
            for child in children:
                if child.is_dir():
                    lines.append(f"  {child.name}/")
                else:
                    lines.append(f"  {child.name}")
        else:
            lines.append(name)
    return "\n".join(lines)


def main() -> None:
    files = iter_files()
    commit, branch = git_head()
    generated = datetime.now().strftime("%Y-%m-%d %H:%M")
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
        f"- Generated: {generated}",
        f"- Commit: `{commit}` (branch `{branch}`)",
        f"- Root: `.` → `{ROOT.name}`",
        "",
        "> FRESHNESS: this map is derived from the commit above. If `git rev-parse HEAD`",
        "> differs, treat it as STALE and regenerate. The code is the source of truth;",
        "> this map is a disposable index — never let it override what the code says.",
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

    lines.extend(["", "## Tests", f"- Test files found: {len(tests)}", "- Coverage signal for top modules:"])
    for _, module in fan_in:
        signal = "test signal" if has_test_signal(module, tests) else "NO test signal"
        lines.append(f"  - `{module}` — {signal}")

    lines.extend(["", "## Top-level structure", "```", top_level_tree(), "```", ""])
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
