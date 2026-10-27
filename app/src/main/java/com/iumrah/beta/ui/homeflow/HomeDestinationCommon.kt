package com.iumrah.beta.ui.homeflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.IumrahBackButton

internal fun tr(language: AppLanguage, ru: String, en: String, uz: String, cyrl: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cyrl
}

@Composable
internal fun InternalNavBar(title: String, chrome: AppChromeStore, dark: Boolean = false) {
    val fg = if (dark) Color.White else MaterialTheme.colorScheme.onBackground
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IumrahBackButton(chrome::back)
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(44.dp))
    }
}

@Composable
internal fun IosCard(
    modifier: Modifier = Modifier,
    radius: Int = 28,
    background: Color = MaterialTheme.colorScheme.surface,
    borderAlpha: Float = .055f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(background, shape)
            .border(.8.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = borderAlpha), shape),
        content = content,
    )
}
