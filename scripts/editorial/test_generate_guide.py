import importlib.util
import json
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("generate_guide.py")
SPEC = importlib.util.spec_from_file_location("generate_guide", MODULE_PATH)
GENERATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = GENERATOR
SPEC.loader.exec_module(GENERATOR)


class GuideGeneratorTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "content/guides").mkdir(parents=True)
        (self.root / "docs/editorial").mkdir(parents=True)
        (self.root / "src/main/resources/static").mkdir(parents=True)
        template = MODULE_PATH.parents[2] / "content/guides/guide-page.template.html"
        (self.root / "content/guides/guide-page.template.html").write_text(
            template.read_text(encoding="utf-8"), encoding="utf-8"
        )
        (self.root / "src/main/resources/static/sitemap.xml").write_text(
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
            '    <url><loc>https://simulatorefire.com/</loc></url>\n'
            '</urlset>\n',
            encoding="utf-8",
        )
        today = date.today().isoformat()
        self.manifest = {
            "$schema": "./guide.schema.json",
            "schema_version": "1.0",
            "content_id": "GUIDE-0001",
            "slug": "/guida/prova-generatore",
            "seo": {
                "title": "Guida di prova al sistema FIRE | Calcolo FIRE Italia",
                "description": "Una descrizione completa usata per verificare la generazione controllata delle guide FIRE italiane.",
                "og_title": "Guida di prova al sistema FIRE",
                "og_description": "Una pagina di prova che verifica struttura, fonti e metadati delle guide.",
            },
            "page": {
                "kicker": "Guida FIRE",
                "h1": "Guida di prova al sistema FIRE",
                "lead": "Questa introduzione è sufficientemente completa per verificare il template editoriale senza pubblicare contenuti reali.",
            },
            "dates": {"published": today, "updated": today},
            "summary_points": [
                "Il manifesto separa i metadati dal contenuto editoriale.",
                "Le fonti devono essere approvate e collegate alla guida.",
                "La pagina finale riceve canonical e dati strutturati.",
            ],
            "source_ids": ["SRC-2026-0001"],
            "internal_links": [
                {"url": "/", "label": "Simulatore FIRE", "reason": "Permette di provare gli scenari descritti nella guida."},
                {"url": "/metodologia", "label": "Metodologia", "reason": "Spiega formule, ipotesi e limiti del simulatore."},
            ],
            "cta": {
                "title": "Prova il simulatore",
                "text": "Confronta le ipotesi della guida costruendo uno scenario personale.",
                "label": "Apri il simulatore",
                "url": "/",
            },
        }
        backlog = {
            "items": [{
                "id": "GUIDE-0001", "content_type": "guide", "slug": "/guida/prova-generatore",
                "status": "pilot", "execution_mode": "manual_pilot",
            }]
        }
        registry = {
            "sources": [{
                "id": "SRC-2026-0001", "status": "approved", "level": "A",
                "title": "Fonte di prova", "responsible_entity": "Ente di prova",
                "url": "https://example.com/fonte", "dates": {"updated": today, "published": None},
                "verification": {"full_content_accessed": True, "original_source_checked": True},
                "conflicts": [],
                "usages": [{"content_id": "GUIDE-0001", "importance": "central"}],
            }]
        }
        self.manifest_path = self.root / "content/guides/GUIDE-0001.json"
        self.body_path = self.root / "content/guides/GUIDE-0001.body.html"
        self.manifest_path.write_text(json.dumps(self.manifest), encoding="utf-8")
        (self.root / "docs/editorial/backlog-editoriale.json").write_text(json.dumps(backlog), encoding="utf-8")
        (self.root / "docs/editorial/registro-fonti.json").write_text(json.dumps(registry), encoding="utf-8")
        self.body = (
            '<section class="content-section" id="prima-sezione" aria-labelledby="prima-sezione-title">'
            '<h2 id="prima-sezione-title">Prima sezione</h2>'
            '<p>Affermazione verificata dalla <a href="https://example.com/fonte" '
            'data-source-id="SRC-2026-0001">fonte di prova</a>.</p></section>'
            '<section class="content-section" id="seconda-sezione" aria-labelledby="seconda-sezione-title">'
            '<h2 id="seconda-sezione-title">Seconda sezione</h2><p>Conclusione originale della prova.</p></section>'
        )
        self.body_path.write_text(self.body, encoding="utf-8")

    def tearDown(self):
        self.temporary.cleanup()

    def test_generates_complete_page_and_updates_sitemap(self):
        output = GENERATOR.generate(self.manifest_path, self.body_path, self.root, False)
        first_render = output.read_text(encoding="utf-8")
        GENERATOR.generate(self.manifest_path, self.body_path, self.root, False)
        page = output.read_text(encoding="utf-8")
        sitemap = (self.root / "src/main/resources/static/sitemap.xml").read_text(encoding="utf-8")
        self.assertEqual(first_render, page)
        self.assertIn("<h1>Guida di prova al sistema FIRE</h1>", page)
        self.assertIn('rel="canonical" href="https://simulatorefire.com/guida/prova-generatore"', page)
        self.assertIn('"@type": "Article"', page)
        self.assertIn("A cura di Stefano Liga", page)
        self.assertNotIn("/autore/stefano-liga", page)
        self.assertIn("processo editoriale automatizzato", page)
        self.assertIn("SRC-2026-0001", page)
        self.assertEqual(1, page.count("<h1>"))
        self.assertEqual(1, sitemap.count("https://simulatorefire.com/guida/prova-generatore"))

    def test_check_only_does_not_write_files(self):
        output = GENERATOR.generate(self.manifest_path, self.body_path, self.root, True)
        self.assertFalse(output.exists())
        sitemap = (self.root / "src/main/resources/static/sitemap.xml").read_text(encoding="utf-8")
        self.assertNotIn("prova-generatore", sitemap)

    def test_accepts_a_guide_marked_for_the_publication_commit(self):
        backlog_path = self.root / "docs/editorial/backlog-editoriale.json"
        backlog = json.loads(backlog_path.read_text(encoding="utf-8"))
        backlog["items"][0]["status"] = "pushed_to_main"
        backlog_path.write_text(json.dumps(backlog), encoding="utf-8")

        output = GENERATOR.generate(self.manifest_path, self.body_path, self.root, True)

        self.assertFalse(output.exists())

    def test_rejects_declared_source_without_inline_citation(self):
        self.body_path.write_text(self.body.replace(' data-source-id="SRC-2026-0001"', ""), encoding="utf-8")
        with self.assertRaises(GENERATOR.GuideGenerationError):
            GENERATOR.generate(self.manifest_path, self.body_path, self.root, True)

    def test_rejects_reserved_markup_in_body(self):
        self.body_path.write_text(self.body + "<script>alert('x')</script>", encoding="utf-8")
        with self.assertRaises(GENERATOR.GuideGenerationError):
            GENERATOR.generate(self.manifest_path, self.body_path, self.root, True)


if __name__ == "__main__":
    unittest.main()
