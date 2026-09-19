#!/usr/bin/env python3
"""Fail-closed inventory/export for PlexonSpawners 3.x managed-spawners.db.

This tool never mutates server/plugin data. It exists only to establish the pre-cutover
physical-record count and old logical quantity before a WildStacker-authoritative 4.0 migration.
"""

from __future__ import annotations

import argparse
import csv
import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class Record:
    record_id: str
    world_uuid: str
    x: int
    y: int
    z: int
    entity_type: str
    old_amount: int
    tier: int
    migration_state: str


def parse(path: Path) -> tuple[int, list[Record]]:
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines:
        raise ValueError("managed-spawners.db is empty")
    header = lines[0].strip()
    if header == "PLEXON_SPAWNERS_DB|1":
        schema = 1
    elif header == "PLEXON_SPAWNERS_DB|2":
        schema = 2
    else:
        raise ValueError(f"unsupported header: {header!r}")

    records: list[Record] = []
    ids: set[str] = set()
    locations: set[tuple[str, int, int, int]] = set()
    for line_no, raw in enumerate(lines[1:], start=2):
        raw = raw.strip()
        if not raw or raw.startswith("#"):
            continue
        fields = raw.split("|")
        expected = 12 if schema == 1 else 14
        if len(fields) != expected or fields[0] != "R":
            raise ValueError(f"line {line_no}: invalid schema-{schema} record shape")

        amount = 1 if schema == 1 else int(fields[12])
        if amount < 1:
            raise ValueError(f"line {line_no}: non-positive logical amount {amount}")
        record_id = fields[1]
        world_uuid = fields[2]
        x, y, z = int(fields[3]), int(fields[4]), int(fields[5])
        key = (world_uuid, x, y, z)
        if record_id in ids:
            raise ValueError(f"line {line_no}: duplicate record id {record_id}")
        if key in locations:
            raise ValueError(f"line {line_no}: duplicate physical location {key}")
        ids.add(record_id)
        locations.add(key)

        records.append(Record(
            record_id=record_id,
            world_uuid=world_uuid,
            x=x,
            y=y,
            z=z,
            entity_type=fields[6],
            old_amount=amount,
            tier=int(fields[8]),
            migration_state="PENDING" if schema == 1 else fields[13],
        ))
    return schema, records


def write_csv(path: Path, records: Iterable[Record]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(Record.__dataclass_fields__.keys()))
        writer.writeheader()
        for record in records:
            writer.writerow(asdict(record))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("database", type=Path)
    parser.add_argument("--csv", dest="csv_path", type=Path)
    parser.add_argument("--json", dest="json_path", type=Path)
    args = parser.parse_args()

    schema, records = parse(args.database)
    summary = {
        "result": "PASS",
        "schema": schema,
        "managed_physical_spawners": len(records),
        "old_logical_total": sum(record.old_amount for record in records),
        "records": [asdict(record) for record in records],
    }

    if args.csv_path:
        write_csv(args.csv_path, records)
    if args.json_path:
        args.json_path.parent.mkdir(parents=True, exist_ok=True)
        args.json_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")

    print(json.dumps({k: v for k, v in summary.items() if k != "records"}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
