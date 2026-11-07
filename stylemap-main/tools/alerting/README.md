# Automated HighPublishLatency test

The script uses existing endpoints and standard-library Python 3 only. It has
not been run against StyleMap as part of implementation. It never runs kubectl,
changes the delay setting, registers accounts, or deletes anything automatically.

## Flow and safeguards

1. Use an **existing, enabled dedicated test account** with a local password.
   Create/confirm it through the existing UI beforehand if needed. OAuth-only
   accounts cannot use this password-login script. No account activation or
   password reset is automated.
2. Fetch `/api/auth/csrf`; retain cookies in memory and use the raw `XSRF-TOKEN`
   cookie in `X-XSRF-TOKEN`, matching `SpaCsrfTokenRequestHandler`. The JSON
   response token may be XOR-masked and is deliberately not used as that header.
3. POST JSON to `/api/auth/login`, refresh CSRF after authentication, then check
   `/api/auth/me`. Password entry is hidden; there are no password/cookie/token
   arguments, environment variables, cookie files, or credential logging.
4. POST multipart `file` and `postData` to `/api/posts`. This creates and publishes
   in one operation; no draft/publish endpoint or new application endpoint is
   needed. Each post has a generated tiny PNG, one test item, no hashtags/links,
   and a unique `[STYLEMAP-ALERT-TEST HighPublishLatency run=... item=...]` marker.
5. The API does not return the new UUID. The script compares this account's
   newest posts before/after each POST, then confirms exact description,
   `authorId`, and `isAuthor`. Do not post concurrently from the same account.
6. A private, non-credential JSON receipt records intended markers **before**
   each POST and confirmed IDs afterward. No existing post is updated. HTTP
   errors, redirects, unexpected JSON, ambiguous IDs, metric resets, and missing
   timer increments stop the run. Mutation requests are never retried.

Default: 25 publications, starts about 12 seconds apart, within a 300-second
start window (roughly 4m50s with the verified 2.5s delay). The hard limits are
30 publications, intervals 10–15 seconds, duration <=300 seconds and request
timeout <=30 seconds. Requests are sequential; slow responses never cause a
concurrent catch-up burst. Individual in-flight operations can finish beyond
the start window. Ctrl+C stops scheduling immediately, discards client cookies,
and preserves the receipt; the server may still commit an in-flight POST.

## Temporarily enable the existing delay

**These commands restart the backend and require explicit approval. They are
instructions only; the script does not execute them.**

Injection is already present: `k8s/05-backend.yaml` imports `stylemap-config`
through `envFrom`; `k8s/01-configmap.yaml` defines the variable;
`application.yaml` maps it to `stylemap.demo.publish-delay-ms`; `PostService`
reads it at startup, clamps to 0..5000ms, and sleeps after timer start. The
publication timer records only after successful transaction completion.

Inspect only the non-sensitive setting and any explicit override:

```sh
kubectl get configmap stylemap-config -n stylemap -o jsonpath='{.data.STYLEMAP_DEMO_PUBLISH_DELAY_MS}{"\n"}'
kubectl get deployment backend -n stylemap -o jsonpath='{.spec.template.spec.containers[?(@.name=="backend")].env[?(@.name=="STYLEMAP_DEMO_PUBLISH_DELAY_MS")].value}{"\n"}'
```

An explicit Deployment environment value overrides `envFrom`. If one is present,
remove just that override **after approval** before using the ConfigMap workflow:
`kubectl set env deployment/backend -n stylemap STYLEMAP_DEMO_PUBLISH_DELAY_MS-`.
Do not dump all environment variables or any Secret.

After approval, set the delay and restart:

```sh
kubectl patch configmap stylemap-config -n stylemap --type merge -p '{"data":{"STYLEMAP_DEMO_PUBLISH_DELAY_MS":"2500"}}'
kubectl rollout restart deployment/backend -n stylemap
kubectl rollout status deployment/backend -n stylemap --timeout=180s
kubectl exec -n stylemap deployment/backend -c backend -- printenv STYLEMAP_DEMO_PUBLISH_DELAY_MS
```

The last command prints only this demo setting. Check for `2500`.

## Run against the port-forward

Start/reestablish this forward **after** every backend rollout. A deployment
forward is pinned to one pod and can terminate when that pod is replaced:

```sh
kubectl port-forward -n stylemap deployment/backend 18080:8080 18081:8081
```

In another terminal, from the repository root:

```sh
python3 tools/alerting/publish_latency_test.py publish \
  --base-url http://127.0.0.1:18080 \
  --metrics-url http://127.0.0.1:18081/actuator/prometheus \
  --interval 12 --duration 300 --max-publications 25 \
  --receipt /tmp/stylemap-latency-slow.json
```

Enter the dedicated account's username and password at the prompts. Use a new
receipt path if the named file already exists; the script refuses to overwrite
a previous run. URLs are restricted to loopback port-forwards to reduce the
chance of accidentally running against a remote service. Do not enable HTTP
debug tracing. Keep receipt files until cleanup is complete.

Each successful operation prints HTTP 200, the new UUID, publication count,
elapsed time, and the publication timer count increment plus mean duration from
the observed count/sum delta. The script reads the actual
`stylemap_outfits_publish_duration_seconds_count` before/after each publication
and fails if it does not increase by at least one. This is a single-process
metric: unrelated concurrent publications can contribute, so exact per-request
attribution is not claimed. Counter/process resets stop the test.

## Observe PENDING, FIRING and email

In separate terminals:

```sh
kubectl port-forward -n monitoring svc/vmalert 18880:8880
kubectl port-forward -n monitoring svc/vm-single-victoria-metrics-single-server 18428:8428
```

Open `http://localhost:18880/vmalert/` and
`http://localhost:18428/vmui/`. Check the loaded HighPublishLatency expression
and the following two queries (units: seconds and approximate event count):

```promql
sum(rate(stylemap_outfits_publish_duration_seconds_sum{job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap"}[2m]))
/
sum(rate(stylemap_outfits_publish_duration_seconds_count{job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap"}[2m]))
```

```promql
sum(increase(stylemap_outfits_publish_duration_seconds_count{job="kubernetes-pods",app="backend",kubernetes_namespace="stylemap"}[2m]))
```

Expect roughly 2.5s and >=5 events. With 12-second spacing, a full 2m window
contains about ten publications. All predicates must remain true for 2m;
allow 15-second scrape/evaluation intervals, query delay and the 15-second
Alertmanager group wait. Capture PENDING then FIRING, and confirm the actual
`[FIRING] HighPublishLatency` email. A PENDING screenshot or HTTP 200 alone is
not proof of a firing alert or delivered email. The script does not assert
vmalert state or email delivery automatically.

## Restore normal latency and prove recovery

After the slow run finishes, restore the existing setting to 0 **with approval**:

```sh
kubectl patch configmap stylemap-config -n stylemap --type merge -p '{"data":{"STYLEMAP_DEMO_PUBLISH_DELAY_MS":"0"}}'
kubectl rollout restart deployment/backend -n stylemap
kubectl rollout status deployment/backend -n stylemap --timeout=180s
kubectl exec -n stylemap deployment/backend -c backend -- printenv STYLEMAP_DEMO_PUBLISH_DELAY_MS
```

Reestablish the backend port-forward, then use the same test account for a
separate normal-latency run (15 additional posts, tracked separately):

```sh
python3 tools/alerting/publish_latency_test.py publish \
  --interval 12 --duration 180 --max-publications 15 \
  --receipt /tmp/stylemap-latency-recovery.json
```

Confirm a measured mean <=1s with sufficient recent publications, the rule
returns inactive, and `[RESOLVED] HighPublishLatency` arrives. Allow the 1m
notification group interval plus email delivery time. A restart or no traffic
can clear the rule by removing data; use the recovery run to demonstrate actual
normal latency, rather than treating missing data as recovery.

## Receipt-scoped cleanup

Start with a dry run; authenticate as the same account through the same base URL:

```sh
python3 tools/alerting/publish_latency_test.py cleanup --receipt /tmp/stylemap-latency-slow.json
python3 tools/alerting/publish_latency_test.py cleanup --receipt /tmp/stylemap-latency-recovery.json
```

After reviewing the exact UUIDs, explicitly execute:

```sh
python3 tools/alerting/publish_latency_test.py cleanup --receipt /tmp/stylemap-latency-slow.json --execute
python3 tools/alerting/publish_latency_test.py cleanup --receipt /tmp/stylemap-latency-recovery.json --execute
```

Only confirmed IDs with the receipt's exact generated description and matching
owner are passed to `DELETE /api/actions/posts/{id}` with a fresh CSRF token.
The existing deletion service removes those posts and their uploaded files.
No broad SQL, account-wide deletion, or filesystem cleanup is used. Deleted
entries are recorded so subsequent cleanup skips them. The script refuses
changed ownership/markers, even for an administrator account.

After a timeout/Ctrl+C, wait for the in-flight operation to finish before
reconciling. For a pending entry without an ID, cleanup scans at most 200 posts
belonging to that account and matches only exact receipt markers. Ambiguous
matches or an incomplete bounded scan stop deletion. An unmatched entry stays
pending for later review; it is not assumed absent forever. If a post was
manually edited/deleted, or a DELETE timed out, review that specific UUID
manually rather than retrying a mutation blindly. Keep unfinished receipts.

## Offline verification (no StyleMap requests)

```sh
python3 -B -m unittest discover -s tools/alerting/tests -v
python3 -B tools/alerting/publish_latency_test.py --help
```

These checks use fake clients and synthetic cookie values only. They verify the
raw-cookie CSRF flow, bounded publication/metric checks, uncertain POST handling,
and ownership/marker restrictions on cleanup. They do not prove live publication
success; the real publication test must be run separately after approval.
