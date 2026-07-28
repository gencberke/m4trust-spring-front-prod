#!/usr/bin/env python3
"""Protect committed Flyway history locally and across a Git base revision."""

from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATIONS = Path("services/core-api/src/main/resources/db/migration")
LEDGER = ROOT / "scripts" / "flyway-checksums.sha256"
VERSIONED = re.compile(r"^V([1-9][0-9]*)__.+\.sql$")
SQL_MIGRATION = re.compile(r".+\.sql$")
ZERO_SHA = "0" * 40


def fail(message: str) -> None:
    raise ValueError(message)


def version(path: str | Path) -> int:
    name = Path(path).name
    match = VERSIONED.fullmatch(name)
    if not match:
        fail(f"invalid Flyway versioned filename: {path}")
    return int(match.group(1))


def migration_paths() -> list[Path]:
    sql_paths = sorted((ROOT / MIGRATIONS).glob("*.sql"))
    invalid = [path.relative_to(ROOT).as_posix() for path in sql_paths if not VERSIONED.fullmatch(path.name)]
    if invalid:
        fail(f"unsupported non-versioned Flyway SQL: {invalid}")
    paths = sorted(sql_paths, key=lambda path: version(path))
    versions = [version(path) for path in paths]
    if len(versions) != len(set(versions)):
        fail("duplicate Flyway version detected")
    return paths


def read_ledger() -> dict[str, str]:
    entries: dict[str, str] = {}
    for line_number, raw in enumerate(
        LEDGER.read_text(encoding="utf-8").splitlines(), start=1
    ):
        if not raw.strip():
            continue
        try:
            digest, relative = raw.split("  ", 1)
        except ValueError:
            fail(f"{LEDGER.relative_to(ROOT)}:{line_number}: invalid checksum line")
        if not re.fullmatch(r"[a-f0-9]{64}", digest):
            fail(f"{LEDGER.relative_to(ROOT)}:{line_number}: invalid SHA-256")
        if relative in entries:
            fail(f"duplicate checksum entry: {relative}")
        entries[relative] = digest
    return entries


def check_local() -> None:
    paths = migration_paths()
    current = {path.relative_to(ROOT).as_posix(): path for path in paths}
    recorded = read_ledger()
    if current.keys() != recorded.keys():
        missing = sorted(recorded.keys() - current.keys())
        unrecorded = sorted(current.keys() - recorded.keys())
        fail(f"checksum inventory mismatch; missing={missing}, unrecorded={unrecorded}")
    for relative, path in current.items():
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != recorded[relative]:
            fail(f"applied migration bytes changed: {relative}")


def git(*args: str) -> str:
    return subprocess.check_output(
        ["git", *args], cwd=ROOT, text=True, stderr=subprocess.STDOUT
    )


def check_base(base: str) -> None:
    if not base or base == ZERO_SHA:
        return
    git("cat-file", "-e", f"{base}^{{commit}}")
    base_files = [
        path
        for path in git("ls-tree", "-r", "--name-only", base, "--", str(MIGRATIONS))
        .splitlines()
        if SQL_MIGRATION.fullmatch(Path(path).name)
    ]
    invalid_base = [path for path in base_files if not VERSIONED.fullmatch(Path(path).name)]
    if invalid_base:
        fail(f"base contains unsupported non-versioned Flyway SQL: {invalid_base}")
    base_max = max((version(path) for path in base_files), default=0)
    changed = git(
        "diff",
        "--name-status",
        "--find-renames",
        base,
        "--",
        str(MIGRATIONS),
    ).splitlines()
    for line in changed:
        fields = line.split("\t")
        status = fields[0]
        paths = fields[1:]
        sql_paths = [path for path in paths if SQL_MIGRATION.fullmatch(Path(path).name)]
        if not sql_paths:
            continue
        invalid_paths = [path for path in sql_paths if not VERSIONED.fullmatch(Path(path).name)]
        if invalid_paths:
            fail(f"unsupported non-versioned Flyway SQL relative to {base}: {line}")
        if status != "A":
            fail(f"existing Flyway migration changed relative to {base}: {line}")
        added_version = version(sql_paths[0])
        if added_version <= base_max:
            fail(
                f"new migration V{added_version} must be above base maximum V{base_max}"
            )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--base",
        help="optional Git base SHA; existing migrations must be unchanged from it",
    )
    args = parser.parse_args()
    try:
        check_local()
        if args.base:
            check_base(args.base)
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        print(f"FAIL Flyway history: {error}", file=sys.stderr)
        return 1
    print("PASS Flyway history and checksums")
    return 0


if __name__ == "__main__":
    sys.exit(main())
