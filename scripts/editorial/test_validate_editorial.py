import importlib.util
import json
import shutil
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("validate_editorial.py")
REPO_ROOT = MODULE_PATH.parents[2]
SPEC = importlib.util.spec_from_file_location("validate_editorial", MODULE_PATH)
VALIDATOR = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = VALIDATOR
SPEC.loader.exec_module(VALIDATOR)


class EditorialValidatorTest(unittest.TestCase):
    def test_current_repository_passes_development_validation(self):
        report = VALIDATOR.ValidationReport()
        backlog = VALIDATOR.validate_backlog(REPO_ROOT, report)
        VALIDATOR.validate_registry(REPO_ROOT, backlog, report)
        manifests = VALIDATOR.validate_guide_sources(REPO_ROOT, "development", report)
        VALIDATOR.validate_pages_and_sitemap(REPO_ROOT, manifests, "development", report)
        self.assertEqual([], report.errors)
        self.assertGreater(report.checks, 1000)

    def test_detects_dependency_cycle(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "docs/editorial"
            target.mkdir(parents=True)
            backlog = json.loads((REPO_ROOT / "docs/editorial/backlog-editoriale.json").read_text(encoding="utf-8"))
            methodology = next(item for item in backlog["items"] if item["id"] == "METH-0001")
            methodology["dependencies"] = ["GUIDE-0001"]
            (target / "backlog-editoriale.json").write_text(json.dumps(backlog), encoding="utf-8")
            report = VALIDATOR.ValidationReport()
            VALIDATOR.validate_backlog(root, report)
            self.assertTrue(any("ciclo" in error for error in report.errors))

    def test_detects_invalid_source_counter(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "docs/editorial"
            target.mkdir(parents=True)
            shutil.copy2(REPO_ROOT / "docs/editorial/backlog-editoriale.json", target)
            registry = json.loads((REPO_ROOT / "docs/editorial/registro-fonti.json").read_text(encoding="utf-8"))
            registry["next_id_by_year"]["2026"] = 99
            (target / "registro-fonti.json").write_text(json.dumps(registry), encoding="utf-8")
            report = VALIDATOR.ValidationReport()
            backlog = VALIDATOR.validate_backlog(root, report)
            VALIDATOR.validate_registry(root, backlog, report)
            self.assertTrue(any("prossimo ID incoerente" in error for error in report.errors))

    def test_detects_sitemap_page_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            static_target = root / "src/main/resources/static"
            shutil.copytree(REPO_ROOT / "src/main/resources/static", static_target)
            sitemap = static_target / "sitemap.xml"
            sitemap.write_text(
                sitemap.read_text(encoding="utf-8").replace(
                    "    <url>\n        <loc>https://simulatorefire.com/metodologia</loc>\n"
                    "        <lastmod>2026-09-24</lastmod>\n    </url>\n",
                    "",
                ),
                encoding="utf-8",
            )
            report = VALIDATOR.ValidationReport()
            VALIDATOR.validate_pages_and_sitemap(root, {}, "development", report)
            self.assertTrue(any("URL diversi" in error for error in report.errors))


if __name__ == "__main__":
    unittest.main()
