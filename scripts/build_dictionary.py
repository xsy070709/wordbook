#!/usr/bin/env python3
"""Convert the ECDICT CSV into the compact SQLite asset used by the app."""

from __future__ import annotations

import argparse
import csv
import sqlite3
from pathlib import Path


def build(source: Path, destination: Path) -> int:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        destination.unlink()

    connection = sqlite3.connect(destination)
    connection.execute("PRAGMA journal_mode=OFF")
    connection.execute("PRAGMA synchronous=OFF")
    connection.execute("PRAGMA page_size=4096")
    connection.execute(
        """
        CREATE TABLE words (
            word TEXT PRIMARY KEY COLLATE NOCASE,
            phonetic TEXT NOT NULL DEFAULT '',
            translation TEXT NOT NULL
        ) WITHOUT ROWID
        """
    )

    count = 0
    batch: list[tuple[str, str, str]] = []
    with source.open("r", encoding="utf-8-sig", newline="") as handle:
        for row in csv.DictReader(handle):
            word = (row.get("word") or "").strip()
            translation = (row.get("translation") or "").strip().replace("\\n", "\n")
            if not word or not translation:
                continue
            batch.append((word, (row.get("phonetic") or "").strip(), translation))
            if len(batch) >= 10_000:
                connection.executemany(
                    "INSERT OR IGNORE INTO words(word, phonetic, translation) VALUES (?, ?, ?)",
                    batch,
                )
                count += len(batch)
                batch.clear()

    if batch:
        connection.executemany(
            "INSERT OR IGNORE INTO words(word, phonetic, translation) VALUES (?, ?, ?)",
            batch,
        )
        count += len(batch)
    connection.commit()
    actual = connection.execute("SELECT COUNT(*) FROM words").fetchone()[0]
    connection.execute("VACUUM")
    connection.close()
    print(f"Imported {actual:,} dictionary entries ({count:,} source rows with translations).")
    return actual


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="Path to ECDICT ecdict.csv")
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("app/src/main/assets/database/dictionary.db"),
    )
    args = parser.parse_args()
    build(args.source, args.output)
