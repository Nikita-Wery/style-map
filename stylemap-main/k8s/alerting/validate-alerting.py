#!/usr/bin/env python3
"""Validate alerting files locally. Never deploys resources or reads real Secrets.

Requires Python/PyYAML, Docker, and kubectl with access to OpenAPI schemas.
Container checks use --network=none and a disposable placeholder SMTP file.
"""
from copy import deepcopy
from pathlib import Path
import json
import re
import subprocess
import tempfile

import yaml

ROOT = Path(__file__).resolve().parent
FILES = ["alert-rules.yaml", "vmalert.yaml", "alertmanager-config.yaml",
         "alertmanager.yaml", "alertmanager-secret.example.yaml"]
PROMTOOL_IMAGE = "prom/prometheus:v3.12.0"


def run(args):
    result = subprocess.run(args, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f"Validation failed: {' '.join(args)}\n{result.stdout}")
    print(result.stdout.strip() or "OK")


def docker(image, args, mounts=(), entrypoint=None):
    command = ["docker", "run", "--rm", "--network=none", "--read-only",
               "--tmpfs", "/tmp:rw,noexec,nosuid,size=256m",
               "--cap-drop=ALL", "--security-opt=no-new-privileges"]
    for source, target in mounts:
        command += ["--mount", f"type=bind,src={source},dst={target},readonly"]
    if entrypoint:
        command += ["--entrypoint", entrypoint]
    return command + [image] + args


def main():
    resources = [doc for name in FILES for doc in yaml.safe_load_all((ROOT / name).read_text())]
    indexed = {(doc["kind"], doc["metadata"]["name"]): doc for doc in resources}
    assert len(indexed) == len(resources), "Duplicate resources"
    assert all(doc["metadata"]["namespace"] == "monitoring" for doc in resources)
    vm = indexed["Deployment", "vmalert"]
    am = indexed["Deployment", "alertmanager"]
    for deployment in (vm, am):
        assert deployment["spec"]["replicas"] == 1
        pod = deployment["spec"]["template"]
        container = pod["spec"]["containers"][0]
        assert re.search(r":v\d+\.\d+\.\d+$", container["image"]), "Unpinned image"
        assert deployment["spec"]["selector"]["matchLabels"] == pod["metadata"]["labels"]
        service = indexed["Service", deployment["metadata"]["name"]]
        assert service["spec"]["type"] == "ClusterIP"
        assert service["spec"]["selector"] == pod["metadata"]["labels"]
        assert service["spec"]["ports"][0]["targetPort"] == container["ports"][0]["name"]
        volumes = {volume["name"]: volume for volume in pod["spec"]["volumes"]}
        assert all(mount["name"] in volumes for mount in container["volumeMounts"])
        for volume in volumes.values():
            if "configMap" in volume:
                assert ("ConfigMap", volume["configMap"]["name"]) in indexed
            if "secret" in volume:
                assert ("Secret", volume["secret"]["secretName"]) in indexed
            if "persistentVolumeClaim" in volume:
                assert ("PersistentVolumeClaim", volume["persistentVolumeClaim"]["claimName"]) in indexed
    rules_text = indexed["ConfigMap", "stylemap-alert-rules"]["data"]["stylemap-alerts.yaml"]
    rules = yaml.safe_load(rules_text)
    group = rules["groups"][0]
    assert group["interval"] == "15s"
    assert {rule["alert"]: rule["for"] for rule in group["rules"]} == {
        "HighPublishLatency": "2m", "HighHttp5xxRate": "2m", "HighJvmHeapUsage": "5m",
        "HighGcPause": "5m", "BackendUnavailable": "1m"}
    config_text = indexed["ConfigMap", "stylemap-alertmanager-config"]["data"]["alertmanager.yaml"]
    config = yaml.safe_load(config_text)
    assert config["route"]["group_wait"] == "15s"
    assert config["route"]["group_interval"] == "1m"
    assert config["route"]["repeat_interval"] == "4h"
    assert config["route"]["group_by"] == ["environment", "service", "alertname", "severity"]
    assert config["route"]["receiver"] == config["receivers"][0]["name"] == "email"
    email = config["receivers"][0]["email_configs"][0]
    assert email["send_resolved"] is True
    assert email["smarthost"] == "smtp.yandex.ru:587"
    assert email["auth_username"] == email["from"] == email["to"] == "nsluzin@kpfu.ru"
    assert email["require_tls"] is True and email["force_implicit_tls"] is False
    assert email["tls_config"]["server_name"] == "smtp.yandex.ru"
    assert not email["tls_config"].get("insecure_skip_verify", False)
    assert "auth_password" not in email and "auth_secret" not in email
    assert "smtp_auth_password" not in config["global"]
    assert "smtp_auth_secret" not in config["global"]
    assert email["auth_password_file"] == "/etc/alertmanager/smtp/password"
    am_pod = am["spec"]["template"]["spec"]
    smtp_volume = next(volume for volume in am_pod["volumes"] if volume["name"] == "smtp")
    assert smtp_volume["secret"]["secretName"] == "stylemap-alertmanager-smtp"
    assert smtp_volume["secret"]["defaultMode"] == 0o440
    assert set(indexed["Secret", "stylemap-alertmanager-smtp"]["stringData"]) == {"password"}
    smtp_mount = next(mount for mount in am_pod["containers"][0]["volumeMounts"] if mount["name"] == "smtp")
    assert smtp_mount["mountPath"] == str(Path(email["auth_password_file"]).parent)
    assert smtp_mount["readOnly"] is True
    assert not any(env["name"].upper().endswith("_PROXY") for env in am_pod["containers"][0].get("env", []))

    with tempfile.TemporaryDirectory(prefix="stylemap-alert-validation-") as directory:
        work = Path(directory)
        work.chmod(0o755)  # Non-root validator images need to read these synthetic files.
        (work / "stylemap-alerts.yaml").write_text(rules_text)
        (work / "alertmanager.yaml").write_text(config_text)
        (work / "password").write_text("validation-placeholder-never-used")
        tests = yaml.safe_load((ROOT / "tests/alerts.test.yaml").read_text())
        # Exercise unsupported collectors, missing/invalid maxima, and per-JVM isolation.
        healthy_high = tests["tests"][1]
        heap_series = [series for series in healthy_high["input_series"] if series["series"].startswith("jvm_memory_")]
        for name, replacement in [("undefined max", "-1"), ("zero max", "0"),
                                  ("infinite max", "+Inf"), ("NaN max", "NaN")]:
            series = deepcopy(heap_series)
            series[3]["values"] = " ".join([replacement] * 61)
            tests["tests"].append({"name": name, "interval": "15s", "input_series": series,
                "promql_expr_test": [{"expr": 'ALERTS{alertname="HighJvmHeapUsage"}', "eval_time": "10m", "exp_samples": []}]})
        for name, series in [
            ("missing max", deepcopy(heap_series[:3] + heap_series[4:])),
            ("unsupported collector", [{**s, "series": s["series"].replace("Eden Space", "G1 Eden Space")} for s in heap_series]),
            ("unrelated service", [{**s, "series": s["series"].replace('app="backend"', 'app="other"')} for s in healthy_high["input_series"] if not s["series"].startswith("probe_success")]),
            ("unrelated namespace", [{**s, "series": s["series"].replace('kubernetes_namespace="stylemap"', 'kubernetes_namespace="other"')} for s in healthy_high["input_series"] if not s["series"].startswith("probe_success")])]:
            tests["tests"].append({"name": name, "interval": "15s", "input_series": series,
                "promql_expr_test": [{"expr": "ALERTS", "eval_time": "10m", "exp_samples": []}]})
        # A low-utilization JVM must not dilute a hot JVM's heap alert.
        second_jvm = [{**s, "series": s["series"].replace("backend-test", "backend-cold").replace("backend:8081", "cold:8081"),
                       "values": "1+0x60" if "used_bytes" in s["series"] else s["values"]} for s in heap_series]
        tests["tests"].append({"name": "heap per JVM isolation", "interval": "15s", "input_series": deepcopy(heap_series) + second_jvm,
            "promql_expr_test": [{"expr": 'count(ALERTS{alertname="HighJvmHeapUsage",alertstate="firing",kubernetes_pod_name="backend-test"})',
                                  "eval_time": "10m", "exp_samples": [{"labels": "{}", "value": 1}]}]})
        recovery = deepcopy(healthy_high)
        recovery["name"] = "all alerts resolve when observed metrics recover"
        for series in recovery["input_series"]:
            metric = series["series"]
            if metric.startswith("stylemap_outfits_publish_duration_seconds_count"):
                series["values"] = "0+2x32 66+2x60"
            elif metric.startswith("stylemap_outfits_publish_duration_seconds_sum"):
                series["values"] = "0+4x32 128.5+0.5x60"
            elif metric.startswith("http_server_requests"):
                series["values"] = "0+2x32 64+0x60" if 'status="500"' in metric else "0+8x32 266+10x60"
            elif metric.startswith("jvm_gc_pause_seconds_sum"):
                series["values"] = "0+0.3x32 9.61+0.01x60"
            elif metric.startswith("jvm_gc_pause_seconds_count"):
                series["values"] = "0+1x93"
            elif metric.startswith("jvm_memory_used_bytes"):
                series["values"] = "90+0x32 10+0x60"
            elif metric.startswith("jvm_memory_max_bytes"):
                series["values"] = "100+0x93"
            elif metric.startswith("probe_success"):
                series["values"] = "0+0x32 1+0x60"
        recovery["promql_expr_test"] = [deepcopy(healthy_high["promql_expr_test"][3]),
            {"expr": "ALERTS", "eval_time": "16m", "exp_samples": []}]
        tests["tests"].append(recovery)
        (work / "alerts.test.yaml").write_text(yaml.safe_dump(tests))

        manifests = [part for name in FILES for part in ["-f", str(ROOT / name)]]
        run(["kubectl", "create", "--dry-run=client", "--validate=strict", *manifests, "-o", "name"])
        vm_container = vm["spec"]["template"]["spec"]["containers"][0]
        run(docker(vm_container["image"], vm_container["args"] + ["-dryRun"], [(work, "/etc/vmalert")]))
        run(docker(PROMTOOL_IMAGE, ["check", "rules", "/validation/stylemap-alerts.yaml"], [(work, "/validation")], "/bin/promtool"))
        run(docker(PROMTOOL_IMAGE, ["test", "rules", "/validation/alerts.test.yaml"], [(work, "/validation")], "/bin/promtool"))
        am_container = am["spec"]["template"]["spec"]["containers"][0]
        run(docker(am_container["image"], ["check-config", "/validation/alertmanager.yaml"],
                   [(work, "/validation"), (work, "/etc/alertmanager/smtp")], "/bin/amtool"))
        (work / "email.tmpl").write_text(
            '{{ define "smtp.subject" }}' + email["headers"]["Subject"] + '{{ end }}\n'
            '{{ define "smtp.text" }}' + email["text"] + '{{ end }}\n')
        labels = {"alertname": "NotificationTest", "environment": "minikube",
                  "service": "stylemap-backend", "severity": "warning"}
        annotations = {"summary": "Synthetic email template test", "description": "No message is sent."}
        for state in ("firing", "resolved"):
            (work / "email-data.json").write_text(json.dumps({
                "receiver": "email", "status": state, "commonLabels": labels,
                "groupLabels": labels, "commonAnnotations": annotations,
                "alerts": [{"status": state, "labels": labels, "annotations": annotations}]}))
            result = subprocess.run(docker(am_container["image"], [
                "template", "render", "--template.glob=/validation/email.tmpl",
                '--template.text={{ template "smtp.subject" . }}\n{{ template "smtp.text" . }}',
                "--template.data=/validation/email-data.json"], [(work, "/validation")], "/bin/amtool"),
                text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            if result.returncode:
                raise RuntimeError(result.stdout)
            assert f"[{state.upper()}] NotificationTest (warning)" in result.stdout
            assert annotations["summary"] in result.stdout and annotations["description"] in result.stdout
        print("Email subject/body templates: FIRING and RESOLVED rendering OK (no delivery attempted)")
        # Kingpin parses the exact deployment flags; --help exits without starting a server.
        result = subprocess.run(docker(am_container["image"], am_container["args"] + ["--help"]),
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if result.returncode:
            raise RuntimeError(result.stdout)
        print("Alertmanager deployment flags: OK")
        print(f"Validation complete: {len(resources)} Kubernetes resources, five rules, {len(tests['tests'])} rule scenarios. No deployment or notifications performed.")


if __name__ == "__main__":
    main()
