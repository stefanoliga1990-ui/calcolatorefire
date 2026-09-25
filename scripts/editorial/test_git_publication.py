import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
from datetime import datetime, timezone
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("git_publication.py")
SPEC = importlib.util.spec_from_file_location("git_publication", MODULE_PATH)
PUBLISHER = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = PUBLISHER
SPEC.loader.exec_module(PUBLISHER)


def run_git(root: Path, *arguments: str) -> str:
    process = subprocess.run(
        ["git", *arguments], cwd=root, text=True, encoding="utf-8", errors="replace",
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True,
    )
    return process.stdout.strip()


class GitPublicationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        temporary_root = Path(self.temporary.name)
        self.remote = temporary_root / "origin.git"
        seed = temporary_root / "seed"
        self.worker = temporary_root / "worker"

        subprocess.run(["git", "init", "--bare", "--initial-branch=main", str(self.remote)], check=True, stdout=subprocess.DEVNULL)
        subprocess.run(["git", "init", "--initial-branch=main", str(seed)], check=True, stdout=subprocess.DEVNULL)
        run_git(seed, "config", "user.name", "Test")
        run_git(seed, "config", "user.email", "test@example.test")

        policy = {
            "$schema": "./git-publication-policy.schema.json", "schema_version": "1.0",
            "branch": "main", "remote": "origin", "remote_ref": "refs/heads/main",
            "require_clean_start": True, "fast_forward_only": True, "force_push_allowed": False,
            "run_full_validation": True, "lock_validity_minutes": 480,
            "heartbeat_interval_minutes": 30, "maximum_changed_files": 12,
            "shared_paths": [
                "docs/editorial/backlog-editoriale.json", "docs/editorial/registro-fonti.json",
                "src/main/resources/static/sitemap.xml", "src/main/resources/static/index.html",
            ],
            "forbidden_prefixes": ["scripts/", "pom.xml", "src/main/java/", "src/test/"],
            "commit_message_template": "Pubblica {content_id}: {working_title}",
        }
        log_policy = {
            "$schema": "./execution-log-policy.schema.json", "schema_version": "1.0",
            "storage_root": ".git/editorial-publication", "audit_directory": "audit",
            "history_directory": "history", "append_only_audit": True,
            "immutable_final_result": True, "maximum_message_length": 500,
            "sensitive_key_fragments": [
                "authorization", "cookie", "owner_token", "password", "secret", "token",
            ],
        }
        backlog = {
            "items": [{
                "id": "GUIDE-0001", "content_type": "guide", "status": "pilot",
                "slug": "/guida/fire-italia", "working_title": "FIRE in Italia",
            }]
        }
        editorial = seed / "docs/editorial"
        editorial.mkdir(parents=True)
        (editorial / "git-publication-policy.json").write_text(json.dumps(policy), encoding="utf-8")
        (editorial / "execution-log-policy.json").write_text(json.dumps(log_policy), encoding="utf-8")
        (editorial / "backlog-editoriale.json").write_text(json.dumps(backlog), encoding="utf-8")
        (editorial / "registro-fonti.json").write_text("{}\n", encoding="utf-8")
        static = seed / "src/main/resources/static"
        static.mkdir(parents=True)
        (static / "sitemap.xml").write_text("<urlset/>\n", encoding="utf-8")
        (static / "index.html").write_text("home\n", encoding="utf-8")
        run_git(seed, "add", ".")
        run_git(seed, "commit", "-m", "Seed")
        run_git(seed, "remote", "add", "origin", str(self.remote))
        run_git(seed, "push", "-u", "origin", "main")

        subprocess.run(["git", "clone", "--branch", "main", str(self.remote), str(self.worker)], check=True, stdout=subprocess.DEVNULL)
        run_git(self.worker, "config", "user.name", "Automation")
        run_git(self.worker, "config", "user.email", "automation@example.test")

    def tearDown(self):
        self.temporary.cleanup()

    def start(self, run_id: str = "run-0001") -> dict:
        session = PUBLISHER.start(self.worker, run_id, "GUIDE-0001")
        self.owner_token = session["owner_token"]
        return session

    def prepare_guide(self) -> None:
        backlog_path = self.worker / "docs/editorial/backlog-editoriale.json"
        backlog = json.loads(backlog_path.read_text(encoding="utf-8"))
        backlog["items"][0]["status"] = "pushed_to_main"
        backlog_path.write_text(json.dumps(backlog), encoding="utf-8")
        content = self.worker / "content/guides"
        output = self.worker / "src/main/resources/static/guida"
        content.mkdir(parents=True)
        output.mkdir(parents=True)
        (content / "GUIDE-0001.json").write_text("{}\n", encoding="utf-8")
        (content / "GUIDE-0001.body.html").write_text("<p>Guida</p>\n", encoding="utf-8")
        (output / "fire-italia.html").write_text("<html></html>\n", encoding="utf-8")

    def test_start_records_remote_snapshot_and_lock(self):
        session = self.start()
        self.assertEqual("active", session["status"])
        self.assertEqual(run_git(self.worker, "rev-parse", "HEAD"), session["initial_remote_head"])
        lock = self.worker / ".git/editorial-publication/lock.json"
        self.assertTrue(lock.is_file())

        with self.assertRaises(PUBLISHER.PublicationStop) as raised:
            PUBLISHER.start(self.worker, "run-0001", "GUIDE-0001")
        self.assertEqual("STOP-ACTIVE-EDITORIAL-LOCK", raised.exception.code)

        with self.assertRaises(PUBLISHER.PublicationStop) as raised:
            PUBLISHER.heartbeat(self.worker, "run-0001", "token-errato")
        self.assertEqual("STOP-ACTIVE-EDITORIAL-LOCK", raised.exception.code)

    def test_scheduled_run_id_uses_three_hour_rome_slot(self):
        reference = datetime(2026, 1, 1, 23, 30, tzinfo=timezone.utc)
        self.assertEqual("editorial-20260102-00", PUBLISHER.scheduled_run_id(reference))
        same_slot = datetime(2026, 1, 2, 1, 59, tzinfo=timezone.utc)
        next_slot = datetime(2026, 1, 2, 2, 0, tzinfo=timezone.utc)
        self.assertEqual("editorial-20260102-00", PUBLISHER.scheduled_run_id(same_slot))
        self.assertEqual("editorial-20260102-03", PUBLISHER.scheduled_run_id(next_slot))

    def test_publish_rejects_out_of_scope_change(self):
        self.start()
        (self.worker / "pom.xml").write_text("blocked\n", encoding="utf-8")
        with self.assertRaises(PUBLISHER.PublicationStop) as raised:
            PUBLISHER.publish(self.worker, "run-0001", self.owner_token, validator=lambda _: None)
        self.assertEqual("STOP-OUT-OF-SCOPE-DIFF", raised.exception.code)
        session = PUBLISHER.load_session(self.worker, "run-0001")
        self.assertEqual("stopped", session["status"])

    def test_publish_is_verified_and_idempotent(self):
        self.start()
        self.prepare_guide()
        session = PUBLISHER.publish(self.worker, "run-0001", self.owner_token, validator=lambda _: None)
        self.assertEqual("published", session["status"])
        self.assertTrue(session["push_performed"])
        self.assertFalse(session["deployment_checked"])
        self.assertEqual(session["commit_sha"], run_git(self.worker, "ls-remote", "--heads", "origin", "refs/heads/main").split()[0])
        self.assertFalse((self.worker / ".git/editorial-publication/lock.json").exists())
        history_path = self.worker / ".git/editorial-publication/history/run-0001.json"
        history = json.loads(history_path.read_text(encoding="utf-8"))
        self.assertEqual("success", history["outcome"])
        self.assertNotIn("owner_token", history)
        commit_count = run_git(self.worker, "rev-list", "--count", "HEAD")

        repeated = PUBLISHER.publish(self.worker, "run-0001", validator=lambda _: None)
        self.assertEqual(session["commit_sha"], repeated["commit_sha"])
        self.assertEqual(commit_count, run_git(self.worker, "rev-list", "--count", "HEAD"))

        # Anche dopo una pubblicazione successiva, il vecchio run resta idempotente perché il suo commit è un antenato.
        (self.worker / "later.txt").write_text("later\n", encoding="utf-8")
        run_git(self.worker, "add", "later.txt")
        run_git(self.worker, "commit", "-m", "Later publication")
        run_git(self.worker, "push", "origin", "main")
        later_count = run_git(self.worker, "rev-list", "--count", "HEAD")
        old_run = PUBLISHER.publish(self.worker, "run-0001", validator=lambda _: None)
        self.assertEqual(session["commit_sha"], old_run["commit_sha"])
        self.assertEqual(later_count, run_git(self.worker, "rev-list", "--count", "HEAD"))

    def test_resumes_after_interruption_immediately_after_commit(self):
        self.start()
        self.prepare_guide()
        original_save = PUBLISHER.save_session
        interrupted = False

        def fail_once_after_commit(root, session):
            nonlocal interrupted
            if session.get("status") == "committed" and not interrupted:
                interrupted = True
                raise OSError("simulated interruption")
            original_save(root, session)

        with mock.patch.object(PUBLISHER, "save_session", side_effect=fail_once_after_commit):
            with self.assertRaises(OSError):
                PUBLISHER.publish(self.worker, "run-0001", self.owner_token, validator=lambda _: None)

        pending = PUBLISHER.load_session(self.worker, "run-0001")
        self.assertEqual("committing", pending["status"])
        with self.assertRaises(PUBLISHER.PublicationStop) as raised:
            PUBLISHER.publish(self.worker, "run-0001", self.owner_token, validator=lambda _: None)
        self.assertEqual("STOP-EXECUTION-INTERRUPTED-AFTER-COMMIT", raised.exception.code)
        completed = PUBLISHER.publish(
            self.worker, "run-0001", self.owner_token, validator=lambda _: None, manual_recovery=True,
        )
        self.assertEqual("published", completed["status"])
        self.assertEqual(completed["commit_sha"], run_git(self.worker, "ls-remote", "--heads", "origin", "refs/heads/main").split()[0])

    def test_heartbeat_checkpoint_and_stale_lock_policy(self):
        self.start()
        research = PUBLISHER.checkpoint(
            self.worker, "run-0001", self.owner_token, "research",
            ["SRC-2026-0001"], ["fonte primaria verificata"],
        )
        self.assertEqual("research", research["phase"])
        self.assertEqual(["SRC-2026-0001"], research["source_ids"])

        lock_path = self.worker / ".git/editorial-publication/lock.json"
        lock = json.loads(lock_path.read_text(encoding="utf-8"))
        lock["heartbeat_at"] = "2000-01-01T00:00:00Z"
        lock_path.write_text(json.dumps(lock), encoding="utf-8")
        with self.assertRaises(PUBLISHER.PublicationStop) as raised:
            PUBLISHER.heartbeat(self.worker, "run-0001", self.owner_token)
        self.assertEqual("STOP-STALE-EDITORIAL-LOCK", raised.exception.code)
        cancelled = PUBLISHER.cancel(self.worker, "run-0001", self.owner_token, manual_recovery=True)
        self.assertEqual("cancelled", cancelled["status"])
        final = json.loads(
            (self.worker / ".git/editorial-publication/history/run-0001.json").read_text(encoding="utf-8")
        )
        self.assertEqual("cancelled", final["outcome"])

    def test_audit_log_is_append_only_and_redacts_secrets(self):
        self.start()
        first = PUBLISHER.append_audit_event(
            self.worker, "run-0001", "test", "stopped",
            message="token=abc123 URL https://user:pass@example.test/path",
        )
        second = PUBLISHER.append_audit_event(self.worker, "run-0001", "test", "success")
        self.assertNotEqual(first, second)
        self.assertTrue(first.exists())
        payload = first.read_text(encoding="utf-8")
        self.assertNotIn("abc123", payload)
        self.assertNotIn("user:pass", payload)
        log = PUBLISHER.read_execution_log(self.worker, "run-0001")
        self.assertEqual(2, len(log["events"]))


if __name__ == "__main__":
    unittest.main()
