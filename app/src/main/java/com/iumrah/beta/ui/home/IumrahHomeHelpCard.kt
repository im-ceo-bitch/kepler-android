package com.iumrah.beta.ui.home

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.iumrah.beta.core.design.IumrahDesign
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.iumrahAnimatedSearchBorder
import kotlinx.coroutines.delay

/** Home's helpContext = home.default: a header-triggered overlay, not a list item. */
@Composable
fun IumrahHomeHelpCard(language: AppLanguage, onDismiss: () -> Unit) {
    val copy = when (language) {
        AppLanguage.RUSSIAN -> Triple("Что делать здесь?", "Начните с маршрута", "Раскройте поиск: укажите аэропорт вылета, маршрут, даты и пассажиров. Нажмите «Найти авиабилеты», чтобы перейти к выбору перелётов.")
        AppLanguage.UZBEK -> Triple("Bu yerda nima qilish kerak?", "Safar yo‘nalishidan boshlang", "Qidiruvni ochib, jo‘nash aeroporti, yo‘nalish, sanalar va yo‘lovchilarni belgilang. Parvozlarni tanlash uchun «Reyslarni topish» tugmasini bosing.")
        AppLanguage.UZBEK_CYRILLIC -> Triple("Бу ерда нима қилиш керак?", "Сафар ёъналишидан бошланг", "Қидирувни очиб, жўнаш аеропорти, ёъналиш, саналар ва ёъловчиларни белгиланг. Парвозларни танлаш учун «Рейсларни топиш» тугмасини босинг.")
        else -> Triple("What can I do here?", "Start with your route", "Expand search and choose your departure airport, route, dates and travelers. Tap “Find flights” to choose your flights.")
    }
    val context = LocalContext.current
    val motion = remember(context) { runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true) }
    val screenReader = remember(context) { (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true }
    var visible by remember(copy.third) { mutableStateOf(if (motion && !screenReader) "" else copy.third) }
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(copy.third, motion, screenReader) {
        if (motion && !screenReader) {
            for (count in 3..copy.third.length + 2 step 3) {
                visible = copy.third.take(count)
                delay(18)
            }
        }
        visible = copy.third
        if (!screenReader) { delay(2500); dismiss() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelLimit = maxHeight * .75f
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .30f)).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss))
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 52.dp, start = 18.dp, end = 18.dp)
                .fillMaxWidth().heightIn(max = panelLimit)
                .iumrahAnimatedSearchBorder(RoundedCornerShape(IumrahDesign.HeroRadius), motion, guidance = true)
                .verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(copy.first, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(copy.second, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = copy.third }) {
                        Text(copy.third, fontSize = 17.sp, lineHeight = 23.sp, modifier = Modifier.alpha(0f))
                        Text(visible, fontSize = 17.sp, lineHeight = 23.sp)
                    }
                }
            }
        }
    }
}
