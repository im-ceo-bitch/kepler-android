#!/usr/bin/env python3
"""Install the real MIT Flutter Cupertino icon asset for native Android Compose.

The Android project is *not Flutter*. This bootstrap vendors the font from the
actual Flutter asset package, reads the canonical Flutter SDK icon codepoint
registry, and builds an exhaustive Kotlin lookup table. It NEVER draws icons.

Network is required on the first build, thereafter all outputs are cached.
"""
import argparse
import io
import json
import pathlib
import re
import sys
import tarfile
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
VENDORED = ROOT / "third_party/cupertino_icons"
FONT = ROOT / "app/src/main/res/font/cupertino_icons.ttf"
LOOKUP = ROOT / "app/src/main/java/com/iumrah/beta/ui/cupertino/CupertinoGlyphCodepoints.kt"
AUDIT = VENDORED / "glyph-resolution.json"
MAPPING = VENDORED / "symbol-map.json"
LATO_FILES = {
    "lato_light.ttf": "Lato-Light.ttf",
    "lato_regular.ttf": "Lato-Regular.ttf",
    "lato_medium.ttf": "Lato-Medium.ttf",
    "lato_semibold.ttf": "Lato-SemiBold.ttf",
    "lato_bold.ttf": "Lato-Bold.ttf",
    "lato_heavy.ttf": "Lato-ExtraBold.ttf",
}
LATO_SOURCE = "https://raw.githubusercontent.com/google/fonts/main/ofl/lato/"
PACKAGE_VERSION = "1.0.9"
FLUTTER_VERSION = "3.35.0"  # stable, fixed release: deterministic icon codepoints
ARCHIVE = f"https://pub.dev/api/archives/cupertino_icons-{PACKAGE_VERSION}.tar.gz"
DART = f"https://raw.githubusercontent.com/flutter/flutter/{FLUTTER_VERSION}/packages/flutter/lib/src/cupertino/icons.dart"


def request(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "iumrah-android-cupertino-bootstrap/1.0"})
    with urllib.request.urlopen(req, timeout=45) as response:
        return response.read(2_000_000)


def parse_dart_symbols(contents: str) -> dict[str, int]:
    codepoints = {
        name: int(hex_code, 16)
        for name, hex_code in re.findall(
            r"static\s+const\s+IconData\s+(\w+)\s*=\s*IconData\s*\(\s*0x([0-9a-fA-F]+)",
            contents,
        )
    }
    # A handful of symbols are aliases rather than direct IconData declarations.
    aliases = dict(re.findall(r"static\s+const\s+IconData\s+(\w+)\s*=\s*(\w+)\s*;", contents))
    for _ in range(4):
        for name, target in aliases.items():
            if target in codepoints:
                codepoints[name] = codepoints[target]
    if len(codepoints) < 300:
        raise ValueError(f"Invalid Flutter icons.dart: found only {len(codepoints)} icon names")
    return codepoints


def parse_android_symbols() -> list[str]:
    kt = (LOOKUP.parent / "CupertinoIcons.kt").read_text(encoding="utf-8")
    enum = kt.split("enum class CupertinoSymbol {", 1)[1].split("}", 1)[0]
    return re.findall(r"\b[A-Z][A-Za-z0-9]+\b", enum)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--refresh", action="store_true", help="Re-fetch pinned icons/SDK registry")
    ap.add_argument("--verify", action="store_true", help="Verify existing locally installed assets")
    args = ap.parse_args()
    symbols = parse_android_symbols()
    desired = json.loads(MAPPING.read_text(encoding="utf-8"))
    missing = set(symbols) - set(desired)
    unused = set(desired) - set(symbols)
    if missing or unused:
        raise ValueError(f"Mapping mismatch. Missing: {sorted(missing)}, surplus: {sorted(unused)}")
    if args.verify:
        assert FONT.exists() and FONT.stat().st_size > 10_000, f"Font not installed: {FONT}"
        assert LOOKUP.exists() and AUDIT.exists(), "Glyph codepoint map not installed"
        assert all((FONT.parent / f).is_file() for f in LATO_FILES), "Lato typefaces not installed"
        info = json.loads(AUDIT.read_text(encoding="utf-8"))
        assert len(info["resolved"]) == len(symbols)
        assert len(set(info["resolved"])) == len(symbols)
        print(f"Verified {len(symbols)} Flutter Cupertino icons, package {PACKAGE_VERSION}.")
        return 0
    if not args.refresh and FONT.exists() and LOOKUP.exists() and AUDIT.exists() and all((FONT.parent / f).exists() for f in LATO_FILES):
        return main_verify(symbols)

    print(f"Fetching official cupertino_icons {PACKAGE_VERSION} + Flutter {FLUTTER_VERSION} icon registry")
    binary = request(ARCHIVE)
    with tarfile.open(fileobj=io.BytesIO(binary), mode="r:gz") as pkg:
        font_member = next((x for x in pkg.getmembers() if x.name.endswith("assets/CupertinoIcons.ttf")), None)
        license_member = next((x for x in pkg.getmembers() if x.name.endswith("LICENSE")), None)
        if font_member is None or license_member is None:
            raise ValueError("Invalid package: missing font or LICENSE")
        font_data = pkg.extractfile(font_member).read()
        license_data = pkg.extractfile(license_member).read()
    if len(font_data) < 10_000 or font_data[:4] not in (b"\x00\x01\x00\x00", b"OTTO", b"true"):
        raise ValueError("CupertinoIcons.ttf is invalid")
    codepoints = parse_dart_symbols(request(DART).decode("utf-8"))
    resolved = {}
    fallback_selections = {}
    unsupported = {}
    for name in symbols:
        candidates = desired[name]
        usable = [(i, candidate) for i, candidate in enumerate(candidates) if candidate in codepoints]
        if not usable:
            unsupported[name] = candidates
        else:
            index, choice = usable[0]
            resolved[name] = {"cupertino_name": choice, "codepoint": codepoints[choice]}
            if index > 0:
                fallback_selections[name] = {"requested": candidates[0], "used": choice}
    if unsupported:
        raise ValueError(f"No Cupertino font glyph for: {unsupported}.")
    kotlin = [
        "package com.iumrah.beta.ui.cupertino", "",
        "// AUTO-GENERATED by scripts/install-cupertino-assets.py — DO NOT HAND-EDIT.",
        f"// cupertino_icons {PACKAGE_VERSION}, Flutter SDK {FLUTTER_VERSION}, MIT font + BSD registry.",
        "internal object CupertinoGlyphCodepoints {",
        "    val bySymbol: Map<CupertinoSymbol, Int> = mapOf(",
    ]
    for name in symbols:
        kotlin.append(f"        CupertinoSymbol.{name} to 0x{resolved[name]['codepoint']:X}, // {resolved[name]['cupertino_name']}")
    kotlin.extend(["    )", "}", ""])
    # Ship font binaries only inside the compiled Android app. No binary font
    # files are included in the source delivery archive.
    lato_data = {}
    for local_name, upstream_name in LATO_FILES.items():
        data = request(LATO_SOURCE + upstream_name)
        if len(data) < 10_000 or data[:4] not in (b"\x00\x01\x00\x00", b"OTTO", b"true"):
            raise ValueError(f"Invalid Google Fonts Lato face: {upstream_name}")
        lato_data[local_name] = data
    FONT.parent.mkdir(parents=True, exist_ok=True)
    FONT.write_bytes(font_data)
    for name, data in lato_data.items():
        (FONT.parent / name).write_bytes(data)
    (VENDORED / "LICENSE").write_bytes(license_data)
    LOOKUP.write_text("\n".join(kotlin), encoding="utf-8")
    AUDIT.write_text(json.dumps({
        "cupertino_icons_version": PACKAGE_VERSION,
        "flutter_sdk_version": FLUTTER_VERSION,
        "resolved": resolved, "candidate_fallbacks": fallback_selections,
        "note": "Cupertino glyphs, not Apple SF Symbols. Fall-backs are not pixel-perfect."},
        indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Installed {len(symbols)} genuine Cupertino glyphs and {len(LATO_FILES)} official Lato faces. Alternate variants: {len(fallback_selections)}")
    for symbol, chosen in fallback_selections.items():
        print(f"  WARNING approximate: {symbol}: {chosen['requested']} -> {chosen['used']}")
    return 0


def main_verify(symbols: list[str]) -> int:
    info = json.loads(AUDIT.read_text(encoding="utf-8"))
    if len(info.get("resolved", {})) != len(symbols):
        raise ValueError("Cached Cupertino font map is incomplete. Retry --refresh")
    print(f"Cached Cupertino asset: {len(symbols)} symbols")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, urllib.error.URLError, ValueError, AssertionError) as exc:
        print(f"Cupertino icon bootstrap FAILED: {exc}", file=sys.stderr)
        print("Requires Internet on first build. This project deliberately does NOT fall back to handmade icons.", file=sys.stderr)
        raise SystemExit(1)
