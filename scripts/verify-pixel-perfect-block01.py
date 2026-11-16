#!/usr/bin/env python3
"""Source/isolated JVM checks for the 2026-11-16 iOS design-system reference.

Use --jvm to compile the real Kotlin catalog and semantic role inference with
minimal graphics stubs. This is not Compose or Android rendering/compilation.
"""
import argparse
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
K = ROOT / 'app/src/main/java/com/iumrah/beta'
REF = json.loads((ROOT/'docs/parity/pixel-perfect-block01-ios-reference.json').read_text())


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--jvm', action='store_true')
    opts = args.parse_args()
    theme = (K/'core/design/IumrahTheme.kt').read_text()
    catalog = (K/'ui/cupertino/CupertinoSfCatalog.kt').read_text()
    icons = (K/'ui/cupertino/CupertinoIcons.kt').read_text()
    semantic = (K/'ui/cupertino/IumrahSemanticIcons.kt').read_text()
    header = (K/'ui/components/IumrahRootPageHeader.kt').read_text()
    buttons = (K/'ui/components/IumrahComponents.kt').read_text()
    alias_pairs = re.findall(r'"([^\"]+)"\s+to\s+CupertinoSymbol\.(\w+)', catalog)
    aliases = dict(alias_pairs)
    assert len(aliases) == len(alias_pairs)
    assert set(REF['literal_sf_names']) <= set(aliases), 'Missing current-iOS icon names'
    for ios_name, expected in REF['metrics'].items():
        name = ios_name[0].upper() + ios_name[1:]
        assert float(re.search(rf'val {name} = ([\d.]+)\.dp',theme)[1]) == expected
    type_roles = dict(display='displayLarge', screenTitle='headlineLarge', sectionTitle='headlineSmall', cardTitle='titleLarge', body='bodyLarge', secondary='bodyMedium', caption='bodySmall')
    for role, style in type_roles.items():
        assert int(re.search(rf'{style} = TextStyle\(\s*fontSize = (\d+)\.sp',theme)[1]) == REF['typography'][role], role
    for symbol in ('bag','creditcard','paperplane','doc.text','checkmark.circle'):
        assert aliases[symbol] != aliases[symbol+'.fill'], symbol
    assert buttons.count('textAlign = TextAlign.Center') >= 2
    assert 'if (unreadCount > 0) CupertinoSymbol.BellBadge else CupertinoSymbol.Bell' in header
    assert 'unreadCount > 9) 26.dp else 16.dp' not in header
    assert 'headerWidth - IumrahDesign.PagePadding * 2 - slotWidth * 2 - 8.dp' in header
    assert 'val slotWidth = if (onHelp != null) 88.dp else 48.dp' in header
    print(f'Pixel block 01: {len(REF["literal_sf_names"])} iOS names, 6 geometry and 7 type roles, 5 outline/fill pairs, button and header contracts PASS')
    if not opts.jvm:
        return
    enum = re.search(r'enum class CupertinoSymbol\s*\{[^}]+\}', icons, re.S)[0]
    # Compile the actual production enum declaration, catalog and role inference.
    # Only Android/Compose color objects are stubbed, not the mapping logic.
    with tempfile.TemporaryDirectory() as tmp:
        temp = Path(tmp)
        (temp/'Symbols.kt').write_text('package com.iumrah.beta.ui.cupertino\n'+enum+'\n')
        (temp/'Color.kt').write_text('package androidx.compose.ui.graphics\ndata class Color(val id:Int=0,val alpha:Float=1f) { companion object { val Black=Color(-1); val White=Color(-2) } }\n')
        names=sorted(set(re.findall(r'IumrahColors\.(System\w+)',semantic)))
        (temp/'Colors.kt').write_text('package com.iumrah.beta.core.design\nimport androidx.compose.ui.graphics.Color\nobject IumrahColors {\n'+''.join(f'val {name} = Color({i})\n' for i,name in enumerate(names))+'}\n')
        # Input strings are loaded from a TSV to keep the test portable.
        (temp/'Roles.tsv').write_text(''.join(f'{n}\t{role}\t{aliases[n]}\n' for n,role in REF['inferred_roles'].items()))
        (temp/'Check.kt').write_text('''import com.iumrah.beta.ui.cupertino.*
import java.io.File
fun main(args: Array<String>) {
    val rows=File(args[0]).readLines()
    for (row in rows) {
        val (name, role, symbol)=row.split("\\t")
        check(IumrahIconRole.inferred(name).name == role) { "$name: ${IumrahIconRole.inferred(name)} != $role" }
        check(CupertinoSfCatalog.resolve(name).name == symbol) { name }
    }
    println("${rows.size} actual Swift/Kotlin role matches; ${rows.size} catalog lookups PASS")
}
''')
        subprocess.run(['kotlinc',str(temp/'Symbols.kt'),str(temp/'Color.kt'),str(temp/'Colors.kt'),str(temp/'Check.kt'),str(K/'ui/cupertino/CupertinoSfCatalog.kt'),str(K/'ui/cupertino/IumrahSemanticIcons.kt'),'-include-runtime','-d',str(temp/'checks.jar')],check=True)
        subprocess.run(['java','-jar',str(temp/'checks.jar'),str(temp/'Roles.tsv')],check=True)

if __name__=='__main__':
    main()
