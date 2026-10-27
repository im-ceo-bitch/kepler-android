package com.iumrah.beta.ui.advisor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.data.advisor.UmrahFlowContent
import com.iumrah.beta.data.advisor.UmrahFlowService
import kotlinx.coroutines.CancellationException

enum class UmrahGuideLanguage(val code: String, val nativeName: String) {
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English"),
    UZBEK("uz", "O‘zbek"),
    KAZAKH("kk", "Қазақша"),
    INDONESIAN("id", "Bahasa Indonesia"),
    TURKISH("tr", "Türkçe"),
    ARABIC("ar", "العربية"),
    MALAY("ms", "Bahasa Melayu"),
    BENGALI("bn", "বাংলা"),
    FRENCH("fr", "Français");

    companion object {
        fun preferred(language: AppLanguage) = when (language) {
            AppLanguage.RUSSIAN -> RUSSIAN
            AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> ENGLISH
            AppLanguage.UZBEK, AppLanguage.UZBEK_CYRILLIC -> UZBEK
        }
    }
}

private enum class Stage { START, TAWAF, POST_TAWAF, SAFA, END, AFTER }
private enum class RitualMode { LISTEN, READ }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UmrahAdvisorScreen(
    appLanguage: AppLanguage,
    chrome: AppChromeStore,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember(context) { UmrahFlowService(context.applicationContext) }
    val player = remember(context) { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var selectedLanguage by remember { mutableStateOf(UmrahGuideLanguage.preferred(appLanguage)) }
    var started by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf(UmrahFlowContent()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stage by remember { mutableStateOf(Stage.START) }
    var startPhase by remember { mutableIntStateOf(0) }
    var tawafRound by remember { mutableIntStateOf(1) }
    var tawafTextMode by remember { mutableIntStateOf(0) }
    var tawafMode by remember { mutableStateOf(RitualMode.LISTEN) }
    var tawafReadingStep by remember { mutableIntStateOf(0) }
    var tawafZikrCount by remember { mutableIntStateOf(0) }
    var postTawafStep by remember { mutableIntStateOf(0) }
    var safaRound by remember { mutableIntStateOf(1) }
    var safaTextMode by remember { mutableIntStateOf(0) }
    var safaMode by remember { mutableStateOf(RitualMode.LISTEN) }
    var safaReadingStep by remember { mutableIntStateOf(0) }
    var endPhase by remember { mutableIntStateOf(0) }
    var showNavigator by remember { mutableStateOf(false) }
    var showSunnahDua by remember { mutableStateOf(false) }
    var showSafaDua by remember { mutableStateOf(false) }
    var navigatorSelection by remember { mutableStateOf(Stage.START) }
    var playingKey by remember { mutableStateOf<String?>(null) }
    var audioLoading by remember { mutableStateOf(false) }
    var audioPlaying by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                audioPlaying = isPlaying
                if (isPlaying) audioLoading = false
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) audioLoading = false
                if (playbackState == Player.STATE_ENDED) { audioPlaying = false; playingKey = null }
            }
            override fun onPlayerError(error: PlaybackException) {
                audioLoading = false; audioPlaying = false; playingKey = null
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    fun text(key: String, fallback: String = key): String = content.translations[key]?.trim().takeUnless { it.isNullOrBlank() } ?: fallback
    fun stageLabel(target: Stage): String = when (target) {
        Stage.START -> text("umrah_start_title", stageTitle(target, selectedLanguage))
        Stage.TAWAF -> text("tawaf_title", stageTitle(target, selectedLanguage))
        Stage.POST_TAWAF -> text("tawaf_break_title", stageTitle(target, selectedLanguage))
        Stage.SAFA -> text("sai_title", stageTitle(target, selectedLanguage))
        Stage.END -> text("tahallul_title", stageTitle(target, selectedLanguage))
        Stage.AFTER -> text("home2_3title", stageTitle(target, selectedLanguage))
    }
    fun stopAudio() {
        player.stop(); player.clearMediaItems()
        playingKey = null; audioLoading = false; audioPlaying = false
    }
    fun play(key: String) {
        val url = content.audio[key]?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return
        if (playingKey == key && (audioPlaying || audioLoading)) { stopAudio(); return }
        stopAudio()
        playingKey = key
        audioLoading = true
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }
    fun reset() {
        stopAudio(); stage = Stage.START; startPhase = 0; tawafRound = 1; tawafTextMode = 0; tawafMode = RitualMode.LISTEN
        tawafReadingStep = 0; tawafZikrCount = 0; postTawafStep = 0; safaRound = 1; safaTextMode = 0
        safaMode = RitualMode.LISTEN; safaReadingStep = 0; endPhase = 0
    }

    LaunchedEffect(started, selectedLanguage) {
        if (!started) return@LaunchedEffect
        // Load persisted content before hitting the network so offline pilgrim
        // guidance does not flash empty UI. A language change never displays
        // cached text belonging to a different language.
        content = service.cached(selectedLanguage.code)
        loading = content.translations.isEmpty()
        error = null
        try {
            content = service.load(selectedLanguage.code)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (content.translations.isEmpty()) error = failure.localizedMessage
        } finally {
            loading = false
        }
    }

    DisposableEffect(Unit) {
        chrome.setImmersive(true)
        onDispose { stopAudio(); chrome.setImmersive(false) }
    }

    if (!started) {
        LanguagePicker(
            selection = selectedLanguage,
            appLanguage = appLanguage,
            onSelect = { selectedLanguage = it },
            onClose = { chrome.back() },
            onStart = { started = true },
        )
        return
    }

    val progress = when (stage) {
        Stage.START -> .04f + (startPhase / 3f) * .06f
        Stage.TAWAF -> .10f + ((tawafRound - 1) / 7f) * .42f
        Stage.POST_TAWAF -> .52f + (postTawafStep / 4f) * .08f
        Stage.SAFA -> .60f + ((safaRound - 1) / 7f) * .32f
        Stage.END -> .94f + (endPhase / 3f) * .05f
        Stage.AFTER -> 1f
    }
    val animatedProgress by animateFloatAsState(progress, label = "advisor-progress")

    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background, Color(0xFF071326).copy(alpha = .10f)))
        )
    ) {
        AdvisorHeader(
            title = stageLabel(stage),
            subtitle = advisorSubtitle(selectedLanguage),
            progress = animatedProgress,
            onBack = { stopAudio(); chrome.back() },
            onNavigate = { stopAudio(); navigatorSelection = stage; showNavigator = true },
        )
        if (loading && content.translations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            error?.let { message -> Text(message, Modifier.padding(horizontal = 20.dp, vertical = 5.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
            AnimatedContent(targetState = stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "advisor-stage") { active ->
                when (active) {
                    Stage.START -> VoiceStep(
                        kicker = text("umrah_start_title", "Start Umrah"), title = "",
                        body = text(listOf("start_text","start_text1","start_text2","start_text3")[startPhase], startFallback(startPhase)),
                        counter = "${startPhase + 1} / 4", audioAvailable = content.audio["tawaf_start"] != null,
                        audioActive = playingKey == "tawaf_start" && audioPlaying, audioBuffering = playingKey == "tawaf_start" && audioLoading,
                        onListen = { play("tawaf_start") }, showPrevious = startPhase > 0,
                        onPrevious = { stopAudio(); if (startPhase > 0) startPhase-- },
                        nextDone = startPhase == 3,
                        onNext = { stopAudio(); if (startPhase < 3) startPhase++ else stage = Stage.TAWAF },
                    )
                    Stage.TAWAF -> RitualScreen(
                        title = text("tawaf_title", "Tawaf"), mode = tawafMode, onMode = { stopAudio(); tawafMode = it },
                        listening = {
                            VoiceStep(
                                kicker = text("tawaf_title", "Tawaf"), title = text("tawaf${tawafRound}_tarab1", "Round $tawafRound"),
                                body = text("tawaf${tawafRound}_text${if (tawafTextMode == 0) 1 else 2}", "Continue Tawaf calmly and remember Allah."),
                                counter = "$tawafRound / 7", audioAvailable = content.audio["tawaf_$tawafRound"] != null,
                                audioActive = playingKey == "tawaf_$tawafRound" && audioPlaying, audioBuffering = playingKey == "tawaf_$tawafRound" && audioLoading,
                                onListen = { play("tawaf_$tawafRound") }, onBodyTap = { tawafTextMode = (tawafTextMode + 1) % 2 },
                                showPrevious = true, onPrevious = {
                                    stopAudio(); tawafTextMode = 0; tawafReadingStep = 0; tawafZikrCount = 0
                                    if (tawafRound > 1) tawafRound-- else { stage = Stage.START; startPhase = 3 }
                                }, nextDone = tawafRound == 7, onNext = {
                                    stopAudio(); tawafTextMode = 0; tawafReadingStep = 0; tawafZikrCount = 0
                                    if (tawafRound < 7) tawafRound++ else stage = Stage.POST_TAWAF
                                }
                            )
                        },
                        reading = {
                            TawafReading(
                                round = tawafRound, step = tawafReadingStep, zikrCount = tawafZikrCount,
                                text = ::text, onZikr = { if (tawafZikrCount < 20) tawafZikrCount++ },
                                onSunnahDua = { showSunnahDua = true },
                                onPrevious = {
                                    if (tawafReadingStep > 0) tawafReadingStep--
                                    else if (tawafRound > 1) { tawafRound--; tawafReadingStep = 0; tawafZikrCount = 0 }
                                    else tawafMode = RitualMode.LISTEN
                                },
                                onNext = {
                                    if (tawafReadingStep < 6) tawafReadingStep++
                                    else { tawafReadingStep = 0; tawafZikrCount = 0; if (tawafRound < 7) tawafRound++ else stage = Stage.POST_TAWAF }
                                }
                            )
                        }
                    )
                    Stage.POST_TAWAF -> {
                        val keys = listOf("pray", "zam_zam", "safa_go", "safa_dua")
                        val copy = when(postTawafStep) {
                            0 -> Triple(text("tawaf_break_title","After Tawaf"), text("tawafpray_title1","Prayer after Tawaf"), text("tawafpray_text1","Pray two rak'ahs when appropriate and safe."))
                            1 -> Triple(text("zamzam_title","Zamzam"), text("zamzam_title1","Drink Zamzam"), text("zamzam_text","Drink Zamzam and make dua."))
                            2 -> Triple(text("safago_title","Go to Safa"), text("safago_title1","Proceed to Safa"), text("safago_text","Proceed to Safa to begin Sa'i."))
                            else -> Triple(text("safadua_title","Dua at Safa"), text("safadua_title","Dua at Safa"), text("start_text3","Remember Allah and make dua before beginning Sa'i."))
                        }
                        VoiceStep(
                            kicker = copy.first, title = copy.second, body = copy.third, counter = "${postTawafStep + 1} / 4",
                            audioAvailable = content.audio[keys[postTawafStep]] != null,
                            audioActive = playingKey == keys[postTawafStep] && audioPlaying, audioBuffering = playingKey == keys[postTawafStep] && audioLoading, onListen = { play(keys[postTawafStep]) },
                            showPrevious = true, onPrevious = { stopAudio(); if (postTawafStep > 0) postTawafStep-- else { stage = Stage.TAWAF; tawafRound = 7 } },
                            nextDone = postTawafStep == 3, onNext = { stopAudio(); if (postTawafStep < 3) postTawafStep++ else { stage = Stage.SAFA; safaRound = 1 } },
                            secondaryAction = if (postTawafStep == 3) text("overlay_safa_title", "Dua at Safa and Marwa") to { showSafaDua = true } else null,
                        )
                    }
                    Stage.SAFA -> RitualScreen(
                        title = text("sai_title", "Safa & Marwa"), mode = safaMode, onMode = { stopAudio(); safaMode = it },
                        listening = {
                            VoiceStep(
                                kicker = text("sai_title","Safa & Marwa"), title = text("safa${safaRound}_title1", "Round $safaRound"),
                                body = text("safa${safaRound}_text${if(safaTextMode == 0) 1 else 2}", "Continue Sa'i calmly and remember Allah."),
                                counter = "$safaRound / 7", audioAvailable = content.audio["safa_$safaRound"] != null,
                                audioActive = playingKey == "safa_$safaRound" && audioPlaying, audioBuffering = playingKey == "safa_$safaRound" && audioLoading,
                                onListen = { play("safa_$safaRound") }, onBodyTap = { safaTextMode = (safaTextMode + 1) % 2 },
                                showPrevious = true, onPrevious = { stopAudio(); safaTextMode = 0; safaReadingStep = 0; if(safaRound > 1) safaRound-- else { stage = Stage.POST_TAWAF; postTawafStep = 3 } },
                                nextDone = safaRound == 7, onNext = { stopAudio(); safaTextMode = 0; safaReadingStep = 0; if(safaRound < 7) safaRound++ else stage = Stage.END },
                            )
                        },
                        reading = {
                            SafaReading(
                                round = safaRound, step = safaReadingStep, text = ::text,
                                onDua = { showSafaDua = true },
                                onPrevious = { if(safaReadingStep > 0) safaReadingStep-- else if(safaRound > 1){ safaRound--; safaReadingStep = 0 } else safaMode = RitualMode.LISTEN },
                                onNext = { if(safaReadingStep < 2) safaReadingStep++ else { safaReadingStep = 0; if(safaRound < 7) safaRound++ else stage = Stage.END } },
                            )
                        }
                    )
                    Stage.END -> VoiceStep(
                        kicker = text("tahallul_title","Complete Umrah"), title = "",
                        body = text(listOf("end_text","end_text1","end_text3")[endPhase], endFallback(endPhase)), counter = "${endPhase + 1} / 3",
                        audioAvailable = content.audio["safa_end"] != null,
                        audioActive = playingKey == "safa_end" && audioPlaying, audioBuffering = playingKey == "safa_end" && audioLoading, onListen = { play("safa_end") }, showPrevious = true,
                        onPrevious = { stopAudio(); if(endPhase > 0) endPhase-- else { stage = Stage.SAFA; safaRound = 7 } },
                        nextDone = endPhase == 2, onNext = { stopAudio(); if(endPhase < 2) endPhase++ else stage = Stage.AFTER }
                    )
                    Stage.AFTER -> Completion(
                        title = text("home2_3title","Umrah completed"), subtitle = text("home2_3_subtitle","May Allah accept your Umrah and your duas."),
                        done = done(selectedLanguage), restart = text("home_3_btn3","Start another Umrah"),
                        onDone = { chrome.back() }, onRestart = { reset() },
                    )
                }
            }
        }
    }

    if (showNavigator) {
        ModalBottomSheet(onDismissRequest = { showNavigator = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(advisorTitle(selectedLanguage), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(stageLabel(stage), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    Stage.START to stageLabel(Stage.START), Stage.TAWAF to stageLabel(Stage.TAWAF),
                    Stage.POST_TAWAF to stageLabel(Stage.POST_TAWAF), Stage.SAFA to stageLabel(Stage.SAFA),
                    Stage.END to stageLabel(Stage.END),
                ).forEach { (target, label) ->
                    val selected = navigatorSelection == target
                    Surface(onClick = { navigatorSelection = target }, shape = RoundedCornerShape(18.dp),
                        color = if(selected) Color(0xFF2E66E6).copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            if (selected) CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(18.dp), tint = Color(0xFF2E66E6))
                        }
                    }
                }
                Button(onClick = { stopAudio(); stage = navigatorSelection; showNavigator = false },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) {
                    Text(text("navigate_button", "Go"), fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { showNavigator = false; chrome.back() }, modifier = Modifier.fillMaxWidth()) {
                    Text(cancelUmrah(selectedLanguage), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (showSunnahDua) {
        ModalBottomSheet(onDismissRequest = { showSunnahDua = false }) {
            DuaSheet(
                title = text("sunna_dua_btn", "Sunnah Dua"),
                arabic = "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً، وَفِي الآخِرَةِ حَسَنَةً، وَقِنَا عَذَابَ النَّارِ",
                transliteration = "Rabbana atina fid-dunya hasanatan, wa fil-akhirati hasanatan, wa qina adhaban-nar",
                note = text("tawaf_common_text2", "You may also make any permissible dua."),
                onClose = { showSunnahDua = false },
            )
        }
    }
    if (showSafaDua) {
        ModalBottomSheet(onDismissRequest = { showSafaDua = false }) {
            DuaSheet(
                title = text("overlay_safa_title", "Dua at Safa and Marwa"),
                arabic = "اللّٰهُ أَكْبَرُ، اللّٰهُ أَكْبَرُ، اللّٰهُ أَكْبَرُ\nلَا إِلٰهَ إِلَّا اللّٰهُ وَحْدَهُ لَا شَرِيكَ لَهُ\nلَهُ الْمُلْكُ وَلَهُ الْحَمْدُ، وَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ",
                transliteration = "Allāhu akbar, Allāhu akbar, Allāhu akbar. Lā ilāha illallāhu waḥdahu lā sharīka lah...",
                note = text("overlay_safa_text1", "Repeat this remembrance, then make your own dua."),
                onClose = { showSafaDua = false },
            )
        }
    }
}

@Composable
private fun LanguagePicker(selection: UmrahGuideLanguage, appLanguage: AppLanguage, onSelect: (UmrahGuideLanguage) -> Unit, onClose: () -> Unit, onStart: () -> Unit) {
    val copy = when (appLanguage) {
        AppLanguage.RUSSIAN -> listOf("Язык iumrah Advisor", "Выберите язык голосового гида перед началом Умры.", "Начать Умру", "Закрыть")
        AppLanguage.UZBEK -> listOf("iumrah Advisor tili", "Umrani boshlashdan oldin ovozli gid tilini tanlang.", "Umrani boshlash", "Yopish")
        AppLanguage.UZBEK_CYRILLIC -> listOf("iumrah Advisor тили", "Умрани бошлашдан олдин овозли гид тилини танланг.", "Умрани бошлаш", "Ёпиш")
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> listOf("iumrah Advisor language", "Choose the voice-guide language before you begin Umrah.", "Start Umrah", "Close")
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(copy[0], fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(copy[1], fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClose) { Text(copy[3]) }
        }
        Spacer(Modifier.height(22.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(UmrahGuideLanguage.entries) { language ->
                val selected = language == selection
                Surface(
                    onClick = { onSelect(language) }, shape = RoundedCornerShape(20.dp),
                    color = if(selected) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                    border = if(selected) null else androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=.08f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(language.nativeName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = if(selected) Color.White else MaterialTheme.colorScheme.onSurface)
                        CupertinoIcon(if(selected) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.CheckCircle, null, Modifier.size(20.dp), tint = if(selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) {
            Text(copy[2], fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdvisorHeader(title: String, subtitle: String, progress: Float, onBack: () -> Unit, onNavigate: () -> Unit) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { CupertinoIcon(CupertinoSymbol.ChevronLeft, null, Modifier.size(22.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onNavigate) { CupertinoIcon(CupertinoSymbol.Menu, null, Modifier.size(20.dp)) }
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), color = Color(0xFF2E66E6), trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha=.08f))
    }
}

@Composable
private fun RitualScreen(title: String, mode: RitualMode, onMode: (RitualMode) -> Unit, listening: @Composable () -> Unit, reading: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
            listOf(RitualMode.LISTEN to "Listen", RitualMode.READ to "Read").forEach { (value, label) ->
                Box(
                    Modifier.weight(1f).clip(CircleShape).background(if(mode == value) Color.Black else Color.Transparent).clickable { onMode(value) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontWeight = FontWeight.Bold, color = if(mode == value) Color.White else MaterialTheme.colorScheme.onSurface) }
            }
        }
        Box(Modifier.fillMaxSize()) { if(mode == RitualMode.LISTEN) listening() else reading() }
    }
}

@Composable
private fun VoiceStep(
    kicker: String, title: String, body: String, counter: String,
    audioAvailable: Boolean, audioActive: Boolean, audioBuffering: Boolean, onListen: () -> Unit,
    showPrevious: Boolean, onPrevious: () -> Unit, nextDone: Boolean, onNext: () -> Unit,
    onBodyTap: (() -> Unit)? = null, secondaryAction: Pair<String, () -> Unit>? = null,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF356BFF).copy(alpha=.10f), Color.Transparent), radius = 650f)))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 34.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(kicker.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = Color(0xFF2E66E6))
            Spacer(Modifier.height(10.dp))
            if(title.isNotBlank()) Text(title, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            // Native Compose rendering of the iOS advisor's blue/violet/orange fluid orb.
            // Playback drives intensity; never suggests microphone metering without permission.
            Box(Modifier.size(150.dp).clickable(enabled = audioAvailable) { onListen() }, contentAlignment = Alignment.Center) {
                AdvisorFluidOrb(active = audioActive, buffering = audioBuffering, modifier = Modifier.fillMaxSize())
                if (audioBuffering) CircularProgressIndicator(Modifier.size(27.dp), strokeWidth = 2.dp, color = Color.White)
                else if (!audioAvailable) CupertinoIcon(CupertinoSymbol.SpeakerSlash, null, Modifier.size(24.dp), tint = Color.White.copy(alpha = .68f))
                else if (!audioActive) CupertinoIcon(CupertinoSymbol.Play, null, Modifier.size(25.dp), tint = Color.White.copy(alpha = .9f))
            }
            Spacer(Modifier.height(24.dp))
            Text(body, modifier = if(onBodyTap != null) Modifier.clickable { onBodyTap() } else Modifier, fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Text(counter, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            secondaryAction?.let { (label, action) ->
                Spacer(Modifier.height(16.dp)); OutlinedButton(onClick = action, shape = CircleShape) { Text(label) }
            }
            Spacer(Modifier.height(100.dp))
        }
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if(showPrevious) FloatingActionButton(onClick = onPrevious, containerColor = MaterialTheme.colorScheme.surface) { CupertinoIcon(CupertinoSymbol.ChevronLeft, null, Modifier.size(22.dp)) } else Spacer(Modifier.size(56.dp))
            Spacer(Modifier.weight(1f))
            ExtendedFloatingActionButton(onClick = onNext, containerColor = Color.Black, contentColor = Color.White) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if(nextDone) "Done" else "Next", fontWeight = FontWeight.Bold)
                    CupertinoIcon(if(nextDone) CupertinoSymbol.Checkmark else CupertinoSymbol.ChevronRight, null, Modifier.size(16.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun TawafReading(round: Int, step: Int, zikrCount: Int, text: (String, String) -> String, onZikr: () -> Unit, onSunnahDua: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val entries = listOf(
        Triple("DUA", text("tawaf${round}_reading_arab1", ""), null),
        Triple("MEANING", text("tawaf${round}_reading_text1", ""), text("tawaf${round}_reading_text2", "")),
        Triple("DUA", text("tawaf${round}_reading_arab2", ""), null),
        Triple("MEANING", text("tawaf${round}_reading_text3", ""), text("tawaf${round}_reading_text4", "")),
        Triple("DUA", text("tawaf${round}_reading_arab3", ""), null),
        Triple("MEANING", text("tawaf${round}_reading_text5", ""), text("tawaf${round}_reading_text6", "")),
        Triple("DHIKR", text("tawaf${round}_zikr_repeat", "Dhikr"), text("tawaf${round}_zikr_text", "Remember Allah and make dua.")),
    )
    val current = entries[step.coerceIn(0, 6)]
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 38.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TAWAF · $round / 7 · ${step + 1} / 7", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp)); Text(current.first, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E66E6))
            Spacer(Modifier.height(20.dp))
            if(step == 6) {
                Surface(onClick = onZikr, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(20 - zikrCount).coerceAtLeast(0)}", fontSize = 54.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.22f))
                        Text(current.second, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        current.third?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.padding(top=10.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = onSunnahDua, shape = CircleShape) { Text("Sunnah Dua") }
            } else {
                Text(current.second.ifBlank { "—" }, fontSize = if(step % 2 == 0) 28.sp else 23.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                current.third?.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(18.dp)); Text(it, fontSize = 19.sp, lineHeight = 27.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(110.dp))
        }
        StepButtons(step == 6, onPrevious, onNext, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SafaReading(round: Int, step: Int, text: (String, String) -> String, onDua: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    val entries = listOf(
        text("safa${round}_title1", "Safa & Marwa") to text("safa${round}_text1", "Continue Sa'i calmly and remember Allah."),
        "DUA" to text("safa${round}_sarab1", "Remember Allah and make dua."),
        "MEANING" to text("safa${round}_text2", "Make dua in your own words while continuing this passage."),
    )
    val current = entries[step.coerceIn(0,2)]
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SA'I · $round / 7 · ${step + 1} / 3", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp)); Text(current.first, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E66E6))
            Spacer(Modifier.height(24.dp)); Text(current.second, fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if(step == 1) { Spacer(Modifier.height(18.dp)); OutlinedButton(onClick = onDua, shape = CircleShape) { Text("Dua at Safa & Marwa") } }
            Spacer(Modifier.height(110.dp))
        }
        StepButtons(step == 2, onPrevious, onNext, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StepButtons(done: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp, vertical=14.dp), verticalAlignment = Alignment.CenterVertically) {
        FloatingActionButton(onClick = onPrevious, containerColor = MaterialTheme.colorScheme.surface) { CupertinoIcon(CupertinoSymbol.ChevronLeft, null, Modifier.size(22.dp)) }
        Spacer(Modifier.weight(1f))
        FloatingActionButton(onClick = onNext, containerColor = Color.Black, contentColor = Color.White) { CupertinoIcon(if(done) CupertinoSymbol.Checkmark else CupertinoSymbol.ChevronRight, null, Modifier.size(22.dp), tint = Color.White) }
    }
}

@Composable
private fun Completion(title: String, subtitle: String, done: String, restart: String, onDone: () -> Unit, onRestart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(58.dp), tint = Color(0xFF2E66E6)); Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp)); Text(subtitle, fontSize = 17.sp, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(30.dp)); Button(onClick=onDone, shape=CircleShape, colors=ButtonDefaults.buttonColors(containerColor=Color.Black), modifier=Modifier.height(54.dp)) { Text(done, fontWeight=FontWeight.Bold) }
        Spacer(Modifier.height(10.dp)); TextButton(onClick=onRestart) { Text(restart, fontWeight=FontWeight.SemiBold) }
    }
}

@Composable
private fun DuaSheet(title: String, arabic: String, transliteration: String, note: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp, vertical=14.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize=24.sp, fontWeight=FontWeight.Bold); TextButton(onClick=onClose){CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(22.dp))} }
        Spacer(Modifier.height(20.dp)); Text(arabic, fontSize=27.sp, lineHeight=42.sp, textAlign=TextAlign.Center)
        Spacer(Modifier.height(20.dp)); Text(transliteration, fontSize=17.sp, lineHeight=25.sp, color=MaterialTheme.colorScheme.onSurfaceVariant, textAlign=TextAlign.Center)
        Spacer(Modifier.height(20.dp)); Surface(shape=RoundedCornerShape(24.dp), color=MaterialTheme.colorScheme.surfaceVariant){ Text(note, Modifier.padding(18.dp), textAlign=TextAlign.Center) }
        Spacer(Modifier.height(30.dp))
    }
}

private fun stageTitle(stage: Stage, l: UmrahGuideLanguage) = when(stage) {
    Stage.START -> when(l){ UmrahGuideLanguage.RUSSIAN -> "Начало Умры"; UmrahGuideLanguage.UZBEK -> "Umrani boshlash"; UmrahGuideLanguage.ARABIC -> "بدء العمرة"; else -> "Start Umrah" }
    Stage.TAWAF -> "Tawaf"
    Stage.POST_TAWAF -> when(l){ UmrahGuideLanguage.RUSSIAN -> "После тавафа"; else -> "After Tawaf" }
    Stage.SAFA -> "Safa & Marwa"
    Stage.END -> when(l){ UmrahGuideLanguage.RUSSIAN -> "Тахаллуль"; else -> "Tahallul" }
    Stage.AFTER -> when(l){ UmrahGuideLanguage.RUSSIAN -> "Умра завершена"; else -> "Umrah completed" }
}
private fun advisorTitle(l: UmrahGuideLanguage) = "iumrah Advisor"
private fun advisorSubtitle(l: UmrahGuideLanguage) = when(l){ UmrahGuideLanguage.RUSSIAN -> "голосовой гид для умры"; UmrahGuideLanguage.UZBEK -> "Umra uchun ovozli gid"; UmrahGuideLanguage.ARABIC -> "دليل صوتي للعمرة"; else -> "voice guide for Umrah" }
private fun cancelUmrah(l: UmrahGuideLanguage) = when(l){ UmrahGuideLanguage.RUSSIAN -> "Выйти из Умры"; UmrahGuideLanguage.UZBEK -> "Umradan chiqish"; else -> "Exit Umrah" }
private fun done(l: UmrahGuideLanguage) = when(l){ UmrahGuideLanguage.RUSSIAN -> "Готово"; UmrahGuideLanguage.UZBEK -> "Tayyor"; else -> "Done" }
private fun startFallback(i: Int) = listOf("Prepare your intention and begin Umrah calmly.", "Enter Masjid al-Haram with attention and keep your intention clear.", "Move toward the beginning of Tawaf and follow the guidance.", "When you are ready, continue to Tawaf.")[i.coerceIn(0,3)]
private fun endFallback(i: Int) = listOf("You have completed Sa'i. Follow the final guidance carefully.", "Complete the final requirement of Umrah calmly and deliberately.", "Your Umrah is complete. May Allah accept it from you.")[i.coerceIn(0,2)]

/** Approximation of IumrahSiriOrb.swift using translucent moving sheets clipped to a sphere.
 *  The 9.05s morph, near-black core and blue/violet/orange palette follow the iOS reference.
 *  Reduced activity when stopped avoids animating with the same force as spoken guidance.
 */
@Composable
private fun AdvisorFluidOrb(active: Boolean, buffering: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "advisor-siri-fluid")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(9050, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "advisor-siri-morph",
    )
    val energy by animateFloatAsState(
        targetValue = if (active) 1f else if (buffering) .66f else .42f,
        animationSpec = tween(410), label = "advisor-siri-energy",
    )
    Canvas(modifier) {
        val side = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        val radius = side * .458f
        val theta = phase * (2f * PI.toFloat())
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF6C3CFB).copy(alpha = .22f * energy), Color.Transparent), center, side * .54f),
            radius = side * .52f, center = center,
        )
        val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center - Offset(radius, radius), center + Offset(radius, radius))) }
        clipPath(clip) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0xFF09102C), Color(0xFF050512), Color.Black), center, radius),
                center = center, radius = radius,
            )
            val colors = listOf(
                Color(0xFF7A2BFF), Color(0xFF1F3FFF), Color(0xFF09A4FF),
                Color(0xFF245AFF), Color(0xFFEA31FD), Color(0xFFFF6C0E), Color(0xFF7334FD),
            )
            for (index in colors.indices) {
                val fraction = index / 6f
                val width = side * (.13f + fraction * .06f)
                val baseY = center.y + side * ((fraction - .5f) * .52f)
                val curvature = side * (.12f + energy * .095f)
                val wave = theta * (1f + index * .06f) + index * 1.34f
                val route = Path()
                // Sheets cross the complete sphere, not radial petals.
                val samples = 36
                fun curve(x: Float): Float {
                    val ratio = (x - center.x) / side
                    return baseY + ratio * side * (-.38f + .10f * sin(wave)) +
                        curvature * sin(ratio * 5.2f + wave) * (1f - kotlin.math.abs(ratio))
                }
                for (sample in 0..samples) {
                    val x = center.x - radius * 1.35f + (radius * 2.70f * sample / samples)
                    val y = curve(x) - width * (.35f + .08f * sin(wave))
                    if (sample == 0) route.moveTo(x, y) else route.lineTo(x, y)
                }
                for (sample in samples downTo 0) {
                    val x = center.x - radius * 1.35f + (radius * 2.70f * sample / samples)
                    route.lineTo(x, curve(x) + width * (.55f + .12f * cos(wave)))
                }
                route.close()
                drawPath(route, brush = Brush.linearGradient(
                    listOf(colors[index].copy(alpha = .14f * energy),
                        colors[index].copy(alpha = (.53f + energy * .25f).coerceAtMost(.94f)),
                        colors[index].copy(alpha = .09f)),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius),
                ))
            }
            drawCircle(
                brush = Brush.radialGradient(listOf(Color.White.copy(alpha = .18f * energy), Color.Transparent),
                    center + Offset(side * .08f * sin(theta), side * .10f * cos(theta)), radius * .65f),
                center = center, radius = radius,
            )
        }
        drawCircle(
            brush = Brush.sweepGradient(
                listOf(Color(0xFF224EFF), Color(0xFF10A5FF), Color(0xFF852BFF), Color(0xFFFF650A), Color(0xFF224EFF)),
                center = center,
            ),
            center = center, radius = radius * .99f,
            style = Stroke(width = side * .009f),
            alpha = .62f * energy,
        )
    }
}
