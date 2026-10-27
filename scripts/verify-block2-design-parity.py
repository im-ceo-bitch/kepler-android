#!/usr/bin/env python3
"""Audit shared Android design primitives against the read-only SwiftUI source.

Offline-safe: standard repository checks read Android only. Passing --ios explicitly
compares with the SOURCE iOS folder (not the modified iOS delivery artifact).
Does not assert pixel-perfect SF glyphs or platform-specific shadow rasterization.
"""
from pathlib import Path
import argparse
import re

ROOT = Path(__file__).resolve().parent.parent
KT = ROOT / 'app/src/main/java/com/iumrah/beta'

def check(ok, name):
    if not ok:
        raise AssertionError(name)
    print('PASS', name)

def extract(pattern, source):
    match = re.search(pattern, source, re.M)
    check(match is not None, f'Expected source pattern: {pattern}')
    return match.group(1)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ios', type=Path, help='Read-only iOS source root')
    args = parser.parse_args()
    theme = (KT/'core/design/IumrahTheme.kt').read_text()
    motion = (KT/'core/design/IumrahMotion.kt').read_text()
    components = (KT/'ui/components/IumrahComponents.kt').read_text()
    surfaces = (KT/'ui/components/IumrahSurfaces.kt').read_text()
    flight_filters = (KT/'ui/trip/FlightSearchFiltersCard.kt').read_text()
    icon = (KT/'ui/cupertino/CupertinoIcons.kt').read_text()
    semantic = (KT/'ui/cupertino/IumrahSemanticIcons.kt').read_text()
    badges = (KT/'ui/cupertino/IumrahIconBadge.kt').read_text()
    catalog = (KT/'ui/cupertino/CupertinoSfCatalog.kt').read_text()
    symbol_map = re.findall(r'"([^"]+)" to CupertinoSymbol\.(\w+)', catalog)

    metrics = {
        'PagePadding':18, 'CardRadius':28, 'HeroRadius':34,
        'CompactRadius':19, 'ControlHeight':56, 'GlassIconSize':46,
    }
    for key,value in metrics.items():
        check(re.search(rf'val {key} = {value}\.dp\b',theme) is not None, f'metric {key} = {value}dp')
    for key in ('lato_regular','lato_bold'):
        check(f'R.font.{key}' in theme, 'bundled Android font: '+key)
    check('FontFamily(' in theme and 'ProvideTextStyle' in theme, 'Lato provided app-wide')
    check('val DarkRaised = Color(0xFF3A3A3C)' in theme, 'dark tertiary grouped raised surface')
    check('LightPage = Color(0xFFF2F2F7)' in theme, 'light grouped page surface')
    check('primary = Color.Black' in theme and 'onPrimary = Color.White' in theme, 'light primary black/white')
    check('primary = Color(0xFFF5F5F5)' in theme, 'dark primary light surface')
    check('durationMillis = 160' in motion and 'CubicBezierEasing(0.0f, 0.0f, 0.58f, 1.0f)' in motion,
          'shared iOS easeOut button timing')
    check(components.count('pressAnimation = IumrahMotion.iosButtonPress')==2, 'both standard buttons use iOS timing')
    check('pressedScale = 0.985f' in components, 'iOS 0.985 press scale')
    check('pressedBackgroundAlpha = .84f' in components, 'iOS primary pressed background alpha')
    check('pressedContentAlpha = .90f' in components, 'iOS secondary pressed content opacity')
    check('haptic = false' in components, 'no extra haptics from standard ButtonStyles')
    check('cornerRadius = IumrahGalaxyMetrics.RadiusButton' in components, 'button corner radius 19dp')
    for token in ('IumrahDesign.CardRadius','0.095f','0.75.dp','18.dp','IumrahDesign.HeroRadius','0.075f','0.7.dp','22.dp','0.14f'):
        check(token in surfaces, 'card & marketing token: '+token)
    check('Modifier.iumrahCard(background = generatorCardColor())' in flight_filters,
          'Flight Search filters use shared iOS card modifier')
    check('paint.getTextBounds(glyph, 0, glyph.length, bounds)' in icon and
          'bounds.left + bounds.right' in icon and 'bounds.top + bounds.bottom' in icon,
          'Cupertino glyph centering uses visible bounds')
    check('androidx.compose.material.icons' not in '\n'.join(x.read_text(errors='replace') for x in KT.rglob('*.kt')),
          'no Material icon imports')
    check('CupertinoSfCatalog.resolve(systemName)' in badges and 'foreground = semantic.onColor' in badges,
          'semantic SF name-based icon badges')
    check(len(symbol_map) >= 200 and len({n for n,_ in symbol_map}) == len(symbol_map),
          'SF symbol mapping nonduplicated >=200')
    symbols = dict(symbol_map)
    distinct_fill_variants = {
        'bed.double.fill': 'BedFill',
        'building.2.fill': 'BuildingFill',
        'clock.fill': 'ClockFill',
        'exclamationmark.circle.fill': 'ExclamationCircleFill',
        'hand.raised.fill': 'HandRaisedFill',
        'map.fill': 'MapFill',
        'person.2.fill': 'PersonsFill',
        'person.crop.circle.fill': 'PersonCircleFill',
    }
    for name, icon_name in distinct_fill_variants.items():
        check(symbols[name] == icon_name and symbols.get(name[:-5]) != icon_name,
              f'outline/fill glyph differentiated: {name}')
    collapsed = sorted(name for name in symbols if name.endswith('.fill') and
                       name[:-5] in symbols and symbols[name[:-5]] == symbols[name])
    check(collapsed == ['airplane.circle.fill', 'bell.badge.fill'],
          'exactly two catalog outline/fill pairs still lack distinct Cupertino variants')

    check(len(re.findall(r'^    [A-Z_]+\(IumrahColors\.System',semantic,re.M)) == 37,
          'all 37 iOS semantic icon roles assigned')
    if args.ios:
        swift = (args.ios/'Sources/Core/DesignSystem.swift').read_text()
        for kotlin_key,swift_key in [('PagePadding','pagePadding'),('CardRadius','cardRadius'),('HeroRadius','heroRadius'),
                                      ('CompactRadius','compactRadius'),('ControlHeight','controlHeight'),('GlassIconSize','glassIconSize')]:
            ios_value = int(extract(rf'static let {swift_key}: CGFloat = (\d+)',swift))
            check(metrics[kotlin_key] == ios_value, 'iOS reference '+swift_key)
        for expected in ('.scaleEffect(configuration.isPressed ? 0.985 : 1)',
                         '.animation(.easeOut(duration: 0.16)',
                         'Color.iumrahPrimaryButtonBackground.opacity(configuration.isPressed ? 0.84 : 1)',
                         '.opacity(configuration.isPressed ? 0.90 : 1)',
                         '.shadow(color: .black.opacity(0.045), radius: 18, y: 8)',
                         '.shadow(color: .black.opacity(dark ? 0.14 : 0.045), radius: 20, y: 9)'):
            check(expected in swift, 'source button/card contract: '+expected[:62])
        swift_roles=dict(re.findall(r'case \.(\w+): return Color\(uiColor: \.system(\w+)\)',swift))
        kotlin_roles=dict(re.findall(r'^    ([A-Z_]+)\(IumrahColors\.System(\w+)\)',semantic,re.M))
        ios_to_android = {re.sub(r'(?<!^)(?=[A-Z])','_',name).upper():color for name,color in swift_roles.items()}
        check(ios_to_android == kotlin_roles, 'all 37 semantic icon roles/color identities match original iOS')
        ios_names=set()
        for f in (args.ios/'Sources').rglob('*.swift'):
            ios_names.update(re.findall(r'Image\(systemName:\s*"([^"]+)"',f.read_text(errors='replace')))
        mapped={name for name,_ in symbol_map}
        check(not (ios_names - mapped), f'all {len(ios_names)} static iOS Image(systemName:) literals have catalog entries')
    print('PASS Block 2 structural contracts. NOT proof of identical device pixels / build.')

if __name__ == '__main__': main()
