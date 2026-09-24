#!/usr/bin/env python3
"""Pubblica una guida su main con lock, allowlist e push verificato."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Callable, Sequence


RUN_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{5,80}$")
CONTENT_ID = re.compile(r"^GUIDE-[0-9]{4}$")


class PublicationStop(RuntimeError):
    """Arresto controllato associato al catalogo editoriale."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def now_utc() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def atomic_json_write(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def load_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", f"{path}: {exc}") from exc
    if not isinstance(value, dict):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", f"{path}: la radice deve essere un oggetto")
    return value


def git(root: Path, *arguments: str, code: str = "STOP-EXECUTION-INTERRUPTED") -> str:
    process = subprocess.run(
        ["git", *arguments], cwd=root, text=True, encoding="utf-8", errors="replace",
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if process.returncode != 0:
        detail = (process.stderr or process.stdout).strip()
        raise PublicationStop(code, f"git {' '.join(arguments)} non riuscito: {detail}")
    return process.stdout.strip()


def repo_paths(root: Path) -> tuple[Path, Path, Path]:
    git_dir_text = git(root, "rev-parse", "--git-dir")
    git_dir = Path(git_dir_text)
    if not git_dir.is_absolute():
        git_dir = root / git_dir
    state_dir = git_dir.resolve() / "editorial-publication"
    return state_dir, state_dir / "lock.json", state_dir / "sessions"


def session_path(root: Path, run_id: str) -> Path:
    if not RUN_ID.fullmatch(run_id):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "run-id non valido (minimo 6 caratteri)")
    return repo_paths(root)[2] / f"{run_id}.json"


def load_policy(root: Path) -> dict:
    policy = load_json(root / "docs/editorial/git-publication-policy.json")
    expected = {
        "branch": "main", "remote": "origin", "remote_ref": "refs/heads/main",
        "require_clean_start": True, "fast_forward_only": True,
        "force_push_allowed": False, "run_full_validation": True,
    }
    if any(policy.get(key) != value for key, value in expected.items()):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "La politica Git non rispetta i vincoli di sicurezza")
    return policy


def normalize_repo_path(value: str) -> str:
    normalized = value.replace("\\", "/")
    path = PurePosixPath(normalized)
    if path.is_absolute() or ".." in path.parts or normalized in {"", "."}:
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", f"Percorso non sicuro nella politica: {value}")
    return path.as_posix()


def changed_paths(root: Path) -> set[str]:
    tracked = git(root, "diff", "--name-only", "-z", "HEAD")
    untracked = git(root, "ls-files", "--others", "--exclude-standard", "-z")
    return {normalize_repo_path(item) for item in (tracked + "\0" + untracked).split("\0") if item}


def current_branch(root: Path) -> str:
    return git(root, "branch", "--show-current")


def head_sha(root: Path) -> str:
    return git(root, "rev-parse", "HEAD")


def remote_tracking_sha(root: Path, policy: dict) -> str:
    return git(root, "rev-parse", f"refs/remotes/{policy['remote']}/{policy['branch']}")


def fetch_main(root: Path, policy: dict) -> None:
    git(root, "fetch", "--no-tags", policy["remote"], policy["branch"], code="STOP-FETCH-FAILED")


def remote_sha(root: Path, policy: dict) -> str:
    output = git(
        root, "ls-remote", "--heads", policy["remote"], policy["remote_ref"],
        code="STOP-FETCH-FAILED",
    )
    rows = [row.split() for row in output.splitlines() if row.strip()]
    if len(rows) != 1 or len(rows[0]) < 2 or rows[0][1] != policy["remote_ref"]:
        raise PublicationStop("STOP-FETCH-FAILED", f"Riferimento remoto non trovato: {policy['remote_ref']}")
    return rows[0][0]


def assert_branch(root: Path, policy: dict) -> None:
    branch = current_branch(root)
    if branch != policy["branch"]:
        raise PublicationStop("STOP-WRONG-BRANCH", f"Branch corrente {branch!r}; richiesto {policy['branch']!r}")


def catalogue_item(root: Path, content_id: str) -> dict:
    backlog = load_json(root / "docs/editorial/backlog-editoriale.json")
    matches = [item for item in backlog.get("items", []) if isinstance(item, dict) and item.get("id") == content_id]
    if len(matches) != 1 or matches[0].get("content_type") != "guide":
        raise PublicationStop("STOP-NO-ELIGIBLE-GUIDE", f"Guida non presente univocamente nel backlog: {content_id}")
    return matches[0]


def allowed_paths(root: Path, policy: dict, content_id: str, related_ids: Sequence[str]) -> set[str]:
    result = {normalize_repo_path(path) for path in policy.get("shared_paths", [])}
    for identifier in (content_id, *related_ids):
        item = catalogue_item(root, identifier)
        if identifier != content_id and item.get("status") != "pushed_to_main":
            raise PublicationStop(
                "STOP-OUT-OF-SCOPE-DIFF", f"La guida correlata {identifier} non risulta già pubblicata",
            )
        slug = item.get("slug", "")
        if not isinstance(slug, str) or not slug.startswith("/guida/"):
            raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", f"Slug non valido per {identifier}")
        result.update({
            f"content/guides/{identifier}.json",
            f"content/guides/{identifier}.body.html",
            f"src/main/resources/static/guida/{slug.rsplit('/', 1)[-1]}.html",
        })
    return result


def acquire_lock(lock_path: Path, value: dict) -> None:
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        descriptor = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError as exc:
        try:
            owner = json.loads(lock_path.read_text(encoding="utf-8")).get("run_id", "sconosciuto")
        except (OSError, json.JSONDecodeError):
            owner = "sconosciuto"
        raise PublicationStop("STOP-ACTIVE-EDITORIAL-LOCK", f"Lock editoriale già presente; proprietario: {owner}") from exc
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def assert_lock_owner(lock_path: Path, run_id: str) -> None:
    if not lock_path.exists():
        raise PublicationStop("STOP-STALE-EDITORIAL-LOCK", "Lock editoriale assente")
    try:
        lock = json.loads(lock_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PublicationStop("STOP-STALE-EDITORIAL-LOCK", f"Lock editoriale illeggibile: {exc}") from exc
    if lock.get("run_id") != run_id:
        raise PublicationStop("STOP-ACTIVE-EDITORIAL-LOCK", f"Il lock appartiene al run {lock.get('run_id', 'sconosciuto')}")


def release_owned_lock(lock_path: Path, run_id: str) -> None:
    assert_lock_owner(lock_path, run_id)
    lock_path.unlink()


def save_session(root: Path, session: dict) -> None:
    atomic_json_write(session_path(root, session["run_id"]), session)


def load_session(root: Path, run_id: str) -> dict:
    path = session_path(root, run_id)
    if not path.is_file():
        raise PublicationStop("STOP-EXECUTION-INTERRUPTED", f"Sessione inesistente: {run_id}")
    return load_json(path)


def start(root: Path, run_id: str, content_id: str, related_ids: Sequence[str] = ()) -> dict:
    if not RUN_ID.fullmatch(run_id):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "run-id non valido (minimo 6 caratteri)")
    if not CONTENT_ID.fullmatch(content_id) or any(not CONTENT_ID.fullmatch(value) for value in related_ids):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "content-id non valido")
    if content_id in related_ids or len(related_ids) != len(set(related_ids)):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "related-content-id duplicati o coincidenti con la guida")

    policy = load_policy(root)
    assert_branch(root, policy)
    if changed_paths(root):
        raise PublicationStop("STOP-REPOSITORY-NOT-CLEAN", "Il repository deve essere pulito all'avvio")

    state_dir, lock_path, _ = repo_paths(root)
    existing_path = session_path(root, run_id)
    if existing_path.exists():
        existing = load_session(root, run_id)
        if existing.get("status") == "published":
            return existing
        if (
            existing.get("status") in {"active", "stopped"}
            and existing.get("content_id") == content_id
            and existing.get("related_content_ids") == list(related_ids)
        ):
            assert_lock_owner(lock_path, run_id)
            return existing
        raise PublicationStop("STOP-ACTIVE-EDITORIAL-LOCK", f"run-id già utilizzato: {run_id}")

    item = catalogue_item(root, content_id)
    if item.get("status") not in {"pilot", "ready", "in_progress"}:
        raise PublicationStop("STOP-NO-ELIGIBLE-GUIDE", f"Stato iniziale non pubblicabile: {item.get('status')}")

    lock_value = {"schema_version": "1.0", "run_id": run_id, "content_id": content_id, "acquired_at": now_utc()}
    acquire_lock(lock_path, lock_value)
    try:
        fetch_main(root, policy)
        local_head = head_sha(root)
        tracked_remote = remote_tracking_sha(root, policy)
        if local_head != tracked_remote:
            ancestor = subprocess.run(
                ["git", "merge-base", "--is-ancestor", local_head, tracked_remote], cwd=root,
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            ).returncode == 0
            if not ancestor:
                raise PublicationStop("STOP-MAIN-NOT-FAST-FORWARD", "main locale e origin/main sono divergenti")
            git(root, "merge", "--ff-only", tracked_remote, code="STOP-MAIN-NOT-FAST-FORWARD")
        if changed_paths(root):
            raise PublicationStop("STOP-REPOSITORY-NOT-CLEAN", "Il repository non è pulito dopo l'allineamento")
        initial_remote = remote_sha(root, policy)
        if head_sha(root) != initial_remote:
            raise PublicationStop("STOP-MAIN-NOT-FAST-FORWARD", "HEAD non coincide con origin/main dopo l'allineamento")
        permitted = sorted(allowed_paths(root, policy, content_id, related_ids))
        session = {
            "schema_version": "1.0", "run_id": run_id, "content_id": content_id,
            "related_content_ids": list(related_ids), "status": "active", "started_at": now_utc(),
            "finished_at": None, "branch": policy["branch"], "base_head": head_sha(root),
            "initial_remote_head": initial_remote, "allowed_paths": permitted,
            "commit_sha": None, "push_performed": False, "deployment_checked": False,
            "attempt_count": 0, "stop_code": None, "stop_message": None,
        }
        save_session(root, session)
        atomic_json_write(lock_path, {**lock_value, "base_head": session["base_head"]})
        return session
    except Exception:
        if not (state_dir / "sessions" / f"{run_id}.json").exists() and lock_path.exists():
            try:
                release_owned_lock(lock_path, run_id)
            except PublicationStop:
                pass
        raise


def run_full_validation(root: Path) -> None:
    executable = shutil.which("pwsh") or shutil.which("powershell")
    if executable is None:
        raise PublicationStop("STOP-TESTS-FAILED", "PowerShell non disponibile per eseguire la suite completa")
    command = [executable, "-NoProfile", "-File", str(root / "scripts/validate-editorial.ps1"), "-Mode", "publication", "-IncludeTests"]
    process = subprocess.run(command, cwd=root)
    if process.returncode != 0:
        raise PublicationStop("STOP-VALIDATION-FAILED", "Validazione editoriale o test non superati")


def assert_publishable_diff(root: Path, policy: dict, session: dict) -> list[str]:
    paths = sorted(changed_paths(root))
    if not paths:
        raise PublicationStop("STOP-OUT-OF-SCOPE-DIFF", "Nessuna modifica da pubblicare")
    maximum = policy.get("maximum_changed_files")
    if not isinstance(maximum, int) or maximum < 1 or len(paths) > maximum:
        raise PublicationStop("STOP-OUT-OF-SCOPE-DIFF", f"Numero di file modificati non ammesso: {len(paths)}")
    allowed = set(session.get("allowed_paths", []))
    recomputed = allowed_paths(root, policy, session["content_id"], session.get("related_content_ids", []))
    if allowed != recomputed:
        raise PublicationStop("STOP-ELIGIBILITY-CHANGED", "La allowlist non coincide più con backlog e policy correnti")
    forbidden = tuple(normalize_repo_path(prefix) for prefix in policy.get("forbidden_prefixes", []))
    outside = [path for path in paths if path not in allowed or any(path == prefix.rstrip("/") or path.startswith(prefix) for prefix in forbidden)]
    if outside:
        raise PublicationStop("STOP-OUT-OF-SCOPE-DIFF", f"File fuori perimetro: {', '.join(outside)}")
    symlinks = [path for path in paths if (root / path).is_symlink()]
    if symlinks:
        raise PublicationStop("STOP-OUT-OF-SCOPE-DIFF", f"Link simbolici non ammessi: {', '.join(symlinks)}")
    return paths


def record_stop(root: Path, session: dict, error: PublicationStop) -> None:
    session["status"] = "committed" if session.get("commit_sha") else "stopped"
    session["stop_code"] = error.code
    session["stop_message"] = str(error)
    save_session(root, session)


def is_ancestor(root: Path, ancestor: str, descendant: str) -> bool:
    return subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, descendant], cwd=root,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode == 0


def publish(root: Path, run_id: str, validator: Callable[[Path], None] = run_full_validation) -> dict:
    policy = load_policy(root)
    session = load_session(root, run_id)
    if session.get("status") == "published":
        fetch_main(root, policy)
        if not is_ancestor(root, session.get("commit_sha", ""), f"refs/remotes/{policy['remote']}/{policy['branch']}"):
            raise PublicationStop("STOP-ORIGIN-MAIN-ADVANCED", "Il commit della sessione non appartiene più alla storia di origin/main")
        return session

    _, lock_path, _ = repo_paths(root)
    assert_lock_owner(lock_path, run_id)
    if session.get("status") not in {"active", "stopped", "committing", "committed"}:
        raise PublicationStop("STOP-EXECUTION-INTERRUPTED", f"Stato sessione non pubblicabile: {session.get('status')}")

    session["attempt_count"] = int(session.get("attempt_count", 0)) + 1
    session["stop_code"] = None
    session["stop_message"] = None
    save_session(root, session)
    try:
        assert_branch(root, policy)

        # Recupero un commit creato subito prima di un'interruzione avvenuta durante il salvataggio della sessione.
        if session.get("status") == "committing" and head_sha(root) != session.get("base_head"):
            parent = git(root, "rev-parse", "HEAD^")
            subject = git(root, "log", "-1", "--format=%s")
            committed_paths = {
                normalize_repo_path(path)
                for path in git(root, "diff-tree", "--no-commit-id", "--name-only", "-r", "-z", "HEAD").split("\0")
                if path
            }
            if (
                parent != session.get("base_head")
                or subject != session.get("commit_message")
                or committed_paths != set(session.get("staged_paths", []))
                or changed_paths(root)
            ):
                raise PublicationStop("STOP-EXECUTION-INTERRUPTED-AFTER-COMMIT", "Commit interrotto non riconoscibile in modo sicuro")
            session.update({"status": "committed", "commit_sha": head_sha(root)})
            save_session(root, session)

        # Se il commit è già nella storia remota, il push precedente è dimostrato anche se main è poi avanzato.
        if session.get("commit_sha"):
            fetch_main(root, policy)
            tracking_ref = f"refs/remotes/{policy['remote']}/{policy['branch']}"
            if is_ancestor(root, session["commit_sha"], tracking_ref):
                session.update({"status": "published", "push_performed": True, "finished_at": now_utc()})
                save_session(root, session)
                release_owned_lock(lock_path, run_id)
                return session

        if session.get("status") == "committed":
            if head_sha(root) != session.get("commit_sha") or changed_paths(root):
                raise PublicationStop("STOP-EXECUTION-INTERRUPTED-AFTER-COMMIT", "Commit locale o working tree non coerenti con la sessione")
        else:
            if head_sha(root) != session.get("base_head"):
                raise PublicationStop("STOP-EXECUTION-INTERRUPTED", "HEAD è cambiato rispetto all'avvio")
            paths = assert_publishable_diff(root, policy, session)
            item = catalogue_item(root, session["content_id"])
            if item.get("status") != "pushed_to_main":
                raise PublicationStop("STOP-ELIGIBILITY-CHANGED", "Prima del publish la guida deve avere stato pushed_to_main nel backlog")
            for identifier in (session["content_id"], *session.get("related_content_ids", [])):
                related_item = catalogue_item(root, identifier)
                required = {
                    f"content/guides/{identifier}.json",
                    f"content/guides/{identifier}.body.html",
                    f"src/main/resources/static/guida/{related_item['slug'].rsplit('/', 1)[-1]}.html",
                }
                if not all((root / path).is_file() and not (root / path).is_symlink() for path in required):
                    raise PublicationStop("STOP-GENERATION-FAILED", f"Sorgenti o pagina generata mancanti per {identifier}")
            validator(root)
            fetch_main(root, policy)
            if remote_tracking_sha(root, policy) != session["initial_remote_head"] or remote_sha(root, policy) != session["initial_remote_head"]:
                raise PublicationStop("STOP-ORIGIN-MAIN-ADVANCED", "origin/main è avanzato durante l'esecuzione")

            git(root, "add", "--", *paths, code="STOP-COMMIT-FAILED")
            staged = {item for item in git(root, "diff", "--cached", "--name-only", "-z").split("\0") if item}
            if staged != set(paths) or changed_paths(root) != set(paths):
                raise PublicationStop("STOP-OUT-OF-SCOPE-DIFF", "Indice Git non coerente con la allowlist")
            title = " ".join(str(item["working_title"]).split()).replace("\n", " ")[:80]
            message = policy["commit_message_template"].format(content_id=session["content_id"], working_title=title)
            session.update({"status": "committing", "staged_paths": paths, "commit_message": message})
            save_session(root, session)
            git(root, "commit", "-m", message, code="STOP-COMMIT-FAILED")
            session.update({"status": "committed", "commit_sha": head_sha(root)})
            save_session(root, session)

        fetch_main(root, policy)
        if remote_tracking_sha(root, policy) != session["initial_remote_head"] or remote_sha(root, policy) != session["initial_remote_head"]:
            raise PublicationStop("STOP-ORIGIN-MAIN-ADVANCED", "origin/main è avanzato prima del push")
        git(root, "push", policy["remote"], f"HEAD:{policy['remote_ref']}", code="STOP-PUSH-REJECTED")
        if remote_sha(root, policy) != session["commit_sha"]:
            raise PublicationStop("STOP-PUSH-REJECTED", "Lo SHA remoto non coincide con il commit pubblicato")
        session.update({"status": "published", "push_performed": True, "finished_at": now_utc()})
        save_session(root, session)
        release_owned_lock(lock_path, run_id)
        return session
    except PublicationStop as error:
        record_stop(root, session, error)
        raise


def cancel(root: Path, run_id: str) -> dict:
    session = load_session(root, run_id)
    _, lock_path, _ = repo_paths(root)
    assert_lock_owner(lock_path, run_id)
    if session.get("commit_sha") or head_sha(root) != session.get("base_head") or changed_paths(root):
        raise PublicationStop("STOP-EXECUTION-INTERRUPTED", "Annullamento vietato: sono presenti modifiche o un commit locale")
    session.update({"status": "cancelled", "finished_at": now_utc()})
    save_session(root, session)
    release_owned_lock(lock_path, run_id)
    return session


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2], help=argparse.SUPPRESS)
    commands = parser.add_subparsers(dest="action", required=True)
    start_parser = commands.add_parser("start")
    start_parser.add_argument("--run-id", required=True)
    start_parser.add_argument("--content-id", required=True)
    start_parser.add_argument("--related-content-id", action="append", default=[])
    for name in ("publish", "status", "cancel"):
        command = commands.add_parser(name)
        command.add_argument("--run-id", required=True)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    try:
        if args.action == "start":
            result = start(root, args.run_id, args.content_id, args.related_content_id)
        elif args.action == "publish":
            result = publish(root, args.run_id)
        elif args.action == "cancel":
            result = cancel(root, args.run_id)
        else:
            result = load_session(root, args.run_id)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except PublicationStop as error:
        print(f"{error.code}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
