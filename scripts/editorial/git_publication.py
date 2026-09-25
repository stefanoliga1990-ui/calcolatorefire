#!/usr/bin/env python3
"""Pubblica una guida su main con lock, allowlist e push verificato."""

from __future__ import annotations

import argparse
import calendar
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path, PurePosixPath
from typing import Callable, Sequence


RUN_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{5,80}$")
CONTENT_ID = re.compile(r"^GUIDE-[0-9]{4}$")
PHASES = ("preflight", "selection", "research", "drafting", "generation", "validation", "commit", "push", "complete")


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
    if (
        not isinstance(policy.get("lock_validity_minutes"), int)
        or not isinstance(policy.get("heartbeat_interval_minutes"), int)
        or policy["lock_validity_minutes"] < policy["heartbeat_interval_minutes"] * 4
    ):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "Configurazione heartbeat e validità lock non sicura")
    return policy


def load_log_policy(root: Path) -> dict:
    policy = load_json(root / "docs/editorial/execution-log-policy.json")
    expected = {
        "storage_root": ".git/editorial-publication", "audit_directory": "audit",
        "history_directory": "history", "append_only_audit": True, "immutable_final_result": True,
    }
    if any(policy.get(key) != value for key, value in expected.items()):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "La politica dei log non rispetta i vincoli obbligatori")
    if not isinstance(policy.get("maximum_message_length"), int):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "Limite dei messaggi di log non valido")
    return policy


def redact_text(value: str, maximum: int) -> str:
    redacted = re.sub(
        r"(?i)\b(authorization|cookie|owner[_-]?token|password|secret|token)\s*[:=]\s*[^\s,;]+",
        r"\1=[REDACTED]", value,
    )
    redacted = re.sub(r"(?i)(https?://)[^/@\s]+:[^/@\s]+@", r"\1[REDACTED]@", redacted)
    return redacted[:maximum]


def sanitize_log_value(value: object, policy: dict) -> object:
    fragments = tuple(policy.get("sensitive_key_fragments", []))
    maximum = policy["maximum_message_length"]
    if isinstance(value, dict):
        return {
            str(key): "[REDACTED]" if any(fragment in str(key).casefold() for fragment in fragments)
            else sanitize_log_value(item, policy)
            for key, item in value.items()
        }
    if isinstance(value, list):
        return [sanitize_log_value(item, policy) for item in value]
    if isinstance(value, str):
        return redact_text(value, maximum)
    if value is None or isinstance(value, (bool, int, float)):
        return value
    return redact_text(str(value), maximum)


def append_audit_event(
    root: Path, run_id: str, action: str, result: str, *, phase: str | None = None,
    content_id: str | None = None, code: str | None = None, message: str | None = None,
) -> Path:
    if not RUN_ID.fullmatch(run_id):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "run-id non valido per il log")
    policy = load_log_policy(root)
    state_dir, _, _ = repo_paths(root)
    directory = state_dir / policy["audit_directory"] / run_id
    directory.mkdir(parents=True, exist_ok=True)
    timestamp = now_utc()
    event = sanitize_log_value({
        "schema_version": "1.0", "event_id": secrets.token_hex(12), "timestamp": timestamp,
        "run_id": run_id, "action": action, "result": result, "phase": phase,
        "content_id": content_id, "code": code, "message": message,
    }, policy)
    filename = f"{datetime.now(timezone.utc):%Y%m%dT%H%M%S%fZ}-{event['event_id']}.json"
    path = directory / filename
    descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(event, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    return path


def final_result(session: dict) -> dict:
    fields = (
        "schema_version", "run_id", "started_at", "finished_at", "status", "outcome", "phase",
        "content_id", "slug", "source_ids", "checks", "changed_files", "base_head",
        "initial_remote_head", "commit_sha", "commit_performed", "push_performed",
        "deployment_checked", "stop_code", "stop_message", "retry", "notification", "recovery",
    )
    return {field: session.get(field) for field in fields}


def write_final_result(root: Path, session: dict) -> Path:
    policy = load_log_policy(root)
    result = sanitize_log_value(final_result(session), policy)
    state_dir, _, _ = repo_paths(root)
    path = state_dir / policy["history_directory"] / f"{session['run_id']}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    serialized = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    try:
        descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError as exc:
        if path.read_text(encoding="utf-8") == serialized:
            return path
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "Il risultato finale immutabile esiste con contenuto diverso") from exc
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        stream.write(serialized)
    return path


def read_execution_log(root: Path, run_id: str) -> dict:
    policy = load_log_policy(root)
    state_dir, _, _ = repo_paths(root)
    audit_dir = state_dir / policy["audit_directory"] / run_id
    events = [load_json(path) for path in sorted(audit_dir.glob("*.json"))] if audit_dir.exists() else []
    final_path = state_dir / policy["history_directory"] / f"{run_id}.json"
    return {
        "run_id": run_id,
        "session": load_session(root, run_id) if session_path(root, run_id).exists() else None,
        "events": events,
        "final_result": load_json(final_path) if final_path.exists() else None,
    }


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


def parse_datetime(value: object) -> datetime:
    if not isinstance(value, str):
        raise ValueError("timestamp assente")
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None:
        raise ValueError("timestamp privo di fuso orario")
    return parsed.astimezone(timezone.utc)


def read_lock(lock_path: Path) -> dict:
    try:
        value = json.loads(lock_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PublicationStop("STOP-STALE-EDITORIAL-LOCK", f"Lock editoriale illeggibile: {exc}") from exc
    if not isinstance(value, dict):
        raise PublicationStop("STOP-STALE-EDITORIAL-LOCK", "Lock editoriale non valido")
    return value


def lock_is_stale(lock: dict, policy: dict, reference: datetime | None = None) -> bool:
    try:
        heartbeat = parse_datetime(lock.get("heartbeat_at"))
    except ValueError:
        return True
    current = reference or datetime.now(timezone.utc)
    if heartbeat > current + timedelta(minutes=5):
        return True
    return current - heartbeat > timedelta(minutes=policy["lock_validity_minutes"])


def acquire_lock(lock_path: Path, value: dict, policy: dict) -> None:
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    try:
        descriptor = os.open(lock_path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError as exc:
        try:
            existing = read_lock(lock_path)
            owner = existing.get("run_id", "sconosciuto")
            if lock_is_stale(existing, policy):
                raise PublicationStop("STOP-STALE-EDITORIAL-LOCK", f"Lock editoriale obsoleto del run {owner}; recupero manuale richiesto")
        except PublicationStop:
            raise
        raise PublicationStop("STOP-ACTIVE-EDITORIAL-LOCK", f"Lock editoriale attivo; proprietario: {owner}") from exc
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def assert_lock_owner(
    lock_path: Path, run_id: str, owner_token: str, policy: dict, *, allow_stale: bool = False,
) -> dict:
    if not lock_path.exists():
        raise PublicationStop("STOP-STALE-EDITORIAL-LOCK", "Lock editoriale assente")
    lock = read_lock(lock_path)
    if lock.get("run_id") != run_id:
        raise PublicationStop("STOP-ACTIVE-EDITORIAL-LOCK", f"Il lock appartiene al run {lock.get('run_id', 'sconosciuto')}")
    if not owner_token or not secrets.compare_digest(str(lock.get("owner_token", "")), owner_token):
        raise PublicationStop("STOP-ACTIVE-EDITORIAL-LOCK", "Token proprietario del lock non valido")
    if lock_is_stale(lock, policy) and not allow_stale:
        raise PublicationStop("STOP-STALE-EDITORIAL-LOCK", "Lock editoriale oltre la validità; recupero manuale richiesto")
    return lock


def release_owned_lock(lock_path: Path, run_id: str, owner_token: str, policy: dict, *, allow_stale: bool = False) -> None:
    assert_lock_owner(lock_path, run_id, owner_token, policy, allow_stale=allow_stale)
    lock_path.unlink()


def heartbeat(
    root: Path, run_id: str, owner_token: str, phase: str | None = None, *,
    allow_stale: bool = False, allow_phase_skip: bool = False,
) -> dict:
    policy = load_policy(root)
    _, lock_path, _ = repo_paths(root)
    lock = assert_lock_owner(lock_path, run_id, owner_token, policy, allow_stale=allow_stale)
    session = load_session(root, run_id)
    current_phase = session.get("phase", "preflight")
    if phase is not None:
        if phase not in PHASES:
            raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", f"Fase non valida: {phase}")
        current_index = PHASES.index(current_phase)
        requested_index = PHASES.index(phase)
        if requested_index < current_index or (requested_index > current_index + 1 and not allow_phase_skip):
            raise PublicationStop("STOP-EXECUTION-INTERRUPTED", f"Transizione di fase non valida: {current_phase} -> {phase}")
        current_phase = phase
    timestamp = now_utc()
    lock.update({"heartbeat_at": timestamp, "phase": current_phase})
    session.update({"heartbeat_at": timestamp, "phase": current_phase})
    atomic_json_write(lock_path, lock)
    save_session(root, session)
    return session


def checkpoint(
    root: Path, run_id: str, owner_token: str, phase: str, source_ids: Sequence[str] = (), checks: Sequence[str] = (),
) -> dict:
    for source_id in source_ids:
        if not re.fullmatch(r"SRC-[0-9]{4}-[0-9]{4}", source_id):
            raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", f"ID fonte non valido: {source_id}")
    for check in checks:
        if not isinstance(check, str) or not check.strip() or len(check) > 160:
            raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", "Descrizione controllo non valida")
    session = heartbeat(root, run_id, owner_token, phase)
    session["source_ids"] = list(dict.fromkeys([*session.get("source_ids", []), *source_ids]))
    session["checks"] = list(dict.fromkeys([*session.get("checks", []), *(check.strip() for check in checks)]))
    save_session(root, session)
    return session


def save_session(root: Path, session: dict) -> None:
    atomic_json_write(session_path(root, session["run_id"]), session)


def load_session(root: Path, run_id: str) -> dict:
    path = session_path(root, run_id)
    if not path.is_file():
        raise PublicationStop("STOP-EXECUTION-INTERRUPTED", f"Sessione inesistente: {run_id}")
    session = load_json(path)
    required = {
        "schema_version", "run_id", "content_id", "slug", "status", "phase", "started_at",
        "heartbeat_at", "finished_at", "source_ids", "checks", "changed_files", "commit_performed",
        "push_performed", "deployment_checked", "outcome", "retry", "notification", "recovery",
    }
    if not required.issubset(session) or session.get("run_id") != run_id:
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", f"Sessione {run_id} incompleta o incoerente")
    if session.get("phase") not in PHASES or session.get("deployment_checked") is not False:
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", f"Sessione {run_id} con fase o deploy non validi")
    if not all(isinstance(session.get(field), list) for field in ("source_ids", "checks", "changed_files")):
        raise PublicationStop("STOP-EDITORIAL-DATA-INVALID", f"Sessione {run_id} con collezioni non valide")
    return session


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
            fetch_main(root, policy)
            if not is_ancestor(root, existing.get("commit_sha", ""), f"refs/remotes/{policy['remote']}/{policy['branch']}"):
                raise PublicationStop("STOP-ORIGIN-MAIN-ADVANCED", "Il commit del run concluso non appartiene a origin/main")
            write_final_result(root, existing)
            return {**existing, "idempotent_replay": True}
        if lock_path.exists():
            existing_lock = read_lock(lock_path)
            if lock_is_stale(existing_lock, policy):
                raise PublicationStop("STOP-STALE-EDITORIAL-LOCK", f"Il run {run_id} possiede un lock obsoleto; recupero manuale richiesto")
            raise PublicationStop("STOP-ACTIVE-EDITORIAL-LOCK", f"Il run {existing_lock.get('run_id', 'sconosciuto')} è ancora attivo")
        raise PublicationStop("STOP-ACTIVE-EDITORIAL-LOCK", f"run-id già utilizzato: {run_id}")

    item = catalogue_item(root, content_id)
    if item.get("status") not in {"pilot", "ready", "in_progress"}:
        raise PublicationStop("STOP-NO-ELIGIBLE-GUIDE", f"Stato iniziale non pubblicabile: {item.get('status')}")

    timestamp = now_utc()
    owner_token = secrets.token_urlsafe(24)
    lock_value = {
        "schema_version": "1.0", "run_id": run_id, "content_id": content_id,
        "owner_token": owner_token, "acquired_at": timestamp, "heartbeat_at": timestamp, "phase": "preflight",
    }
    acquire_lock(lock_path, lock_value, policy)
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
            "slug": item["slug"], "related_content_ids": list(related_ids), "status": "active",
            "phase": "selection", "started_at": timestamp, "heartbeat_at": now_utc(),
            "finished_at": None, "branch": policy["branch"], "base_head": head_sha(root),
            "initial_remote_head": initial_remote, "allowed_paths": permitted,
            "source_ids": [], "checks": [], "changed_files": [],
            "commit_sha": None, "commit_performed": False, "push_performed": False, "deployment_checked": False,
            "attempt_count": 0, "outcome": None, "stop_code": None, "stop_message": None,
            "retry": None, "notification": None, "recovery": None,
        }
        save_session(root, session)
        atomic_json_write(lock_path, {**lock_value, "base_head": session["base_head"], "phase": "selection", "heartbeat_at": session["heartbeat_at"]})
        return {**session, "owner_token": owner_token, "idempotent_replay": False}
    except Exception:
        if not (state_dir / "sessions" / f"{run_id}.json").exists() and lock_path.exists():
            try:
                release_owned_lock(lock_path, run_id, owner_token, policy)
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
    try:
        catalogue = load_json(root / "docs/editorial/condizioni-arresto.json")
        rule = next(
            (condition for condition in catalogue.get("conditions", []) if condition.get("code") == error.code),
            None,
        )
    except PublicationStop:
        rule = None
    session["status"] = "committed" if session.get("commit_sha") else "stopped"
    session["stop_code"] = error.code
    session["stop_message"] = str(error)
    session["outcome"] = rule.get("outcome") if rule else "failed"
    session["retry"] = rule.get("retry") if rule else "manual"
    session["notification"] = rule.get("notification") if rule else "failed_runs_only"
    session["recovery"] = rule.get("recovery") if rule else "Verificare manualmente lo stato prima di proseguire."
    save_session(root, session)


def is_ancestor(root: Path, ancestor: str, descendant: str) -> bool:
    return subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, descendant], cwd=root,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode == 0


def publish(
    root: Path, run_id: str, owner_token: str = "", validator: Callable[[Path], None] = run_full_validation,
    *, manual_recovery: bool = False,
) -> dict:
    policy = load_policy(root)
    session = load_session(root, run_id)
    if session.get("status") == "published":
        fetch_main(root, policy)
        if not is_ancestor(root, session.get("commit_sha", ""), f"refs/remotes/{policy['remote']}/{policy['branch']}"):
            raise PublicationStop("STOP-ORIGIN-MAIN-ADVANCED", "Il commit della sessione non appartiene più alla storia di origin/main")
        write_final_result(root, session)
        return {**session, "idempotent_replay": True}

    _, lock_path, _ = repo_paths(root)
    assert_lock_owner(lock_path, run_id, owner_token, policy, allow_stale=manual_recovery)
    heartbeat(root, run_id, owner_token, allow_stale=manual_recovery)
    if session.get("status") not in {"active", "stopped", "committing", "committed"}:
        raise PublicationStop("STOP-EXECUTION-INTERRUPTED", f"Stato sessione non pubblicabile: {session.get('status')}")

    session["attempt_count"] = int(session.get("attempt_count", 0)) + 1
    session["stop_code"] = None
    session["stop_message"] = None
    session["outcome"] = None
    session["retry"] = None
    session["notification"] = None
    session["recovery"] = None
    save_session(root, session)
    resumed_after_commit = session.get("status") in {"committing", "committed"}
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
            session.update({"status": "committed", "commit_sha": head_sha(root), "commit_performed": True})
            save_session(root, session)
            resumed_after_commit = True

        # Se il commit è già nella storia remota, il push precedente è dimostrato anche se main è poi avanzato.
        if session.get("commit_sha"):
            fetch_main(root, policy)
            tracking_ref = f"refs/remotes/{policy['remote']}/{policy['branch']}"
            if is_ancestor(root, session["commit_sha"], tracking_ref):
                session.update({
                    "status": "published", "phase": "complete", "commit_performed": True,
                    "push_performed": True, "finished_at": now_utc(), "outcome": "success",
                })
                save_session(root, session)
                write_final_result(root, session)
                release_owned_lock(lock_path, run_id, owner_token, policy, allow_stale=manual_recovery)
                return session

        if session.get("status") == "committed":
            if head_sha(root) != session.get("commit_sha") or changed_paths(root):
                raise PublicationStop("STOP-EXECUTION-INTERRUPTED-AFTER-COMMIT", "Commit locale o working tree non coerenti con la sessione")
        else:
            if head_sha(root) != session.get("base_head"):
                raise PublicationStop("STOP-EXECUTION-INTERRUPTED", "HEAD è cambiato rispetto all'avvio")
            paths = assert_publishable_diff(root, policy, session)
            session["changed_files"] = paths
            save_session(root, session)
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
            session = heartbeat(root, run_id, owner_token, "validation", allow_phase_skip=True)
            session["checks"] = list(dict.fromkeys([*session.get("checks", []), "validazione publication", "test Python", "test Maven"]))
            save_session(root, session)
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
            session["phase"] = "commit"
            save_session(root, session)
            heartbeat(root, run_id, owner_token, "commit")
            git(root, "commit", "-m", message, code="STOP-COMMIT-FAILED")
            session = load_session(root, run_id)
            session.update({"status": "committed", "commit_sha": head_sha(root), "commit_performed": True})
            save_session(root, session)
            resumed_after_commit = False

        if resumed_after_commit and not manual_recovery:
            raise PublicationStop(
                "STOP-EXECUTION-INTERRUPTED-AFTER-COMMIT",
                "Il run contiene un commit locale non confermato: usare il recupero manuale dopo averlo verificato",
            )

        session = heartbeat(root, run_id, owner_token, "push", allow_stale=manual_recovery)
        fetch_main(root, policy)
        if remote_tracking_sha(root, policy) != session["initial_remote_head"] or remote_sha(root, policy) != session["initial_remote_head"]:
            raise PublicationStop("STOP-ORIGIN-MAIN-ADVANCED", "origin/main è avanzato prima del push")
        git(root, "push", policy["remote"], f"HEAD:{policy['remote_ref']}", code="STOP-PUSH-REJECTED")
        if remote_sha(root, policy) != session["commit_sha"]:
            raise PublicationStop("STOP-PUSH-REJECTED", "Lo SHA remoto non coincide con il commit pubblicato")
        session.update({"status": "published", "push_performed": True, "finished_at": now_utc(), "outcome": "success"})
        session["phase"] = "complete"
        save_session(root, session)
        write_final_result(root, session)
        release_owned_lock(lock_path, run_id, owner_token, policy, allow_stale=manual_recovery)
        return session
    except PublicationStop as error:
        if session.get("status") != "published":
            record_stop(root, session, error)
        raise


def cancel(root: Path, run_id: str, owner_token: str, *, manual_recovery: bool = False) -> dict:
    session = load_session(root, run_id)
    if session.get("status") == "cancelled":
        write_final_result(root, session)
        return {**session, "idempotent_replay": True}
    policy = load_policy(root)
    _, lock_path, _ = repo_paths(root)
    assert_lock_owner(lock_path, run_id, owner_token, policy, allow_stale=manual_recovery)
    if session.get("commit_sha") or head_sha(root) != session.get("base_head") or changed_paths(root):
        raise PublicationStop("STOP-EXECUTION-INTERRUPTED", "Annullamento vietato: sono presenti modifiche o un commit locale")
    session.update({
        "status": "cancelled", "outcome": "cancelled", "phase": "complete", "finished_at": now_utc(),
        "retry": None, "notification": None, "recovery": None,
    })
    save_session(root, session)
    release_owned_lock(lock_path, run_id, owner_token, policy, allow_stale=manual_recovery)
    write_final_result(root, session)
    return session


def status_view(root: Path, run_id: str) -> dict:
    session = load_session(root, run_id)
    policy = load_policy(root)
    _, lock_path, _ = repo_paths(root)
    if not lock_path.exists():
        state = "absent"
    else:
        lock = read_lock(lock_path)
        if lock.get("run_id") != run_id:
            state = "owned_by_another_run"
        else:
            state = "stale" if lock_is_stale(lock, policy) else "active"
    return {**session, "lock_state": state}


def scheduled_run_id(reference: datetime | None = None) -> str:
    utc = (reference or datetime.now(timezone.utc)).astimezone(timezone.utc)
    march_last = calendar.monthrange(utc.year, 3)[1]
    october_last = calendar.monthrange(utc.year, 10)[1]
    march_sunday = march_last - ((datetime(utc.year, 3, march_last).weekday() + 1) % 7)
    october_sunday = october_last - ((datetime(utc.year, 10, october_last).weekday() + 1) % 7)
    daylight_start = datetime(utc.year, 3, march_sunday, 1, tzinfo=timezone.utc)
    daylight_end = datetime(utc.year, 10, october_sunday, 1, tzinfo=timezone.utc)
    offset = timedelta(hours=2 if daylight_start <= utc < daylight_end else 1)
    local = utc + offset
    slot_hour = (local.hour // 3) * 3
    return f"editorial-{local:%Y%m%d}-{slot_hour:02d}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2], help=argparse.SUPPRESS)
    commands = parser.add_subparsers(dest="action", required=True)
    start_parser = commands.add_parser("start")
    start_parser.add_argument("--run-id", required=True)
    start_parser.add_argument("--content-id", required=True)
    start_parser.add_argument("--related-content-id", action="append", default=[])
    heartbeat_parser = commands.add_parser("heartbeat")
    heartbeat_parser.add_argument("--run-id", required=True)
    heartbeat_parser.add_argument("--owner-token", required=True)
    checkpoint_parser = commands.add_parser("checkpoint")
    checkpoint_parser.add_argument("--run-id", required=True)
    checkpoint_parser.add_argument("--owner-token", required=True)
    checkpoint_parser.add_argument("--phase", choices=PHASES[2:6], required=True)
    checkpoint_parser.add_argument("--source-id", action="append", default=[])
    checkpoint_parser.add_argument("--check", action="append", default=[])
    publish_parser = commands.add_parser("publish")
    publish_parser.add_argument("--run-id", required=True)
    publish_parser.add_argument("--owner-token", required=True)
    publish_parser.add_argument("--manual-recovery", action="store_true")
    cancel_parser = commands.add_parser("cancel")
    cancel_parser.add_argument("--run-id", required=True)
    cancel_parser.add_argument("--owner-token", required=True)
    cancel_parser.add_argument("--manual-recovery", action="store_true")
    for name in ("status", "log"):
        command = commands.add_parser(name)
        command.add_argument("--run-id", required=True)
    commands.add_parser("run-id")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    try:
        if args.action == "start":
            result = start(root, args.run_id, args.content_id, args.related_content_id)
        elif args.action == "heartbeat":
            result = heartbeat(root, args.run_id, args.owner_token)
        elif args.action == "checkpoint":
            result = checkpoint(root, args.run_id, args.owner_token, args.phase, args.source_id, args.check)
        elif args.action == "publish":
            result = publish(root, args.run_id, args.owner_token, manual_recovery=args.manual_recovery)
        elif args.action == "cancel":
            result = cancel(root, args.run_id, args.owner_token, manual_recovery=args.manual_recovery)
        elif args.action == "run-id":
            result = {"run_id": scheduled_run_id(), "timezone": "Europe/Rome", "slot_hours": 3}
        elif args.action == "log":
            result = read_execution_log(root, args.run_id)
        else:
            result = status_view(root, args.run_id)
        if args.action not in {"run-id", "status", "log"}:
            append_audit_event(
                root, args.run_id, args.action, "success", phase=result.get("phase"),
                content_id=result.get("content_id"), message=f"Comando {args.action} completato",
            )
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except PublicationStop as error:
        run_id = getattr(args, "run_id", None)
        if run_id:
            try:
                session = load_session(root, run_id) if session_path(root, run_id).exists() else {}
                append_audit_event(
                    root, run_id, args.action, "stopped", phase=session.get("phase"),
                    content_id=session.get("content_id"), code=error.code, message=str(error),
                )
            except Exception:
                pass
        print(f"{error.code}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
