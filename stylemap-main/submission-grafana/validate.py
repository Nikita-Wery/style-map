"""Validate JSON, metric inventory and optionally live read-only VM queries."""
import argparse
import datetime
import json
import math
import re
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen
from urllib.error import HTTPError

root=Path(__file__).resolve().parent
parser=argparse.ArgumentParser()
parser.add_argument("--vm-url", help="Existing VictoriaMetrics URL, e.g. http://localhost:18428")
args=parser.parse_args()
names=json.loads((root/"exported-metric-names.json").read_text())
results=[]
for filename, family in (("stylemap-application-dashboard.json","backend"),("postgresql-exporter-dashboard.json","postgresql")):
    d=json.loads((root/filename).read_text())
    assert d["id"] is None and d["uid"] is None
    ids=[p["id"] for p in d["panels"]]
    assert len(ids)==len(set(ids))
    for p in d["panels"]:
        for t in p.get("targets",[]):
            q=t["expr"]
            used=set(re.findall(r'\b(?:http_server|jvm|hikaricp|process|system|pg)_[a-zA-Z0-9_]+',q))
            assert used <= set(names[family]), (p["title"],used-set(names[family]))
            assert t["datasource"]=={"type":"prometheus","uid":"${DS_PROMETHEUS}"}
            item=dict(dashboard=d["title"],panel=p["title"],refId=t["refId"],expr=q,metric_names="PASS")
            if args.vm_url:
                resolved=q.replace("$__rate_interval","5m").replace("${database:regex}",".*")
                try:
                    with urlopen(args.vm_url.rstrip("/")+"/api/v1/query?"+urlencode(dict(query=resolved)),timeout=60) as response:
                        data=json.load(response)
                except HTTPError as exc:
                    raise AssertionError((p["title"], resolved, exc.read().decode())) from exc
                assert data["status"]=="success",data
                assert not data.get("error"),data
                values=data["data"]["result"]
                item.update(api_status=data["status"],series=len(values),
                    nonfinite=sum(not math.isfinite(float(v["value"][1])) for v in values),
                    resolved_expr=resolved, warnings=data.get("warnings",[]))
            results.append(item)
for f in root.rglob("*.json"):
    json.loads(f.read_text())
report=dict(checked_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),
            grafana_import_verified=False, live_vm=bool(args.vm_url),queries=results)
if args.vm_url:
    (root/"validation-results.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
print(f'PASS: {len(results)} panel expressions; JSON, unique IDs, portable DS, metric names'+("; live VM API queries" if args.vm_url else ""))
for r in results:
    if args.vm_url and (not r["series"] or r["nonfinite"]):
        print(f'NO DATA / nonfinite: {r["panel"]} {r["refId"]}: {r["series"]} series, {r["nonfinite"]} nonfinite')
