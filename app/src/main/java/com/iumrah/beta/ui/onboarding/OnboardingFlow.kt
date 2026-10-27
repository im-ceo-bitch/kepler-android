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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import com.iumrah.beta.R
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
    val hapticView = LocalView.current
    var showIntro by remember { mutableStateOf(true) }
    var isFinishing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Mirrors the deterministic SwiftUI launch sequence: settle -> 1.05 s hold -> fade -> pager.
        delay(1_050)
        showIntro = false
    }
    LaunchedEffect(pager.currentPage, showIntro) {
        if (!showIntro) IumrahHaptics.selection(hapticView)
    }

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
                        alpha = if (isFinishing) .18f else 1f
                        scaleX = if (isFinishing) .975f else 1f
                        scaleY = if (isFinishing) .975f else 1f
                    }
                    .then(if (Build.VERSION.SDK_INT >= 31 && isFinishing) Modifier.blur(5.dp) else Modifier),
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { index ->
                    val offset = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    val pageScale = 1f - (0.035f * offset) // SwiftUI scrollTransition: 1 -> 0.965
                    val pageAlpha = 1f - (0.42f * offset) // 1 -> 0.58
                    val blurDp = 2.6f * offset
                    OnboardingPageContent(
                        page = pages[index],
                        language = language,
                        active = pager.currentPage == index,
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
                    modifier = Modifier.align(Alignment.TopEnd),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 26.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        pages.indices.forEach { IumrahDot(selected = pager.currentPage == it) }
                    }
                    if (pager.currentPage == pages.lastIndex) {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            L10n.text("onboarding_bismillah", language),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    IumrahPrimaryButton(
                        title = if (pager.currentPage == pages.lastIndex) L10n.text("onboarding_start", language) else L10n.text("onboarding_next", language),
                        onClick = {
                            if (pager.currentPage == pages.lastIndex) {
                                if (!isFinishing) {
                                    IumrahHaptics.success(hapticView)
                                    isFinishing = true
                                    scope.launch {
                                        delay(620)
                                        onFinished()
                                    }
                                }
                            } else {
                                scope.launch {
                                    pager.animateScrollToPage(
                                        pager.currentPage + 1,
                                        animationSpec = IumrahMotion.content,
                                    )
                                }
                            }
                        },
                        enabled = !isFinishing,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showIntro,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(220)),
        ) {
            OnboardingIntro(language)
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
private fun OnboardingIntro(language: AppLanguage) {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }
    val scale by animateFloatAsState(if (settled) 1f else .94f, IumrahMotion.softReveal, label = "intro-icon-scale")
    val wordmarkY by animateFloatAsState(if (settled) 0f else 8f, IumrahMotion.softReveal, label = "intro-wordmark-y")
    val captionAlpha by animateFloatAsState(if (settled) .88f else 0f, tween(260), label = "intro-caption-alpha")

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0D0F)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(
                painter = painterResource(R.drawable.iumrah_onboarding_brand),
                contentDescription = null,
                modifier = Modifier.size(132.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            )
            Image(
                painter = painterResource(R.drawable.iumrah_header_wordmark_light),
                contentDescription = "iumrah",
                modifier = Modifier.width(225.dp).height(58.dp).graphicsLayer { translationY = wordmarkY },
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.statusBarsPadding().padding(top = 12.dp, end = 22.dp)) {
        IumrahPressable(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
            cornerRadius = 99.dp,
            background = MaterialTheme.colorScheme.surface.copy(alpha = .90f),
            pressedScale = .92f,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.Globe, contentDescription = "Language")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(languageTitle(item)) },
                    trailingIcon = { if (item == language) Icon(CupertinoSymbol.Checkmark, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onLanguageChange(item)
                    },
                )
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
    modifier: Modifier = Modifier,
) {
    val y by animateFloatAsState(if (active) 0f else 18f, IumrahMotion.softReveal, label = "onboarding-copy-y")
    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        OnboardingScene(scene = page.scene, active = active, language = language)

        if (page.scene != 4) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp).graphicsLayer { translationY = y }) {
                Text(
                    L10n.text(page.kicker, language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.50f),
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    L10n.text(page.title, language),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(13.dp))
                Text(
                    L10n.text(page.body, language),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.67f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    L10n.text(page.footnote, language),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.42f),
                )
            }
        }
    }
}

@Composable
private fun OnboardingScene(scene: Int, active: Boolean, language: AppLanguage) {
    val radius = RoundedCornerShape(38.dp)
    val scale by animateFloatAsState(if (active) 1f else .93f, IumrahMotion.cinematic, label = "scene-$scene-scale")
    val alpha by animateFloatAsState(if (active) 1f else .66f, tween(300), label = "scene-$scene-alpha")
    val x by animateFloatAsState(if (active) 0f else 24f, IumrahMotion.cinematic, label = "scene-$scene-x")

    Box(
        Modifier
            .fillMaxWidth()
            .height(330.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha; translationX = x },
        contentAlignment = Alignment.Center,
    ) {
        when (scene) {
            0 -> Box(Modifier.fillMaxSize().clip(radius).background(Color(0xFF111315))) {
                LoopingRawVideo(
                    resourceName = "home_story_02",
                    play = active,
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
                }
            }

            1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SceneIcon(CupertinoSymbol.Route)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IumrahPill(L10n.text("onboarding_chip_makkah_madinah", language))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IumrahPill(L10n.text("onboarding_chip_flight", language))
                        IumrahPill(L10n.text("onboarding_chip_hotel", language))
                    }
                }
            }

            2 -> Box(Modifier.fillMaxSize().clip(radius).background(Color(0xFF101417)), contentAlignment = Alignment.Center) {
                LoopingRawVideo("flight_search", modifier = Modifier.fillMaxSize(), play = active, muted = true) { }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.46f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SceneIcon(CupertinoSymbol.AirplaneTakeoff, light = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        SceneIcon(CupertinoSymbol.Hotel, small = true, light = true)
                        SceneIcon(CupertinoSymbol.Route, small = true, light = true)
                    }
                    IumrahPill(L10n.text("onboarding_chip_makkah_madinah", language), background = Color.White.copy(alpha=.14f), foreground = Color.White)
                }
            }

            3 -> Box(Modifier.fillMaxSize().clip(radius).background(Color(0xFF0E2422)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Image(painter = painterResource(R.drawable.iumrah_care_mark), contentDescription = "iumrah Care", modifier = Modifier.height(76.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IumrahPill("24/7", background = Color.White.copy(alpha=.12f), foreground = Color.White)
                        IumrahPill(L10n.text("onboarding_chip_status", language), background = Color.White.copy(alpha=.12f), foreground = Color.White)
                    }
                }
            }

            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painter = painterResource(R.drawable.iumrah_onboarding_brand),
                    contentDescription = "iumrah",
                    modifier = Modifier.size(150.dp),
                    contentScale = ContentScale.Fit,
                )
                Image(
                    painter = painterResource(if (androidx.compose.foundation.isSystemInDarkTheme()) R.drawable.iumrah_header_wordmark_light else R.drawable.iumrah_header_wordmark_dark),
                    contentDescription = "iumrah",
                    modifier = Modifier.width(205.dp).height(52.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    L10n.text("onboarding_closing_headline", language),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    L10n.text("onboarding_closing_message", language),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha=.60f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
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
