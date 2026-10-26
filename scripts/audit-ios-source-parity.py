#!/usr/bin/env python3
"""Audit central visual tokens and SF name coverage against an actual iOS checkout.

A source-level audit, NOT a claim of screenshot/pixel parity or native compilation.
"""
import argparse
import collections
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent


def ios_names(src: Path):
    counts = collections.Counter()
    files = list((src / 'Sources').rglob('*.swift'))
    for path in files:
        text = path.read_text(encoding='utf-8')
        # Both direct SF Image and reusable systemName: "..." parameters.
        for name in re.findall(r'\b(?:Image\(systemName:|systemName:)\s*"([^"]+)"', text):
            counts[name] += 1
    return counts, files


def geometry(swift: str, kt: str):
    names = {'pagePadding':'PagePadding','cardRadius':'CardRadius',
             'heroRadius':'HeroRadius','compactRadius':'CompactRadius',
             'controlHeight':'ControlHeight','glassIconSize':'GlassIconSize'}
    result = {}
    for ios_name, android_name in names.items():
        a = re.search(rf'\bstatic\s+let\s+{ios_name}:\s*CGFloat\s*=\s*([0-9]+(?:\.[0-9]+)?)', swift)
        b = re.search(rf'\bval\s+{android_name}\s*=\s*([0-9]+(?:\.[0-9]+)?)\.dp', kt)
        if not a or not b: result[ios_name] = {'error':'missing token'}
        else: result[ios_name] = {'ios_points':float(a[1]),'android_dp':float(b[1]),
                                'same_nominal_value':float(a[1])==float(b[1])}
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ios', required=True, type=Path, help='Root of kepler-ios-main repository')
    parser.add_argument('--report', type=Path, default=ROOT / 'docs/parity/IOS_ANDROID_SOURCE_COMPARISON_20261025.md')
    args = parser.parse_args()
    ios = args.ios.resolve()
    assert (ios / 'Sources/Core/DesignSystem.swift').is_file(),f'Not an iOS source checkout: {ios}'
    counts, swift_files = ios_names(ios)
    src = (ROOT / 'app/src/main/java/com/iumrah/beta/ui/cupertino/CupertinoSfCatalog.kt').read_text()
    aliases = dict(re.findall(r'"([^"]+)"\s+to\s+CupertinoSymbol\.(\w+)', src))
    missing = sorted(set(counts) - set(aliases))
    values = geometry((ios/'Sources/Core/DesignSystem.swift').read_text(),
                      (ROOT/'app/src/main/java/com/iumrah/beta/core/design/IumrahTheme.kt').read_text())
    swift_joined = '\n'.join(f.read_text(encoding='utf-8') for f in swift_files)
    ios_lato_count = len(re.findall(r'\.font\(IumrahLato\.font\(', swift_joined))
    sys_name_calls = len(re.findall(r'Image\(systemName:', swift_joined))
    sys_font_references = len(re.findall(r'\.font\(\.system\(', swift_joined))
    light_page = 'LightPage = Color(0xFFF2F2F7)' in (ROOT/'app/src/main/java/com/iumrah/beta/core/design/IumrahTheme.kt').read_text()
    payload = {
        'ios_files':len(swift_files), 'ios_direct_sf_calls':sys_name_calls,
        'literal_sf_unique':len(counts),'literal_sf_occurrences':sum(counts.values()),
        'android_sf_mappings':len(aliases), 'unmapped_symbols':missing,
        'metrics': values, 'ios_lato_text_styles':ios_lato_count,
        'ios_remaining_system_font_modifiers_including_symbols':sys_font_references,
        'android_uses_ios_light_grouped_page_background':light_page,
        'warning':'Nominal source geometry only; native point-to-dp equivalence, adaptive colors, SF-vs-Cupertino glyph shapes, typography line metrics, screen layouts and device pixel parity require runtime/screenshot validation.',
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    metrics_rows='\n'.join('| '+k+' | '+str(v.get('ios_points','?'))+' | '+str(v.get('android_dp','?'))+' | '+('✓' if v.get('same_nominal_value') else '✗')+' |' for k,v in values.items())
    args.report.write_text(f'''# iumrah iOS ↔ Android source visual parity audit (2026-10-25)

Read-only comparison against the **original, unchanged uploaded SwiftUI iOS** tree. Changes are applied exclusively to Android.

## Checkable facts

- iOS Swift files: **{len(swift_files)}**; direct `Image(systemName:)` occurrences: **{sys_name_calls}**.
- Distinct literal SF Symbol names: **{len(counts)}**, with **{sum(counts.values())}** literal references in Swift source.
- Android SF→Cupertino alias entries: **{len(aliases)}**; **missing literal aliases: {len(missing)}**.
- Missing literal names: {', '.join('`'+s+'`' for s in missing) if missing else 'none'}.
- Original iOS Lato text style calls: **{ios_lato_count}**. Existing `.font(.system(...))` styles: **{sys_font_references}**. No typography changes were made to iOS; Android is configured for Lato Regular/Bold.
- Android light page color matches iOS standard grouped page approximation: **{light_page}** (`#F2F2F7` in light mode).

## Shared nominal design metrics

| Design token | iOS pt | Android dp | Numeric equality |
|---|---:|---:|---|
{metrics_rows}

## Important limitations

1. **SF Symbols cannot be shipped to Android**; Android uses the MIT Flutter CupertinoIcons glyph asset. Some aliases are semantically similar, not geometrically identical. No pixel-perfect icon claim.
2. Values in pt vs dp are numerically synchronized but may produce different on-screen pixels due to density, font rendering, navigation bars and safe areas.
3. iOS has dynamic UIKit appearance, including iOS 26 Liquid Glass. Android Compose's deterministic surfaces are not a literal implementation of Liquid Glass.
4. Sources only: **no Android Gradle build, Xcode build, emulator test or screenshot regression comparison has been run** in this environment.
5. iOS source is left completely unchanged and remains the visual reference. Android uses Lato regular/bold globally via Compose typography. Since original iOS uses its existing fonts, typographic pixel parity is not guaranteed.
6. Only Android Gradle bootstrap installs original Flutter CupertinoIcons and OFL Lato fonts before native compilation. The original iOS project and its CI are not modified. Font binaries are not distributed in this source archive.
7. Semantic SVGs, illustrations, maps, photos, OS components and screen-specific logic need device-level comparisons before claiming total visual identity.

Audit command:

```bash
python3 scripts/audit-ios-source-parity.py --ios /path/to/kepler-ios-main
```
''',encoding='utf-8')
    args.report.with_suffix('.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in payload.items() if k not in ('metrics','warning')},ensure_ascii=False,indent=2))
    assert not missing,'Missing SF Symbol mappings: '+', '.join(missing)
    assert all(v.get('same_nominal_value') for v in values.values()), 'Divergent shared geometry tokens'
    assert light_page, 'Divergent light page background'
    print('PASS: literal SF symbol coverage + 6 shared metrics + light page; iOS is read-only')

if __name__ == '__main__': main()
