import csv
from datetime import date
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

MODULE_PATH = Path(__file__).resolve().parents[1] / "import_gtfs.py"
SPEC = importlib.util.spec_from_file_location("import_gtfs", MODULE_PATH)
gtfs = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = gtfs
SPEC.loader.exec_module(gtfs)


class ImportTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.source, self.output = self.directory / "feed.zip", self.directory / "index.json"
        self.rules = [gtfs.OperatorRule("1", "S-Bahn Berlin GmbH", "S_BAHN", ("109",)),
                      gtfs.OperatorRule("796", "Berliner Verkehrsbetriebe", "U_BAHN", ("400",))]
        self.tables = {
            "agency.txt": [
                {"agency_id": "1", "agency_name": "S-Bahn Berlin GmbH"},
                {"agency_id": "796", "agency_name": "Berliner Verkehrsbetriebe"},
                {"agency_id": "108", "agency_name": "DB Regio"}],
            "routes.txt": [
                {"route_id": "s-a", "agency_id": "1", "route_short_name": "S5", "route_type": "109"},
                {"route_id": "s-b", "agency_id": "1", "route_short_name": "S5", "route_type": "109"},
                {"route_id": "u", "agency_id": "796", "route_short_name": "U2", "route_type": "400"},
                {"route_id": "sev", "agency_id": "796", "route_short_name": "U2", "route_type": "700"},
                {"route_id": "other-s", "agency_id": "108", "route_short_name": "S5", "route_type": "109"}],
            "trips.txt": [
                {"trip_id": "sa", "route_id": "s-a", "service_id": "daily", "direction_id": "0"},
                {"trip_id": "sb", "route_id": "s-b", "service_id": "daily", "direction_id": "1"},
                {"trip_id": "u", "route_id": "u", "service_id": "daily", "direction_id": "0"},
                {"trip_id": "sev", "route_id": "sev", "service_id": "daily", "direction_id": "0"},
                {"trip_id": "other-s", "route_id": "other-s", "service_id": "daily", "direction_id": "0"}],
            "stops.txt": [
                {"stop_id": "parent", "stop_name": "Station", "stop_lat": "", "stop_lon": "", "parent_station": ""},
                {"stop_id": "near", "stop_name": "Near platform", "stop_lat": "52.52", "stop_lon": "13.4", "parent_station": "parent"},
                {"stop_id": "far", "stop_name": "Far branch", "stop_lat": "52.8", "stop_lon": "13.9", "parent_station": ""},
                {"stop_id": "bus", "stop_name": "Replacement bus", "stop_lat": "52.5", "stop_lon": "13.4", "parent_station": ""},
                {"stop_id": "other", "stop_name": "Outside operator", "stop_lat": "52.5", "stop_lon": "13.4", "parent_station": ""}],
            "stop_times.txt": [
                {"trip_id": "sa", "stop_id": "near"}, {"trip_id": "sb", "stop_id": "far"},
                {"trip_id": "sb", "stop_id": "near"}, {"trip_id": "u", "stop_id": "near"},
                {"trip_id": "sev", "stop_id": "bus"}, {"trip_id": "other-s", "stop_id": "other"}],
            "calendar.txt": [{"service_id": "daily", **{d: "1" for d in ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]},
                              "start_date": "20261101", "end_date": "20261231"}],
        }

    def write_feed(self):
        with zipfile.ZipFile(self.source, "w") as archive:
            for filename, rows in self.tables.items():
                if not rows:
                    continue
                stream = io.StringIO()
                writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
                writer.writeheader()
                writer.writerows(rows)
                archive.writestr(filename, stream.getvalue())

    def run_import(self, **kwargs):
        self.write_feed()
        metadata = gtfs.import_feed(self.source, self.output, self.rules, attribution="Synthetic test data", **kwargs)
        return metadata, json.loads(self.output.read_text())

    def test_all_directions_variants_join_and_line_dedup(self):
        metadata, result = self.run_import()
        stops = {s["id"]: s for s in result["stations"]}
        self.assertEqual(set(stops), {"near", "far"})
        self.assertEqual([l["name"] for l in stops["near"]["lines"]], ["S5", "U2"])
        self.assertEqual(stops["far"]["lines"][0]["name"], "S5")
        self.assertEqual(metadata["selectedRoutes"], 3)
        self.assertEqual(metadata["lineCount"], 2)
        self.assertEqual(metadata["indexPolicy"], "draft-whole-feed-union")
        self.assertFalse(metadata["liveServiceVerified"])
        self.assertEqual(len(metadata["sourceSha256"]), 64)

    def test_sev_and_other_operator_same_type_and_line_name_are_excluded(self):
        _, result = self.run_import()
        self.assertNotIn("bus", [s["id"] for s in result["stations"]])
        self.assertNotIn("other", [s["id"] for s in result["stations"]])

    def test_real_platform_coordinate_preserved_despite_empty_parent_coordinate(self):
        _, result = self.run_import()
        stop = next(s for s in result["stations"] if s["id"] == "near")
        self.assertEqual((stop["lat"], stop["lon"], stop["parentStationId"]), (52.52, 13.4, "parent"))

    def test_exact_operator_identity_required(self):
        self.tables["agency.txt"][0]["agency_name"] = "Different operator"
        with self.assertRaisesRegex(gtfs.ImportFailure, "expected"):
            self.run_import()

    def test_calendar_addition_and_cancellation_applied(self):
        self.tables["calendar_dates.txt"] = [
            {"service_id": "daily", "date": "20261117", "exception_type": "2"},
            {"service_id": "special", "date": "20261117", "exception_type": "1"}]
        self.tables["trips.txt"][0]["service_id"] = "special"
        metadata, result = self.run_import(start=date(2026, 11, 17), end=date(2026, 11, 17))
        self.assertEqual([s["id"] for s in result["stations"]], ["near"])
        self.assertEqual(result["stations"][0]["lines"][0]["name"], "S5")
        self.assertEqual(metadata["selectedTrips"], 1)
        self.assertEqual(metadata["indexPolicy"], "service-window-union")

    def test_calendar_weekday_and_future_service_do_not_leak(self):
        self.tables["calendar.txt"][0]["tuesday"] = "0"
        with self.assertRaisesRegex(gtfs.ImportFailure, "no served"):
            self.run_import(start=date(2026, 11, 17), end=date(2026, 11, 17))

    def test_calendar_dates_without_calendar_supported(self):
        del self.tables["calendar.txt"]
        self.tables["calendar_dates.txt"] = [{"service_id": "daily", "date": "20261117", "exception_type": "1"}]
        _, result = self.run_import(start=date(2026, 11, 17), end=date(2026, 11, 17))
        self.assertEqual(len(result["stations"]), 2)

    def test_missing_parent_rejects_and_preserves_previous_output(self):
        self.tables["stops.txt"][1]["parent_station"] = "missing"
        self.output.write_text("previous complete index")
        with self.assertRaisesRegex(gtfs.ImportFailure, "parent_station"):
            self.run_import()
        self.assertEqual(self.output.read_text(), "previous complete index")

    def test_missing_trip_stop_route_service_references_rejected(self):
        for table, column in [("stop_times.txt", "trip_id"), ("stop_times.txt", "stop_id"), ("trips.txt", "route_id"), ("trips.txt", "service_id")]:
            with self.subTest(table=table, column=column):
                previous = self.tables[table][0][column]
                self.tables[table][0][column] = "missing"
                with self.assertRaises(gtfs.ImportFailure):
                    self.run_import()
                self.tables[table][0][column] = previous

    def test_nonfinite_or_out_of_range_coordinates_rejected(self):
        for value in ["NaN", "Infinity", "91", "", "nonsense"]:
            with self.subTest(value=value):
                self.tables["stops.txt"][1]["stop_lat"] = value
                with self.assertRaisesRegex(gtfs.ImportFailure, "coordinates"):
                    self.run_import()

    def test_duplicate_trip_is_not_silently_ignored(self):
        self.tables["trips.txt"].append(dict(self.tables["trips.txt"][0]))
        with self.assertRaisesRegex(gtfs.ImportFailure, "duplicate trip_id"):
            self.run_import()

    def test_cross_operator_line_identity_collision_rejected(self):
        self.rules.append(gtfs.OperatorRule("108", "DB Regio", "S_BAHN", ("109",)))
        with self.assertRaisesRegex(gtfs.ImportFailure, "ambiguous line"):
            self.run_import()

    def test_selected_trip_with_missing_stop_times_rejected(self):
        self.tables["stop_times.txt"] = [r for r in self.tables["stop_times.txt"] if r["trip_id"] != "u"]
        with self.assertRaisesRegex(gtfs.ImportFailure, "missing stop_times"):
            self.run_import()

    def test_missing_csv_columns_controlled_error(self):
        for row in self.tables["routes.txt"]:
            del row["route_type"]
        with self.assertRaisesRegex(gtfs.ImportFailure, "missing columns"):
            self.run_import()

    def test_invalid_window_rejected(self):
        for start, end in [(date(2026, 11, 17), None), (date(2026, 11, 17), date(2026, 11, 16)),
                           (date(2026, 1, 1), date(2027, 2, 1))]:
            with self.subTest(start=start, end=end), self.assertRaisesRegex(gtfs.ImportFailure, "both dates"):
                self.run_import(start=start, end=end)


if __name__ == "__main__":
    unittest.main()
