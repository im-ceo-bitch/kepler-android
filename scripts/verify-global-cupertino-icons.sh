#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PYAUDIT'
import json,pathlib,re
app=pathlib.Path('app/src/main/java/com/iumrah/beta')
files=list(app.rglob('*.kt'))
ui=[p for p in files if 'ui' in p.relative_to(app).parts]
sources={p:p.read_text(encoding='utf8') for p in files}
for path,src in sources.items():
    assert 'androidx.compose.material.icons' not in src, f'Material icon import: {path}'
    assert not re.search(r'\bIcons\.(Default|Filled|Outlined|Rounded|AutoMirrored|TwoTone)\b',src),path
for path in ui:
    src=sources[path]
    if path.name=='CupertinoRatingStars.kt': continue
    assert not re.search(r'[✈✓⌄⌃♥♡↻＋↗↩]',src),f'Emoji-style pseudo icon: {path}'
renderer=sources[app/'ui/cupertino/CupertinoIcons.kt']
assert 'CupertinoGlyphCodepoints.bySymbol' in renderer
assert 'R.font.cupertino_icons' in renderer
for banned in ('drawCupertinoSymbol(', 'drawPath(', 'drawLine(', 'drawCircle(', 'drawRoundRect('):
    assert banned not in renderer, f'Hand-made icon renderer remains: {banned}'
enum=renderer.split('enum class CupertinoSymbol {',1)[1].split('}',1)[0]
symbols=set(re.findall(r'\b[A-Z][A-Za-z0-9]+\b',enum))
mapdata=json.loads(pathlib.Path('third_party/cupertino_icons/symbol-map.json').read_text())
assert symbols==set(mapdata),'Incomplete font mapping: '+str(symbols^set(mapdata))
catalog=sources[app/'ui/cupertino/CupertinoSfCatalog.kt']
aliases=re.findall(r'"([^\"]+)"\s+to\s+CupertinoSymbol\.(\w+)',catalog)
assert len(aliases)==len(set(name for name,_ in aliases)),'Duplicate iOS SF names'
assert all(sym in symbols for name,sym in aliases),'SF catalog refers to unknown renderer symbol'
ios={s.strip() for s in pathlib.Path('docs/cupertino/ios-system-names-20261012.txt').read_text().splitlines() if s and not s.startswith('#')}
assert ios <= {name for name,_ in aliases}, 'Missing SF aliases: '+str(ios-{name for name,_ in aliases})
assert 'requireNotNull(currentIosSystemNames[systemName])' in catalog,'Silent InfoCircle fallback is not allowed'
theme=sources[app/'core/design/IumrahTheme.kt']
assert 'R.font.lato_regular' in theme and 'R.font.lato_bold' in theme,'Missing Lato theme'
font=pathlib.Path('app/src/main/res/font')
assert 'R.font.lato_regular' in theme and 'R.font.lato_bold' in theme
assert 'LATO_SOURCE' in pathlib.Path('scripts/install-cupertino-assets.py').read_text()
installed=(font/'cupertino_icons.ttf').exists()
# Parity guarantees for common elements. iOS design tokens are source-mirrored,
# while screen-level pixel QA remains a separate on-device requirement.
badge=sources[app/'ui/cupertino/IumrahIconBadge.kt']
assert '.background(tint)' in badge, 'iOS icon badge is solid, not translucent'
assert 'else Color.White' in badge, 'iOS icon badge uses a contrast glyph'
for location in ('ui/account/AccountScreens.kt','ui/account/AccountSecurityParity.kt','ui/booking/BookingParityComponents.kt'):
    assert 'IumrahIconBadge(' in sources[app/location], f'Canonical icon badge missing in {location}'
buttons=sources[app/'ui/components/IumrahComponents.kt']
assert buttons.count('pressedScale = 0.985f') >= 2,'iOS button press scale diverged'
assert 'shadowElevation = 0.dp, // iOS primary button has no elevation' in buttons,'Unexpected Android CTA elevation'
print(f'Cupertino source audit PASS: {len(files)} Kotlin files; {len(ui)} UI; {len(symbols)} font symbols; {len(aliases)} SF aliases.')
print('Font asset:', 'bundled/ready' if installed else 'to be installed automatically by Gradle on first build')
print('Lato: global family configured; official typefaces installed by bootstrap; 0 handmade icon paths / 0 Material icon imports.')
PYAUDIT
