"""Offline tool checks: no sockets, credentials, Kubernetes calls or publications."""
import contextlib
import http.cookiejar
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid

spec = importlib.util.spec_from_file_location("publish_latency_test", Path(__file__).parents[1] / "publish_latency_test.py")
tool = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tool)
RUN = str(uuid.uuid4())
OWNER = str(uuid.uuid4())
POST = str(uuid.uuid4())


class FakeClient:
    base = "http://127.0.0.1:18080"

    def __init__(self, receipt, fail_post=False, foreign=False):
        self.receipt = receipt
        self.fail_post = fail_post
        self.foreign = foreign
        self.requests = []
        self.list_calls = 0

    def own_ids(self, account_id, page=0):
        self.list_calls += 1
        return ([] if self.list_calls == 1 else [POST]), True

    def csrf_headers(self):
        return {}

    def request(self, method, path, body=None, headers=None):
        self.requests.append((method, path))
        if self.fail_post:
            raise tool.TestError("Synthetic unknown POST outcome")
        return {"status": "success"}

    def details(self, post_id):
        return {"id": POST, "authorId": str(uuid.uuid4()) if self.foreign else OWNER,
                "isAuthor": True, "description": tool.description(RUN, 1)}


class PublicationToolTest(unittest.TestCase):
    def receipt(self, post_id=POST):
        return {"version": 1, "run_id": RUN, "account_id": OWNER,
                "base_url": FakeClient.base, "entries": [
                    {"sequence": 1, "description": tool.description(RUN, 1),
                     "post_id": post_id, "state": "published" if post_id else "pending"}]}

    def test_login_uses_raw_cookie_and_refreshes_csrf_after_authentication(self):
        client = tool.Client(FakeClient.base)
        raw = uuid.uuid4().hex
        client.cookies.set_cookie(http.cookiejar.Cookie(
            0, "XSRF-TOKEN", raw, None, False, "127.0.0.1", False, False,
            "/", True, False, None, True, None, None, {}))
        replies = [{"headerName": "X-XSRF-TOKEN", "token": uuid.uuid4().hex}, {},
                   {"headerName": "X-XSRF-TOKEN", "token": uuid.uuid4().hex},
                   {"id": OWNER, "username": "synthetic-account"}]
        with patch.object(client, "request", side_effect=replies) as request:
            account = client.login("synthetic-account", uuid.uuid4().hex)
        self.assertEqual(OWNER, account["id"])
        self.assertEqual(raw, request.call_args_list[1].args[3]["X-XSRF-TOKEN"])
        self.assertEqual(["/api/auth/csrf", "/api/auth/login", "/api/auth/csrf", "/api/auth/me"],
                         [call.args[1] for call in request.call_args_list])

    def test_publication_tracks_uuid_and_confirms_real_timer_delta(self):
        args = SimpleNamespace(metrics_url="unused", timeout=1, max_publications=1, duration=10, interval=12)
        zero = {tool.COUNT: 0, tool.SUM: 0}
        one = {tool.COUNT: 1, tool.SUM: 2.5}
        client = FakeClient(self.receipt())
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            path = Path(directory) / "receipt.json"
            with patch.object(tool.uuid, "uuid4", return_value=uuid.UUID(RUN)), \
                    patch.object(tool, "snapshot", side_effect=[zero, zero, one, one]):
                tool.publish(client, {"id": OWNER}, args, path)
            receipt = json.loads(path.read_text())
        self.assertEqual(POST, receipt["entries"][0]["post_id"])
        self.assertEqual(1, receipt["entries"][0]["timer_count_increment"])
        self.assertEqual([("POST", "/api/posts")], client.requests)

    def test_unknown_post_outcome_is_not_retried_and_marker_survives(self):
        args = SimpleNamespace(metrics_url="unused", timeout=1, max_publications=1, duration=10, interval=12)
        client = FakeClient(self.receipt(), fail_post=True)
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            path = Path(directory) / "receipt.json"
            with patch.object(tool.uuid, "uuid4", return_value=uuid.UUID(RUN)), \
                    patch.object(tool, "snapshot", return_value={tool.COUNT: 0, tool.SUM: 0}):
                with self.assertRaises(tool.TestError):
                    tool.publish(client, {"id": OWNER}, args, path)
            receipt = json.loads(path.read_text())
        self.assertEqual("pending", receipt["entries"][0]["state"])
        self.assertIsNone(receipt["entries"][0]["post_id"])
        self.assertEqual([("POST", "/api/posts")], client.requests)

    def test_cleanup_never_deletes_foreign_posts_even_if_is_author_is_true(self):
        receipt = self.receipt()
        client = FakeClient(receipt, foreign=True)
        with self.assertRaises(tool.TestError), contextlib.redirect_stdout(io.StringIO()):
            tool.cleanup(client, {"id": OWNER}, receipt, Path("unused"), execute=True)
        self.assertEqual([], client.requests)

    def test_cleanup_dry_run_then_explicit_delete_is_receipt_scoped(self):
        receipt = self.receipt()
        client = FakeClient(receipt)
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()):
            path = Path(directory) / "receipt.json"
            tool.cleanup(client, {"id": OWNER}, receipt, path, execute=False)
            self.assertEqual([], client.requests)
            tool.cleanup(client, {"id": OWNER}, receipt, path, execute=True)
            self.assertEqual("deleted", json.loads(path.read_text())["entries"][0]["state"])
        self.assertEqual([("DELETE", f"/api/actions/posts/{POST}")], client.requests)

    def test_missing_increment_and_counter_reset_stop_verification(self):
        with self.assertRaises(tool.TestError):
            tool.verify_increment({tool.COUNT: 10, tool.SUM: 20}, {tool.COUNT: 10, tool.SUM: 20})
        with self.assertRaises(tool.TestError):
            tool.verify_increment({tool.COUNT: 10, tool.SUM: 20}, {tool.COUNT: 1, tool.SUM: 2})

    def test_receipt_cannot_target_arbitrary_descriptions(self):
        receipt = self.receipt()
        receipt["entries"][0]["description"] = "An existing user outfit"
        with self.assertRaises(tool.TestError):
            tool.validate_receipt(receipt)

    def test_cleanup_refuses_a_post_edited_since_publication(self):
        receipt = self.receipt()
        client = FakeClient(receipt)
        with patch.object(client, "details", return_value={
                "id": POST, "authorId": OWNER, "isAuthor": True, "description": "Manually changed"}), \
                contextlib.redirect_stdout(io.StringIO()), self.assertRaises(tool.TestError):
            tool.cleanup(client, {"id": OWNER}, receipt, Path("unused"), execute=True)
        self.assertEqual([], client.requests)

    def test_limits_fail_before_any_network_or_password_prompt(self):
        for flags in (["--max-publications", "31"], ["--interval", "9"], ["--duration", "301"]):
            with self.subTest(flags=flags), patch.object(sys, "argv", ["tool", "publish", *flags]), \
                    patch.object(tool, "snapshot") as snapshot, patch.object(tool.getpass, "getpass") as prompt, \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, tool.main())
                snapshot.assert_not_called()
                prompt.assert_not_called()

    def test_remote_or_credential_bearing_urls_are_rejected(self):
        for url in ("https://stylemap.example", "http://username:password@localhost:18080"):
            with self.assertRaises(tool.TestError):
                tool.local_url(url)


if __name__ == "__main__":
    unittest.main()
