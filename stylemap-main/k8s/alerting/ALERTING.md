# StyleMap alerting

The SMTP configuration was deployed and email delivery was verified
with real FIRING and RESOLVED notifications for four alerts,
including HighJvmHeapUsage. The public configuration uses
placeholder email addresses and requires private SMTP credentials.
Existing scrape configuration, application code, business metrics and SLI/SLO
definitions are unchanged. Commands below are for a later deployment/test.
The reported monitoring-namespace network check to `smtp.yandex.ru:587` returned
`EXIT_CODE=0`; reachability does not establish authentication or email delivery.

## Architecture and resources

```text
backend Actuator / postgres exporter / Blackbox -> VictoriaMetrics
VictoriaMetrics <- queries every 15s -- vmalert -> Alertmanager -> Yandex SMTP (STARTTLS) -> email
Grafana and VMUI continue using VictoriaMetrics as before.
```

Everything new runs in `monitoring`. No operator or CRDs are required.

| File | Resources / purpose |
| --- | --- |
| `alert-rules.yaml` | `stylemap-alert-rules` ConfigMap, five alert rules |
| `vmalert.yaml` | One-replica Deployment; ClusterIP `vmalert:8880` |
| `alertmanager-config.yaml` | `stylemap-alertmanager-config` ConfigMap; native SMTP email routing |
| `alertmanager.yaml` | One-replica Deployment; ClusterIP `alertmanager:9093`; 1Gi PVC |
| `alertmanager-secret.example.yaml` | Placeholder only; real Secret is created privately |
| `tests/alerts.test.yaml`, `validate-alerting.py` | Offline expression/transition tests and configuration validation |

Images are pinned to `victoriametrics/vmalert:v1.154.0` (matching the existing
VictoriaMetrics release) and `prom/alertmanager:v0.34.2`. Alertmanager supports
`email_configs.auth_password_file`; the SMTP password is read from a mounted
Secret and never interpolated into the ConfigMap. Minikube's existing default
`standard` hostpath StorageClass was confirmed; the Alertmanager PVC explicitly
uses it. `Recreate` prevents
simultaneous old/new Alertmanager pods competing for the RWO volume.

Alertmanager persists notification state and silences, not a complete incident
archive. This initial vmalert has no remote-write/read state persistence: its
pending timers restart when it restarts. Inspect current states in vmalert;
`ALERTS` history is not automatically written into VictoriaMetrics.

## Rules and prerequisites

| Alert | Predicate | `for` | Severity |
| --- | --- | --- | --- |
| `HighPublishLatency` | 2m mean successful publication >1s; approximately >=5 publications/2m | 2m | warning |
| `HighHttp5xxRate` | 5m backend HTTP 5xx fraction >5%; approximately >=20 requests and >=5 errors/5m | 2m | critical |
| `HighJvmHeapUsage` | Per-JVM used heap / validated pool maximum >85% | 5m | warning |
| `HighGcPause` | Per-JVM 5m mean GC pause >0.2s; approximately >=5 events/5m | 5m | warning |
| `BackendUnavailable` | Explicit Blackbox readiness `probe_success == 0` | 1m | critical |

`increase()` extrapolates, so volume limits are approximate. Positive-denominator
filters prevent zero-traffic divisions. Missing input produces no alert; an
inactive alert alone does not prove healthy data. Check VM targets and metric
freshness separately, including exporter failure and unsupported JVM pools.

Application sums are restricted to `job="kubernetes-pods",app="backend",
kubernetes_namespace="stylemap"`. JVM rules preserve pod and instance identity,
so one unhealthy JVM is not averaged with another. HTTP scope includes all
backend routes and `uri="UNKNOWN"`; it excludes errors generated solely by
nginx. No 5xx series is expected until an actual 5xx request completes.

### Heap semantics verified before implementation

On 2026-11-07, the running backend exposed Serial GC (`Copy` and
`MarkSweepCompact`) and these disjoint heap pools:

| Pool | Maximum bytes |
| --- | ---: |
| Eden Space | 53,739,520 |
| Survivor Space | 6,684,672 |
| Tenured Gen | 134,217,728 |
| Total available across the exposed pools | 194,641,920 |

The rule sums matching heap used/max pools. Nonheap Metaspace reported max -1,
which is excluded by `area="heap"`. `jvm_gc_max_data_size_bytes` reported the
Tenured maximum only and is **not** used as total heap capacity.

The rule requires exactly these three pools, complete used/max pairs, finite
nonnegative usage and finite positive maxima. Undefined (-1), zero, infinite,
NaN, missing or changed pools suppress this rule. Revalidate after JVM/collector
changes; this formula is intentionally not a portable G1/ZGC heap formula.
Do not silently drop unknown maxima and label the remaining sum total heap.

GC mean aggregates duration and event count across cause/action/collector within
each JVM. It is neither p95 nor wall-clock GC overhead. Five events and a 5m
hold reduce noise; sustained 200ms means are machine/collector dependent.

## Validate without deploying

Prerequisites: Docker, Python 3 with PyYAML, and kubectl access to Kubernetes
OpenAPI schemas. Pull validation images if not already cached:

```sh
docker pull victoriametrics/vmalert:v1.154.0
docker pull prom/alertmanager:v0.34.2
docker pull prom/prometheus:v3.12.0
python3 k8s/alerting/validate-alerting.py
```

The script checks client-side Kubernetes schemas, names/selectors/volume
references, pinned images, the exact vmalert flags with `-dryRun`, PromQL and
rules with promtool, and SMTP email configuration with the pinned image's amtool.
It renders FIRING/RESOLVED subject and body templates with synthetic alert data
and parses Alertmanager's exact flags with `--help`. Disposable validators
have networking disabled and use synthetic credentials. No server starts in
these checks and no Secret is fetched. Synthetic series cover pending/firing,
observed recovery, absence, zero traffic, low volume, invalid/missing heap
maxima, changed pools, namespace/service scope and separate JVMs.

Client schema checks do not prove future PVC binding, volume ownership, image
pulls, Service connectivity, SMTP authentication/email delivery, or actual alert timing.
Rule fixtures use Prometheus semantics; vmalert validates its own MetricsQL
parser, but production evaluation must still be checked against VictoriaMetrics.

Validation on 2026-11-07 passed for eight resource schemas, five expressions and
14 synthetic scenarios. Read-only queries against the existing VictoriaMetrics
v1.154.0 also accepted all five expressions, confirmed the three heap maxima
above, and found three backend HTTP request counter series. None of the five
predicates returned an active result at that instant; this is not a substitute
for later pending/firing/delivery tests.

The SMTP receiver also passed the pinned Alertmanager v0.34.2 `amtool
check-config` using a synthetic password and networking disabled. This validates
field support and configuration structure, not SMTP authentication or delivery.

## SMTP account and password Secret

The native email receiver uses:

| Setting | Value |
| --- | --- |
| Server | `smtp.yandex.ru:587` |
| Username / sender / initial recipient | `nsluzin@kpfu.ru` |
| Encryption | STARTTLS required (`require_tls: true`, `force_implicit_tls: false`) |
| Certificate verification | Enabled; server name `smtp.yandex.ru` |
| Password file | `/etc/alertmanager/smtp/password` |
| Secret / key | `stylemap-alertmanager-smtp` / `password` |

Use the SMTP/app password authorized by this account's mail policy. The server
must permit SMTP authentication and sending from the configured address.
An open network connection alone does not verify either prerequisite. This SMTP
connection is direct; no HTTP proxy environment variables are configured.

Create the new Secret privately before applying the Deployment. Run this in an
interactive terminal: the password is entered with echo disabled, never passed
as a command argument or environment variable, and written only to a temporary
private file that is removed when kubectl exits. Do not use `--from-literal`,
shell tracing, or a real `stringData` manifest in Git:

```sh
python3 - <<'PYSETUP'
import getpass, os, pathlib, subprocess, tempfile, warnings
os.umask(0o077)
warnings.simplefilter('error', getpass.GetPassWarning)
try:
    password = getpass.getpass('SMTP password (hidden): ')
except (getpass.GetPassWarning, EOFError):
    raise SystemExit('Use an interactive terminal with hidden password input.') from None
if not password:
    raise SystemExit('SMTP password must not be empty.')
with tempfile.TemporaryDirectory(prefix='stylemap-smtp-') as directory:
    password_file = pathlib.Path(directory) / 'password'
    password_file.write_text(password)
    subprocess.run(['kubectl', '-n', 'monitoring', 'create', 'secret', 'generic',
                    'stylemap-alertmanager-smtp',
                    '--from-file=password=' + str(password_file)], check=True)
PYSETUP
```

Do not apply `alertmanager-secret.example.yaml`; it contains a placeholder only.
If the real Secret already exists, creation fails without overwriting it.
Coordinate any password rotation privately and restart Alertmanager afterward.
Do not print the Secret, `cat` its mounted file, or enable SMTP protocol/debug
logging. Kubernetes Secrets require appropriate RBAC and storage protections;
base64 is not encryption.

## Deployment sequence (not performed)

Run from the repository root. The `monitoring` namespace and VictoriaMetrics
Service must already exist. Reconfirm `standard` storage. The reported SMTP
network test passed; any policy change must continue to allow DNS and direct
TCP 587 egress to `smtp.yandex.ru`.

```sh
kubectl get namespace monitoring
kubectl get service -n monitoring vm-single-victoria-metrics-single-server
kubectl get storageclass standard
```

Create the SMTP password Secret using the private helper above, then:

```sh
kubectl apply -f k8s/alerting/alertmanager-config.yaml
kubectl apply -f k8s/alerting/alertmanager.yaml
kubectl rollout restart deployment/alertmanager -n monitoring
kubectl rollout status deployment/alertmanager -n monitoring --timeout=180s
kubectl apply -f k8s/alerting/alert-rules.yaml
kubectl apply -f k8s/alerting/vmalert.yaml
kubectl rollout status deployment/vmalert -n monitoring --timeout=180s
```

Use these explicit file paths, not recursive directory apply: Helm values,
validation fixtures and the placeholder Secret are not deployment inputs.
No VictoriaMetrics Helm upgrade or backend restart is needed for alerting.

Verify startup without printing credentials:

```sh
kubectl get pods -n monitoring -l app=alertmanager
kubectl logs -n monitoring deployment/alertmanager --since=10m --tail=100
kubectl get pvc -n monitoring alertmanager-data
```

Confirm the rollout is ready and configuration loading succeeded. A missing
password file indicates a Secret/key/mount mismatch; permission errors require
checking the existing `fsGroup: 65534` and Secret file mode `0440`. Do not print
the file to diagnose it. Authentication or sender rejection must be investigated
privately with the mail account administrator. Certificate verification remains
enabled; do not bypass TLS to make delivery work. No VictoriaMetrics PVC needs
to be changed.

For later rule/config edits, reapply the corresponding ConfigMap and restart its
Deployment to ensure reload. vmalert restart resets pending timers. Keep real
secrets out of screenshots and do not dump full Alertmanager configuration/UI
status when collecting evidence.

## Inspect states and test delivery

In separate terminals:

```sh
kubectl port-forward -n monitoring svc/vmalert 8880:8880
kubectl port-forward -n monitoring svc/alertmanager 9093:9093
```

With the Alertmanager forward running, verify readiness:

```sh
curl -fsS http://localhost:9093/-/ready
```

Open `http://localhost:8880/vmalert/` for groups/rules and pending/firing states,
and `http://localhost:9093/` for grouped firing alerts. vmalert also exposes
`/api/v1/rules` and `/api/v1/alerts`. Inactive/pending belong to the evaluator;
Alertmanager normally receives firing and resolved notifications only. A health
probe confirms process health, not successful rule evaluation or delivery.

Routing groups by environment, service, alert name and severity, waits 15s for
the first group, updates at 1m intervals, and repeats at 4h. `send_resolved` is
explicit. UI state and email delivery can therefore differ in time.

After approval, use this synthetic delivery test through the forwarded API. It
bypasses vmalert and clearly identifies itself as a notification test:

```sh
python3 - <<'PY'
import datetime, json, urllib.request
now = datetime.datetime.now(datetime.timezone.utc)
alert = {'labels': {'alertname': 'NotificationTest', 'environment': 'minikube',
                   'service': 'stylemap-backend', 'severity': 'warning'},
         'annotations': {'summary': 'Synthetic SMTP email delivery test',
                         'description': 'Routing test only; no application failure.'},
         'startsAt': now.isoformat(), 'endsAt': (now + datetime.timedelta(minutes=10)).isoformat()}
def send():
    request = urllib.request.Request('http://localhost:9093/api/v2/alerts',
        data=json.dumps([alert]).encode(), headers={'Content-Type': 'application/json'}, method='POST')
    with urllib.request.urlopen(request, timeout=10) as response:
        print('Alertmanager accepted test:', response.status)
# A heredoc consumes stdin, so read confirmation from the interactive terminal.
with open('/dev/tty', 'r+') as terminal:
    send()
    terminal.write('Confirm the FIRING email in the inbox (at least 15s), then press Enter to resolve: ')
    terminal.flush()
    if not terminal.readline():
        raise SystemExit('No confirmation; the test alert will expire automatically.')
    alert['endsAt'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    send()
PY
```

Wait at least the 1m group interval, plus SMTP delivery time, for the RESOLVED
email. Check inbox and spam folders: subjects begin `[FIRING] NotificationTest`
and `[RESOLVED] NotificationTest`. API acceptance only confirms that Alertmanager
accepted the payload; record delivery success only after observing both emails.
This manual test bypasses vmalert and does not prove an application alert predicate.

## Safe demonstrations and recovery

### Real backend outage and recovery

Run only in the disposable Minikube environment when a temporary backend outage
is acceptable. Keep VictoriaMetrics, vmalert, Alertmanager, Blackbox and
PostgreSQL running. Record the replica count and install a shell cleanup trap
so interruption also restores it:

```bash
(
set -e
backend_replicas=$(kubectl get deployment backend -n stylemap -o jsonpath='{.spec.replicas}')
test "$backend_replicas" -gt 0 || { echo 'Backend must initially have at least one replica'; exit 1; }
restore_backend() {
  kubectl scale deployment/backend -n stylemap --replicas="$backend_replicas"
}
trap restore_backend EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
kubectl scale deployment/backend -n stylemap --replicas=0
# Watch vmalert: BackendUnavailable becomes pending, then firing after 1m.
# Allow scrape/evaluation time and at least the 15s grouping wait.
read -r -p 'After observing the FIRING BackendUnavailable email, press Enter to restore: '
restore_backend
kubectl rollout status deployment/backend -n stylemap --timeout=180s
trap - EXIT INT TERM
)
```

Verify `probe_success{job="stylemap-readiness"}` returns 1 in VictoriaMetrics,
the rule returns inactive, and an email with subject
`[RESOLVED] BackendUnavailable` arrives (allow the 1m group interval plus mail
delivery time). Preserve the same alert name and labels between firing and
resolution. Capture the actual FIRING and RESOLVED messages as evidence; if
mail is missing, inspect normal Alertmanager logs and spam filtering without
showing credentials. Scaling the backend down does not delete uploads or
database data. The test does not touch any PVC.

### Other rule demonstrations

Use disposable Minikube accounts/data and capture baseline metrics first.
Maintain qualifying traffic for the entire pending interval; allow for rolling
windows, 15s scrape/evaluation, and notification grouping after each change.

| Alert | Safe demonstration | Resolve / limitations |
| --- | --- | --- |
| Publication latency | Set the existing delay to 2500ms, restart backend, then publish small valid outfits frequently enough to keep >=5/2m for the 2m hold. | Restore delay to 0, restart, and continue normal publications until the 2m mean recovers. |
| HTTP 5xx | No deterministic safe 5xx fault switch currently exists. For a **rule-only demo**, copy the ConfigMap to a temporary local file, change both `status=~"5.."` matchers in this rule to `status="401"`, add `demo="true"` and explicit DEMO annotations, then generate >=20 anonymous protected API requests and >=5 401s per 5m. | Restore the committed ConfigMap and restart vmalert. This demonstrates HTTP metric evaluation/notifications, **not real 5xx behavior**. Original 5xx behavior is covered by synthetic rule fixtures; a genuine fault experiment needs a separately reviewed method. |
| JVM heap | Prefer an explicitly labeled, temporary rule-only demo: lower 0.85 below the currently observed heap fraction, retaining all pool guards and `for: 5m`. | Restore the committed rule. Do not fill heap to OOM or change container/JVM limits merely to obtain a screenshot. Real sustained >85% requires a separately reviewed bounded workload. |
| GC pause | Generate bounded ordinary workload to observe recent GC events. For a rule-only demo, lower 0.2 below the measured recent mean, retaining the >=5 events gate and `for: 5m`; label/annotate as DEMO. | Restore the committed rule and stop workload. GC pauses >200ms cannot safely be guaranteed on every host; if insufficient events occur, use the offline fixture evidence instead. |
| Backend readiness | Record the current backend replica count, scale only the backend to 0, and leave the Blackbox exporter running for >1m. | Restore the recorded replica count and wait for rollout/readiness. This causes a temporary application outage but does not delete data or disrupt PostgreSQL. |

Existing latency mechanism, only during an approved demonstration:

```sh
kubectl patch configmap stylemap-config -n stylemap --type merge -p '{"data":{"STYLEMAP_DEMO_PUBLISH_DELAY_MS":"2500"}}'
kubectl rollout restart deployment/backend -n stylemap
kubectl rollout status deployment/backend -n stylemap
# Generate publications and collect evidence, then restore:
kubectl patch configmap stylemap-config -n stylemap --type merge -p '{"data":{"STYLEMAP_DEMO_PUBLISH_DELAY_MS":"0"}}'
kubectl rollout restart deployment/backend -n stylemap
kubectl rollout status deployment/backend -n stylemap
```

For every temporary rule demo: keep the edited file outside Git, apply only that
ConfigMap, restart vmalert, show DEMO labels/annotations in screenshots, and
restore with `kubectl apply -f k8s/alerting/alert-rules.yaml` followed by
`kubectl rollout restart deployment/vmalert -n monitoring`. Reestablish a clean
baseline after the restart and retain the offline tests as evidence for the
original thresholds. Do not submit a lowered-threshold screenshot as proof that
the original threshold was breached.

## Submission screenshots

1. Loaded five rules with expressions, holds and severities in vmalert.
2. A baseline inactive state and matching VictoriaMetrics metric values.
3. A pending alert, then the same alert firing after its hold.
4. Alertmanager's grouped firing alerts.
5. FIRING and RESOLVED emails showing alert name, severity and timestamps; exclude credentials.
6. Recovered metrics and inactive rule after cleanup; restored delay/replicas.

Include sanitized manifests, validator output, actual image versions, trigger
timings, and an explicit list of any DEMO overrides. Missing series and process
health alone are not evidence that all five alert paths have been exercised.

References: [vmalert](https://docs.victoriametrics.com/victoriametrics/vmalert/),
[Alertmanager 0.34.2 configuration](https://github.com/prometheus/alertmanager/blob/v0.34.2/docs/configuration.md),
[Alertmanager email configuration](https://github.com/prometheus/alertmanager/blob/v0.34.2/docs/configuration.md#email_config).
