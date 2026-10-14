#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PYAUDIT'
import pathlib, re
app = pathlib.Path('app/src/main/java/com/iumrah/beta')
files = list(app.rglob('*.kt'))
ui = [file for file in files if 'ui' in file.relative_to(app).parts]
code = {file: file.read_text(encoding='utf-8') for file in files}
for path, source in code.items():
    assert 'androidx.compose.material.icons' not in source, f'Material Icons import: {path}'
    assert not re.search(r'\bIcons\.(Default|Filled|Outlined|Rounded|AutoMirrored|TwoTone)\b', source), path
# No Unicode pseudo-icon inside on-screen controls. Route separators and
# textual hotel-category labels (e.g. 3★) are typography, not standalone icons.
for path in ui:
    source = code[path]
    if path.name == 'CupertinoRatingStars.kt': continue
    assert not re.search(r'[✈✓⌄⌃♥♡↻＋↗↩]', source), f'Unmigrated pseudo-icon: {path}'
    assert '"★".repeat(' not in source, f'Text-rendered rating icon: {path}'
renderer = code[app/'ui/cupertino/CupertinoIcons.kt']
catalog = code[app/'ui/cupertino/CupertinoSfCatalog.kt']
enum = renderer.split('enum class CupertinoSymbol {', 1)[1].split('}', 1)[0]
symbols = set(re.findall(r'\b[A-Z][A-Za-z0-9]+', enum))
drawing = renderer.split('when (symbol) {', 1)[1]
branches = set(re.findall(r'CupertinoSymbol\.(\w+)', drawing))
assert symbols <= branches, 'Symbols missing renderer: ' + repr(sorted(symbols - branches))
mappings = re.findall(r'"([^"]+)"\s+to\s+CupertinoSymbol\.(\w+)', catalog)
assert len(mappings) == len(set(name for name, sym in mappings)), 'Duplicate SF symbol names'
assert all(symbol in symbols for name, symbol in mappings), 'Catalog points to missing renderer symbols'
ios = {line.strip() for line in pathlib.Path('docs/cupertino/ios-system-names-20261012.txt').read_text().splitlines()
    if line and not line.startswith('#')}
missing = ios - {name for name, symbol in mappings}
assert not missing, 'Missing current iOS SF name mappings: ' + repr(sorted(missing))
assert 'LocalContentColor.current else tint' in renderer, 'Cupertino icons must support dark theme'
assert 'modifier.semantics' in renderer, 'Cupertino content descriptions must reach accessibility'
print(f'Global Cupertino icon audit PASS: {len(files)} Kotlin files, {len(ui)} UI Kotlin files, '
      f'{len(symbols)} rendered icons, {len(mappings)} SF aliases, {len(ios)} current iOS names covered; '
      '0 Material Icons imports / 0 icon-text controls.')
PYAUDIT
