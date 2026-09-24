#!/usr/bin/env python3
"""Valida le sorgenti editoriali e genera una pagina guida canonica."""

from __future__ import annotations

import argparse
import html
import json
import math
import re
import sys
import tempfile
from dataclasses import dataclass
from datetime import date
from html.parser import HTMLParser
from pathlib import Path
from string import Template
from urllib.parse import urlparse
from xml.etree import ElementTree


SITE_ORIGIN = "https://simulatorefire.com"
CONTENT_ID_PATTERN = re.compile(r"^GUIDE-[0-9]{4}$")
SLUG_PATTERN = re.compile(r"^/guida/[a-z0-9]+(?:-[a-z0-9]+)*$")
SOURCE_ID_PATTERN = re.compile(r"^SRC-[0-9]{4}-[0-9]{4}$")
DATE_PATTERN = re.compile(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
PLACEHOLDER_PATTERN = re.compile(r"\b(?:TODO|TBD|FIXME|LOREM IPSUM)\b|\[(?:INSERIRE|COMPLETARE)[^]]*]", re.IGNORECASE)

ROOT_KEYS = {
    "$schema", "schema_version", "content_id", "slug", "seo", "page", "dates",
    "summary_points", "source_ids", "internal_links", "cta",
}
NESTED_KEYS = {
    "seo": {"title", "description", "og_title", "og_description"},
    "page": {"kicker", "h1", "lead"},
    "dates": {"published", "updated"},
    "cta": {"title", "text", "label", "url"},
}
LINK_KEYS = {"url", "label", "reason"}

ALLOWED_TAGS = {
    "section", "p", "h2", "h3", "ul", "ol", "li", "strong", "em", "a", "code", "pre",
    "div", "table", "thead", "tbody", "tr", "th", "td", "dl", "dt", "dd", "aside", "small",
}
ALLOWED_ATTRIBUTES = {
    "section": {"id", "class", "aria-labelledby"},
    "h2": {"id"},
    "h3": {"id"},
    "a": {"href", "data-source-id"},
    "pre": {"class"},
    "div": {"class"},
    "aside": {"class", "aria-label", "aria-labelledby"},
    "th": {"scope"},
}
ALLOWED_CLASSES = {
    "content-section", "content-notice", "warning", "method-grid", "method-card", "table-wrap",
    "formula", "definition-grid",
}


class GuideGenerationError(Exception):
    pass


@dataclass(frozen=True)
class SectionHeading:
    section_id: str
    title: str


class BodyValidator(HTMLParser):
    def __init__(self, source_urls: dict[str, str]) -> None:
        super().__init__(convert_charrefs=True)
        self.source_urls = source_urls
        self.errors: list[str] = []
        self.stack: list[str] = []
        self.sections: list[SectionHeading] = []
        self.section_ids: set[str] = set()
        self.heading_ids: set[str] = set()
        self.cited_source_ids: set[str] = set()
        self._current_section: dict[str, str | None] | None = None
        self._current_h2_parts: list[str] | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        tag = tag.lower()
        attrs_dict = {name.lower(): (value or "") for name, value in attrs}
        if tag not in ALLOWED_TAGS:
            self.errors.append(f"Tag non consentito nel corpo: <{tag}>")
            self.stack.append(tag)
            return

        if tag == "section" and self.stack:
            self.errors.append("Ogni section deve essere un elemento principale del frammento")
        elif tag != "section" and not any(open_tag == "section" for open_tag in self.stack):
            self.errors.append(f"L'elemento <{tag}> deve essere contenuto in una section")

        allowed = ALLOWED_ATTRIBUTES.get(tag, set())
        for name in attrs_dict:
            if name not in allowed:
                self.errors.append(f"Attributo non consentito su <{tag}>: {name}")

        for class_name in attrs_dict.get("class", "").split():
            if class_name not in ALLOWED_CLASSES:
                self.errors.append(f"Classe CSS non consentita: {class_name}")

        if tag == "section":
            if any(open_tag == "section" for open_tag in self.stack):
                self.errors.append("Le section principali non possono essere annidate")
            section_id = attrs_dict.get("id", "")
            labelled_by = attrs_dict.get("aria-labelledby", "")
            classes = set(attrs_dict.get("class", "").split())
            if "content-section" not in classes:
                self.errors.append("Ogni section deve avere la classe content-section")
            if not re.fullmatch(r"[a-z0-9]+(?:-[a-z0-9]+)*", section_id):
                self.errors.append(f"ID di section non valido: {section_id or '(mancante)'}")
            elif section_id in self.section_ids:
                self.errors.append(f"ID di section duplicato: {section_id}")
            else:
                self.section_ids.add(section_id)
            self._current_section = {"id": section_id, "labelled_by": labelled_by, "h2_id": None, "title": None}

        if tag == "h2":
            if self._current_section is None:
                self.errors.append("Ogni h2 del corpo deve appartenere a una section")
            heading_id = attrs_dict.get("id", "")
            if not heading_id:
                self.errors.append("Ogni h2 deve avere un id")
            elif heading_id in self.heading_ids:
                self.errors.append(f"ID di titolo duplicato: {heading_id}")
            else:
                self.heading_ids.add(heading_id)
            if self._current_section is not None:
                if self._current_section["h2_id"] is not None:
                    self.errors.append(f"La section {self._current_section['id']} contiene più di un h2")
                self._current_section["h2_id"] = heading_id
            self._current_h2_parts = []

        if tag == "a":
            href = attrs_dict.get("href", "")
            source_id = attrs_dict.get("data-source-id", "")
            if href.startswith("http://"):
                self.errors.append(f"Link esterno non HTTPS: {href}")
            if href.startswith("https://"):
                if not source_id:
                    self.errors.append(f"Link esterno privo di data-source-id: {href}")
                elif source_id not in self.source_urls:
                    self.errors.append(f"Fonte non dichiarata o non approvata: {source_id}")
                elif href != self.source_urls[source_id]:
                    self.errors.append(f"URL della citazione {source_id} diverso dal registro")
                else:
                    self.cited_source_ids.add(source_id)
            elif href and not href.startswith(("/", "#")):
                self.errors.append(f"Link non ammesso: {href}")
            if source_id and not href.startswith("https://"):
                self.errors.append(f"data-source-id usato su un link non HTTPS: {source_id}")

        self.stack.append(tag)

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        if not self.stack or self.stack[-1] != tag:
            expected = self.stack[-1] if self.stack else "nessuno"
            self.errors.append(f"Chiusura </{tag}> non valida; elemento atteso: {expected}")
            return
        if tag == "h2" and self._current_h2_parts is not None:
            title = " ".join("".join(self._current_h2_parts).split())
            if not title:
                self.errors.append("Titolo h2 vuoto")
            if self._current_section is not None:
                self._current_section["title"] = title
            self._current_h2_parts = None
        if tag == "section" and self._current_section is not None:
            section = self._current_section
            if section["h2_id"] != section["labelled_by"]:
                self.errors.append(
                    f"aria-labelledby della section {section['id']} non coincide con l'id del suo h2"
                )
            if section["id"] and section["title"]:
                self.sections.append(SectionHeading(str(section["id"]), str(section["title"])))
            self._current_section = None
        if self.stack:
            self.stack.pop()

    def handle_data(self, data: str) -> None:
        if self._current_h2_parts is not None:
            self._current_h2_parts.append(data)


def load_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise GuideGenerationError(f"File non trovato: {path}") from exc
    except json.JSONDecodeError as exc:
        raise GuideGenerationError(f"JSON non valido in {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise GuideGenerationError(f"La radice di {path} deve essere un oggetto JSON")
    return value


def require_exact_keys(value: dict, expected: set[str], context: str) -> None:
    missing = expected - set(value)
    extra = set(value) - expected
    if missing:
        raise GuideGenerationError(f"{context}: campi mancanti: {', '.join(sorted(missing))}")
    if extra:
        raise GuideGenerationError(f"{context}: campi non previsti: {', '.join(sorted(extra))}")


def require_text(value: object, context: str, minimum: int, maximum: int) -> str:
    if not isinstance(value, str):
        raise GuideGenerationError(f"{context} deve essere una stringa")
    normalized = " ".join(value.split())
    if not minimum <= len(normalized) <= maximum:
        raise GuideGenerationError(f"{context} deve contenere tra {minimum} e {maximum} caratteri")
    if PLACEHOLDER_PATTERN.search(normalized):
        raise GuideGenerationError(f"{context} contiene un segnaposto non risolto")
    return normalized


def parse_iso_date(value: object, context: str) -> date:
    if not isinstance(value, str) or not DATE_PATTERN.fullmatch(value):
        raise GuideGenerationError(f"{context} deve usare il formato YYYY-MM-DD")
    try:
        return date.fromisoformat(value)
    except ValueError as exc:
        raise GuideGenerationError(f"{context} non è una data valida") from exc


def validate_manifest(manifest: dict) -> None:
    require_exact_keys(manifest, ROOT_KEYS, "manifesto")
    if manifest["$schema"] != "./guide.schema.json" or manifest["schema_version"] != "1.0":
        raise GuideGenerationError("Schema o versione del manifesto non supportati")
    if not isinstance(manifest["content_id"], str) or not CONTENT_ID_PATTERN.fullmatch(manifest["content_id"]):
        raise GuideGenerationError("content_id non valido")
    if not isinstance(manifest["slug"], str) or not SLUG_PATTERN.fullmatch(manifest["slug"]):
        raise GuideGenerationError("slug non valido")

    for key, expected in NESTED_KEYS.items():
        if not isinstance(manifest[key], dict):
            raise GuideGenerationError(f"{key} deve essere un oggetto")
        require_exact_keys(manifest[key], expected, key)

    seo = manifest["seo"]
    require_text(seo["title"], "seo.title", 30, 65)
    require_text(seo["description"], "seo.description", 70, 160)
    require_text(seo["og_title"], "seo.og_title", 10, 95)
    require_text(seo["og_description"], "seo.og_description", 40, 200)
    page = manifest["page"]
    require_text(page["kicker"], "page.kicker", 3, 80)
    require_text(page["h1"], "page.h1", 10, 110)
    require_text(page["lead"], "page.lead", 50, 500)
    cta = manifest["cta"]
    require_text(cta["title"], "cta.title", 5, 100)
    require_text(cta["text"], "cta.text", 20, 300)
    require_text(cta["label"], "cta.label", 3, 80)
    validate_internal_url(cta["url"], "cta.url")

    published = parse_iso_date(manifest["dates"]["published"], "dates.published")
    updated = parse_iso_date(manifest["dates"]["updated"], "dates.updated")
    if updated < published:
        raise GuideGenerationError("La data di aggiornamento precede la pubblicazione")
    if published > date.today() or updated > date.today():
        raise GuideGenerationError("Le date della guida non possono essere future")

    points = manifest["summary_points"]
    if not isinstance(points, list) or not 3 <= len(points) <= 8:
        raise GuideGenerationError("summary_points deve contenere da 3 a 8 elementi")
    normalized_points = [require_text(point, "summary_points[]", 10, 300) for point in points]
    if len(set(normalized_points)) != len(normalized_points):
        raise GuideGenerationError("summary_points contiene duplicati")

    source_ids = manifest["source_ids"]
    if not isinstance(source_ids, list) or not source_ids:
        raise GuideGenerationError("source_ids deve contenere almeno una fonte")
    if any(not isinstance(source_id, str) or not SOURCE_ID_PATTERN.fullmatch(source_id) for source_id in source_ids):
        raise GuideGenerationError("source_ids contiene un identificatore non valido")
    if len(set(source_ids)) != len(source_ids):
        raise GuideGenerationError("source_ids contiene duplicati")

    links = manifest["internal_links"]
    if not isinstance(links, list) or len(links) < 2:
        raise GuideGenerationError("internal_links deve contenere almeno due collegamenti")
    urls: list[str] = []
    for index, link in enumerate(links):
        if not isinstance(link, dict):
            raise GuideGenerationError(f"internal_links[{index}] deve essere un oggetto")
        require_exact_keys(link, LINK_KEYS, f"internal_links[{index}]")
        validate_internal_url(link["url"], f"internal_links[{index}].url")
        require_text(link["label"], f"internal_links[{index}].label", 3, 100)
        require_text(link["reason"], f"internal_links[{index}].reason", 10, 240)
        urls.append(link["url"])
    if len(set(urls)) != len(urls):
        raise GuideGenerationError("internal_links contiene URL duplicati")
    if "/" not in urls or "/metodologia" not in urls:
        raise GuideGenerationError("internal_links deve includere il simulatore (/) e la metodologia")


def validate_internal_url(value: object, context: str) -> None:
    if not isinstance(value, str) or not re.fullmatch(r"/(?:|[a-z0-9]+(?:[-/][a-z0-9]+)*)", value):
        raise GuideGenerationError(f"{context} non è un URL interno canonico valido")


def validate_backlog(manifest: dict, backlog: dict) -> None:
    items = [item for item in backlog.get("items", []) if item.get("id") == manifest["content_id"]]
    if len(items) != 1:
        raise GuideGenerationError(f"{manifest['content_id']} deve comparire una sola volta nel backlog")
    item = items[0]
    if item.get("content_type") != "guide" or item.get("slug") != manifest["slug"]:
        raise GuideGenerationError("content_id e slug non coincidono con il backlog")
    if item.get("status") not in {"pilot", "in_progress"}:
        raise GuideGenerationError("La guida deve essere in stato pilot o in_progress prima della generazione")
    if item.get("status") == "in_progress" and item.get("execution_mode") != "automatic":
        raise GuideGenerationError("Una guida in_progress deve essere configurata per l'esecuzione automatica")


def validate_sources(manifest: dict, registry: dict) -> dict[str, dict]:
    records = {source.get("id"): source for source in registry.get("sources", [])}
    selected: dict[str, dict] = {}
    has_central_source = False
    for source_id in manifest["source_ids"]:
        source = records.get(source_id)
        if source is None:
            raise GuideGenerationError(f"Fonte assente dal registro: {source_id}")
        if source.get("status") != "approved" or source.get("level") not in {"A", "B", "C"}:
            raise GuideGenerationError(f"Fonte non approvata: {source_id}")
        verification = source.get("verification", {})
        if not verification.get("full_content_accessed") or not verification.get("original_source_checked"):
            raise GuideGenerationError(f"Fonte non verificata integralmente: {source_id}")
        if any(conflict.get("status") == "unresolved" for conflict in source.get("conflicts", [])):
            raise GuideGenerationError(f"Fonte con conflitto irrisolto: {source_id}")
        usages = [usage for usage in source.get("usages", []) if usage.get("content_id") == manifest["content_id"]]
        if not usages:
            raise GuideGenerationError(f"La fonte {source_id} non registra l'uso per {manifest['content_id']}")
        if any(usage.get("importance") == "central" for usage in usages):
            has_central_source = True
        selected[source_id] = source
    if not has_central_source:
        raise GuideGenerationError("La guida deve avere almeno una fonte registrata come central")
    return selected


def validate_body(body: str, sources: dict[str, dict]) -> list[SectionHeading]:
    if PLACEHOLDER_PATTERN.search(body):
        raise GuideGenerationError("Il corpo contiene un segnaposto non risolto")
    if re.search(r"<(?:html|head|body|h1|script|style|meta|link)\b", body, re.IGNORECASE):
        raise GuideGenerationError("Il corpo contiene elementi riservati al template")
    source_urls = {source_id: source["url"] for source_id, source in sources.items()}
    validator = BodyValidator(source_urls)
    validator.feed(body)
    validator.close()
    if validator.stack:
        validator.errors.append("Elementi HTML non chiusi: " + ", ".join(validator.stack))
    if validator.errors:
        raise GuideGenerationError("Corpo HTML non valido:\n- " + "\n- ".join(validator.errors))
    if len(validator.sections) < 2:
        raise GuideGenerationError("Il corpo deve contenere almeno due section con h2")
    uncited = set(sources) - validator.cited_source_ids
    if uncited:
        raise GuideGenerationError("Fonti dichiarate ma mai citate nel corpo: " + ", ".join(sorted(uncited)))
    return validator.sections


def validate_unique_metadata(manifest: dict, static_root: Path, output_path: Path) -> None:
    title_token = f"<title>{manifest['seo']['title']}</title>"
    description_token = f"name=\"description\" content=\"{manifest['seo']['description']}\""
    canonical_token = f"rel=\"canonical\" href=\"{SITE_ORIGIN}{manifest['slug']}\""
    for page_path in static_root.rglob("*.html"):
        if page_path.resolve() == output_path.resolve():
            continue
        page = page_path.read_text(encoding="utf-8")
        if title_token in page:
            raise GuideGenerationError(f"Title SEO già usato in {page_path}")
        if description_token in page:
            raise GuideGenerationError(f"Meta description già usata in {page_path}")
        if canonical_token in page:
            raise GuideGenerationError(f"Canonical già usato in {page_path}")


def validate_internal_routes(manifest: dict, root: Path) -> None:
    for link in [*manifest["internal_links"], {"url": manifest["cta"]["url"]}]:
        url = link["url"]
        if url in {"/", "/metodologia"}:
            continue
        if url.startswith("/guida/"):
            target = root / "src/main/resources/static/guida" / f"{url.rsplit('/', 1)[-1]}.html"
            if target.exists():
                continue
        raise GuideGenerationError(f"Collegamento interno senza destinazione pubblicabile: {url}")


def italian_date(value: str) -> str:
    months = (
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )
    parsed = date.fromisoformat(value)
    return f"{parsed.day} {months[parsed.month - 1]} {parsed.year}"


def escape(value: object) -> str:
    return html.escape(str(value), quote=True)


def source_reference(source: dict) -> str:
    relevant_date = source.get("dates", {}).get("updated") or source.get("dates", {}).get("published")
    date_note = f" · {italian_date(relevant_date)}" if relevant_date else ""
    return (
        f'<li><a href="{escape(source["url"])}">{escape(source["title"])}</a>'
        f'<small>{escape(source["responsible_entity"])}{date_note} · {escape(source["id"])}</small></li>'
    )


def render_page(manifest: dict, body: str, sections: list[SectionHeading], sources: dict[str, dict], template: str) -> str:
    canonical_url = f"{SITE_ORIGIN}{manifest['slug']}"
    article = {
        "@context": "https://schema.org",
        "@type": "Article",
        "headline": manifest["page"]["h1"],
        "description": manifest["seo"]["description"],
        "url": canonical_url,
        "mainEntityOfPage": canonical_url,
        "datePublished": manifest["dates"]["published"],
        "dateModified": manifest["dates"]["updated"],
        "inLanguage": "it-IT",
        "author": {
            "@type": "Person",
            "name": "Stefano Liga",
            "jobTitle": "Ideatore e sviluppatore di Calcolo FIRE Italia",
        },
        "publisher": {
            "@type": "Organization",
            "name": "Calcolo FIRE Italia",
            "url": f"{SITE_ORIGIN}/",
            "logo": {"@type": "ImageObject", "url": f"{SITE_ORIGIN}/images/logo-percorso-indipendenza.png?v=2"},
        },
        "image": f"{SITE_ORIGIN}/images/og-simulatore-fire.jpg?v=3",
    }
    breadcrumb = {
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        "itemListElement": [
            {"@type": "ListItem", "position": 1, "name": "Simulatore FIRE", "item": f"{SITE_ORIGIN}/"},
            {"@type": "ListItem", "position": 2, "name": manifest["page"]["h1"], "item": canonical_url},
        ],
    }
    plain_body = re.sub(r"<[^>]+>", " ", body)
    word_count = len(re.findall(r"\b[\wÀ-ÿ]+\b", html.unescape(plain_body)))
    reading_time = max(1, math.ceil(word_count / 200))
    values = {
        "meta_description": escape(manifest["seo"]["description"]),
        "og_title": escape(manifest["seo"]["og_title"]),
        "og_description": escape(manifest["seo"]["og_description"]),
        "canonical_url": escape(canonical_url),
        "og_image_alt": escape(manifest["page"]["h1"]),
        "published_iso": escape(manifest["dates"]["published"]),
        "updated_iso": escape(manifest["dates"]["updated"]),
        "seo_title": escape(manifest["seo"]["title"]),
        "article_json_ld": json.dumps(article, ensure_ascii=False, indent=2).replace("</", "<\\/"),
        "breadcrumb_json_ld": json.dumps(breadcrumb, ensure_ascii=False, indent=2).replace("</", "<\\/"),
        "breadcrumb_label": escape(manifest["page"]["h1"]),
        "kicker": escape(manifest["page"]["kicker"]),
        "h1": escape(manifest["page"]["h1"]),
        "lead": escape(manifest["page"]["lead"]),
        "published_human": italian_date(manifest["dates"]["published"]),
        "updated_human": italian_date(manifest["dates"]["updated"]),
        "reading_time": str(reading_time),
        "toc_items": "".join(f'<li><a href="#{escape(item.section_id)}">{escape(item.title)}</a></li>' for item in sections),
        "summary_items": "".join(f"<li>{escape(point)}</li>" for point in manifest["summary_points"]),
        "body_html": body.strip(),
        "internal_link_items": "".join(
            f'<li><a href="{escape(link["url"])}">{escape(link["label"])}</a>'
            f'<small>{escape(link["reason"])}</small></li>' for link in manifest["internal_links"]
        ),
        "source_items": "".join(source_reference(sources[source_id]) for source_id in manifest["source_ids"]),
        "cta_title": escape(manifest["cta"]["title"]),
        "cta_text": escape(manifest["cta"]["text"]),
        "cta_label": escape(manifest["cta"]["label"]),
        "cta_url": escape(manifest["cta"]["url"]),
        "content_id": escape(manifest["content_id"]),
    }
    rendered = Template(template).substitute(values)
    if rendered.count("<h1>") != 1 or rendered.count('rel="canonical"') != 1:
        raise GuideGenerationError("Il template deve generare un solo H1 e un solo canonical")
    for block in re.findall(r'<script type="application/ld\+json">([\s\S]*?)</script>', rendered):
        json.loads(block)
    return rendered.rstrip() + "\n"


def update_sitemap(sitemap_path: Path, canonical_url: str, last_modified: str, check_only: bool) -> bool:
    xml = sitemap_path.read_text(encoding="utf-8")
    ElementTree.fromstring(xml)
    if f"<loc>{canonical_url}</loc>" in xml:
        return False
    block = (
        "    <url>\n"
        f"        <loc>{canonical_url}</loc>\n"
        f"        <lastmod>{last_modified}</lastmod>\n"
        "    </url>\n"
    )
    if "</urlset>" not in xml:
        raise GuideGenerationError("sitemap.xml non contiene la chiusura urlset")
    updated = xml.replace("</urlset>", block + "</urlset>")
    ElementTree.fromstring(updated)
    if not check_only:
        atomic_write(sitemap_path, updated)
    return True


def atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", newline="\n", delete=False, dir=path.parent) as handle:
        handle.write(content)
        temporary = Path(handle.name)
    temporary.replace(path)


def generate(manifest_path: Path, body_path: Path, root: Path, check_only: bool) -> Path:
    manifest = load_json(manifest_path)
    validate_manifest(manifest)
    backlog = load_json(root / "docs/editorial/backlog-editoriale.json")
    registry = load_json(root / "docs/editorial/registro-fonti.json")
    validate_backlog(manifest, backlog)
    sources = validate_sources(manifest, registry)
    try:
        body = body_path.read_text(encoding="utf-8")
    except FileNotFoundError as exc:
        raise GuideGenerationError(f"Corpo della guida non trovato: {body_path}") from exc
    sections = validate_body(body, sources)
    validate_internal_routes(manifest, root)

    output = root / "src/main/resources/static/guida" / f"{manifest['slug'].rsplit('/', 1)[-1]}.html"
    validate_unique_metadata(manifest, root / "src/main/resources/static", output)
    template_path = root / "content/guides/guide-page.template.html"
    template = template_path.read_text(encoding="utf-8")
    rendered = render_page(manifest, body, sections, sources, template)
    sitemap = root / "src/main/resources/static/sitemap.xml"
    sitemap_changed = update_sitemap(
        sitemap, f"{SITE_ORIGIN}{manifest['slug']}", manifest["dates"]["updated"], check_only
    )
    if not check_only:
        atomic_write(output, rendered)
    action = "VALIDA" if check_only else "GENERA"
    sitemap_note = "da aggiornare" if sitemap_changed else "gia presente"
    print(f"{action} {manifest['content_id']} -> {output.relative_to(root)}; sitemap {sitemap_note}")
    return output


def infer_body_path(manifest_path: Path) -> Path:
    return manifest_path.with_name(manifest_path.stem + ".body.html")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    selection = parser.add_mutually_exclusive_group(required=True)
    selection.add_argument("--manifest", type=Path, help="Manifesto JSON della guida")
    selection.add_argument("--all", action="store_true", help="Valida o rigenera tutte le guide sorgente")
    parser.add_argument("--body", type=Path, help="Corpo HTML; se omesso viene dedotto dal manifesto")
    parser.add_argument("--check-only", action="store_true", help="Esegue i controlli senza scrivere output o sitemap")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2], help=argparse.SUPPRESS)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    try:
        if args.all:
            manifests = sorted((root / "content/guides").glob("GUIDE-[0-9][0-9][0-9][0-9].json"))
            if not manifests:
                print("Nessuna guida sorgente presente: controllo completato senza modifiche")
                return 0
            for manifest_path in manifests:
                generate(manifest_path, infer_body_path(manifest_path), root, args.check_only)
        else:
            manifest_path = args.manifest.resolve()
            body_path = args.body.resolve() if args.body else infer_body_path(manifest_path)
            generate(manifest_path, body_path, root, args.check_only)
    except (GuideGenerationError, OSError, KeyError, TypeError, json.JSONDecodeError) as exc:
        print(f"ERRORE: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
