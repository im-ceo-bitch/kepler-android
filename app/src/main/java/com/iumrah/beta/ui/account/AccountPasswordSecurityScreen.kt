package com.iumrah.beta.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import kotlinx.coroutines.launch

private fun pwdTr(l: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (l) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
    else -> en
}

/** Android Compose equivalent of iOS IumrahPasswordSecurityView. */
@Composable
fun AccountPasswordSecurityScreen(language: AppLanguage, accountStore: IumrahAccountStore, chrome: AppChromeStore) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = current.length >= 8 && next.length >= 8 && next == repeat && current != next

    ApNavPage(pwdTr(language, "Пароль", "Password", "Parol", "Парол"), chrome) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(36.dp), color = Color.Black) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                    CupertinoIcon(CupertinoSymbol.Key, null, Modifier.size(28.dp), Color.White)
                }
            }
            Text(pwdTr(language, "Безопасность пароля", "Password security", "Parol xavfsizligi", "Парол хавфсизлиги"), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(pwdTr(language, "Используйте уникальный пароль для аккаунта iumrah.", "Use a password you do not reuse on other services.", "iumrah uchun boshqa joyda ishlatmaydigan parolni tanlang.", "iumrah учун бошқа жойда ишлатмайдиган паролни танланг."), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        if (success) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(pwdTr(language, "Пароль изменён", "Password changed", "Parol o‘zgartirildi", "Парол ўзгартирилди"), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(pwdTr(language, "Для безопасности остальные сеансы завершены. Это устройство остаётся в аккаунте.", "Other sessions were signed out for security. This device stays signed in.", "Boshqa seanslar xavfsizlik uchun tugatildi. Ushbu qurilmada kirish saqlanadi.", "Бошқа сеанслар хавфсизлик учун тугатилди. Ушбу қурилмада кириш сақланади."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { chrome.back() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp)) { Text(pwdTr(language, "Готово", "Done", "Tayyor", "Тайёр")) }
                }
            }
        } else {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PasswordSecurityInput(pwdTr(language, "Текущий пароль", "Current password", "Joriy parol", "Жорий парол"), current) { current = it }
                    PasswordSecurityInput(pwdTr(language, "Новый пароль", "New password", "Yangi parol", "Янги парол"), next) { next = it }
                    PasswordSecurityInput(pwdTr(language, "Повторите новый пароль", "Repeat new password", "Yangi parolni takrorlang", "Янги паролни такрорланг"), repeat) { repeat = it }
                    Text(pwdTr(language, "Не менее 8 символов · пароли должны совпадать и отличаться от текущего.", "At least 8 characters · passwords must match and differ from your current password.", "Kamida 8 belgi · parollar mos va avvalgisidan farqli bo‘lishi kerak.", "Камида 8 белги · пароллар мос ва аввалгисидан фарқли бўлиши керак."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(pwdTr(language, "После изменения пароля остальные сеансы будут завершены.", "Other sessions will be signed out after changing the password.", "Parol yangilangach, boshqa seanslar tugatiladi.", "Парол янгилангандан кейин бошқа сеанслар тугатилади."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Button(onClick = {
                if (!valid || busy) return@Button
                busy = true; error = null
                scope.launch {
                    runCatching { accountStore.changePassword(current, next) }
                        .onSuccess { success = it.ok; current = ""; next = ""; repeat = "" }
                        .onFailure { error = it.message }
                    busy = false
                }
            }, enabled = valid && !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(28.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp) else Text(pwdTr(language, "Изменить пароль", "Change password", "Parolni o‘zgartirish", "Паролни ўзгартириш"))
            }
            TextButton(onClick = chrome::openAccountPasswordRecovery, modifier = Modifier.fillMaxWidth()) {
                Text(pwdTr(language, "Забыли пароль?", "Forgot password?", "Parolni unutdingizmi?", "Паролни унутдингизми?"))
            }
        }
    }
}

@Composable
private fun PasswordSecurityInput(title: String, value: String, update: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = update, label = { Text(title) }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true, shape = RoundedCornerShape(17.dp))
}
