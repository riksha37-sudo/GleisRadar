#!/usr/bin/env python3
"""Import a local GTFS ZIP into an auditable, offline S/U station-line index.

No network requests. Python standard library only. stop_times is streamed; a
scratch SQLite database stores trip joins and unique station/line relations.
Platform IDs/coordinates remain unchanged (parent IDs are metadata only).
"""
from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from functools import lru_cache
import hashlib
import io
import json
import math
from pathlib import Path
import sqlite3
import sys
import tempfile
from typing import Iterator
import zipfile


class ImportFailure(ValueError):
    """An invalid feed is rejected; the destination is not replaced."""


@dataclass(frozen=True)
class OperatorRule:
    agency_id: str
    agency_name: str
    mode: str
    route_types: tuple[str, ...]

    @classmethod
    def parse(cls, value: str) -> "OperatorRule":
        parts = value.split("|")
        if len(parts) != 4 or any(not p.strip() for p in parts):
            raise argparse.ArgumentTypeError("operator must be ID|exact agency name|S_BAHN or U_BAHN|route types separated by commas")
        rule = cls(parts[0], parts[1], parts[2], tuple(parts[3].split(",")))
        if rule.mode not in {"S_BAHN", "U_BAHN"} or any(not t.isdigit() for t in rule.route_types):
            raise argparse.ArgumentTypeError("invalid mode or numeric route type in operator rule")
        return rule


def _rows(z: zipfile.ZipFile, name: str, required: set[str], optional: bool = False) -> Iterator[dict[str, str]]:
    if name not in z.namelist():
        if optional:
            return
        raise ImportFailure(f"missing GTFS file: {name}")
    with z.open(name) as raw, io.TextIOWrapper(raw, encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if not required <= set(reader.fieldnames or []):
            raise ImportFailure(f"{name}: missing columns {sorted(required - set(reader.fieldnames or []))}")
        for number, row in enumerate(reader, 2):
            if None in row or any(v is None for v in row.values()):
                raise ImportFailure(f"{name}:{number}: malformed CSV row")
            yield row


def _date(value: str) -> date:
    try:
        return datetime.strptime(value, "%Y%m%d").date()
    except ValueError as e:
        raise ImportFailure(f"invalid GTFS service date: {value!r}") from e


def _services(z: zipfile.ZipFile, start: date | None, end: date | None) -> tuple[set[str], set[str], dict]:
    calendars: dict[str, tuple[date, date, tuple[bool, ...]]] = {}
    exceptions: dict[str, dict[date, str]] = {}
    coverage: list[date] = []
    weekdays = ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    for row in _rows(z, "calendar.txt", {"service_id", "start_date", "end_date", *weekdays}, optional=True):
        key = row["service_id"]
        if not key or key in calendars:
            raise ImportFailure(f"duplicate/empty calendar service_id: {key}")
        first, last = _date(row["start_date"]), _date(row["end_date"])
        if last < first or any(row[d] not in {"0", "1"} for d in weekdays):
            raise ImportFailure(f"invalid calendar for service {key}")
        calendars[key] = (first, last, tuple(row[d] == "1" for d in weekdays))
        coverage.extend((first, last))
    for row in _rows(z, "calendar_dates.txt", {"service_id", "date", "exception_type"}, optional=True):
        key, day, kind = row["service_id"], _date(row["date"]), row["exception_type"]
        if not key or kind not in {"1", "2"} or day in exceptions.get(key, {}):
            raise ImportFailure(f"invalid/duplicate calendar exception for service {key}")
        exceptions.setdefault(key, {})[day] = kind
        coverage.append(day)
    all_ids = set(calendars) | set(exceptions)
    if not all_ids:
        raise ImportFailure("calendar.txt/calendar_dates.txt contain no services")
    if start is None:
        active = all_ids
    else:
        assert end is not None
        active = set()
        for key in all_ids:
            first, last, days = calendars.get(key, (start, start - timedelta(days=1), (False,) * 7))
            additions = exceptions.get(key, {})
            cursor = start
            while cursor <= end:
                exception = additions.get(cursor)
                if exception == "1" or (exception != "2" and first <= cursor <= last and days[cursor.weekday()]):
                    active.add(key)
                    break
                cursor += timedelta(days=1)
    return all_ids, active, {"first": min(coverage).isoformat(), "last": max(coverage).isoformat()}


def _coordinate(row: dict[str, str]) -> tuple[float, float]:
    try:
        lat, lon = float(row["stop_lat"]), float(row["stop_lon"])
        if not math.isfinite(lat) or not math.isfinite(lon) or not (-90 <= lat <= 90 and -180 <= lon <= 180):
            raise ValueError()
        return lat, lon
    except (ValueError, KeyError) as e:
        raise ImportFailure(f"invalid coordinates at served stop {row.get('stop_id')}") from e


def import_feed(source: Path, destination: Path, rules: list[OperatorRule], *,
                region: str = "vbb", start: date | None = None, end: date | None = None,
                attribution: str, source_url: str = "", license_id: str = "UNVERIFIED",
                license_url: str = "") -> dict:
    """Validate, join, then atomically replace output. Return output metadata.

    No date window means a deliberately labelled draft union of all feed trips.
    A date window is inclusive, at most 366 days, and applies GTFS exceptions.
    A valid import does not itself grant data rights or prove current live service.
    """
    source, destination = Path(source), Path(destination)
    if source.resolve() == destination.resolve():
        raise ImportFailure("input and output must be different files")
    if not rules or not attribution.strip() or not region.strip():
        raise ImportFailure("explicit operator rules, region and attribution are required")
    if (start is None) != (end is None) or (start and end and not 0 <= (end - start).days < 366):
        raise ImportFailure("provide both dates in order, with a window of at most 366 days")
    for rule in rules:
        if rule.mode not in {"S_BAHN", "U_BAHN"} or not rule.route_types:
            raise ImportFailure("invalid operator mode or empty route types")
    rule_keys = [(r.agency_id, t) for r in rules for t in r.route_types]
    if len(set(rule_keys)) != len(rule_keys):
        raise ImportFailure("operator rules overlap")
    digest = hashlib.sha256()
    with source.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    with zipfile.ZipFile(source) as z, tempfile.TemporaryDirectory(prefix="gtfs-index-") as scratch:
        if len(z.namelist()) != len(set(z.namelist())):
            raise ImportFailure("duplicate ZIP member names")
        agencies: dict[str, str] = {}
        for row in _rows(z, "agency.txt", {"agency_id", "agency_name"}):
            key = row["agency_id"]
            if not key or key in agencies:
                raise ImportFailure(f"duplicate/empty agency_id: {key}")
            agencies[key] = row["agency_name"]
        for rule in rules:
            if agencies.get(rule.agency_id) != rule.agency_name:
                raise ImportFailure(f"operator {rule.agency_id}: expected {rule.agency_name!r}, found {agencies.get(rule.agency_id)!r}")
        mapping = {(r.agency_id, t): r for r in rules for t in r.route_types}
        lines: dict[tuple[str, str], int] = {}
        line_data: dict[int, dict] = {}
        line_operators: dict[tuple[str, str], str] = {}
        routes: dict[str, int | None] = {}
        selected_route_count = 0
        for row in _rows(z, "routes.txt", {"route_id", "agency_id", "route_short_name", "route_type"}):
            key = row["route_id"]
            if not key or key in routes or row["agency_id"] not in agencies:
                raise ImportFailure(f"invalid/duplicate route or agency reference: {key}")
            rule = mapping.get((row["agency_id"], row["route_type"]))
            line_id = None
            if rule:
                name = row["route_short_name"].strip()
                if not name:
                    raise ImportFailure(f"selected route {key} has no line name")
                line_key = (rule.mode, name)
                if line_key in line_operators and line_operators[line_key] != rule.agency_id:
                    raise ImportFailure(f"ambiguous line {rule.mode}/{name} across selected operators; select one operator or split regional indexes")
                line_operators[line_key] = rule.agency_id
                if line_key not in lines:
                    lines[line_key] = len(lines) + 1
                    line_data[lines[line_key]] = {"region": region, "mode": rule.mode, "name": name}
                line_id = lines[line_key]
                selected_route_count += 1
            routes[key] = line_id
        if not lines:
            raise ImportFailure("operator rules selected no S/U routes")
        all_services, active_services, calendar_range = _services(z, start, end)
        stops: dict[str, dict[str, str]] = {}
        for row in _rows(z, "stops.txt", {"stop_id", "stop_name", "stop_lat", "stop_lon"}):
            key = row["stop_id"]
            if not key or key in stops:
                raise ImportFailure(f"duplicate/empty stop_id: {key}")
            stops[key] = row
        for key, row in stops.items():
            parent = row.get("parent_station", "")
            if parent and (parent not in stops or parent == key):
                raise ImportFailure(f"stop {key}: missing/self parent_station {parent}")
        db = sqlite3.connect(str(Path(scratch) / "join.sqlite"))
        try:
            db.execute("PRAGMA journal_mode=OFF")
            db.execute("CREATE TABLE trips(id TEXT PRIMARY KEY, line INTEGER)")
            db.execute("CREATE TABLE station_lines(stop TEXT, line INTEGER, PRIMARY KEY(stop,line))")
            selected_trips = 0
            for row in _rows(z, "trips.txt", {"trip_id", "route_id", "service_id"}):
                if not row["trip_id"] or row["route_id"] not in routes or row["service_id"] not in all_services:
                    raise ImportFailure(f"trip {row['trip_id']}: invalid route/service reference")
                line = routes[row["route_id"]] if row["service_id"] in active_services else None
                try:
                    db.execute("INSERT INTO trips VALUES (?,?)", (row["trip_id"], line))
                except sqlite3.IntegrityError as e:
                    raise ImportFailure(f"duplicate trip_id: {row['trip_id']}") from e
                selected_trips += line is not None
            db.commit()

            @lru_cache(maxsize=8192)
            def trip_line(key: str) -> int | None:
                result = db.execute("SELECT line FROM trips WHERE id=?", (key,)).fetchone()
                if result is None:
                    raise ImportFailure(f"stop_times: unknown trip_id {key}")
                return result[0]

            rows_read = 0
            seen_selected_trips: set[str] = set()
            for row in _rows(z, "stop_times.txt", {"trip_id", "stop_id"}):
                rows_read += 1
                stop_id = row["stop_id"]
                if stop_id not in stops:
                    raise ImportFailure(f"stop_times: unknown stop_id {stop_id}")
                line = trip_line(row["trip_id"])
                if line is not None:
                    seen_selected_trips.add(row["trip_id"])
                    db.execute("INSERT OR IGNORE INTO station_lines VALUES (?,?)", (stop_id, line))
                if rows_read % 50000 == 0:
                    db.commit()
            db.commit()
            if len(seen_selected_trips) != selected_trips:
                raise ImportFailure("selected trips missing stop_times; incomplete feed")
            stations = []
            for (stop_id,) in db.execute("SELECT DISTINCT stop FROM station_lines ORDER BY stop"):
                row = stops[stop_id]
                lat, lon = _coordinate(row)
                if not row["stop_name"].strip():
                    raise ImportFailure(f"served stop {stop_id}: empty name")
                station_lines = [line_data[line] for (line,) in db.execute("SELECT line FROM station_lines WHERE stop=?", (stop_id,))]
                stations.append({"id": stop_id, "name": row["stop_name"], "lat": lat, "lon": lon,
                                 "parentStationId": row.get("parent_station") or None,
                                 "lines": sorted(station_lines, key=lambda x: (x["mode"], x["name"]))})
            if not stations:
                raise ImportFailure("no served S/U stations in selected service window")
        finally:
            db.close()
        metadata = {
            "schemaVersion": 1,
            "importerVersion": "1.0",
            "sourceFile": source.name,
            "sourceSha256": digest.hexdigest(),
            "sourceUrl": source_url or None,
            "attribution": attribution,
            "license": license_id,
            "licenseUrl": license_url or None,
            "importedAt": datetime.now(timezone.utc).isoformat(),
            "region": region,
            "operators": [{"id": r.agency_id, "name": r.agency_name, "mode": r.mode, "routeTypes": list(r.route_types)} for r in rules],
            "calendarRange": calendar_range,
            "serviceWindow": {"from": start.isoformat(), "through": end.isoformat()} if start else None,
            "indexPolicy": "service-window-union" if start else "draft-whole-feed-union",
            "geometryPolicy": "served platform/stop coordinates; parent metadata only; no provider ID crosswalk",
            "liveServiceVerified": False,
            "selectedRoutes": selected_route_count,
            "selectedTrips": selected_trips,
            "stopTimeRowsRead": rows_read,
            "stationCount": len(stations),
            "lineCount": len({(line["mode"], line["name"]) for s in stations for line in s["lines"]}),
        }
        payload = {"metadata": metadata, "stations": stations}
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=destination.parent, suffix=".tmp", delete=False) as out:
            temporary = Path(out.name)
            json.dump(payload, out, ensure_ascii=False, indent=2, allow_nan=False)
            out.write("\n")
        temporary.replace(destination)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    return metadata


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="local GTFS ZIP (never downloaded)")
    parser.add_argument("destination", type=Path)
    parser.add_argument("--operator", action="append", type=OperatorRule.parse, required=True,
                        help="ID|exact agency name|S_BAHN or U_BAHN|route types, e.g. '1|S-Bahn Berlin GmbH|S_BAHN|109'")
    parser.add_argument("--region", default="vbb")
    parser.add_argument("--from-date", type=date.fromisoformat)
    parser.add_argument("--through-date", type=date.fromisoformat)
    parser.add_argument("--attribution", required=True)
    parser.add_argument("--source-url", default="")
    parser.add_argument("--license", default="UNVERIFIED")
    parser.add_argument("--license-url", default="")
    args = parser.parse_args()
    try:
        metadata = import_feed(args.source, args.destination, args.operator, region=args.region,
                               start=args.from_date, end=args.through_date, attribution=args.attribution,
                               source_url=args.source_url, license_id=args.license, license_url=args.license_url)
    except (ImportFailure, OSError, zipfile.BadZipFile, UnicodeError, csv.Error, sqlite3.Error) as e:
        print(f"GTFS import rejected: {e}", file=sys.stderr)
        return 2
    print(json.dumps(metadata, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
