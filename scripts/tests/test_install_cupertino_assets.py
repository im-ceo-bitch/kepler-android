#!/usr/bin/env python3
"""Offline regression tests for the Cupertino first-build download failure.

Run: python3 -m unittest discover -s scripts/tests -p 'test_install_cupertino_assets.py' -v
No network, Android SDK, icon font files, or credentials required.
"""
import importlib.util
import io
import pathlib
import tarfile
import unittest
from unittest.mock import patch

SCRIPT = pathlib.Path(__file__).resolve().parents[1] / "install-cupertino-assets.py"
spec = importlib.util.spec_from_file_location("iumrah_cupertino_bootstrap", SCRIPT)
bootstrap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bootstrap)


class FakeResponse:
    def __init__(self, data: bytes, length=None):
        self.data = io.BytesIO(data)
        self.headers = {"Content-Length": str(length if length is not None else len(data))}
    def read(self, count=-1):
        return self.data.read(count)
    def __enter__(self):
        return self
    def __exit__(self, *args):
        self.data.close()


class TestCupertinoDownloader(unittest.TestCase):
    def test_large_archive_response_is_not_truncated_at_2mb(self):
        data = b"A" * (2_000_000 + 25_000)
        with patch.object(bootstrap.urllib.request, "urlopen", return_value=FakeResponse(data)):
            actual = bootstrap.request("https://example.invalid/icons.tar.gz")
        self.assertEqual(len(actual), len(data))
        self.assertEqual(actual, data)

    def test_download_retries_when_declared_length_does_not_match(self):
        full = b"hello" * 3000
        short = full[:2000]
        responses = [FakeResponse(short, len(full)), FakeResponse(full)]
        with patch.object(bootstrap.urllib.request, "urlopen", side_effect=responses) as opener, \
                patch.object(bootstrap.time, "sleep") as sleep:
            self.assertEqual(bootstrap.request("https://example.invalid/icons.tar.gz"), full)
        self.assertEqual(opener.call_count, 2)
        sleep.assert_called_once()

    def test_network_error_retries(self):
        with patch.object(bootstrap.urllib.request, "urlopen", side_effect=[OSError("temporary"), FakeResponse(b"good")]) as opener, \
                patch.object(bootstrap.time, "sleep"):
            self.assertEqual(bootstrap.request("https://example.invalid"), b"good")
        self.assertEqual(opener.call_count, 2)

    def test_download_safety_limit(self):
        data = b"x" * 21
        with patch.object(bootstrap.urllib.request, "urlopen", return_value=FakeResponse(data)):
            with self.assertRaisesRegex(ValueError, "too large"):
                bootstrap.request("https://example.invalid/huge", max_bytes=20)

    def test_retry_when_gzip_is_truncated_without_http_length_hint(self):
        good_font = b"\x00\x01\x00\x00" + b"x" * 15_000
        archive = io.BytesIO()
        with tarfile.open(fileobj=archive, mode="w:gz") as pkg:
            for name, contents in [
                ("cupertino_icons/assets/CupertinoIcons.ttf", good_font),
                ("cupertino_icons/LICENSE", b"MIT License"),
            ]:
                info = tarfile.TarInfo(name)
                info.size = len(contents)
                pkg.addfile(info, io.BytesIO(contents))
        valid = archive.getvalue()
        # Damaged gzip in first attempt, valid in second. Validate parse retries.
        with patch.object(bootstrap, "request", side_effect=[valid[:20], valid]) as loader, \
                patch.object(bootstrap.time, "sleep"):
            font_data, license_data = bootstrap.fetch_cupertino_package()
        self.assertEqual(loader.call_count, 2)
        self.assertEqual(font_data, good_font)
        self.assertEqual(license_data, b"MIT License")

    def test_invalid_archive_rejected_after_bounded_retries(self):
        with patch.object(bootstrap, "request", return_value=b"not a gzip") as loader, \
                patch.object(bootstrap.time, "sleep"):
            with self.assertRaisesRegex(ValueError, "after 3 attempts"):
                bootstrap.fetch_cupertino_package()
        self.assertEqual(loader.call_count, 3)


if __name__ == "__main__":
    unittest.main()
