#!/usr/bin/env python3
"""Convert FirepadCN/pocket_dict_5000 Dart data into the app SQLite asset."""

from __future__ import annotations

import argparse
import ast
import re
import sqlite3
from pathlib import Path


ENTRY = re.compile(
    r"^\s*('(?:\\.|[^'])*'):\s*CommonWordEntry\("
    r"word:\s*('(?:\\.|[^'])*'),\s*"
    r"pronunciation:\s*('(?:\\.|[^'])*'),\s*"
    r"meaning:\s*('(?:\\.|[^'])*')\),\s*$"
)


def parse_dart_string(value: str) -> str:
    return ast.literal_eval(value)


def build(source: Path, destination: Path) -> int:
    entries: list[tuple[str, str, str]] = []
    for line in source.read_text(encoding="utf-8").splitlines():
        match = ENTRY.match(line)
        if not match:
            continue
        key, _display_word, pronunciation, meaning = map(parse_dart_string, match.groups())
        entries.append((key, pronunciation, meaning))

    if len(entries) < 4_000:
        raise ValueError(f"Only parsed {len(entries)} entries; input format may have changed")

    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        destination.unlink()
    connection = sqlite3.connect(destination)
    connection.execute(
        "CREATE TABLE words (word TEXT PRIMARY KEY COLLATE NOCASE, "
        "phonetic TEXT NOT NULL DEFAULT '', translation TEXT NOT NULL) WITHOUT ROWID"
    )
    connection.executemany("INSERT OR IGNORE INTO words VALUES (?, ?, ?)", entries)
    connection.commit()
    count = connection.execute("SELECT COUNT(*) FROM words").fetchone()[0]
    connection.execute("VACUUM")
    connection.close()
    print(f"Built pocket dictionary with {count:,} searchable forms: {destination}")
    return count


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/database/dictionary.db"),
    )
    args = parser.parse_args()
    build(args.source, args.output)
