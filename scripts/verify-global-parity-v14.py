#!/usr/bin/env python3
"""Verify only measurable Android-side invariants; not a pixel-perfect/device claim."""
from pathlib import Path
import re

R = Path(__file__).resolve().parent.parent
C = R/'app/src/main/java/com/iumrah/beta'

def check(test, name):
    if not test: raise AssertionError(name)
    print('PASS:', name)

settings=(C/'core/settings/AppSettingsStore.kt').read_text()
for lang,code in [('RUSSIAN','ru'),('ENGLISH','en'),('ARABIC','ar'),('TURKISH','tr'),('INDONESIAN','id'),('MALAY','ms'),('UZBEK','uz'),('UZBEK_CYRILLIC','uz-cyrl')]:
    check(bool(re.search(rf'{lang}\("{code}"',settings)),f'{lang} supported and persisted with iOS code {code}')
check('val isRightToLeft: Boolean get() = this == ARABIC' in settings,'Arabic RTL semantics defined')
check('LocalLayoutDirection provides' in (C/'IumrahApp.kt').read_text(),'RTL applied at application root')
catalogue=(C/'core/localization/IumrahInternationalTranslations.kt').read_text()
for code in ('ar','tr','id','ms'):
    check(f'"{code}" -> {code}Key(value)' in catalogue and f'"{code}" -> {code}Phrase(value)' in catalogue,
          f'{code}: iOS reviewed key + phrase catalogues')
check('IumrahInternationalTranslations.byKey(language.code, key)' in (C/'core/localization/L10n.kt').read_text(),
      'localized lookup uses reviewed catalogues and English fallback')
ui=(C/'ui/account/AccountScreens.kt').read_text()
check('var pending by remember(settings.language)' in ui and 'settingsStore.setLanguage(pending)' in ui,
      'language selection is not committed before confirmation')
check('R.drawable.iumrah_language_selection_hero' in ui and
      (R/'app/src/main/res/drawable-nodpi/iumrah_language_selection_hero.jpg').is_file(),
      'original iOS language hero is present')
# Explicit 4-way Kotlin language switches must not remain after exposing eight values.
issues=[]; checked=0
for f in C.rglob('*.kt'):
    text=f.read_text()
    for m in re.finditer(r'\bwhen\s*\(\s*(language|lang|l|pending|settings\.language)\s*\)\s*\{',text):
        d=1; pos=m.end()
        while pos<len(text) and d:
            if text[pos]=='{':d+=1
            elif text[pos]=='}':d-=1
            pos+=1
        body=text[m.end():pos]
        if 'AppLanguage.' not in body:continue
        checked+=1
        if 'else ->' in body:continue
        if any('AppLanguage.'+key not in body for key in ('ENGLISH','RUSSIAN','UZBEK','UZBEK_CYRILLIC','ARABIC','TURKISH','INDONESIAN','MALAY')):
            issues.append(f'{f.name}:{text.count(chr(10),0,m.start())+1}')
check(not issues, f'{checked} explicit language switches account for all 8 variants: {issues}')
check('FontFamily(' in (C/'core/design/IumrahTheme.kt').read_text() and
      'R.font.lato_regular' in (C/'core/design/IumrahTheme.kt').read_text() and
      'R.font.lato_bold' in (C/'core/design/IumrahTheme.kt').read_text(),
      'global Lato Regular/Bold resources remain configured')
check('CupertinoGlyphCodepoints.bySymbol' in (C/'ui/cupertino/CupertinoIcons.kt').read_text(),
      'official Cupertino glyph lookup remains active')
print('Global v14 source contracts PASS. Requires Android CI build + device visual/function QA for 1:1 claim.')
