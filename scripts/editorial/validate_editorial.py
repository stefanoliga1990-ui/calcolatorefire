#!/usr/bin/env python3
"""Esegue i controlli editoriali, SEO e di pubblicazione del repository."""

from __future__ import annotations

import argparse
import importlib.util
import json
import re
import sys
from dataclasses import dataclass, field
from datetime import date, datetime
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse
from xml.etree import ElementTree


SITE_ORIGIN = "https://simulatorefire.com"
CONTENT_ID = re.compile(r"^(?:GUIDE|METH)-[0-9]{4}$")
GUIDE_ID = re.compile(r"^GUIDE-[0-9]{4}$")
SOURCE_ID = re.compile(r"^SRC-([0-9]{4})-([0-9]{4})$")
SLUG = re.compile(r"^/(?:guida/[a-z0-9]+(?:-[a-z0-9]+)*|metodologia)$")
TOKEN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")

BACKLOG_ROOT_KEYS = {"$schema", "schema_version", "updated_at", "selection_policy", "items"}
SELECTION_KEYS = {
    "required_status", "automation_eligible", "dependencies_must_have_status", "order_by",
    "maximum_items_per_run", "when_no_item_matches",
}
BACKLOG_ITEM_KEYS = {
    "id", "content_type", "status", "execution_mode", "automation_eligible", "sequence", "priority",
    "slug", "working_title", "cluster", "parent_id", "search_intent", "primary_query",
    "secondary_queries", "reader_outcome", "unique_value", "simulator_features", "source_focus",
    "risk_level", "freshness", "dependencies", "notes",
}
SOURCE_ROOT_KEYS = {"$schema", "schema_version", "updated_at", "next_id_by_year", "sources"}
SOURCE_KEYS = {
    "id", "status", "level", "type", "title", "responsible_entity", "authors", "url",
    "persistent_identifier", "dates", "applicability", "locator", "verification", "usages",
    "limitations", "conflicts", "local_copy", "notes",
}
SOURCE_NESTED_KEYS = {
    "dates": {"published", "updated", "first_accessed_at", "last_accessed_at"},
    "applicability": {"valid_from", "valid_to", "as_of", "reference_period", "version", "provisional"},
    "locator": {"article", "section", "page", "table_or_figure", "dataset_or_series"},
    "verification": {"checked_at", "checked_by", "full_content_accessed", "original_source_checked"},
}
USAGE_KEYS = {"content_id", "claim", "importance", "verified_at"}
CONFLICT_KEYS = {"source_id", "status", "resolution"}
LOCAL_COPY_KEYS = {"path", "sha256", "license_note"}


@dataclass
class ValidationReport:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    checks: int = 0

    def check(self, condition: bool, message: str) -> None:
        self.checks += 1
        if not condition:
            self.errors.append(message)

    def warn(self, condition: bool, message: str) -> None:
        if not condition:
            self.warnings.append(message)


class PageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.lang: str | None = None
        self.title_parts: list[str] = []
        self._in_title = False
        self.h1_count = 0
        self.canonicals: list[str] = []
        self.meta_names: dict[str, list[str]] = {}
        self.meta_properties: dict[str, list[str]] = {}
        self.ids: set[str] = set()
        self.duplicate_ids: set[str] = set()
        self.hrefs: list[str] = []
        self.asset_urls: list[str] = []
        self.json_ld: list[str] = []
        self._json_parts: list[str] | None = None

    @property
    def title(self) -> str:
        return " ".join("".join(self.title_parts).split())

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = {name.lower(): value or "" for name, value in attrs}
        tag = tag.lower()
        if tag == "html":
            self.lang = values.get("lang")
        elif tag == "title":
            self._in_title = True
        elif tag == "h1":
            self.h1_count += 1
        elif tag == "link":
            relations = values.get("rel", "").split()
            if "canonical" in relations:
                self.canonicals.append(values.get("href", ""))
            elif values.get("href"):
                self.asset_urls.append(values["href"])
        elif tag == "meta":
            if values.get("name"):
                self.meta_names.setdefault(values["name"].lower(), []).append(values.get("content", ""))
            if values.get("property"):
                self.meta_properties.setdefault(values["property"].lower(), []).append(values.get("content", ""))
        elif tag == "a":
            self.hrefs.append(values.get("href", ""))
        elif tag in {"img", "script"} and values.get("src"):
            self.asset_urls.append(values["src"])
        elif tag == "script" and values.get("type", "").lower() == "application/ld+json":
            self._json_parts = []
        identifier = values.get("id")
        if identifier:
            if identifier in self.ids:
                self.duplicate_ids.add(identifier)
            self.ids.add(identifier)

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if tag == "title":
            self._in_title = False
        elif tag == "script" and self._json_parts is not None:
            self.json_ld.append("".join(self._json_parts))
            self._json_parts = None

    def handle_data(self, data: str) -> None:
        if self._in_title:
            self.title_parts.append(data)
        if self._json_parts is not None:
            self._json_parts.append(data)


def load_json(path: Path, report: ValidationReport) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        report.errors.append(f"{path}: JSON non valido o non leggibile: {exc}")
        return {}
    report.check(isinstance(value, dict), f"{path}: la radice deve essere un oggetto")
    return value if isinstance(value, dict) else {}


def exact_keys(value: object, expected: set[str], context: str, report: ValidationReport) -> bool:
    if not isinstance(value, dict):
        report.errors.append(f"{context}: deve essere un oggetto")
        return False
    missing = expected - set(value)
    extra = set(value) - expected
    report.check(not missing, f"{context}: campi mancanti: {', '.join(sorted(missing))}")
    report.check(not extra, f"{context}: campi non previsti: {', '.join(sorted(extra))}")
    return not missing and not extra


def valid_date(value: object) -> bool:
    if not isinstance(value, str):
        return False
    try:
        date.fromisoformat(value)
        return bool(re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value))
    except ValueError:
        return False


def valid_datetime(value: object) -> bool:
    if not isinstance(value, str):
        return False
    try:
        datetime.fromisoformat(value.replace("Z", "+00:00"))
        return "T" in value
    except ValueError:
        return False


def nonempty(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def unique_nonempty_strings(value: object, minimum: int = 0) -> bool:
    return (
        isinstance(value, list)
        and len(value) >= minimum
        and all(nonempty(item) for item in value)
        and len(value) == len(set(value))
    )


def validate_backlog(root: Path, report: ValidationReport) -> dict:
    path = root / "docs/editorial/backlog-editoriale.json"
    backlog = load_json(path, report)
    if not exact_keys(backlog, BACKLOG_ROOT_KEYS, "backlog", report):
        return backlog
    report.check(backlog["$schema"] == "./backlog-editoriale.schema.json", "backlog: riferimento allo schema errato")
    report.check(backlog["schema_version"] == "1.0", "backlog: versione schema non supportata")
    report.check(valid_date(backlog["updated_at"]), "backlog.updated_at: data non valida")
    policy = backlog["selection_policy"]
    if exact_keys(policy, SELECTION_KEYS, "backlog.selection_policy", report):
        expected = {
            "required_status": "ready",
            "automation_eligible": True,
            "dependencies_must_have_status": "pushed_to_main",
            "order_by": ["priority_ascending", "sequence_ascending"],
            "maximum_items_per_run": 1,
            "when_no_item_matches": "stop_without_changes",
        }
        report.check(policy == expected, "backlog.selection_policy: regole diverse dal contratto")
    items = backlog["items"]
    if not isinstance(items, list) or not items:
        report.errors.append("backlog.items: deve essere un array non vuoto")
        return backlog

    ids: list[str] = []
    slugs: list[str] = []
    sequences: list[int] = []
    primary_queries: list[str] = []
    statuses = {"planned", "pilot", "ready", "in_progress", "pushed_to_main", "needs_correction", "blocked", "discarded"}
    modes = {"manual_prerequisite", "manual_pilot", "automatic"}
    for index, item in enumerate(items):
        context = f"backlog.items[{index}]"
        if not exact_keys(item, BACKLOG_ITEM_KEYS, context, report):
            continue
        identifier = item["id"]
        report.check(isinstance(identifier, str) and CONTENT_ID.fullmatch(identifier) is not None, f"{context}.id non valido")
        report.check(item["content_type"] in {"guide", "methodology"}, f"{context}.content_type non valido")
        report.check(item["status"] in statuses, f"{context}.status non valido")
        report.check(item["execution_mode"] in modes, f"{context}.execution_mode non valido")
        report.check(isinstance(item["automation_eligible"], bool), f"{context}.automation_eligible deve essere booleano")
        report.check(isinstance(item["sequence"], int) and item["sequence"] >= 0, f"{context}.sequence non valida")
        report.check(item["priority"] in {"P0", "P1", "P2", "P3"}, f"{context}.priority non valida")
        report.check(isinstance(item["slug"], str) and SLUG.fullmatch(item["slug"]) is not None, f"{context}.slug non valido")
        report.check(nonempty(item["working_title"]), f"{context}.working_title vuoto")
        report.check(isinstance(item["cluster"], str) and TOKEN.fullmatch(item["cluster"]) is not None, f"{context}.cluster non valido")
        report.check(item["parent_id"] is None or (isinstance(item["parent_id"], str) and CONTENT_ID.fullmatch(item["parent_id"])), f"{context}.parent_id non valido")
        for field_name in ("search_intent", "primary_query", "reader_outcome", "unique_value"):
            report.check(nonempty(item[field_name]), f"{context}.{field_name} vuoto")
        for field_name in ("secondary_queries", "simulator_features", "source_focus"):
            report.check(unique_nonempty_strings(item[field_name], 1), f"{context}.{field_name} deve contenere valori unici")
        report.check(item["risk_level"] in {"low", "medium", "high"}, f"{context}.risk_level non valido")
        report.check(item["freshness"] in {"low", "medium", "high"}, f"{context}.freshness non valida")
        report.check(unique_nonempty_strings(item["dependencies"]), f"{context}.dependencies non valide")
        report.check(item["notes"] is None or nonempty(item["notes"]), f"{context}.notes non valida")
        if item["content_type"] == "methodology":
            report.check(str(identifier).startswith("METH-") and item["slug"] == "/metodologia", f"{context}: metodologia incoerente")
        else:
            report.check(str(identifier).startswith("GUIDE-") and str(item["slug"]).startswith("/guida/"), f"{context}: guida incoerente")
        expected_eligible = item["execution_mode"] == "automatic"
        report.check(item["automation_eligible"] is expected_eligible, f"{context}: automazione incoerente con execution_mode")
        if item["status"] == "pilot":
            report.check(item["execution_mode"] == "manual_pilot", f"{context}: stato pilot richiede manual_pilot")
        ids.append(identifier)
        slugs.append(item["slug"])
        sequences.append(item["sequence"])
        primary_queries.append(item["primary_query"].casefold().strip())

    report.check(len(ids) == len(set(ids)), "backlog: identificatori duplicati")
    report.check(len(slugs) == len(set(slugs)), "backlog: slug duplicati")
    report.check(len(sequences) == len(set(sequences)), "backlog: sequence duplicate")
    report.check(len(primary_queries) == len(set(primary_queries)), "backlog: primary_query duplicate")
    known = set(ids)
    graph: dict[str, list[str]] = {}
    for item in items:
        if not isinstance(item, dict) or item.get("id") not in known:
            continue
        references = list(item.get("dependencies", []))
        if item.get("parent_id") is not None:
            references.append(item["parent_id"])
        for reference in references:
            report.check(reference in known, f"backlog {item['id']}: riferimento inesistente {reference}")
            report.check(reference != item["id"], f"backlog {item['id']}: autoriferimento")
        graph[item["id"]] = list(item.get("dependencies", []))
    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(node: str) -> None:
        if node in visiting:
            report.errors.append(f"backlog: ciclo nelle dipendenze che coinvolge {node}")
            return
        if node in visited:
            return
        visiting.add(node)
        for dependency in graph.get(node, []):
            if dependency in graph:
                visit(dependency)
        visiting.remove(node)
        visited.add(node)

    for node in graph:
        visit(node)
    return backlog


def validate_registry(root: Path, backlog: dict, report: ValidationReport) -> dict:
    path = root / "docs/editorial/registro-fonti.json"
    registry = load_json(path, report)
    if not exact_keys(registry, SOURCE_ROOT_KEYS, "registro fonti", report):
        return registry
    report.check(registry["$schema"] == "./registro-fonti.schema.json", "registro fonti: riferimento schema errato")
    report.check(registry["schema_version"] == "1.0", "registro fonti: versione schema non supportata")
    report.check(valid_date(registry["updated_at"]), "registro fonti: updated_at non valida")
    counters = registry["next_id_by_year"]
    report.check(isinstance(counters, dict) and bool(counters), "registro fonti: next_id_by_year non valido")
    content_ids = {item.get("id") for item in backlog.get("items", []) if isinstance(item, dict)}
    sources = registry["sources"]
    if not isinstance(sources, list):
        report.errors.append("registro fonti: sources deve essere un array")
        return registry
    identifiers: list[str] = []
    max_by_year: dict[str, int] = {}
    for index, source in enumerate(sources):
        context = f"registro.sources[{index}]"
        if not exact_keys(source, SOURCE_KEYS, context, report):
            continue
        identifier = source["id"]
        match = SOURCE_ID.fullmatch(identifier) if isinstance(identifier, str) else None
        report.check(match is not None, f"{context}.id non valido")
        if match:
            year, number = match.groups()
            max_by_year[year] = max(max_by_year.get(year, 0), int(number))
        identifiers.append(identifier)
        report.check(source["status"] in {"approved", "superseded", "unavailable", "rejected"}, f"{context}.status non valido")
        report.check(source["level"] in {"A", "B", "C", "D"}, f"{context}.level non valido")
        report.check(source["type"] in {
            "normative_act", "administrative_practice", "institutional_page", "official_dataset",
            "scientific_study", "standard_or_specification", "book_or_manual", "technical_analysis",
            "journalistic_content", "commercial_document", "other",
        }, f"{context}.type non valido")
        report.check(nonempty(source["title"]), f"{context}.title vuoto")
        report.check(nonempty(source["responsible_entity"]), f"{context}.responsible_entity vuoto")
        report.check(unique_nonempty_strings(source["authors"]), f"{context}.authors non valido")
        parsed_url = urlparse(source["url"]) if isinstance(source["url"], str) else None
        report.check(bool(parsed_url and parsed_url.scheme == "https" and parsed_url.netloc), f"{context}.url deve essere HTTPS")
        report.check(source["persistent_identifier"] is None or nonempty(source["persistent_identifier"]), f"{context}.persistent_identifier non valido")
        nested_valid = True
        for nested, keys in SOURCE_NESTED_KEYS.items():
            nested_valid = exact_keys(source[nested], keys, f"{context}.{nested}", report) and nested_valid
        if not nested_valid:
            continue
        dates = source["dates"]
        for field_name in ("published", "updated"):
            report.check(dates.get(field_name) is None or valid_date(dates.get(field_name)), f"{context}.dates.{field_name} non valida")
        for field_name in ("first_accessed_at", "last_accessed_at"):
            report.check(valid_datetime(dates.get(field_name)), f"{context}.dates.{field_name} non valida")
        if valid_datetime(dates.get("first_accessed_at")) and valid_datetime(dates.get("last_accessed_at")):
            first = datetime.fromisoformat(dates["first_accessed_at"].replace("Z", "+00:00"))
            last = datetime.fromisoformat(dates["last_accessed_at"].replace("Z", "+00:00"))
            report.check(last >= first, f"{context}: ultimo accesso precedente al primo")
        applicability = source["applicability"]
        for field_name in ("valid_from", "valid_to", "as_of"):
            report.check(applicability.get(field_name) is None or valid_date(applicability.get(field_name)), f"{context}.applicability.{field_name} non valida")
        report.check(isinstance(applicability.get("provisional"), bool), f"{context}.applicability.provisional non booleano")
        for field_name in ("reference_period", "version"):
            report.check(applicability.get(field_name) is None or nonempty(applicability.get(field_name)), f"{context}.applicability.{field_name} non valido")
        locator = source["locator"]
        for field_name in SOURCE_NESTED_KEYS["locator"]:
            report.check(locator.get(field_name) is None or nonempty(locator.get(field_name)), f"{context}.locator.{field_name} non valido")
        verification = source["verification"]
        report.check(valid_datetime(verification.get("checked_at")), f"{context}.verification.checked_at non valida")
        report.check(verification.get("checked_by") in {"automation", "Stefano Liga"}, f"{context}.verification.checked_by non valido")
        report.check(isinstance(verification.get("full_content_accessed"), bool), f"{context}.verification.full_content_accessed non booleano")
        report.check(isinstance(verification.get("original_source_checked"), bool), f"{context}.verification.original_source_checked non booleano")
        usages = source["usages"]
        report.check(isinstance(usages, list), f"{context}.usages deve essere un array")
        usage_keys: list[tuple[str, str]] = []
        if isinstance(usages, list):
            for usage_index, usage in enumerate(usages):
                usage_context = f"{context}.usages[{usage_index}]"
                if not exact_keys(usage, USAGE_KEYS, usage_context, report):
                    continue
                report.check(usage["content_id"] in content_ids, f"{usage_context}.content_id non presente nel backlog")
                report.check(nonempty(usage["claim"]), f"{usage_context}.claim vuoto")
                report.check(usage["importance"] in {"central", "context"}, f"{usage_context}.importance non valida")
                report.check(valid_datetime(usage["verified_at"]), f"{usage_context}.verified_at non valida")
                usage_keys.append((usage["content_id"], usage["claim"].casefold().strip()))
            report.check(len(usage_keys) == len(set(usage_keys)), f"{context}.usages contiene duplicati")
        report.check(unique_nonempty_strings(source["limitations"]), f"{context}.limitations non valide")
        conflicts = source["conflicts"]
        report.check(isinstance(conflicts, list), f"{context}.conflicts deve essere un array")
        if isinstance(conflicts, list):
            for conflict_index, conflict in enumerate(conflicts):
                conflict_context = f"{context}.conflicts[{conflict_index}]"
                if exact_keys(conflict, CONFLICT_KEYS, conflict_context, report):
                    report.check(isinstance(conflict["source_id"], str) and SOURCE_ID.fullmatch(conflict["source_id"]) is not None, f"{conflict_context}.source_id non valido")
                    report.check(conflict["status"] in {"resolved", "unresolved"}, f"{conflict_context}.status non valido")
                    if conflict["status"] == "resolved":
                        report.check(nonempty(conflict["resolution"]), f"{conflict_context}: risoluzione mancante")
        local_copy = source["local_copy"]
        if local_copy is not None and exact_keys(local_copy, LOCAL_COPY_KEYS, f"{context}.local_copy", report):
            report.check(nonempty(local_copy["path"]), f"{context}.local_copy.path vuoto")
            report.check(isinstance(local_copy["sha256"], str) and re.fullmatch(r"[a-f0-9]{64}", local_copy["sha256"]) is not None, f"{context}.local_copy.sha256 non valido")
            report.check(nonempty(local_copy["license_note"]), f"{context}.local_copy.license_note vuota")
            if nonempty(local_copy["path"]):
                report.check((root / local_copy["path"]).is_file(), f"{context}.local_copy.path non esiste")
        report.check(source["notes"] is None or nonempty(source["notes"]), f"{context}.notes non valida")
        if source["status"] == "approved":
            report.check(source["level"] in {"A", "B", "C"}, f"{context}: fonte approvata di livello non ammesso")
            report.check(bool(usages), f"{context}: fonte approvata senza utilizzi")
            report.check(verification.get("full_content_accessed") is True, f"{context}: fonte approvata non consultata integralmente")
        if source["level"] == "C" and isinstance(usages, list):
            report.check(all(usage.get("importance") == "context" for usage in usages), f"{context}: fonte C usata per affermazioni centrali")
        if source["level"] == "D":
            report.check(source["status"] == "rejected" and not usages, f"{context}: fonte D deve essere rejected e senza utilizzi")

    report.check(len(identifiers) == len(set(identifiers)), "registro fonti: identificatori duplicati")
    known_sources = set(identifiers)
    for source in sources:
        if isinstance(source, dict):
            for conflict in source.get("conflicts", []):
                if isinstance(conflict, dict):
                    report.check(conflict.get("source_id") in known_sources, f"registro {source.get('id')}: conflitto con fonte inesistente")
                    report.check(conflict.get("source_id") != source.get("id"), f"registro {source.get('id')}: conflitto autoreferenziale")
    if isinstance(counters, dict):
        for year, next_value in counters.items():
            report.check(re.fullmatch(r"[0-9]{4}", year) is not None, f"registro fonti: anno contatore non valido {year}")
            report.check(isinstance(next_value, int) and 1 <= next_value <= 9999, f"registro fonti: contatore non valido per {year}")
            if isinstance(next_value, int):
                report.check(next_value == max_by_year.get(year, 0) + 1, f"registro fonti: prossimo ID incoerente per {year}")
        for year in max_by_year:
            report.check(year in counters, f"registro fonti: manca il contatore per {year}")
    return registry


def load_generator(root: Path):
    path = root / "scripts/editorial/generate_guide.py"
    spec = importlib.util.spec_from_file_location("editorial_guide_generator", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Impossibile caricare il generatore")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def validate_guide_sources(root: Path, mode: str, report: ValidationReport) -> dict[str, dict]:
    manifests: dict[str, dict] = {}
    generator = load_generator(root)
    content_root = root / "content/guides"
    for manifest_path in sorted(content_root.glob("GUIDE-[0-9][0-9][0-9][0-9].json")):
        try:
            manifest = generator.load_json(manifest_path)
            generator.validate_manifest(manifest)
            backlog = generator.load_json(root / "docs/editorial/backlog-editoriale.json")
            registry = generator.load_json(root / "docs/editorial/registro-fonti.json")
            generator.validate_backlog(manifest, backlog)
            sources = generator.validate_sources(manifest, registry)
            body_path = generator.infer_body_path(manifest_path)
            body = body_path.read_text(encoding="utf-8")
            sections = generator.validate_body(body, sources)
            generator.validate_internal_routes(manifest, root)
            output = root / "src/main/resources/static/guida" / f"{manifest['slug'].rsplit('/', 1)[-1]}.html"
            generator.validate_unique_metadata(manifest, root / "src/main/resources/static", output)
            template = (content_root / "guide-page.template.html").read_text(encoding="utf-8")
            expected = generator.render_page(manifest, body, sections, sources, template)
            manifests[manifest["content_id"]] = manifest
            if output.exists():
                report.check(output.read_text(encoding="utf-8") == expected, f"{manifest['content_id']}: pagina generata non allineata ai sorgenti")
            elif mode == "publication":
                report.errors.append(f"{manifest['content_id']}: pagina generata mancante in modalità publication")
        except (Exception,) as exc:
            report.errors.append(f"{manifest_path}: {exc}")
    output_root = root / "src/main/resources/static/guida"
    if output_root.exists():
        expected_slugs = {manifest["slug"].rsplit("/", 1)[-1] for manifest in manifests.values()}
        for output in output_root.glob("*.html"):
            if output.stem not in expected_slugs:
                report.errors.append(f"Pagina guida senza sorgente: {output.relative_to(root)}")
    return manifests


def canonical_route(path: Path, static_root: Path) -> str | None:
    relative = path.relative_to(static_root).as_posix()
    if relative == "index.html":
        return "/"
    if relative == "metodologia.html":
        return "/metodologia"
    if relative.startswith("guida/") and relative.endswith(".html"):
        return "/guida/" + path.stem
    if relative.startswith("autore/") and relative.endswith(".html"):
        return "/autore/" + path.stem
    return None


def local_target_exists(url: str, routes: set[str], static_root: Path) -> bool:
    parsed = urlparse(url)
    if parsed.scheme or parsed.netloc:
        return True
    path = parsed.path
    if path in routes:
        return True
    if not path.startswith("/"):
        return False
    asset = static_root / path.lstrip("/")
    return asset.is_file()


def validate_pages_and_sitemap(root: Path, manifests: dict[str, dict], mode: str, report: ValidationReport) -> None:
    static_root = root / "src/main/resources/static"
    pages: list[tuple[Path, str, PageParser]] = []
    for path in sorted(static_root.rglob("*.html")):
        route = canonical_route(path, static_root)
        if route is None:
            continue
        parser = PageParser()
        parser.feed(path.read_text(encoding="utf-8"))
        parser.close()
        pages.append((path, route, parser))
    routes = {route for _, route, _ in pages}
    titles: list[str] = []
    descriptions: list[str] = []
    canonicals: list[str] = []
    for path, route, page in pages:
        context = path.relative_to(root).as_posix()
        expected_canonical = f"{SITE_ORIGIN}{route}" if route != "/" else f"{SITE_ORIGIN}/"
        report.check(page.lang == "it", f"{context}: lang deve essere it")
        report.check(10 <= len(page.title) <= 65, f"{context}: title assente o fuori da 10-65 caratteri")
        descriptions_for_page = page.meta_names.get("description", [])
        report.check(len(descriptions_for_page) == 1, f"{context}: deve esistere una sola meta description")
        if descriptions_for_page:
            report.check(70 <= len(descriptions_for_page[0]) <= 160, f"{context}: meta description fuori da 70-160 caratteri")
            descriptions.append(descriptions_for_page[0])
        report.check(page.h1_count == 1, f"{context}: deve esistere un solo H1")
        report.check(len(page.canonicals) == 1 and page.canonicals[0] == expected_canonical, f"{context}: canonical errato")
        if page.canonicals:
            canonicals.extend(page.canonicals)
        report.check(page.meta_properties.get("og:url") == [expected_canonical], f"{context}: og:url errato o duplicato")
        robots = " ".join(page.meta_names.get("robots", [])).casefold()
        report.check("noindex" not in robots and "nofollow" not in robots, f"{context}: direttiva robots bloccante")
        report.check(not page.duplicate_ids, f"{context}: ID HTML duplicati: {', '.join(sorted(page.duplicate_ids))}")
        if route != "/":
            report.check(bool(page.json_ld), f"{context}: dati strutturati JSON-LD assenti")
        for index, block in enumerate(page.json_ld):
            try:
                json.loads(block)
            except json.JSONDecodeError as exc:
                report.errors.append(f"{context}: JSON-LD {index + 1} non valido: {exc}")
        for href in page.hrefs:
            if not href:
                report.errors.append(f"{context}: link privo di href")
                continue
            parsed = urlparse(href)
            if parsed.scheme in {"http", "https"}:
                report.check(parsed.scheme == "https", f"{context}: link esterno non HTTPS {href}")
                continue
            if href.startswith("#"):
                report.check(href[1:] in page.ids, f"{context}: ancora inesistente {href}")
            else:
                report.check(local_target_exists(href, routes, static_root), f"{context}: link interno non risolvibile {href}")
        for asset_url in page.asset_urls:
            if urlparse(asset_url).scheme:
                continue
            report.check(local_target_exists(asset_url, routes, static_root), f"{context}: asset non risolvibile {asset_url}")
        titles.append(page.title)
    report.check(len(titles) == len(set(titles)), "Pagine pubbliche: title duplicati")
    report.check(len(descriptions) == len(set(descriptions)), "Pagine pubbliche: meta description duplicate")
    report.check(len(canonicals) == len(set(canonicals)), "Pagine pubbliche: canonical duplicati")

    sitemap_path = static_root / "sitemap.xml"
    try:
        tree = ElementTree.parse(sitemap_path)
        namespace = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
        entries: list[tuple[str, str | None]] = []
        for item in tree.findall("sm:url", namespace):
            location = item.findtext("sm:loc", default="", namespaces=namespace)
            modified = item.findtext("sm:lastmod", default=None, namespaces=namespace)
            entries.append((location, modified))
        locations = [location for location, _ in entries]
        expected_locations = {f"{SITE_ORIGIN}{route}" if route != "/" else f"{SITE_ORIGIN}/" for route in routes}
        report.check(len(locations) == len(set(locations)), "sitemap: URL duplicati")
        report.check(set(locations) == expected_locations, "sitemap: URL diversi dalle pagine pubbliche")
        manifest_by_slug = {manifest["slug"]: manifest for manifest in manifests.values()}
        for location, modified in entries:
            parsed_path = urlparse(location).path
            if parsed_path in manifest_by_slug:
                report.check(modified == manifest_by_slug[parsed_path]["dates"]["updated"], f"sitemap: lastmod errata per {location}")
            elif modified is not None:
                report.check(valid_date(modified), f"sitemap: lastmod non valida per {location}")
    except (OSError, ElementTree.ParseError) as exc:
        report.errors.append(f"sitemap non valida: {exc}")

    robots = (static_root / "robots.txt").read_text(encoding="utf-8")
    report.check("User-agent: *" in robots and "Allow: /" in robots, "robots.txt: scansione generale non consentita")
    report.check(f"Sitemap: {SITE_ORIGIN}/sitemap.xml" in robots, "robots.txt: riferimento sitemap assente o errato")
    if mode == "publication" and manifests:
        report.check("/autore/stefano-liga" in routes, "publication: pagina autore richiesta dalle guide ma non pubblicata")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("development", "publication"), default="development")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2], help=argparse.SUPPRESS)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    report = ValidationReport()
    backlog = validate_backlog(root, report)
    validate_registry(root, backlog, report)
    manifests = validate_guide_sources(root, args.mode, report)
    validate_pages_and_sitemap(root, manifests, args.mode, report)
    for warning in report.warnings:
        print(f"AVVISO: {warning}")
    if report.errors:
        for error in report.errors:
            print(f"ERRORE: {error}", file=sys.stderr)
        print(f"VALIDAZIONE FALLITA: {len(report.errors)} errori, {report.checks} controlli", file=sys.stderr)
        return 1
    print(
        f"VALIDAZIONE OK: {report.checks} controlli, {len(backlog.get('items', []))} voci backlog, "
        f"{len(manifests)} guide sorgente, modalita {args.mode}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
