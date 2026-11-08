"""Offline audit of actual Grafana exports. Never changes dashboard files."""
import argparse
import datetime
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PAIRS = (
    ("StyleMapApplicationDashboard.json", "stylemap-application-dashboard.json", "backend"),
    ("PostgresExporterDashboard.json", "postgresql-exporter-dashboard.json", "postgresql"),
)


def load(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"Duplicate JSON key {key}: {path}")
            result[key] = value
        return result
    return json.loads(path.read_text(), object_pairs_hook=unique,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def review():
    parsed = {str(p.relative_to(ROOT)): load(p) for p in sorted(ROOT.rglob("*.json"))}
    names = parsed["exported-metric-names.json"]
    old_results = parsed["validation-results.json"]
    checks = []
    for exported, generated, family in PAIRS:
        actual, baseline = parsed[exported], parsed[generated]
        ids = [p["id"] for p in actual["panels"]]
        assert len(ids) == len(set(ids)), exported
        assert len(actual["panels"]) == len(baseline["panels"]), exported
        variables = actual["templating"]["list"]
        assert len({v["name"] for v in variables}) == len(variables)
        ds = next(v for v in variables if v["name"] == "DS_PROMETHEUS")
        assert ds["type"] == "datasource" and ds["query"] == "prometheus"
        expected_ds = dict(type="prometheus", uid="${DS_PROMETHEUS}")
        query_count = 0
        panel_checks = []
        for source, panel in zip(baseline["panels"], actual["panels"]):
            for key in ("id", "title", "type", "gridPos", "description", "targets"):
                assert source.get(key) == panel.get(key), (exported, panel["title"], key)
            assert source.get("fieldConfig", {}).get("defaults", {}).get("unit") == panel.get("fieldConfig", {}).get("defaults", {}).get("unit")
            if panel.get("targets"):
                assert panel["datasource"] == expected_ds
            refs = [t["refId"] for t in panel.get("targets", [])]
            assert len(refs) == len(set(refs))
            for target in panel.get("targets", []):
                assert target["datasource"] == expected_ds
                query = target["expr"]
                metrics = set(re.findall(r'\b(?:http_server|jvm|hikaricp|process|system|pg)_[a-zA-Z0-9_]+', query))
                assert metrics <= set(names[family]), (exported, metrics-set(names[family]))
                assert any(q["expr"] == query and q.get("api_status") == "success"
                           for q in old_results["queries"]), (exported, query)
                used_variables = set(re.findall(r'\$\{(\w+)(?::[^}]+)?\}|\$(\w+)', query))
                assert {a or b for a, b in used_variables} <= {v["name"] for v in variables} | {"__rate_interval"}
                query_count += 1
            if panel["type"] != "row":
                panel_checks.append(dict(title=panel["title"], type=panel["type"],
                    unit=panel.get("fieldConfig", {}).get("defaults", {}).get("unit"),
                    expressions=[t["expr"] for t in panel.get("targets", [])]))
        if family == "postgresql":
            database = next(v for v in variables if v["name"] == "database")
            assert database == next(v for v in baseline["templating"]["list"] if v["name"] == "database")
            assert database["datasource"] == expected_ds
            assert "pg_stat_database_numbackends" in names[family]
        # Built-in dashboard annotations use Grafana's internal datasource, not another metrics backend.
        for annotation in actual.get("annotations", {}).get("list", []):
            assert annotation.get("builtIn") == 1
            assert annotation["datasource"] == dict(type="grafana", uid="-- Grafana --")
        checks.append(dict(export=exported, generated=generated, sha256=sha(ROOT/exported),
            title=actual["title"], id=actual["id"], uid=actual["uid"], schemaVersion=actual["schemaVersion"],
            pluginVersions=sorted({p["pluginVersion"] for p in actual["panels"] if "pluginVersion" in p}),
            datasource_variable=ds, panels=len(panel_checks), rows=len(ids)-len(panel_checks),
            targets=query_count, targets_layout_descriptions_units_equal=True,
            panels_inspected=panel_checks))
    return dict(reviewed_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
        mode="offline; no new Grafana or VictoriaMetrics queries", json_files_validated=sorted(parsed),
        previous_live_validation_at=old_results["checked_at"], dashboards=checks,
        screenshots_found=[str(p.relative_to(ROOT)) for p in ROOT.rglob("*") if p.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}],
        recordings_found=[str(p.relative_to(ROOT)) for p in ROOT.rglob("*") if p.suffix.lower() in {".mp4", ".webm", ".mkv", ".mov"}])


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, help="Write a NEW audit JSON; existing files are never overwritten")
    args = parser.parse_args()
    result = review()
    if args.output:
        with args.output.open("x") as output:
            json.dump(result, output, ensure_ascii=False, indent=2)
            output.write("\n")
    for d in result["dashboards"]:
        print(f'PASS {d["export"]}: {d["panels"]} panels, {d["targets"]} targets; same queries/layout/units; SHA256 {d["sha256"]}')
    print("PASS strict JSON, datasource references, template dependencies, metric inventory, historical query match")
    print("No new runtime measurements; screenshots:", len(result["screenshots_found"]), "; recordings:", len(result["recordings_found"]))
