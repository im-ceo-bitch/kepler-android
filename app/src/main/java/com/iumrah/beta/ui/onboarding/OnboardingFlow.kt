package com.iumrah.beta.ui.onboarding
import com.iumrah.beta.ui.cupertino.Icon

import com.iumrah.beta.ui.cupertino.CupertinoSymbol

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.snap
import com.iumrah.beta.core.design.rememberIumrahMotionAllowed
import com.iumrah.beta.core.design.iosSpring
import com.iumrah.beta.ui.components.IumrahOrbitV5
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.analytics.IumrahIntelligence
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.IumrahMotion
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.IumrahDot
import com.iumrah.beta.ui.components.IumrahPill
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.media.LoopingRawVideo
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow

private data class OnboardingPage(
    val kicker: String,
    val title: String,
    val body: String,
    val footnote: String,
    val scene: Int,
)

@Composable
fun OnboardingFlow(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onFinished: () -> Unit,
) {
    val pages = remember {
        listOf(
            OnboardingPage("onboarding_welcome_kicker", "onboarding_welcome_title", "onboarding_welcome_body", "onboarding_welcome_footnote", 0),
            OnboardingPage("onboarding_builder_kicker", "onboarding_builder_title", "onboarding_builder_body", "onboarding_builder_footnote", 1),
            OnboardingPage("onboarding_package_kicker", "onboarding_package_title", "onboarding_package_body", "onboarding_package_footnote", 2),
            OnboardingPage("onboarding_care_kicker", "onboarding_care_title", "onboarding_care_body", "onboarding_care_footnote", 3),
            OnboardingPage("onboarding_closing_kicker", "onboarding_closing_title", "onboarding_closing_body", "onboarding_closing_footnote", 4),
        )
    }
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val motionAllowed = rememberIumrahMotionAllowed()
    val hapticView = LocalView.current
    var showIntro by remember { mutableStateOf(true) }
    var introFading by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(true) }
    fun finish() {
        if (isFinishing) return
        IumrahHaptics.success(hapticView)
        isFinishing = true
        IumrahIntelligence.record("tap", "onboarding", "finish")
        scope.launch { delay(620); onFinished() }
    }

    LaunchedEffect(Unit) {
        // Mirrors the deterministic SwiftUI launch sequence: settle -> 1.05 s hold -> fade -> pager.
        delay(1_050)
        introFading = true
        delay(230) // Match SwiftUI: introductory copy fades before the cinematic pager mounts.
        showIntro = false
    }
    var previousPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pager.settledPage, showIntro) {
        if (!showIntro) {
            if (previousPage != null && previousPage != pager.settledPage) IumrahHaptics.selection(hapticView)
            previousPage = pager.settledPage
            IumrahIntelligence.record("onboarding_step", "onboarding", "step_${pager.settledPage}")
        }
    }

    val finishScale by animateFloatAsState(if (isFinishing && motionAllowed) .975f else 1f, tween(340), label = "onboarding-finish-scale")
    val finishAlpha by animateFloatAsState(if (isFinishing) .18f else 1f, tween(340), label = "onboarding-finish-alpha")
    val finishBlur by animateFloatAsState(if (isFinishing && motionAllowed) 5f else 0f, tween(340), label = "onboarding-finish-blur")
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedVisibility(
            visible = !showIntro,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(220)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = finishAlpha
                        scaleX = finishScale
                        scaleY = finishScale
                    }
                    .then(if (Build.VERSION.SDK_INT >= 31 && finishBlur > .05f) Modifier.blur(finishBlur.dp) else Modifier),
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !isFinishing,
                ) { index ->
                    val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    val pageScale = if (motionAllowed) 1f - (0.035f * offset) else 1f // SwiftUI scrollTransition: 1 -> 0.965
                    val pageAlpha = 1f - (0.42f * offset) // 1 -> 0.58
                    val blurDp = if (motionAllowed) 2.6f * offset else 0f
                    OnboardingPageContent(
                        page = pages[index],
                        language = language,
                        active = pager.currentPage == index && !isFinishing,
                        muted = isMuted,
                        onToggleMute = { isMuted = !isMuted },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = pageScale
                                scaleY = pageScale
                                alpha = pageAlpha
                            }
                            .then(if (Build.VERSION.SDK_INT >= 31 && blurDp > .05f) Modifier.blur(blurDp.dp) else Modifier),
                    )
                }

                OnboardingTopBar(
                    language = language,
                    onLanguageChange = onLanguageChange,
                    darkChrome = pager.currentPage == 0 || pager.currentPage == 2,
                    onSkip = ::finish,
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp).padding(bottom = maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), 20.dp) + 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        pages.indices.forEach { index ->
                            val selected = pager.currentPage == index
                            val width by animateFloatAsState(if (selected) 25f else 7f, tween(220), label = "onboarding-dot")
                            Box(Modifier.size(width.dp, 7.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = if (selected) 1f else .18f)))
                        }
                    }
                    if (pager.currentPage == pages.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            L10n.text("onboarding_bismillah", language),
                            fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    IumrahPrimaryButton(
                        title = if (pager.currentPage == pages.lastIndex) L10n.text("onboarding_start", language) else L10n.text("onboarding_next", language),
                        onClick = {
                            if (pager.currentPage == pages.lastIndex) {
                                finish()
                            } else {
                                scope.launch {
                                    pager.animateScrollToPage(
                                        pager.currentPage + 1,
                                        animationSpec = if (motionAllowed) iosSpring(.48f, .88f) else snap(),
                                    )
                                }
                            }
                        },
                        enabled = !isFinishing && !pager.isScrollInProgress,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showIntro,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220)),
        ) {
            OnboardingIntro(language, fading = introFading)
        }

        AnimatedVisibility(
            visible = isFinishing,
            enter = fadeIn(tween(340)),
            exit = fadeOut(tween(220)),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        painter = painterResource(R.drawable.iumrah_onboarding_brand),
                        contentDescription = "iumrah",
                        modifier = Modifier.size(106.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Image(
                        painter = painterResource(if (androidx.compose.foundation.isSystemInDarkTheme()) R.drawable.iumrah_header_wordmark_light else R.drawable.iumrah_header_wordmark_dark),
                        contentDescription = "iumrah",
                        modifier = Modifier.width(202.dp).height(52.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingIntro(language: AppLanguage, fading: Boolean) {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    val scale by animateFloatAsState(if (settled) 1f else .94f, IumrahMotion.softReveal, label = "intro-icon-scale")
    val wordmarkY by animateFloatAsState(if (settled) 0f else 8f, IumrahMotion.softReveal, label = "intro-wordmark-y")
    val captionAlpha by animateFloatAsState(if (settled && !fading) .88f else 0f, tween(if (fading) 220 else 260), label = "intro-caption-alpha")
    val markAlpha by animateFloatAsState(if (settled && !fading) 1f else 0f, tween(220), label = "intro-mark-alpha")

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0D0F)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(
                painter = painterResource(R.drawable.iumrah_onboarding_brand),
                contentDescription = null,
                modifier = Modifier.size(132.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = markAlpha },
            )
            Image(
                painter = painterResource(R.drawable.iumrah_header_wordmark_light),
                contentDescription = "iumrah",
                modifier = Modifier.width(225.dp).height(58.dp).graphicsLayer { translationY = wordmarkY; alpha = markAlpha },
                contentScale = ContentScale.Fit,
            )
            Text(
                L10n.text("onboarding_intro_line", language),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = captionAlpha),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
    }
}

@Composable
private fun OnboardingTopBar(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    darkChrome: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val foreground = if (darkChrome) Color.White else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().padding(top = maxOf(WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), 44.dp) + 12.dp, start = 22.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.height(44.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onSkip)
                .padding(horizontal = 8.dp), contentAlignment = Alignment.Center,
        ) {
            Text(L10n.text("onboarding_skip", language), color = foreground,
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Box {
            IumrahPressable(
                onClick = { expanded = true },
                modifier = Modifier.size(48.dp),
                cornerRadius = 99.dp,
                background = if (darkChrome) Color.White.copy(alpha = .14f) else MaterialTheme.colorScheme.surface.copy(alpha = .9f),
                pressedScale = .92f,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(CupertinoSymbol.Globe, contentDescription = "Language", tint = foreground)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AppLanguage.entries.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.nativeTitle) },
                        trailingIcon = { if (item == language) Icon(CupertinoSymbol.Checkmark, contentDescription = null) },
                        onClick = { expanded = false; onLanguageChange(item) },
                    )
                }
            }
        }
    }
}

private fun languageTitle(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "Русский"
    AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "English"
    AppLanguage.UZBEK -> "O‘zbekcha"
    AppLanguage.UZBEK_CYRILLIC -> "Ўзбекча"
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    language: AppLanguage,
    active: Boolean,
    muted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionAllowed = rememberIumrahMotionAllowed()
    val y by animateFloatAsState(if (active || !motionAllowed) 0f else 18f, IumrahMotion.softReveal,
        label = "onboarding-copy-y")
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val reserve = maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(), 20.dp) + 112.dp
        val availableHeight = (maxHeight - reserve).coerceAtLeast(0.dp)
        val small = availableHeight < 650.dp
        val copyLength = L10n.text(page.title, language).length + L10n.text(page.body, language).length + L10n.text(page.footnote, language).length
        val fraction = if (small) {
            if (copyLength > 175) .46f else if (copyLength > 145) .49f else .52f
        } else {
            if (copyLength > 190) .56f else if (copyLength > 155) .59f else .62f
        }
        val sceneHeight = (availableHeight * fraction).coerceIn(if (small) 286.dp else 404.dp, if (small) 392.dp else 496.dp)
        Column(Modifier.fillMaxWidth().height(availableHeight).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(if (page.scene == 4) availableHeight.coerceAtLeast(490.dp) else sceneHeight)
                .clip(RoundedCornerShape(bottomStart = if (page.scene == 4) 0.dp else 44.dp,
                    bottomEnd = if (page.scene == 4) 0.dp else 44.dp))) {
                OnboardingScene(scene = page.scene, active = active, language = language,
                    muted = muted, onToggleMute = onToggleMute,
                    modifier = Modifier.fillMaxSize())
                if (page.scene != 4) {
                    Box(Modifier.fillMaxWidth().height(176.dp).align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(0f to Color.Transparent, .28f to MaterialTheme.colorScheme.background.copy(alpha = .18f), .62f to MaterialTheme.colorScheme.background.copy(alpha = .64f), .86f to MaterialTheme.colorScheme.background.copy(alpha = .94f), 1f to MaterialTheme.colorScheme.background)))
                }
            }
            if (page.scene != 4) {
                Column(Modifier.fillMaxWidth().graphicsLayer { translationY = y }
                    .padding(horizontal = 22.dp, vertical = if (small) 16.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (small) 9.dp else 11.dp)) {
                    Text(L10n.text(page.kicker, language), fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                        letterSpacing = 2.2.sp)
                    Text(L10n.text(page.title, language),
                        fontSize = if (small) 33.sp else 36.sp,
                        lineHeight = if (small) 36.sp else 39.sp,
                        letterSpacing = (-.95).sp,
                        fontWeight = FontWeight.Bold)
                    Text(L10n.text(page.body, language),
                        fontSize = if (small) 16.sp else 17.sp,
                        lineHeight = if (small) 19.sp else 22.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f))
                    Text(L10n.text(page.footnote, language),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f),
                        maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun OnboardingScene(scene: Int, active: Boolean, language: AppLanguage,
    muted: Boolean, onToggleMute: () -> Unit, modifier: Modifier = Modifier) {
    val radius = RoundedCornerShape(bottomStart = 44.dp, bottomEnd = 44.dp)
    val motionAllowed = rememberIumrahMotionAllowed()
    val scale by animateFloatAsState(if (active || !motionAllowed) 1f else .93f, IumrahMotion.cinematic, label = "scene-$scene-scale")
    val alpha by animateFloatAsState(if (active) 1f else .66f, tween(300), label = "scene-$scene-alpha")
    val x by animateFloatAsState(if (active || !motionAllowed) 0f else 24f, IumrahMotion.cinematic, label = "scene-$scene-x")

    Box(
        modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha; translationX = x },
        contentAlignment = Alignment.Center,
    ) {
        when (scene) {
            0 -> Box(Modifier.fillMaxSize().clip(radius).background(Color(0xFF111315))) {
                LoopingRawVideo(
                    resourceName = "home_story_02",
                    play = active,
                    muted = muted,
                    modifier = Modifier.fillMaxSize(),
                    fallback = {
                        Image(
                            painter = painterResource(R.drawable.iumrah_makkah_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    },
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.16f), Color.Transparent, Color.Black.copy(alpha=.58f)))))
                Row(Modifier.align(Alignment.BottomStart).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IumrahPill(L10n.text("city_makkah", language), background = Color.White.copy(alpha=.16f), foreground = Color.White)
                    IumrahPill(L10n.text("city_madinah", language), background = Color.White.copy(alpha=.16f), foreground = Color.White)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = .16f))
                        .clickable(onClick = onToggleMute), contentAlignment = Alignment.Center) {
                        Icon(if (muted) CupertinoSymbol.SpeakerSlash else CupertinoSymbol.Speaker,
                            if (muted) "Sound off" else "Sound on", Modifier.size(19.dp), Color.White)
                    }
                }
            }

            1 -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFF6F8F7), Color(0xFFE8F2EF)))),
                contentAlignment = Alignment.Center) {
                OnboardingBuilderPreview(language, Modifier.fillMaxWidth(.86f).rotate(if (active) -2.2f else -.7f))
            }

            2 -> Box(Modifier.fillMaxSize().clip(radius).background(Color(0xFF101417)), contentAlignment = Alignment.Center) {
                LoopingRawVideo("flight_search", modifier = Modifier.fillMaxSize(), play = active, muted = true) { }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.46f)))
                OnboardingPackagePreview(language, Modifier.fillMaxWidth(.86f).rotate(if (active) -2.4f else -.8f))
            }

            3 -> Box(Modifier.fillMaxSize().background(Color(0xFFE9F3F1)), contentAlignment = Alignment.Center) {
                OnboardingCarePreview(language, Modifier.fillMaxWidth(.87f).rotate(if (active) 3.0f else 1.2f))
            }

            else -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val compact = maxHeight < 760.dp
                fun tr(ru: String, en: String, uz: String, cy: String) = when (language) {
                    AppLanguage.RUSSIAN -> ru
                    AppLanguage.UZBEK -> uz
                    AppLanguage.UZBEK_CYRILLIC -> cy
                    else -> en
                }
                Column(Modifier.fillMaxSize().padding(top = 54.dp, start = 22.dp, end = 22.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp)) {
                    Text("iumrah · " + tr("ЧАСТНАЯ УМРА", "PRIVATE UMRAH", "SHAXSIY UMRA", "ШАХСИЙ УМРА"), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .60f))
                    IumrahOrbitV5(diameter = if (compact) 210.dp else 252.dp, lightOnDark = androidx.compose.foundation.isSystemInDarkTheme(), active = active, accessibilityTitle = "iumrah")
                    Text(L10n.text("onboarding_closing_headline", language), fontSize = if (compact) 20.sp else 22.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text(L10n.text("onboarding_closing_message", language), fontSize = 17.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .60f), textAlign = TextAlign.Center)
                    val benefits = listOf(
                        CupertinoSymbol.Airplane to tr("Рейсы", "Flights", "Reyslar", "Рейслар"),
                        CupertinoSymbol.Building to tr("Отели", "Hotels", "Mehmonxonalar", "Меҳмонхоналар"),
                        CupertinoSymbol.Persons to tr("Гид", "Guide", "Gid", "Гид"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        benefits.forEach { (symbol, title) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(symbol, null, Modifier.size(12.dp))
                                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SceneIcon(
    icon: CupertinoSymbol,
    small: Boolean = false,
    light: Boolean = false,
) {
    val size = if (small) 54.dp else 82.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (small) 22.dp else 30.dp))
            .background(if (light) Color.White.copy(alpha=.14f) else MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(if (small) 25.dp else 35.dp),
            tint = if (light) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Cinematic marketing mocks are intentionally illustrative, never actual fares or bookings.
 * Like SwiftUI OnboardingBuilderMock / PackageMock / CareMock, all values below are static. */
@Composable
private fun OnboardingBuilderPreview(language: AppLanguage, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.surface)
        .padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SceneIcon(CupertinoSymbol.AirplaneTakeoff, small = true)
            Column(Modifier.weight(1f)) {
                Text(L10n.text("trip_origin_title", language), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("TAS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Icon(CupertinoSymbol.ChevronRight, null, Modifier.size(16.dp))
        }
        Text(L10n.text("trip_destination_title", language), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(L10n.text("city_makkah", language), modifier = Modifier.weight(1f).padding(5.dp),
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            Text(L10n.text("onboarding_chip_makkah_madinah", language),
                modifier = Modifier.weight(2f).clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface).padding(7.dp),
                style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OnboardingMockPill("27 Sep", Modifier.weight(1f))
            OnboardingMockPill("30 Sep", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(21.dp), Color(0xFF218364))
            Text(L10n.text("tier_standard", language), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnboardingMockPill(value: String, modifier: Modifier = Modifier) {
    Box(modifier.height(38.dp).clip(RoundedCornerShape(22.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OnboardingPackagePreview(language: AppLanguage, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF1E5349), Color(0xFF101716)))).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(L10n.text("final_price", language), color = Color.White.copy(alpha = .78f),
                    style = MaterialTheme.typography.labelLarge)
                Text("2", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Text("4 100 $", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = Color.White)
            Text(L10n.text("final_price_note", language), color = Color.White.copy(alpha = .65f),
                style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("2 050 $", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("2 050 $", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L10n.text("booking_included", language), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge)
            listOf("booking_outbound_flight", "booking_makkah_hotel", "booking_madinah_hotel", "booking_transfer_title").forEach { key ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(17.dp), Color(0xFF248568))
                    Text(L10n.text(key, language), style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun OnboardingCarePreview(language: AppLanguage, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.surface).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L10n.text("home_hero_kicker", language), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(L10n.text("onboarding_chip_status", language), fontWeight = FontWeight.Bold)
            Text("TAS → MED", fontWeight = FontWeight.SemiBold)
            Text("27–30 Sep", style = MaterialTheme.typography.bodySmall)
            OnboardingMockPill("2 050 $", Modifier.fillMaxWidth())
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF12453C), Color(0xFF378E7D)))).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.iumrah_care_mark), "iumrah Care", Modifier.size(43.dp))
                Text("24/7", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text("iumrah Care", style = MaterialTheme.typography.headlineSmall,
                color = Color.White, fontWeight = FontWeight.Bold)
            Text(L10n.text("care_subtitle", language), style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = .87f), maxLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("care_metric_answers", "care_metric_updates", "care_metric_care").forEach { key ->
                    Text(L10n.text(key, language), color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
