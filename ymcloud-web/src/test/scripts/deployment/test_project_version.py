#!/usr/bin/env python3
"""验证发布工具只使用父 POM 的 revision，拒绝缺失或不可用的版本。"""

import importlib.util
import pathlib
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[5]
SPEC = importlib.util.spec_from_file_location("project_version", ROOT / "docker/project_version.py")
version = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(version)


class ProjectVersionTest(unittest.TestCase):
    def setUp(self):
        artifacts = ROOT / "target/phase3-verification"
        artifacts.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="version-test-", dir=artifacts)
        self.addCleanup(self.temporary.cleanup)
        self.directory = pathlib.Path(self.temporary.name)

    def write_pom(self, properties, project_version="${revision}"):
        (self.directory / "pom.xml").write_text(
            '<project xmlns="http://maven.apache.org/POM/4.0.0">'
            '<modelVersion>4.0.0</modelVersion><version>' + project_version + '</version>'
            '<properties>' + properties + '</properties></project>', encoding="utf-8",
        )

    def test_changing_revision_changes_the_release_version(self):
        for value in ("4.0.0", "4.1.0", "5.0.0-rc.1", "5.0.0-SNAPSHOT"):
            with self.subTest(version=value):
                self.write_pom("<revision>" + value + "</revision>")
                self.assertEqual(value, version.project_version(self.directory))

    def test_literal_project_version_cannot_bypass_revision(self):
        self.write_pom("<revision>4.0.0</revision>", "3.0.0")
        with self.assertRaises(ValueError):
            version.project_version(self.directory)

    def test_missing_duplicate_and_empty_revisions_are_rejected(self):
        for properties in ("", "<revision/>", "<revision>4.0.0</revision><revision>5.0.0</revision>"):
            with self.subTest(properties=properties):
                self.write_pom(properties)
                with self.assertRaises(ValueError):
                    version.project_version(self.directory)

    def test_invalid_versions_cannot_be_used_as_image_tags(self):
        for value in ("v4.0.0", "4.0", "04.0.0", "4.0.0+build", "4.0.0/other", "${unknown}"):
            with self.subTest(version=value):
                self.write_pom("<revision>" + value + "</revision>")
                with self.assertRaises(ValueError):
                    version.project_version(self.directory)


if __name__ == "__main__":
    unittest.main()
