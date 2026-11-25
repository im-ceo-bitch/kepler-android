#!/usr/bin/env python3
"""Source-level guards for visually verified Android screen regressions.

Not a substitute for Android compilation, screenshot baselines or real backend tests.
"""
from pathlib import Path
root = Path(__file__).resolve().parents[2] / 'app/src/main/java/com/iumrah/beta'
shell = (root / 'ui/shell/AppShell.kt').read_text()
account = (root / 'ui/account/AccountScreens.kt').read_text()
rates = (root / 'ui/hotels/NuiteePagedCatalog.kt').read_text()
assert 'modifier = Modifier.fillMaxSize()' in shell and '.clipToBounds()' in shell and '.padding(bottom = bottomNavigationFootprint)' in shell and 'height(statusInset)' in shell, 'Root viewport has lost safe status treatment'
assert 'height(statusInset)' in shell and 'SignatureBlack' in shell, 'Status-bar ink/background mismatch'
assert 'isAppearanceLightStatusBars = !rootHeaderVisible && (isHomeRoot || !darkPage)' in shell, 'Status bar icons may be dark on black'
assert 'rememberTextMeasurer()' in shell and '(105 downTo 76)' in shell, 'Tab width must be measured rather than inferred from character count'
assert account.index('item { SeamlessGuestProfile(') < account.index('item { GuestSettingsSection('), 'Guest account order changed'
assert account.index('item { GuestSettingsSection(') < account.index('item { com.iumrah.beta.ui.home.HomeBrandSlogan(language) }'), 'Slogan must be below guest settings'
assert 'SettingsRow(CupertinoSymbol.ChartBar,tr(language,"Usage analytics"' not in account, 'iOS6 settings gained unexpected row'
assert 'settingsIconTint(icon)' in account, 'Setting badges lack semantic colors'
assert 'markMissing(if (sandbox) "sandbox" else "invalid_occupancy")' in rates, 'Sandbox rates must not show permanent loading'
assert 'markMissing("provider_error")' in rates and 'if (limited)' in rates and 'markMissing("not_returned")' in rates, 'Rate-limit cleanup was removed'
print('PASS: screenshot regression guards for root inset, tab titles, account hierarchy, and rate states')
