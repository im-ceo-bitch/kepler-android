package com.iumrah.beta.ui.umrahflow

import android.media.MediaPlayer
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.advisor.UmrahFlowContent
import com.iumrah.beta.data.advisor.UmrahFlowService
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.domain.umrahflow.UmrahFlowAudioUrlPolicy
import com.iumrah.beta.domain.umrahflow.UmrahFlowCheckpointStore
import com.iumrah.beta.domain.umrahflow.UmrahFlowProgress
import com.iumrah.beta.domain.umrahflow.UmrahPhase
import kotlinx.coroutines.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ink = Color(0xFF08090C)
private val glass = Color.White.copy(alpha = .075f)
private val outline = Color.White.copy(alpha = .15f)

private fun label(lang: String, ru: String, en: String, uz: String): String = when (lang) {
    "ru" -> ru
    "uz" -> uz
    else -> en
}

private fun preferredLanguage(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "ru"
    AppLanguage.UZBEK, AppLanguage.UZBEK_CYRILLIC -> "uz"
    else -> language.code
}

private val guideLanguages = listOf(
    "ru" to "Русский", "en" to "English", "uz" to "O‘zbek", "ar" to "العربية",
    "tr" to "Türkçe", "id" to "Indonesia", "ms" to "Melayu", "kk" to "Қазақша",
    "fr" to "Français", "bn" to "বাংলা",
)

/** Native Android counterpart of iOS UmrahFlowRootView; all ritual counts require user action. */
@Composable
fun UmrahFlowScreen(language: AppLanguage, chrome: AppChromeStore) {
    val context = LocalContext.current
    val service = remember(context) { UmrahFlowService(context) }
    val checkpoint = remember(context) { UmrahFlowCheckpointStore(context) }
    var progress by remember { mutableStateOf(UmrahFlowProgress()) }
    var showResume by remember { mutableStateOf(checkpoint.hasCheckpoint()) }
    var showExit by remember { mutableStateOf(false) }
    var showStages by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var guideLanguage by remember { mutableStateOf(preferredLanguage(language)) }
    var content by remember { mutableStateOf(service.cached(guideLanguage)) }
    var loading by remember { mutableStateOf(false) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var audioError by remember { mutableStateOf(false) }

    fun stopAudio() {
        val old = audioPlayer
        audioPlayer = null
        playing = false
        old?.runCatching { reset(); release() }
    }
    fun update(next: UmrahFlowProgress) {
        if (next == progress) return
        progress = next
        checkpoint.save(next)
    }
    fun playAudio(key: String) {
        val url = content.audio[key] ?: return
        if (!UmrahFlowAudioUrlPolicy.isAllowed(url)) return
        stopAudio()
        audioError = false
        runCatching {
            val player = MediaPlayer()
            audioPlayer = player
            player.setOnPreparedListener { prepared -> prepared.start(); playing = true }
            player.setOnCompletionListener { stopAudio() }
            player.setOnErrorListener { _, _, _ -> audioError = true; stopAudio(); true }
            player.setDataSource(url)
            player.prepareAsync()
        }.onFailure { audioError = true; stopAudio() }
    }
    LaunchedEffect(guideLanguage) {
        content = service.cached(guideLanguage)
        loading = content.translations.isEmpty()
        try { content = service.load(guideLanguage) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep the bundled, reviewed catalog offline. */ }
        finally { loading = false }
    }
    LaunchedEffect(progress.phase, guideLanguage) { stopAudio() }
    DisposableEffect(audioPlayer) {
        val currentPlayer = audioPlayer
        onDispose { currentPlayer?.runCatching { reset(); release() } }
    }

    val title = when (progress.phase) {
        UmrahPhase.START -> label(guideLanguage, "Начало умры", "Begin Umrah", "Umrani boshlash")
        UmrahPhase.TAWAF -> label(guideLanguage, "Таваф", "Tawaf", "Tavof")
        UmrahPhase.POST_TAWAF -> label(guideLanguage, "После тавафа", "After Tawaf", "Tavofdan keyin")
        UmrahPhase.SAI -> label(guideLanguage, "Са‘й", "Sa'i", "Sa’y")
        UmrahPhase.END -> label(guideLanguage, "Завершение", "Completion", "Yakunlash")
        UmrahPhase.COMPLETED -> label(guideLanguage, "Умра завершена", "Umrah completed", "Umra yakunlandi")
    }
    val audioKey = when (progress.phase) {
        UmrahPhase.START -> "tawaf_start"
        UmrahPhase.TAWAF -> "tawaf_${(progress.tawafCompleted + 1).coerceAtMost(7)}"
        UmrahPhase.POST_TAWAF -> "zam_zam"
        UmrahPhase.SAI -> "safa_${(progress.saiCompleted + 1).coerceAtMost(7)}"
        UmrahPhase.END -> "safa_end"
        UmrahPhase.COMPLETED -> ""
    }
    val showAudio = content.audio[audioKey]?.let(UmrahFlowAudioUrlPolicy::isAllowed) == true

    Box(Modifier.fillMaxSize().background(ink)) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(
            colors = listOf(Color(0xFF25272F), ink), radius = 900f,
        )))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRoundAction(CupertinoSymbol.Close, label(guideLanguage, "Закрыть", "Close", "Yopish")) { showExit = true }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("iumrah Flow", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(label(guideLanguage, "Этап ${progress.milestone} из 5", "Stage ${progress.milestone} of 5", "5 dan ${progress.milestone}-bosqich"), color = Color.White.copy(alpha = .58f), fontSize = 11.sp)
                }
                FlowRoundAction(CupertinoSymbol.Menu, label(guideLanguage, "Этапы", "Stages", "Bosqichlar")) { showStages = true }
                Box {
                    FlowRoundAction(CupertinoSymbol.Globe, label(guideLanguage, "Язык", "Language", "Til")) { showLanguages = true }
                    DropdownMenu(expanded = showLanguages, onDismissRequest = { showLanguages = false }) {
                        guideLanguages.forEach { (code, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { guideLanguage = code; showLanguages = false })
                        }
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress.progressFraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color.White, trackColor = Color.White.copy(alpha = .10f),
            )
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
                    color = Color.White, textAlign = TextAlign.Center)
                if (progress.phase == UmrahPhase.TAWAF || progress.phase == UmrahPhase.SAI) {
                    FlowOrbit(
                        completed = if (progress.phase == UmrahPhase.TAWAF) progress.tawafCompleted else progress.saiCompleted,
                        label = if (progress.phase == UmrahPhase.TAWAF) "TAWAF" else "SA'I",
                    )
                    Text(
                        if (progress.phase == UmrahPhase.TAWAF) label(guideLanguage, "Завершено кругов: ${progress.tawafCompleted} / 7", "Completed circuits: ${progress.tawafCompleted} / 7", "Aylanishlar: ${progress.tawafCompleted} / 7")
                        else label(guideLanguage, "Завершено переходов: ${progress.saiCompleted} / 7", "Completed legs: ${progress.saiCompleted} / 7", "Qatnovlar: ${progress.saiCompleted} / 7"),
                        color = Color.White.copy(alpha = .76f), fontWeight = FontWeight.Medium, fontSize = 15.sp,
                    )
                }
                val lines = guideText(progress, content)
                FlowGuideCard(lines, loading, guideLanguage)
                if (showAudio) {
                    FlowActionButton(
                        title = if (playing) label(guideLanguage, "Остановить аудио", "Stop audio", "Audioni to‘xtatish")
                        else label(guideLanguage, "Слушать аудиогид", "Listen to guide", "Audiogidni tinglash"),
                        primary = false,
                    ) { if (playing || audioPlayer != null) stopAudio() else playAudio(audioKey) }
                }
                if (audioError) Text(label(guideLanguage, "Аудио временно недоступно", "Audio unavailable", "Audio mavjud emas"),
                    fontSize = 12.sp, color = Color.White.copy(alpha = .55f))
                if (progress.phase == UmrahPhase.TAWAF && progress.tawafCompleted < 7) {
                    FlowSwipeControl(
                        label(guideLanguage, "Проведите вправо после завершения круга", "Swipe right after finishing a circuit", "Aylanishdan so‘ng o‘ngga suring"),
                    ) { update(progress.completeTawafTurn()) }
                } else if (progress.phase == UmrahPhase.SAI && progress.saiCompleted < 7) {
                    val destination = if (progress.saiCompleted % 2 == 0) "Safa → Marwa" else "Marwa → Safa"
                    FlowSwipeControl("$destination  ·  " + label(guideLanguage, "Проведите после прохода", "Swipe after this leg", "O‘tib bo‘lgach suring")) {
                        update(progress.completeSaiLeg())
                    }
                }
                if (progress.phase == UmrahPhase.COMPLETED) {
                    FlowActionButton(label(guideLanguage, "Вернуться на главную", "Return to Home", "Bosh sahifaga qaytish"), true) {
                        checkpoint.clear(); chrome.back()
                    }
                } else {
                    FlowActionButton(
                        when (progress.phase) {
                            UmrahPhase.START -> label(guideLanguage, "Начать таваф", "Begin Tawaf", "Tavofni boshlash")
                            UmrahPhase.TAWAF -> label(guideLanguage, "К молитве и Замзаму", "Prayer and Zamzam", "Namoz va Zamzamga")
                            UmrahPhase.POST_TAWAF -> label(guideLanguage, "Перейти к са‘й", "Continue to Sa'i", "Sa’yga o‘tish")
                            UmrahPhase.SAI -> label(guideLanguage, "К завершению", "Continue to completion", "Yakunlashga o‘tish")
                            else -> label(guideLanguage, "Завершить умру", "Complete Umrah", "Umrani yakunlash")
                        }, primary = true, enabled = progress.canAdvance,
                    ) { update(progress.advance()) }
                }
                if (!progress.canAdvance) Text(label(guideLanguage,
                    "Следующий шаг станет доступен после всех семи подтверждений.",
                    "The next stage unlocks after seven manual confirmations.",
                    "Keyingi bosqich yetti tasdiqdan keyin ochiladi."),
                    color = Color.White.copy(alpha = .45f), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showResume) AlertDialog(
        onDismissRequest = { progress = checkpoint.restore() ?: UmrahFlowProgress(); showResume = false },
        title = { Text(label(guideLanguage, "Продолжить умру?", "Resume Umrah?", "Umrani davom ettirasizmi?")) },
        text = { Text(label(guideLanguage, "Сохранён ваш предыдущий этап. Вы можете продолжить или начать заново.", "Your previous progress is saved. Resume or start again.", "Oldingi bosqich saqlangan. Davom eting yoki qayta boshlang.")) },
        confirmButton = { TextButton(onClick = { progress = checkpoint.restore() ?: UmrahFlowProgress(); showResume = false }) { Text(label(guideLanguage, "Продолжить", "Resume", "Davom etish")) } },
        dismissButton = { TextButton(onClick = { checkpoint.clear(); progress = UmrahFlowProgress(); showResume = false }) { Text(label(guideLanguage, "Заново", "Restart", "Qayta boshlash")) } },
    )
    if (showExit) AlertDialog(
        onDismissRequest = { showExit = false },
        title = { Text(label(guideLanguage, "Выйти из Umrah Flow?", "Exit Umrah Flow?", "Umrah Flow'dan chiqish?")) },
        text = { Text(label(guideLanguage, "Ваш прогресс останется на этом устройстве.", "Your progress will stay saved on this device.", "Jarayon ushbu qurilmada saqlanadi.")) },
        confirmButton = { TextButton(onClick = { stopAudio(); showExit = false; chrome.back() }) { Text(label(guideLanguage, "Выйти", "Exit", "Chiqish")) } },
        dismissButton = { TextButton(onClick = { showExit = false }) { Text(label(guideLanguage, "Отмена", "Cancel", "Bekor qilish")) } },
    )
    if (showStages) AlertDialog(
        onDismissRequest = { showStages = false },
        title = { Text(label(guideLanguage, "Этапы умры", "Umrah stages", "Umra bosqichlari")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UmrahPhase.entries.filter { it != UmrahPhase.COMPLETED }.forEachIndexed { index, phase ->
                    Text(
                        "${index + 1}. " + when (phase) {
                            UmrahPhase.START -> "Start / Начало"
                            UmrahPhase.TAWAF -> "Tawaf / Таваф"
                            UmrahPhase.POST_TAWAF -> "Prayer & Zamzam"
                            UmrahPhase.SAI -> "Sa'i / Са‘й"
                            else -> "Completion / Завершение"
                        },
                        modifier = Modifier.fillMaxWidth().clickable(enabled = index + 1 <= progress.milestone) {
                            update(progress.copy(phase = phase)); showStages = false
                        }.padding(7.dp),
                        color = if (index + 1 <= progress.milestone) Color.Unspecified else Color.Gray,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { showStages = false }) { Text(label(guideLanguage, "Закрыть", "Close", "Yopish")) } },
    )
}

private fun guideText(progress: UmrahFlowProgress, content: UmrahFlowContent): List<String> {
    val t = content.translations
    val keys = when (progress.phase) {
        UmrahPhase.START -> listOf("home1_sub", "start_text", "start_text1", "start_text2", "start_text3")
        UmrahPhase.TAWAF -> {
            val n = (progress.tawafCompleted + 1).coerceAtMost(7)
            listOf("tawaf${n}_text1", "tawaf${n}_text2", "tawaf${n}_reading_text1", "tawaf${n}_reading_text2")
        }
        UmrahPhase.POST_TAWAF -> listOf("tawafpray_title1", "tawafpray_text1", "zamzam_title", "zamzam_text")
        UmrahPhase.SAI -> {
            val n = (progress.saiCompleted + 1).coerceAtMost(7)
            listOf("safago_text", "safa${n}_title1", "safa${n}_text1", "safa${n}_text2")
        }
        UmrahPhase.END -> listOf("end_text", "end_text1", "end_text3")
        UmrahPhase.COMPLETED -> listOf("home2_3title", "home2_3_subtitle")
    }
    return keys.mapNotNull { t[it]?.trim()?.takeIf(String::isNotEmpty) }.distinct()
}

@Composable
private fun FlowGuideCard(lines: List<String>, loading: Boolean, language: String) {
    Column(
        Modifier.fillMaxWidth().animateContentSize()
            .background(glass, RoundedCornerShape(28.dp))
            .border(.75.dp, outline, RoundedCornerShape(28.dp))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Text(label(language, "ИНСТРУКЦИЯ", "GUIDANCE", "YO‘RIQNOMA"), fontSize = 11.sp,
            letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .56f))
        if (lines.isEmpty() && loading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(26.dp))
        else if (lines.isEmpty()) Text(label(language,
            "Текст инструкции сейчас недоступен. Проверьте подключение.",
            "Guidance text is unavailable. Check your connection.",
            "Yo‘riqnoma mavjud emas. Internetni tekshiring."), color = Color.White.copy(alpha = .70f))
        else lines.forEachIndexed { index, line ->
            if (index != 0) HorizontalDivider(color = Color.White.copy(alpha = .10f))
            Text(line, fontSize = 15.sp, lineHeight = 23.sp, color = Color.White.copy(alpha = .91f))
        }
    }
}

@Composable
private fun FlowOrbit(completed: Int, label: String) {
    Box(Modifier.fillMaxWidth().height(235.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(222.dp)) {
            val mid = Offset(size.width / 2f, size.height / 2f)
            val orbitRadius = size.minDimension * .405f
            drawCircle(Color.White.copy(alpha = .07f), orbitRadius, mid, style = Stroke(width = 2f))
            for (index in 0 until 7) {
                val radians = index * (2 * PI / 7) - PI / 2
                val point = Offset(
                    mid.x + (orbitRadius * cos(radians)).toFloat(),
                    mid.y + (orbitRadius * sin(radians)).toFloat(),
                )
                drawCircle(if (index < completed) Color.White else Color.White.copy(alpha = .23f), radius = 10f, center = point)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$completed / 7", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(label, fontSize = 11.sp, letterSpacing = 2.sp, color = Color.White.copy(alpha = .52f))
        }
    }
}

@Composable
private fun FlowSwipeControl(prompt: String, onConfirm: () -> Unit) {
    var distance by remember(prompt) { mutableStateOf(0f) }
    val currentConfirm by rememberUpdatedState(onConfirm)
    val shape = RoundedCornerShape(99.dp)
    Box(
        Modifier.fillMaxWidth().height(68.dp).background(Color.White.copy(alpha = .12f), shape)
            .border(1.dp, Color.White.copy(alpha = .32f), shape)
            .pointerInput(prompt) {
                detectHorizontalDragGestures(
                    onDragStart = { distance = 0f },
                    onDragEnd = {
                        if (distance > 100f) currentConfirm()
                        distance = 0f
                    },
                    onDragCancel = { distance = 0f },
                    onHorizontalDrag = { change, delta -> change.consume(); distance += delta },
                )
            }
            .semantics { contentDescription = prompt },
        contentAlignment = Alignment.Center,
    ) {
        Text("→  $prompt", Modifier.padding(horizontal = 17.dp),
            color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FlowActionButton(title: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) Color.White else Color.White.copy(alpha = .13f),
            contentColor = if (primary) Color.Black else Color.White,
            disabledContainerColor = Color.White.copy(alpha = .12f),
            disabledContentColor = Color.White.copy(alpha = .40f),
        ),
    ) { Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun FlowRoundAction(symbol: CupertinoSymbol, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(45.dp).background(glass, CircleShape).border(1.dp, outline, CircleShape)
            .clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { CupertinoIcon(symbol, description, Modifier.size(21.dp), Color.White) }
}
