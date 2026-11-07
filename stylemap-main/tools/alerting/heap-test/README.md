# Isolated HighJvmHeapUsage fixture

This is a standalone Spring Boot 4.0.3 / Java 25 JVM with Micrometer 1.16.3.
Actuator exports **real native JVM memory metrics**. No metric values are
fabricated or manually registered. No StyleMap database, user data, storage,
SMTP configuration, Secrets, existing alerts or scrape configuration are changed.

Implementation/build tests do not arm real retention. Kubernetes deployment and
`POST /heap/start` are separate actions and require approval before execution.

## Isolation and budgets

| Setting | Fixed value |
| --- | --- |
| JVM | `-Xms128m -Xmx128m -XX:+UseSerialGC -XX:NewRatio=9` |
| Kubernetes request | CPU 100m, memory 256Mi |
| Kubernetes limit | CPU 500m, memory 512Mi |
| Allocation | One <=256 KiB array per >=100ms; sequential, touched pages |
| Retained payload cap | 104 MiB, independently enforced by allocator and state machine |
| Target | 87.5%; enter HOLDING only at 87–88% after collection |
| Heap safety | No allocation with <12 MiB projected headroom; abort/release at >=92% |
| Container safety | Abort/release at >=70% of the 512 MiB cgroup limit |
| Ramp deadline | 90 seconds, at most four target calibrations |
| Hold deadline | Eight minutes after reaching target |
| Overall active deadline | Ten minutes per start; pod lifetime <=20 minutes |
| Restart policy | Never |

The old-generation ratio allows retained objects to fit in old space. Allocation
uses the sum of the same three native pool maxima used by the unchanged alert.
The fixture refuses unknown/partial pools, a heap outside 96–128 MiB usable
capacity, missing/unlimited/mismatched cgroups, or an initially high baseline.
Thus accidental `/heap/start` on an ordinary unbounded local JVM fails closed.

Ordinary metrics/HTTP/monitor garbage can temporarily raise used heap above the
post-GC target. While holding, the supervisor samples once per second and requests
collection at 90%; persistent pressure aborts. It never adds retained blocks
after entering HOLDING. Java collection is requested, not a portable guarantee;
failed collection or failure to sustain >85% must be investigated, not bypassed
by increasing limits or changing alerts. The defensive OOME catch releases
references, but OOM is not an intended test outcome or proof of success.

The pod must be in `stylemap` with `app=backend` to match the existing rule.
Its additional label is `alert_test=heap`, and its name is `stylemap-heap-test`.
An intentionally unset custom readiness gate keeps **Pod Ready=False**, so the
existing Services (which have `publishNotReadyAddresses=false`) exclude it.
Never set that gate True. The sole declared port is `heap-metrics:19081`, not
8080/8081 or the named `management` port. Existing annotation-based pod discovery
does not filter readiness and scrapes the real management endpoint directly.

The control server binds only `127.0.0.1:19082` and also checks loopback clients.
The separate management server binds `0.0.0.0:19081`; tests confirm it does not
expose `/heap/*`. There is no Service, database, Secret, service-account token,
PVC or production ConfigMap mount. Only a private, disk-backed 16Mi `/tmp`
emptyDir is used. No production pod is patched or restarted.

The fixture still shares the Minikube node. On the implementation check, the
node reported approximately 4,210 MiB available; recheck before deploying and
require at least 1 GiB available. `kubectl top` was unavailable because the
Metrics API is not installed. Container limits/guards reduce risk but cannot
guarantee immunity to unrelated node-wide pressure or native-memory problems.

## Local build and non-pressure tests

From the repository root, with JDK 25, Maven and Docker:

```sh
mvn -f tools/alerting/heap-test/pom.xml verify
docker build --pull=false -t stylemap-heap-test:v1 tools/alerting/heap-test
```

The image reuses the existing local `stylemap-backend:k8s-v1` JRE base, replacing
its executable JAR with this independent fixture. If building on another machine,
first build that base using the existing project instructions. No container is
started by these build commands. The Docker context includes only the fixture
JAR, excluding credentials and other source/build files.

Safety unit tests use a fake clock, simulated MXBean/cgroup samples and numeric
allocation counters, never real retained arrays. A local HTTP test only reads
IDLE status and native Actuator metrics; it never arms the live engine.
Manifest tests cover readiness isolation, ports, memory budgets and no Secret/PVC
mounts. Invalid real-allocator size tests reject before allocating any block.

## Deploy only after approval

First recheck memory, Services and the current `kubernetes-pods` scrape job. The
readiness gate is safe only while Services continue excluding NotReady pods.

```sh
minikube ssh -- free -m
kubectl get service backend backend-management -n stylemap \
  -o custom-columns=NAME:.metadata.name,PUBLISH_NOT_READY:.spec.publishNotReadyAddresses
kubectl get hpa,pdb,networkpolicy -n stylemap
kubectl create --dry-run=client --validate=strict -f tools/alerting/heap-test/pod.yaml
minikube image load stylemap-heap-test:v1
kubectl create -f tools/alerting/heap-test/pod.yaml
kubectl wait -n stylemap pod/stylemap-heap-test \
  --for=jsonpath='{.status.phase}'=Running --timeout=180s
kubectl get pod stylemap-heap-test -n stylemap \
  -o jsonpath='{.status.phase}{" Ready="}{.status.conditions[?(@.type=="Ready")].status}{"\n"}'
```

Expect `Running Ready=False` once status settles. Do not wait for Ready=True.
The container itself may report ready; the **Pod** readiness gate must remain
false. Do not reuse an existing pod or blindly apply over a previous test.

Check both Services' ready endpoints before arming retention:

```sh
for service in backend backend-management; do
  echo "$service"
  kubectl get endpointslices -n stylemap \
    -l "kubernetes.io/service-name=$service" -o json |
  python3 -c '
import json, sys
for item in json.load(sys.stdin)["items"]:
    for endpoint in item.get("endpoints", []):
        if endpoint.get("conditions", {}).get("ready") is True:
            name = endpoint.get("targetRef", {}).get("name")
            if name == "stylemap-heap-test":
                raise SystemExit("ABORT: test pod is a ready application endpoint")
            print(name)
' || exit 1
done
```

Only serving backend pods should appear. If isolation checks fail, delete only
the test pod and investigate; do not change backend Services or scrape rules.

## Forward and verify baseline

Run each forward in its own terminal:

```sh
kubectl port-forward -n stylemap pod/stylemap-heap-test 19081:19081 19082:19082
kubectl port-forward -n monitoring svc/vm-single-victoria-metrics-single-server 18428:8428
kubectl port-forward -n monitoring svc/vmalert 18880:8880
kubectl port-forward -n monitoring svc/alertmanager 19093:9093
```

In another terminal, wait for the app to start and warm the actual metrics:

```sh
curl -fsS http://localhost:19082/heap/status | python3 -m json.tool
curl -fsS http://localhost:19081/actuator/prometheus \
  | rg '^jvm_memory_(used|max)_bytes\{[^}]*area="heap"'
```

Expect IDLE, zero retained bytes and valid pools. The native maxima must be
finite/positive for exactly Eden Space, Survivor Space and Tenured Gen. Verify
the fixture is scraped (`up=1`) before starting; an absent series is not healthy.

In the same monitoring shell, define:

```sh
vmq() {
  curl -fsSG http://localhost:18428/api/v1/query --data-urlencode "query=$1" \
    | python3 -m json.tool
}
vmq 'up{job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap",kubernetes_pod_name="stylemap-heap-test"}'
vmq 'jvm_memory_max_bytes{job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap",kubernetes_pod_name="stylemap-heap-test",area="heap"}'
```

## Arm the real retained heap

Only after approval and the baseline/isolation checks:

```sh
curl -sS --fail-with-body -X POST http://localhost:19082/heap/start \
  | python3 -m json.tool
```

Success is HTTP 202 and RAMPING. HTTP 409 means busy or a safety precondition
failed; inspect `reason`, do not retry blindly or weaken the guard. There are
no request parameters to increase target, allocation cap or deadlines.

```sh
curl -fsS http://localhost:19082/heap/status | python3 -m json.tool
```

Wait for HOLDING. `retainedBytes` then remains constant. The status includes
actual heap/cgroup samples, pool validity, reason and remaining active seconds.
ABORTED/EXPIRED releases references and never automatically resumes allocation.

Observe the actual ratio, scoped to this test pod only:

```sh
vmq 'sum by (kubernetes_pod_name,instance)(jvm_memory_used_bytes{job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap",kubernetes_pod_name="stylemap-heap-test",area="heap"}) / sum by (kubernetes_pod_name,instance)(jvm_memory_max_bytes{job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap",kubernetes_pod_name="stylemap-heap-test",area="heap"})'
```

This chart filter does not change the alert. The existing rule still evaluates
all backend JVMs separately, with its original 85% threshold, five-minute hold,
pool guards, labels and 15s evaluation interval. No rule reload is required.

Open `http://localhost:18880/vmalert/`, `http://localhost:18428/vmui/` and
`http://localhost:19093/`. Check the specific test-pod alert through the API:

```sh
curl -fsS http://localhost:18880/api/v1/alerts |
python3 -c '
import json, sys
for alert in json.load(sys.stdin)["data"]["alerts"]:
    labels = alert.get("labels", {})
    if labels.get("alertname") == "HighJvmHeapUsage" and labels.get("kubernetes_pod_name") == "stylemap-heap-test":
        print(json.dumps(alert, indent=2))
'
```

Capture PENDING, then FIRING after five continuous minutes >85%, allowing
scrape/query/evaluation delay and the 15s notification group wait. Confirm the
actual `[FIRING] HighJvmHeapUsage` email names `stylemap-heap-test`. Do not claim
delivery from an API response alone. This proves real test-JVM pressure and
the unchanged alert pipeline, not pressure in the serving StyleMap process.

## Release, verify recovery, then delete

Release does not restart the JVM:

```sh
curl -fsS -X POST http://localhost:19082/heap/release | python3 -m json.tool
curl -fsS http://localhost:19082/heap/status | python3 -m json.tool
kubectl get pod stylemap-heap-test -n stylemap \
  -o jsonpath='{.metadata.uid}{" restarts="}{.status.containerStatuses[0].restartCount}{"\n"}'
```

Expect RELEASED and retainedBytes=0. References/list storage are cleared before
requesting GC. With fixed Xms, RSS/committed heap need not fall; **used heap**
must fall. Keep the same pod running and recheck the ratio above until it is
below 85%, the specific alert clears, and `[RESOLVED] HighJvmHeapUsage` arrives.
Allow the 1m notification group interval plus delivery time. Do not delete the
pod first and present missing data as measured recovery.

After collecting both emails and recovery metrics:

```sh
kubectl delete -f tools/alerting/heap-test/pod.yaml
minikube image rm stylemap-heap-test:v1
docker image rm stylemap-heap-test:v1
```

Stop forwards with Ctrl+C. If host Docker and Minikube share an image store,
the second image removal may report it is already absent. No alert, deployment,
Service, ConfigMap, Secret or PVC restoration is needed. If interrupted after
arming, memory auto-releases after eight minutes holding (ten minutes overall);
the pod also has a twenty-minute deadline. Delete the expired pod afterward.

No build/test command above sends email or calls `/heap/start` on a real JVM.
The demonstration is complete only after the separately approved deployment,
real metric transition and observed FIRING/RESOLVED emails.
