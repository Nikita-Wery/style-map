#!/usr/bin/env python3
"""Bounded publication test using existing APIs. Credentials stay in memory.

No Kubernetes commands, retries of mutations, automatic account registration,
or automatic deletion. Cleanup requires a receipt and explicit --execute.
"""
import argparse
import getpass
import http.cookiejar
import json
import math
import os
from pathlib import Path
import re
import struct
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import warnings
import zlib

MAX_PUBLICATIONS = 30
PAGE_SIZE = 50
MAX_SCAN_PAGES = 4
COUNT = "stylemap_outfits_publish_duration_seconds_count"
SUM = "stylemap_outfits_publish_duration_seconds_sum"


class TestError(Exception):
    pass


def local_url(value, allow_path=False):
    parts = urllib.parse.urlsplit(value)
    if (parts.scheme not in ("http", "https") or parts.hostname not in ("127.0.0.1", "localhost", "::1")
            or parts.username or parts.password or parts.query or parts.fragment
            or (not allow_path and parts.path not in ("", "/"))):
        raise TestError("Use a loopback port-forward URL without credentials, query or fragment.")
    return value.rstrip("/")


def canonical_id(value):
    try:
        return str(uuid.UUID(str(value)))
    except (ValueError, TypeError, AttributeError):
        raise TestError("Invalid UUID in API response or receipt.") from None


def description(run_id, sequence):
    return f"[STYLEMAP-ALERT-TEST HighPublishLatency run={run_id} item={sequence:03d}] temporary test outfit"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


class Client:
    def __init__(self, base_url, timeout=15):
        self.base = local_url(base_url)
        self.timeout = timeout
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(self.cookies), NoRedirect())

    def request(self, method, path, body=None, headers=None, expected=200):
        request = urllib.request.Request(self.base + path, data=body, method=method,
                                         headers={"Accept": "application/json", **(headers or {})})
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                code = response.status
                raw = response.read(2_000_001)
        except urllib.error.HTTPError as error:
            raise TestError(f"{method} {path.split('?')[0]}: HTTP {error.code}; stopped without retry.") from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise TestError("Network failure; stopped without retry. An in-flight mutation may have committed; keep the receipt.") from None
        if code != expected or len(raw) > 2_000_000:
            raise TestError(f"Unexpected HTTP {code} or oversized response; stopped.")
        try:
            data = json.loads(raw)
        except (ValueError, UnicodeError):
            raise TestError("Expected JSON; redirects/HTML are not accepted.") from None
        return data

    def csrf_headers(self):
        data = self.request("GET", "/api/auth/csrf")
        if not isinstance(data, dict) or data.get("headerName") != "X-XSRF-TOKEN":
            raise TestError("Unexpected CSRF header configuration.")
        # The JSON token can be XOR-masked; Spring's SPA handler expects the RAW cookie in the header.
        tokens = [cookie.value for cookie in self.cookies if cookie.name == "XSRF-TOKEN" and cookie.path == "/"]
        if len(tokens) != 1 or not tokens[0]:
            raise TestError("Expected one in-memory XSRF-TOKEN cookie.")
        return {"X-XSRF-TOKEN": urllib.parse.unquote(tokens[0])}

    def login(self, username, password):
        headers = {**self.csrf_headers(), "Content-Type": "application/json"}
        self.request("POST", "/api/auth/login", json.dumps({"username": username, "password": password}).encode(), headers)
        self.csrf_headers()  # Refresh after authentication/session fixation protection.
        account = self.request("GET", "/api/auth/me")
        if not isinstance(account, dict) or account.get("username") != username:
            raise TestError("Authenticated identity did not match the requested test account.")
        account["id"] = canonical_id(account.get("id"))
        return account

    def own_ids(self, account_id, page=0):
        data = self.request("GET", f"/api/posts/user/{account_id}?page={page}&size={PAGE_SIZE}")
        if not isinstance(data, dict) or not isinstance(data.get("content"), list):
            raise TestError("Unexpected own-post listing response.")
        if not all(isinstance(post, dict) for post in data["content"]):
            raise TestError("Unexpected own-post listing item.")
        ids = [canonical_id(post.get("id")) for post in data["content"]]
        if len(ids) > PAGE_SIZE or len(set(ids)) != len(ids):
            raise TestError("Unexpected own-post listing size/duplicates.")
        return ids, data.get("last") is True or len(ids) < PAGE_SIZE

    def details(self, post_id):
        data = self.request("GET", f"/api/posts/{canonical_id(post_id)}")
        if not isinstance(data, dict) or canonical_id(data.get("id")) != post_id:
            raise TestError("Unexpected post details response.")
        return data


def tiny_png():
    def chunk(kind, payload):
        return struct.pack("!I", len(payload)) + kind + payload + struct.pack("!I", zlib.crc32(kind + payload))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack("!2I5B", 1, 1, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"\x00\x40\x80\xc0\xff")) + chunk(b"IEND", b""))


def multipart(run_id, sequence):
    boundary = "stylemap-" + uuid.uuid4().hex
    payload = {"description": description(run_id, sequence), "hashtags": [], "outfitItems": [
        {"name": "Alert test outfit", "brand": "STYLEMAP-ALERT-TEST", "links": {}}]}
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="postData"\r\n'
            'Content-Type: text/plain; charset=utf-8\r\n\r\n').encode() + json.dumps(payload).encode() + b"\r\n"
    body += (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
             f'filename="alert-test-{run_id}-{sequence:03d}.png"\r\nContent-Type: image/png\r\n\r\n').encode()
    body += tiny_png() + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def snapshot(url, timeout):
    request = urllib.request.Request(url, headers={"Accept": "text/plain"})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(request, timeout=timeout) as response:
            text = response.read(5_000_001).decode()
    except (urllib.error.URLError, TimeoutError, OSError, UnicodeError):
        raise TestError("Metrics endpoint unavailable; stopped without retrying publication.") from None
    if len(text) > 5_000_000:
        raise TestError("Oversized metrics response.")
    values = {}
    for name in (COUNT, SUM, "process_start_time_seconds"):
        samples = re.findall(r"^" + name + r"(?:\{[^\n}]*\})?\s+([^\s]+)", text, re.MULTILINE)
        if len(samples) > 1 or (name in (COUNT, SUM) and len(samples) != 1):
            raise TestError("Expected a single backend publication timer; check the management port-forward.")
        if samples:
            try:
                value = float(samples[0])
            except ValueError:
                raise TestError("Invalid timer sample.") from None
            if not math.isfinite(value) or value < 0:
                raise TestError("Non-finite or negative timer sample.")
            values[name] = value
    return values


def verify_increment(before, after):
    if (after[COUNT] < before[COUNT] or after[SUM] < before[SUM]
            or before.get("process_start_time_seconds") != after.get("process_start_time_seconds")):
        raise TestError("Backend restarted or timer reset; stop and reestablish the baseline.")
    delta = after[COUNT] - before[COUNT]
    if delta < 1:
        raise TestError("Publication returned success but the timer count did not increment; stopped.")
    return delta, (after[SUM] - before[SUM]) / delta


def save_receipt(path, receipt, new=False):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if new:
        with path.open("x", encoding="utf-8"):
            pass
        path.chmod(0o600)
    with tempfile.NamedTemporaryFile("w", dir=path.parent, encoding="utf-8", delete=False) as temporary:
        temporary_path = Path(temporary.name)
        try:
            json.dump(receipt, temporary, indent=2)
            temporary.flush()
            os.fsync(temporary.fileno())
        except BaseException:
            temporary_path.unlink(missing_ok=True)
            raise
    try:
        os.replace(temporary_path, path)
    finally:
        temporary_path.unlink(missing_ok=True)


def validate_receipt(receipt):
    try:
        if receipt["version"] != 1:
            raise TestError("Unsupported receipt version.")
        canonical_id(receipt["run_id"])
        canonical_id(receipt["account_id"])
        local_url(receipt["base_url"])
        entries = receipt["entries"]
        if not isinstance(entries, list) or len(entries) > MAX_PUBLICATIONS:
            raise TestError("Invalid receipt size.")
        seen = set()
        post_ids = set()
        for entry in entries:
            number = entry["sequence"]
            if (type(number) is not int or not 1 <= number <= MAX_PUBLICATIONS or number in seen
                    or entry["description"] != description(receipt["run_id"], number)):
                raise TestError("Receipt contains an invalid test marker or duplicate sequence.")
            seen.add(number)
            if entry.get("state") not in ("pending", "published", "deleted"):
                raise TestError("Invalid receipt state.")
            if entry.get("post_id"):
                post_id = canonical_id(entry["post_id"])
                if post_id in post_ids:
                    raise TestError("Duplicate post ID in receipt.")
                post_ids.add(post_id)
    except (KeyError, TypeError):
        raise TestError("Malformed receipt.") from None


def matches(details, receipt, entry):
    return (details.get("authorId") == receipt["account_id"] and details.get("isAuthor") is True
            and details.get("description") == entry["description"])


def publish(client, account, args, receipt_path):
    run_id = str(uuid.uuid4())
    receipt = {"version": 1, "run_id": run_id, "account_id": account["id"],
               "base_url": client.base, "entries": []}
    save_receipt(receipt_path, receipt, new=True)
    print(f"Run {run_id}; receipt: {receipt_path}", flush=True)
    print("Only new posts are created. No automatic cleanup or mutation retries.", flush=True)
    baseline = snapshot(args.metrics_url, args.timeout)
    started = time.monotonic()
    completed = 0
    for number in range(1, args.max_publications + 1):
        if time.monotonic() - started >= args.duration:
            break
        operation_start = time.monotonic()
        existing, _ = client.own_ids(account["id"])
        entry = {"sequence": number, "description": description(run_id, number),
                 "post_id": None, "state": "pending"}
        receipt["entries"].append(entry)
        save_receipt(receipt_path, receipt)  # Durable marker BEFORE sending the POST.
        before = snapshot(args.metrics_url, args.timeout)
        body, content_type = multipart(run_id, number)
        headers = {**client.csrf_headers(), "Content-Type": content_type}
        response = client.request("POST", "/api/posts", body, headers)
        if not isinstance(response, dict) or response.get("status") != "success":
            raise TestError("Unexpected publication response; keep receipt and do not retry the POST.")
        completed += 1
        print(f"[{completed}/{args.max_publications}] POST /api/posts HTTP 200; identifying new post", flush=True)
        current, _ = client.own_ids(account["id"])
        candidates = []
        for post_id in set(current) - set(existing):
            if matches(client.details(post_id), receipt, entry):
                candidates.append(post_id)
        if len(candidates) != 1:
            raise TestError("Cannot uniquely identify the committed post; stop and reconcile from the receipt.")
        entry.update(post_id=candidates[0], state="published")
        save_receipt(receipt_path, receipt)
        after = snapshot(args.metrics_url, args.timeout)
        delta, mean = verify_increment(before, after)
        entry["timer_count_increment"] = delta
        save_receipt(receipt_path, receipt)
        elapsed = time.monotonic() - started
        print(f"  post={entry['post_id']} timer count +{delta:g}; observed mean={mean:.3f}s; elapsed={elapsed:.1f}s", flush=True)
        if number < args.max_publications:
            wait = min(args.interval - (time.monotonic() - operation_start), args.duration - elapsed)
            if wait > 0:
                time.sleep(wait)
    after = snapshot(args.metrics_url, args.timeout)
    delta, mean = verify_increment(baseline, after)
    print(f"Complete: {completed} successful POST responses; timer count +{delta:g}; mean={mean:.3f}s.")
    print("Timer is process-wide: concurrent publications can contribute to its delta. Keep the receipt for cleanup.")


def cleanup(client, account, receipt, path, execute):
    validate_receipt(receipt)
    if account["id"] != receipt["account_id"] or client.base != receipt["base_url"]:
        raise TestError("Cleanup must use the original account ID and exact base URL.")
    pending = {entry["description"]: entry for entry in receipt["entries"]
               if entry["state"] != "deleted" and not entry.get("post_id")}
    if pending:
        print("Reconciling uncertain POST outcomes using exact markers on this account only.")
        for page in range(MAX_SCAN_PAGES):
            ids, last = client.own_ids(account["id"], page)
            for post_id in ids:
                details = client.details(post_id)
                entry = pending.get(details.get("description"))
                if entry and matches(details, receipt, entry):
                    if entry.get("post_id") and entry["post_id"] != post_id:
                        raise TestError("Duplicate marker found; refusing cleanup.")
                    entry["post_id"] = post_id
            if last:
                break
        else:
            raise TestError("Bounded reconciliation exceeded 200 account posts; inspect manually. No deletion performed.")
        save_receipt(path, receipt)
    # Validate every target BEFORE deleting any. Missing/changed/foreign posts stop cleanup.
    targets = []
    for entry in receipt["entries"]:
        if entry["state"] == "deleted":
            continue
        if not entry.get("post_id"):
            print(f"Item {entry['sequence']}: no matching post found; left pending for later reconciliation.")
            continue
        if not matches(client.details(entry["post_id"]), receipt, entry):
            raise TestError("Ownership/marker changed; no cleanup deletion performed.")
        targets.append(entry)
    print(f"{'EXECUTE' if execute else 'DRY RUN'}: {len(targets)} exact owned test posts; run={receipt['run_id']}")
    for entry in targets:
        print(f"  item={entry['sequence']:03d} post={entry['post_id']}", flush=True)
        if execute:
            if not matches(client.details(entry["post_id"]), receipt, entry):
                raise TestError("Target changed before deletion; stopped.")
            response = client.request("DELETE", f"/api/actions/posts/{entry['post_id']}", headers=client.csrf_headers())
            if not isinstance(response, dict) or response.get("status") != "success":
                raise TestError("Unexpected delete response; stopped without retry.")
            entry["state"] = "deleted"
            save_receipt(path, receipt)
            print("  DELETE HTTP 200; receipt updated", flush=True)
    if not execute:
        print("No deletion performed. Review this plan before rerunning with --execute.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    for name in ("publish", "cleanup"):
        command = subparsers.add_parser(name)
        command.add_argument("--base-url", default="http://127.0.0.1:18080")
        command.add_argument("--username", help="Existing enabled dedicated test account; password is prompted privately")
        command.add_argument("--timeout", type=float, default=15)
        command.add_argument("--receipt", type=Path, required=name == "cleanup")
        if name == "publish":
            command.add_argument("--metrics-url", default="http://127.0.0.1:18081/actuator/prometheus")
            command.add_argument("--interval", type=float, default=12)
            command.add_argument("--duration", type=float, default=300)
            command.add_argument("--max-publications", type=int, default=25)
        else:
            command.add_argument("--execute", action="store_true", help="Delete only exact owned posts recorded in this receipt")
    args = parser.parse_args()
    client = None
    try:
        local_url(args.base_url)
        if not math.isfinite(args.timeout) or not 1 <= args.timeout <= 30:
            raise TestError("Timeout must be 1..30 seconds.")
        receipt = None
        if args.command == "publish":
            args.metrics_url = local_url(args.metrics_url, allow_path=True)
            if not (10 <= args.interval <= 15 and 10 <= args.duration <= 300
                    and 1 <= args.max_publications <= MAX_PUBLICATIONS):
                raise TestError("Bounds: interval 10..15s, duration 10..300s, maximum publications 1..30.")
            args.receipt = args.receipt or Path(tempfile.gettempdir()) / f"stylemap-publish-{uuid.uuid4()}.json"
            if args.receipt.exists():
                raise TestError("Receipt already exists; use a new path for a new run.")
            # Fail before prompting or creating posts if the timer endpoint is wrong.
            snapshot(args.metrics_url, args.timeout)
        else:
            if args.receipt.stat().st_size > 100_000:
                raise TestError("Oversized receipt.")
            receipt = json.loads(args.receipt.read_text())
            validate_receipt(receipt)
            if local_url(args.base_url) != receipt["base_url"]:
                raise TestError("Use the exact original base URL from the receipt.")
        username = args.username or input("Dedicated test account username: ").strip()
        warnings.simplefilter("error", getpass.GetPassWarning)
        try:
            password = getpass.getpass("Test account password (hidden): ")
        except (getpass.GetPassWarning, EOFError):
            raise TestError("An interactive terminal with hidden password entry is required.") from None
        if not username or not password:
            raise TestError("Username and password must not be empty.")
        client = Client(args.base_url, args.timeout)
        account = client.login(username, password)
        del password
        print(f"Authenticated test account id={account['id']}; credentials/cookies remain in memory.", flush=True)
        if args.command == "publish":
            publish(client, account, args, args.receipt)
        else:
            cleanup(client, account, receipt, args.receipt, args.execute)
        return 0
    except KeyboardInterrupt:
        print("\nInterrupted. No retry or automatic deletion. An in-flight POST may have committed.")
        if args.receipt:
            print(f"Keep receipt for reconciliation/cleanup: {args.receipt}")
        return 130
    except (TestError, OSError, ValueError) as error:
        # Never print HTTP bodies, cookies, headers, passwords, or raw network exceptions.
        message = str(error) if isinstance(error, TestError) else "Local input/file error; stopped."
        print(f"ERROR: {message}")
        if args.receipt:
            print(f"Keep receipt if present: {args.receipt}")
        return 1
    finally:
        if client:
            client.cookies.clear()


if __name__ == "__main__":
    raise SystemExit(main())
