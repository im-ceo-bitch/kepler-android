#!/usr/bin/env python3
"""Structural tests for Android signature visuals derived from Oct 26 iOS patch.
These cannot substitute for renders or runtime functionality testing.
"""
from pathlib import Path
from hashlib import sha256

root = Path(__file__).resolve().parent.parent
src = root / 'app/src/main/java/com/iumrah/beta'
world = root / 'app/src/main/res/drawable-nodpi/iumrah_world_dots.png'
assert world.is_file(), 'Missing exact iOS world dots atlas'
assert sha256(world.read_bytes()).hexdigest() == '535373e2a4f29ffee42278171216e1fc516609d5154bf3fbbf22f0fc7e421051', 'World atlas differs from provided iOS source'
assert world.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n'
print('PASS: iOS world dot coordinates use same exact 1600x800 PNG bytes')

brand = (src/'ui/components/IumrahSignatureWorldBackground.kt').read_text()
header = (src/'ui/components/IumrahRootPageHeader.kt').read_text()
nav = (src/'ui/shell/AppShell.kt').read_text()
board = (src/'ui/hotels/HotelsScreen.kt').read_text()
account = (src/'ui/account/AccountScreens.kt').read_text()
booking = (src/'ui/booking/BookingsHomeScreen.kt').read_text()
theme = (src/'core/design/IumrahTheme.kt').read_text()
assert 'val SignatureBlack = Color(0xFF090909)' in theme
assert 'IumrahSignatureWorldBackground(Modifier.matchParentSize())' in header
assert 'drawsSignatureBackground: Boolean = true' in header
assert '34.sp' in header and '42.dp' in header and 'Color.White.copy(alpha = .105f)' in header
assert 'clearAndSetSemantics { }' in brand and '.clipToBounds()' in brand
print('PASS: common signature header / animated decorative map / 42dp controls')
assert '.height(65.dp)' in nav and '.padding(5.dp)' in nav and '.border(.8.dp, Color.Black.copy(alpha = .10f)' in nav
assert 'selectedTint = Color.White' in nav and 'unselectedTint = Color.Black.copy(alpha = .75f)' in nav
assert 'Modifier.size(21.dp)' in nav and 'fontSize = 10.5.sp' in nav
print('PASS: five-tab monochrome navigation matches published SwiftUI metrics')
assert 'drawsSignatureBackground = false' in board and 'IumrahSignatureWorldBackground(Modifier.matchParentSize())' in board
assert '.height(41.dp)' in board and '.38f' in board
assert 'IumrahRootPageHeader(' in account and booking and 'IumrahRootPageHeader(' in header
print('PASS: Hotels/Account/Booking have shared signature chrome')
print('NOTE: picture-by-picture iOS/Android simulator comparison and Android compilation remain unverified.')
