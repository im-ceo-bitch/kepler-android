#!/usr/bin/env python3
"""Source and asset regressions for Block 04C/05; not device visual verification."""
from pathlib import Path
import hashlib
root=Path(__file__).resolve().parents[1]
base=root/'app/src/main/java/com/iumrah/beta'
def read(name): return (base/name).read_text()
def check(condition, title):
    assert condition, title
    print('PASS:', title)
service=read('ui/home/IumrahServicesScreen.kt')
onboarding=read('ui/onboarding/OnboardingFlow.kt')
video=read('ui/media/LoopingVideo.kt')
store=read('data/booking/BookingStore.kt')
esim=read('ui/esim/ESIMScreen.kt')
check(service.count('-> ServiceCopy(')==36, 'Nine services, four explicit language variants')
check('height(164.dp)' in service and 'RoundedCornerShape(26.dp)' in service and 'width(282.dp).height(322.dp)' in service and 'HomeCarouselDots(Service.entries.size, selected)' in service, 'iOS service card geometry and page dots')
check('rememberSnapFlingBehavior(lazyListState = rowState)' in service and 'https://ksavisa.sa/visa/tourism/details' in service, 'Snapping and official visa reference')
check('IumrahOrbitV5(diameter =' in onboarding and 'pager.settledPage' in onboarding and 'userScrollEnabled = !isFinishing' in onboarding, 'Shared Orbit, settled-page haptics and finishing guard')
check('introFading = true' in onboarding and 'delay(1_050)' in onboarding and 'delay(230)' in onboarding and 'delay(620)' in onboarding, 'iOS intro and finish timing')
check('val availableHeight = (maxHeight - reserve).coerceAtLeast(0.dp)' in onboarding and 'verticalScroll(rememberScrollState())' in onboarding, 'Footer safe area and long copy')
check('Lifecycle.State.RESUMED' in video and 'lifecycle.removeObserver(observer)' in video and 'player.release()' in video, 'Video lifecycle and disposal')
check('if (headersFor(currentSession) != headers) return emptyList()' in store and 'current.sessions.none { it.id == bookingID && isVisible(it) && headersFor(it) == headers }' in store, 'Stale eSIM authorization response rejected')
check('LaunchedEffect(sessionID, foreground)' in esim and 'throw cancelled' in esim and 'finally' in esim, 'eSIM polling limited to foreground, cancellation propagated')
for f in ['IumrahAnimatedSearchBorder.kt','IumrahSignatureWorldBackground.kt','PaymentMethodsMarquee.kt']:
    check('rememberIumrahMotionAllowed()' in read('ui/components/'+f), f+' respects lifecycle and reduced motion')
check('wrapContentWidth(Alignment.Start, unbounded = true)' in read('ui/components/PaymentMethodsMarquee.kt'), 'Marquee full repeated row is measured')
asset=root/'app/src/main/res/drawable-nodpi/iumrah_care_team_hero.jpg'
check(asset.is_file() and asset.stat().st_size>1000,'Care service source photo present')
print('Block 04C/05 structural checks passed; Gradle and screenshot QA not performed.')
