import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from unittest import mock
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
            "run_full_validation": True, "maximum_changed_files": 12,
            "shared_paths": [
                "docs/editorial/backlog-editoriale.json", "docs/editorial/registro-fonti.json",
                "src/main/resources/static/sitemap.xml", "src/main/resources/static/index.html",
            ],
            "forbidden_prefixes": ["scripts/", "pom.xml", "src/main/java/", "src/test/"],
            "commit_message_template": "Pubblica {content_id}: {working_title}",
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
        return PUBLISHER.start(self.worker, run_id, "GUIDE-0001")

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

    def test_publish_rejects_out_of_scope_change(self):
        self.start()
        (self.worker / "pom.xml").write_text("blocked\n", encoding="utf-8")
        with self.assertRaises(PUBLISHER.PublicationStop) as raised:
            PUBLISHER.publish(self.worker, "run-0001", validator=lambda _: None)
        self.assertEqual("STOP-OUT-OF-SCOPE-DIFF", raised.exception.code)
        session = PUBLISHER.load_session(self.worker, "run-0001")
        self.assertEqual("stopped", session["status"])

    def test_publish_is_verified_and_idempotent(self):
        self.start()
        self.prepare_guide()
        session = PUBLISHER.publish(self.worker, "run-0001", validator=lambda _: None)
        self.assertEqual("published", session["status"])
        self.assertTrue(session["push_performed"])
        self.assertFalse(session["deployment_checked"])
        self.assertEqual(session["commit_sha"], run_git(self.worker, "ls-remote", "--heads", "origin", "refs/heads/main").split()[0])
        self.assertFalse((self.worker / ".git/editorial-publication/lock.json").exists())
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
                PUBLISHER.publish(self.worker, "run-0001", validator=lambda _: None)

        pending = PUBLISHER.load_session(self.worker, "run-0001")
        self.assertEqual("committing", pending["status"])
        completed = PUBLISHER.publish(self.worker, "run-0001", validator=lambda _: None)
        self.assertEqual("published", completed["status"])
        self.assertEqual(completed["commit_sha"], run_git(self.worker, "ls-remote", "--heads", "origin", "refs/heads/main").split()[0])


if __name__ == "__main__":
    unittest.main()
