"""Regenerate portable dashboards. No network access or Grafana writes."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DS = {"type": "prometheus", "uid": "${DS_PROMETHEUS}"}
B = 'app="backend",kubernetes_namespace="stylemap",job="kubernetes-pods"'
P = 'job="stylemap-readiness",instance="http://backend-management.stylemap.svc.cluster.local:8081/actuator/health/readiness"'
panels = []
y = 0


def row(title):
    global y
    panels.append(dict(id=len(panels)+1, type="row", title=title, collapsed=False,
                       gridPos=dict(x=0, y=y, w=24, h=1), panels=[]))
    y += 1


def panel(title, expressions, unit="short", description="", kind="timeseries", thresholds=None):
    global y
    if isinstance(expressions, str):
        expressions = [(expressions, title)]
    defaults = dict(unit=unit, noValue="Нет данных", color=dict(mode="palette-classic"),
                    thresholds=dict(mode="absolute", steps=thresholds or [dict(color="blue", value=None)]))
    if kind == "stat":
        defaults["color"] = dict(mode="thresholds")
    panels.append(dict(id=len(panels)+1, title=title, type=kind, datasource=DS,
        gridPos=dict(x=0, y=y, w=24, h=7), description=description,
        fieldConfig=dict(defaults=defaults, overrides=[]),
        options=(dict(reduceOptions=dict(calcs=["lastNotNull"], fields="", values=False),
                      colorMode="value", graphMode="none", textMode="auto") if kind == "stat" else
                 dict(legend=dict(displayMode="table", placement="bottom", calcs=["lastNotNull"]),
                      tooltip=dict(mode="multi", sort="desc"))),
        targets=[dict(refId=chr(65+i), expr=q, legendFormat=l, datasource=DS,
                      range=kind != "stat", instant=kind == "stat", interval="15s", editorMode="code")
                 for i, (q,l) in enumerate(expressions)]))
    y += 7


def rate(metric, extra=""):
    return f'rate({metric}{{{B}{extra}}}[$__rate_interval])'


def dashboard(title, tags):
    # Compact rows: four summary stats, two charts. Flush before each section.
    layout_y=0
    x=0
    height=0
    for p in panels:
        if p["type"]=="row":
            if x:
                layout_y+=height
            p["gridPos"]=dict(x=0,y=layout_y,w=24,h=1)
            layout_y+=1
            x=height=0
            continue
        w=6 if p["type"]=="stat" else 12
        h=5 if p["type"]=="stat" else 8
        if x+w>24:
            layout_y+=height
            x=height=0
        p["gridPos"]=dict(x=x,y=layout_y,w=w,h=h)
        x+=w
        height=max(height,h)
        if x==24:
            layout_y+=height
            x=height=0
    return dict(id=None, uid=None, title=title, version=1, schemaVersion=39, editable=True,
        timezone="browser", refresh="15s", time=dict(**{"from":"now-1h", "to":"now"}),
        tags=tags, annotations=dict(list=[]), links=[], panels=list(panels),
        templating=dict(list=[dict(name="DS_PROMETHEUS", label="VictoriaMetrics datasource",
            type="datasource", query="prometheus", refresh=1, options=[], current={})]))


row("Application health")
health = [dict(color="red",value=None),dict(color="green",value=1)]
panel("Backend scrape health", f'min(up{{{B}}})', "short",
      "0: хотя бы один обнаруженный target недоступен; 1: все обнаруженные UP. Исчезнувший target не учитывается; пусто — неизвестно.", "stat", health)
panel("Blackbox readiness", f'probe_success{{{P}}}', "short",
      "Readiness backend, включая db; не frontend/Ingress. Нет данных не равно UP.", "stat", health)
total = f'sum({rate("http_server_requests_seconds_count")})'
panel("HTTP RPS", total, "reqps", "Скорость завершённых запросов; пусто — нет серии, 0 — наблюдаемый нулевой трафик.", "stat")
# Zero-valued fallback is allowed only when the observed total has samples.
err = f'(sum({rate("http_server_requests_seconds_count", ",status=~\"5..\"")}) or (0 * {total}))'
panel("HTTP 5xx percentage", f'100 * {err} / ({total} > 0)', "percent",
      "Нет трафика: Нет данных. Если общий counter существует, отсутствие 5xx-серии означает отсутствие записанных 5xx. Пороги 1/5% — визуальные, не SLA.", "stat",
      [dict(color="green",value=None),dict(color="orange",value=1),dict(color="red",value=5)])
row("RED / Golden Signals")
panel("Request rate by status", f'sum by (status) ({rate("http_server_requests_seconds_count")})', "reqps",
      "Traffic и errors; все статусы, включая отклонённые Security запросы. Легенда — HTTP status.")
panels[-1]["targets"][0]["legendFormat"] = "{{status}}"
panel("HTTP errors: 4xx / 5xx", [(f'(sum({rate("http_server_requests_seconds_count", ",status=~\""+code+"..\"")}) or (0 * {total}))',code+"xx") for code in ("4","5")], "reqps",
      "Ошибки в запросах/с; fallback 0 применяется только при наблюдаемом общем трафике, не при отсутствии метрик.")
panel("Average HTTP latency", f'sum({rate("http_server_requests_seconds_sum")}) / ({total} > 0)', "s",
      "Взвешенное среднее: rate(sum)/rate(count). При нулевом трафике нет значения.")
panel("HTTP latency p95 / p99", [(f'histogram_quantile({q}, sum by (le) ({rate("http_server_requests_seconds_bucket")}))',name) for q,name in ((0.95,"p95"),(0.99,"p99"))], "s",
      "Приближённые квантили по объединённым histogram buckets всех backend pod/status/URI. le сохраняется; при отсутствии наблюдений NaN.")
panel("Top request rates by URI", f'topk(10, sum by (uri) ({rate("http_server_requests_seconds_count")}))', "reqps",
      "MVC route templates. UNKNOWN включает запросы, отклонённые до MVC; они не исключаются.")
panels[-1]["targets"][0]["legendFormat"] = "{{uri}}"
row("JVM / USE — utilization, saturation, errors")
heap = B + ',area="heap"'
panel("JVM heap used / maximum", [(f'sum by (kubernetes_pod_name) (jvm_memory_{m}_bytes{{{heap}}})',m+" {{kubernetes_pod_name}}") for m in ("used","max")], "bytes",
      "Сумма heap pools по pod; max отображается только для JVM с определённым лимитом.")
panel("JVM heap utilization", f'100 * sum by (kubernetes_pod_name) (jvm_memory_used_bytes{{{heap}}}) / (sum by (kubernetes_pod_name) (jvm_memory_max_bytes{{{heap}}}) > 0)', "percent",
      "Heap utilization по pod; max<=0 даёт Нет данных. Визуальные пороги, не SLO.", thresholds=[dict(color="green",value=None),dict(color="orange",value=75),dict(color="red",value=90)])
panel("CPU utilization: process / system", [(f'{m}{{{B}}}',name+" {{kubernetes_pod_name}}") for m,name in (("process_cpu_usage","process"),("system_cpu_usage","system"))], "percentunit",
      "Micrometer gauges 0..1: не применять rate(). System CPU — доступное JVM окружение, не гарантированно весь Kubernetes node.")
panel("JVM live threads", f'jvm_threads_live_threads{{{B}}}', "short", "Число живых JVM threads по pod; контекст runtime saturation.")
panel("GC pause time / second", f'sum by (kubernetes_pod_name) ({rate("jvm_gc_pause_seconds_sum")})', "s",
      "Секунды stop-the-world пауз за секунду; rate counter длительности, не средняя пауза.")
panel("HikariCP connections", [(f'hikaricp_connections_{m}{{{B}}}',m+" {{pool}} {{kubernetes_pod_name}}") for m in ("active","idle","pending","max")], "short",
      "Active/idle/max — utilization и capacity; pending — saturation, очередь ожидания подключения.")
panel("HikariCP pool utilization", f'100 * hikaricp_connections_active{{{B}}} / (hikaricp_connections_max{{{B}}} > 0)', "percent",
      "Active/max по pool и pod. Нулевой max не маскируется.", thresholds=[dict(color="green",value=None),dict(color="orange",value=75),dict(color="red",value=90)])
panel("HikariCP connection timeout rate", f'sum by (pool, kubernetes_pod_name) ({rate("hikaricp_connections_timeout_total")})', "ops",
      "USE errors: подключения, не полученные до timeout, за секунду.")
row("SLA — readiness component, 30 days / target ≥99%")
panel("Observed readiness availability — 30d", f'100 * avg_over_time(probe_success{{{P}}}[30d])', "percent",
      "Цель ≥99%, период 30 суток из submission-slo/Отчёт_SLI_SLO_SLA.md. Только имеющиеся probe samples; неполная история не подтверждает SLA. Это readiness-компонент, не общий SLA и не договорная гарантия.", "stat",
      [dict(color="red",value=None),dict(color="green",value=99)])
panel("Readiness sample coverage — 30d", f'100 * count_over_time(probe_success{{{P}}}[30d]) / 172800', "percent",
      "30*24*60*60/15 = 172800 плановых проб. При <100% SLA не подтверждён. Дубликаты/смену scrape interval проверять отдельно; >100% — проверить ingestion.", "stat",
      [dict(color="red",value=None),dict(color="green",value=100),dict(color="orange",value=100.1)])
app = dashboard("StyleMap - Application Overview", ["stylemap","homework","RED","USE"])
(ROOT/"stylemap-application-dashboard.json").write_text(json.dumps(app,ensure_ascii=False,indent=2)+"\n")

# Adapt the actual catalog dashboard; retain only queries whose metrics exist in live exporter.
source = json.loads((ROOT/"upstream/postgresql-9628-revision-8.json").read_text())
metrics = set(json.loads((ROOT/"exported-metric-names.json").read_text())["postgresql"])
import re
panels.clear()
y = 0
row("PostgreSQL — adapted Grafana catalog 9628 revision 8")
PG = 'kubernetes_namespace="stylemap",kubernetes_pod_name="postgres-0",job="kubernetes-pods"'
panel("PostgreSQL connection health", f'pg_up{{{PG}}}', "short", "pg_up — связь exporter с БД; не только scrape health.", "stat",health)
audit = []
for old in source["panels"]:
    targets = old.get("targets",[])
    if not targets:
        continue
    exprs=[]
    reason=""
    omitted_targets=[]
    for t in targets:
        q=t.get("expr","")
        names=set(re.findall(r'\b(?:pg|process)_[a-zA-Z0-9_]+',q))
        absent=names-metrics
        if absent or any(n.startswith("process_") for n in names) or "pg_static" in names:
            reason="missing: "+", ".join(sorted(absent)) if absent else "exporter process / legacy static info"
            omitted_targets.append(dict(expr=q,reason=reason))
            continue
        q=re.sub(r'release="\$release",?\s*',"",q)
        q=re.sub(r'instance[=~]+"\$instance"',PG,q)
        q=q.replace('mode=~"$mode"','mode=~".*"')
        q=q.replace("SUM(","sum(").replace("irate(","rate(").replace("[5m]","[$__rate_interval]")
        q=re.sub(r'\s*!=\s*0',"",q)
        # Upstream tuple counters were displayed as totals; show meaningful rates.
        q=re.sub(r'(pg_stat_database_tup_\w+\{[^}]+\})',r'rate(\1[$__rate_interval])',q)
        q=q.replace('datname=~"$datname"','datname=~"${database:regex}"')
        exprs.append((q,t.get("legendFormat") or old["title"]))
    audit.append(dict(title=old["title"],status="adapted" if exprs else "omitted",reason=reason,omitted_targets=omitted_targets))
    if exprs:
        fmt=old.get("fieldConfig",{}).get("defaults",{}).get("unit") or old.get("format") or (old.get("yaxes") or [{}])[0].get("format") or "short"
        if any("pg_stat_database_tup_" in q for q,_ in exprs):
            fmt="ops"
        if old["title"]=="Cache Hit Rate":
            fmt="percentunit"
        panel(old["title"],exprs,fmt,"Адаптация dashboard 9628 r8. Kubernetes scope вместо Helm release; metrics проверены по live exporter. Counters используют rate; настройки и gauges — напрямую.")
pg=dashboard("StyleMap - PostgreSQL Exporter (9628 adapted)",["stylemap","postgresql","9628-adapted"])
pg["links"]=[dict(title="Upstream 9628 revision 8",type="link",url="https://grafana.com/grafana/dashboards/9628-postgresql-database/",targetBlank=True)]
pg["templating"]["list"].append(dict(name="database",label="Database",type="query",datasource=DS,
    query=f'label_values(pg_stat_database_numbackends{{{PG}}}, datname)',refresh=1,multi=True,
    includeAll=True,allValue=".*",current=dict(text="All",value="$__all"),options=[]))
(ROOT/"postgresql-exporter-dashboard.json").write_text(json.dumps(pg,ensure_ascii=False,indent=2)+"\n")
(ROOT/"postgresql-compatibility.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
# Exact panel/PromQL inventory used by both dashboards.
out=["# Панели и точные PromQL выражения", "", "Grafana раскрывает `$__rate_interval`; минимум 60s при scrape 15s. DS_PROMETHEUS — существующий Prometheus-compatible VictoriaMetrics datasource.", ""]
for d in (app,pg):
    out += ["## "+d["title"],""]
    for p in d["panels"]:
        if p["type"]=="row":continue
        out += ["### "+p["title"],"",p["description"],""]
        for t in p["targets"]:out += ["```promql",t["expr"],"```",""]
(ROOT/"QUERIES.md").write_text("\n".join(out))
