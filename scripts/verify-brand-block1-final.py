#!/usr/bin/env python3
"""Block 1 source-contract regression tests. Does NOT certify visual pixel parity.

Grounded in unchanged iOS 2026-10-26 header + world dots patch.
Run on Android checkout after applying cumulative update.
"""
from pathlib import Path
from hashlib import sha256
import re

root = Path(__file__).resolve().parent.parent
src = root/'app/src/main/java/com/iumrah/beta'
header = (src/'ui/components/IumrahRootPageHeader.kt').read_text()
world = (src/'ui/components/IumrahSignatureWorldBackground.kt').read_text()
nav = (src/'ui/shell/AppShell.kt').read_text()
hotels = (src/'ui/hotels/HotelsScreen.kt').read_text()
home = (src/'ui/home/HomeScreen.kt').read_text()
atlas = root/'app/src/main/res/drawable-nodpi/iumrah_world_dots.png'
wordmark = root/'app/src/main/res/drawable-nodpi/iumrah_header_wordmark_dark.png'


def check(condition, message):
    if not condition:
        raise AssertionError('BLOCK 1: ' + message)
    print('PASS: ' + message)


check(sha256(atlas.read_bytes()).hexdigest() == '535373e2a4f29ffee42278171216e1fc516609d5154bf3fbbf22f0fc7e421051', 'iOS world atlas is byte-identical')
check(sha256(wordmark.read_bytes()).hexdigest() == '40e8e7d143ef8957715ea3a29c801e565cd87a838f739f645209c64862088d97', 'iOS white header wordmark is byte-identical')
check('RuntimeShader(WORLD_DOT_WAVE_AGSL)' in world and 'createRuntimeShaderEffect(shader, "sourceImage")' in world and 'Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU' in world, 'Android 13+ uses native per-pixel AGSL, not a gradient approximation')
for fragment in ('uv.y * 8.5 + motion * 0.23', 'uv.y * 17.0 - motion * 0.13', 'uv.x * 1.72 - motion * 0.14 + bend', '6.2831853', 'pow(max(lightPhase, 0.0), 3.0)', 'pow(max(-lightPhase, 0.0), 2.0)', '0.46 + 0.51 * highlight - 0.30 * shadow + 0.048 * breathing', 'mix(0.67, dynamicLuminance, intensity) * gain', 'return source * brightness;'):
    check(fragment in world, 'iOS Metal wave formula AGSL: ' + fragment[:38])
check('shader.setFloatUniform("width", size.width)' in world and 'shader.setFloatUniform("height", size.height)' in world and 'shader.setFloatUniform("time", t)' in world, 'AGSL receives real bitmap pixel dimensions and elapsed seconds')
check('width * .94f' in world and '.coerceIn(300.dp, 920.dp)' in world and '.offset(x = width * .07f)' in world and '.25f to Color.White.copy(alpha = .32f)' in world and '.61f to Color.White' in world, 'geographic framing and fixed opacity mask use iOS exact constants')
check('.clearAndSetSemantics { }' in world and 'contentDescription = null' in world, 'atlas cannot intercept touch and is hidden from accessibility')
check('if (drawsSignatureBackground) Modifier.iumrahSignatureBleed()' in header and 'Modifier.iumrahSignatureBleed()' in hotels and 'placeable.placeRelative(-bleedPx, 0)' in world and 'maxWidth + bleedPx * 2' in world, 'header and Hotels map use deterministic two-sided 18dp bleed, without double expansion')
check('horizontalArrangement = Arrangement.spacedBy(spacing)' in header and 'wideNeeded = if (showsConnectivityStatus) 340.dp else 243.dp' in header and 'mediumNeeded = if (showsConnectivityStatus) 297.dp else 202.dp' in header, 'ViewThatFits sizes chosen by content width, not device class')
for screen, mode in ((320,'small'),(360,'medium'),(375,'medium'),(390,'wide'),(414,'wide')):
    available = screen-36
    actual = 'wide' if available>=340 else ('medium' if available>=297 else 'small')
    check(actual==mode, f'Header {screen}dp: {actual} preset')
check('fixedHeight = 42.dp' in header and 'Color.White.copy(alpha = .105f)' in header and 'BorderStroke(.8.dp, Color.White.copy(alpha = .25f))' in header, 'wordmark 42dp and button fills/borders match iOS')
check('unreadCount > 9) 26.dp else 16.dp' in header and '.offset(x = 3.dp, y = (-4).dp)' in header and 'if (unreadCount > 9) "9+"' in header, 'notification badge displays numbered 16dp or 9+ capsule, not a 7dp dot')
check('val gradientStops = listOf(' in header and 'Color.White.copy(alpha = .55f)' in header and 'Color.White.copy(alpha = .98f)' in header and 'Brush.sweepGradient(*rotatedStops.toTypedArray())' in header and 'tween(1550, easing = LinearEasing)' in header, 'connectivity pill uses moving WHITE/GRAY angular gradient at reference speed')
check('spring(dampingRatio = .82f, stiffness = 220f)' in header and 'delay(1_650)' in header and 'delay(3_650)' in header and 'delay(620)' in header, 'pill spring and cycle approximate iOS response and timing')
check('language = language' in home and all(word in header for word in ('"В сети"', '"Onlayn"', '"Онлайн"', '"Online"')), 'Home connectivity status localized to 4 Android languages')
for label, path in (('Home','ui/home/HomeScreen.kt'),('Hotels','ui/hotels/HotelsScreen.kt'),('Booking','ui/booking/BookingsHomeScreen.kt'),('Care','ui/care/CareHomeScreen.kt'),('Account','ui/account/AccountScreens.kt')):
    check('IumrahRootPageHeader(' in (src/path).read_text(), f'{label} uses global signature header')
check('.height(65.dp)' in nav and '.padding(5.dp)' in nav and 'RoundedCornerShape(25.dp)' in nav and 'Color.White.copy(alpha = .96f)' in nav and '.padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 2.dp)' in nav, 'five-slot bottom bar geometry matches iOS')
check('drawsSignatureBackground = false' in hotels and 'IumrahSignatureWorldBackground(Modifier.matchParentSize())' in hotels and '.height(41.dp)' in hotels, 'Hotels shared monochrome surface and 41dp segmented nav')
print('BLOCK 1 source-contract checks passed; APK compile and screenshot comparison remain outstanding.')
