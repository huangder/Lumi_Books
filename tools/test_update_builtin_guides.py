"""Verify guide updates keep lessons reachable and repeatable without external covers."""

import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from unittest.mock import patch

import update_builtin_guides as guides


class UpdateBuiltinGuidesTest(unittest.TestCase):
    def test_repairs_misplaced_lesson_and_is_idempotent(self):
        with zipfile.ZipFile(guides.GUIDE_DIR / "guide_zh-CN.epub") as source:
            entries = {name: source.read(name) for name in source.namelist()}
        advanced = guides.CONTENT["zh-CN"]["toc"].strip()
        content = entries["OEBPS/content.xhtml"].decode("utf-8").replace(advanced, "")
        # Recreate the old updater's output: the lesson belongs to the release notes.
        content = content.replace('</section>\n<section id="s3">', advanced + '</section>\n<section id="s3">')
        entries["OEBPS/content.xhtml"] = content.encode("utf-8")
        with tempfile.TemporaryDirectory() as directory:
            guide = Path(directory) / "guide.epub"
            with zipfile.ZipFile(guide, "w") as target:
                for name, data in entries.items():
                    target.writestr(name, data)
            with patch.object(guides, "SOURCE_COVER_DIR", Path(directory) / "missing"):
                guides.update_guide(guide, "zh-CN")
                first = guide.read_bytes()
                guides.update_guide(guide, "zh-CN")
                self.assertEqual(first, guide.read_bytes())
            with zipfile.ZipFile(guide) as updated:
                self.assertEqual("mimetype", updated.namelist()[0])
                self.assertEqual(zipfile.ZIP_STORED, updated.getinfo("mimetype").compress_type)
                root = ET.fromstring(updated.read("OEBPS/content.xhtml"))
                ns = {"h": "http://www.w3.org/1999/xhtml"}
                lesson = root.find('.//h:section[@id="txt-toc-rules"]', ns)
                self.assertIsNotNone(lesson)
                self.assertIn("符号、卷章层级和兼容规则", "".join(lesson.itertext()))
                updates = root.find('.//h:section[@id="updates-2-1-9"]', ns)
                self.assertNotIn("符号、卷章层级和兼容规则", "".join(updates.itertext()))
                nav = ET.fromstring(updated.read("OEBPS/nav.xhtml"))
                self.assertEqual(1, len(nav.findall('.//h:a[@href="content.xhtml#txt-toc-rules"]', ns)))


if __name__ == "__main__":
    unittest.main()
