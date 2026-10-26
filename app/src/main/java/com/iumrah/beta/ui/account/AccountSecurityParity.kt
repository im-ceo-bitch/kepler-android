package com.iumrah.beta.ui.account

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.auth.GoogleSignInSupport
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.models.account.IumrahSecurityOverview
import com.iumrah.beta.models.account.IumrahSecuritySession
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.IumrahIconBadge
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


private val SecurityCare = Color(0xFF30B0C7)
private val SecurityBlue = Color(0xFF1677FF)
private val SecurityGreen = Color(0xFF34C759)
private val SecurityOrange = Color(0xFFFF9500)
private val SecurityRed = Color(0xFFFF3B30)
private val SecurityGray = Color(0xFF8E8E93)

private fun secTr(language: AppLanguage, en: String, ru: String, uz: String, cy: String): String = when (language) {
    AppLanguage.ENGLISH -> en
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}

@Composable
internal fun AccountSecurityParityContent(
    language: AppLanguage,
    accountStore: IumrahAccountStore,
    chrome: AppChromeStore,
) {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    var overview by remember { mutableStateOf<IumrahSecurityOverview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPrimaryDialog by remember { mutableStateOf(false) }
    var primaryPassword by remember { mutableStateOf("") }
    var primaryBusy by remember { mutableStateOf(false) }
    var emailDialog by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var emailChallenge by remember { mutableStateOf<String?>(null) }
    var emailCode by remember { mutableStateOf("") }
    var emailBusy by remember { mutableStateOf(false) }
    var pendingTermination by remember { mutableStateOf<IumrahSecuritySession?>(null) }
    var pendingTerminateOthers by remember { mutableStateOf(false) }
    var workingSessionID by remember { mutableStateOf<String?>(null) }
    var terminatingOthers by remember { mutableStateOf(false) }
    var linkingGoogle by remember { mutableStateOf(false) }
    var linkingApple by remember { mutableStateOf(false) }

    fun reload() {
        loading = overview == null
        scope.launch {
            runCatching { accountStore.securityOverview(language.localeTag) }
                .onSuccess { value -> overview = value; error = null }
                .onFailure { throwable -> error = securityErrorMessage(language, throwable) }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { SecurityNavigationHeader(language, chrome) }
        item { SecurityHero(overview?.iumrahID) }
        item {
            Text(
                secTr(
                    language,
                    "A new session can end only itself. Only your protected primary device can end other sessions.",
                    "Новый сеанс может завершить только себя. Остальные сеансы может завершать только Ваше защищённое основное устройство.",
                    "Yangi seans faqat o‘zini tugata oladi. Boshqa seanslarni faqat himoyalangan asosiy qurilmangiz tugata oladi.",
                    "Янги сеанс фақат ўзини тугата олади. Бошқа сеансларни фақат ҳимояланган асосий қурилмангиз тугата олади.",
                ),
                modifier = Modifier.padding(horizontal = 4.dp),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }

        overview?.let { value ->
            item {
                SecurityCard {
                    SecuritySectionTitle(
                        if (value.primaryDeviceProtected) CupertinoSymbol.ShieldCheck else CupertinoSymbol.ExclamationCircle,
                        secTr(language, "Primary device", "Основное устройство", "Asosiy qurilma", "Асосий қурилма"),
                        if (value.primaryDeviceProtected) SecurityCare else SecurityOrange,
                    )
                    Spacer(Modifier.height(14.dp))
                    when {
                        value.currentDeviceIsPrimary -> SecurityStatusRow(
                            CupertinoSymbol.Device,
                            secTr(language, "This is your primary device", "Это Ваше основное устройство", "Bu asosiy qurilmangiz", "Бу асосий қурилмангиз"),
                            secTr(language, "It can securely manage the other sessions.", "Оно может безопасно управлять остальными сеансами.", "U boshqa seanslarni xavfsiz boshqara oladi.", "У бошқа сеансларни хавфсиз бошқара олади."),
                            SecurityCare,
                        )
                        value.primaryDeviceProtected -> SecurityStatusRow(
                            CupertinoSymbol.Lock,
                            secTr(language, "Secondary session", "Дополнительный сеанс", "Qo‘shimcha seans", "Қўшимча сеанс"),
                            secTr(language, "This device can end only its own session.", "Это устройство может завершить только свой сеанс.", "Bu qurilma faqat o‘z seansini tugata oladi.", "Бу қурилма фақат ўз сеансини тугата олади."),
                            SecurityGray,
                        )
                        else -> {
                            Text(
                                secTr(language, "Confirm your current password once to make this Android phone the protected primary device.", "Один раз подтвердите текущий пароль, чтобы сделать этот Android-телефон защищённым основным устройством.", "Ushbu Android telefonni himoyalangan asosiy qurilma qilish uchun joriy parolni bir marta tasdiqlang.", "Ушбу Android телефонни ҳимояланган асосий қурилма қилиш учун жорий паролни бир марта тасдиқланг."),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            SecurityPrimaryButton(
                                title = secTr(language, "Protect this phone", "Защитить этот телефон", "Bu telefonni himoyalash", "Бу телефонни ҳимоялаш"),
                                icon = CupertinoSymbol.LockShield,
                            ) {
                                primaryPassword = ""
                                error = null
                                showPrimaryDialog = true
                            }
                        }
                    }
                }
            }

            item {
                SecurityCard {
                    SecuritySectionTitle(CupertinoSymbol.Mail, secTr(language, "Email sign-in", "Вход по почте", "Email orqali kirish", "Email орқали кириш"), SecurityBlue)
                    Spacer(Modifier.height(14.dp))
                    if (value.loginEmail != null) {
                        SecurityStatusRow(
                            CupertinoSymbol.CheckCircle,
                            value.loginEmail.email,
                            secTr(language, "Verified for sign-in and password recovery.", "Подтверждена для входа и восстановления пароля.", "Kirish va parolni tiklash uchun tasdiqlangan.", "Кириш ва паролни тиклаш учун тасдиқланган."),
                            SecurityGreen,
                        )
                    } else {
                        Text(
                            secTr(language, "Add and verify an email to sign in without remembering your iumrah ID and to recover your password.", "Добавьте и подтвердите почту, чтобы входить без запоминания iumrah ID и восстанавливать пароль.", "iumrah ID ni eslamasdan kirish va parolni tiklash uchun email qo‘shing va tasdiqlang.", "iumrah ID ни эсламасдан кириш ва паролни тиклаш учун email қўшинг ва тасдиқланг."),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    SecuritySecondaryButton(
                        title = if (value.loginEmail == null) secTr(language, "Add verified email", "Добавить подтверждённую почту", "Tasdiqlangan email qo‘shish", "Тасдиқланган email қўшиш") else secTr(language, "Change email", "Изменить почту", "Emailni o‘zgartirish", "Emailни ўзгартириш"),
                        icon = CupertinoSymbol.Mail,
                        enabled = value.currentDeviceIsPrimary,
                    ) {
                        email = value.loginEmail?.email.orEmpty()
                        emailChallenge = null
                        emailCode = ""
                        error = null
                        emailDialog = true
                    }
                    if (!value.currentDeviceIsPrimary) {
                        Spacer(Modifier.height(10.dp))
                        SecurityLockedNote(language, secTr(language, "Only the primary device can change the sign-in email.", "Почту для входа может изменить только основное устройство.", "Kirish emailini faqat asosiy qurilma o‘zgartira oladi.", "Кириш emailини фақат асосий қурилма ўзгартира олади."))
                    }
                }
            }

            item {
                SecurityProviderCard(
                    language = language,
                    title = "Sign in with Apple",
                    icon = CupertinoSymbol.Apple,
                    linked = value.apple.linked,
                    iumrahID = value.iumrahID,
                    provider = "Apple",
                    primaryDevice = value.currentDeviceIsPrimary,
                    busy = linkingApple,
                    onConnect = {
                        if (value.currentDeviceIsPrimary && !linkingApple && !linkingGoogle) {
                            linkingApple = true
                            error = secTr(
                            language,
                            "Apple Sign-In on Android needs the Apple Web Services redirect configuration. The account security flow is ready; no fake Apple login is used.",
                            "Для Apple Sign-In на Android требуется Web Services redirect Apple. Контур безопасности аккаунта готов; фиктивный вход Apple не используется.",
                            "Android’da Apple Sign-In uchun Apple Web Services redirect sozlamasi kerak. Akkaunt xavfsizligi tayyor; soxta Apple kirishi ishlatilmaydi.",
                                "Android’да Apple Sign-In учун Apple Web Services redirect созламаси керак. Аккаунт хавфсизлиги тайёр; сохта Apple кириши ишлатилмайди.",
                            )
                            linkingApple = false
                        }
                    },
                )
            }

            item {
                SecurityProviderCard(
                    language = language,
                    title = "Sign in with Google",
                    icon = CupertinoSymbol.Globe,
                    linked = value.google?.linked == true,
                    iumrahID = value.iumrahID,
                    provider = "Google",
                    primaryDevice = value.currentDeviceIsPrimary,
                    busy = linkingGoogle,
                    onConnect = {
                        if (value.currentDeviceIsPrimary && !linkingGoogle && !linkingApple) {
                            val host = activity
                            if (host == null) {
                            error = secTr(language, "Google Sign-In is unavailable in this window.", "Google Sign-In недоступен в этом окне.", "Bu oynada Google Sign-In mavjud emas.", "Бу ойнада Google Sign-In мавжуд эмас.")
                        } else {
                            linkingGoogle = true
                            scope.launch {
                                runCatching { GoogleSignInSupport.signIn(host) }
                                    .mapCatching { accountStore.linkGoogle(it) }
                                    .onSuccess { reload() }
                                    .onFailure { throwable ->
                                        if (!throwable::class.simpleName.orEmpty().contains("Cancel", ignoreCase = true)) {
                                            error = securityErrorMessage(language, throwable)
                                        }
                                    }
                                linkingGoogle = false
                            }
                        }
                        }
                    },
                )
            }

            item {
                val current = value.sessions.firstOrNull { it.isCurrent }
                val others = value.sessions.filterNot { it.isCurrent }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(secTr(language, "Devices and sessions", "Устройства и сеансы", "Qurilmalar va seanslar", "Қурилмалар ва сеанслар"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text(secTr(language, "Devices signed in to this iumrah ID", "Устройства, вошедшие в этот iumrah ID", "Ushbu iumrah ID ga kirgan qurilmalar", "Ушбу iumrah ID га кирган қурилмалар"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${value.sessions.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily, color = SecurityGray)
                    }
                    current?.let { session ->
                        SecuritySessionGroupLabel(secTr(language, "THIS DEVICE", "ЭТО УСТРОЙСТВО", "BU QURILMA", "БУ ҚУРИЛМА"))
                        SecuritySessionCard(language, session, workingSessionID == session.id, onTerminate = { pendingTermination = session })
                    }
                    if (others.isNotEmpty()) {
                        SecuritySessionGroupLabel(secTr(language, "ACTIVE SESSIONS", "АКТИВНЫЕ СЕАНСЫ", "FAOL SEANSLAR", "ФАОЛ СЕАНСЛАР"))
                        others.forEach { session ->
                            SecuritySessionCard(language, session, workingSessionID == session.id, onTerminate = { pendingTermination = session })
                        }
                        if (value.currentDeviceIsPrimary) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable(enabled = workingSessionID == null && !terminatingOthers) { pendingTerminateOthers = true },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .045f),
                            ) {
                                Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CupertinoIcon(CupertinoSymbol.HandRaised, null, Modifier.size(18.dp), SecurityRed)
                                    Spacer(Modifier.width(10.dp))
                                    Text(secTr(language, "End all other sessions", "Завершить все другие сеансы", "Boshqa barcha seanslarni tugatish", "Бошқа барча сеансларни тугатиш"), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SecurityRed)
                                    if (terminatingOthers) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = SecurityRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        error?.takeIf { it.isNotBlank() }?.let { message ->
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = SecurityRed.copy(alpha = .08f)) {
                    Text(message, Modifier.fillMaxWidth().padding(16.dp), fontSize = 12.sp, color = SecurityRed)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                CupertinoIcon(CupertinoSymbol.HandRaised, null, Modifier.size(16.dp), SecurityGray)
                Text(
                    secTr(language, "Email, Apple, Google and your eight-digit iumrah ID are secure keys to one account — never separate profiles.", "Почта, Apple, Google и восьмизначный iumrah ID являются защищёнными ключами к одному аккаунту, а не отдельными профилями.", "Email, Apple, Google va sakkiz xonali iumrah ID bitta akkauntning xavfsiz kalitlaridir — alohida profillar emas.", "Email, Apple, Google ва саккиз хонали iumrah ID битта аккаунтнинг хавфсиз калитларидир — алоҳида профиллар эмас."),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = SecurityGray,
                )
            }
        }
    }

    if (showPrimaryDialog) {
        AlertDialog(
            onDismissRequest = { if (!primaryBusy) showPrimaryDialog = false },
            icon = { CupertinoIcon(CupertinoSymbol.LockShield, null, Modifier.size(32.dp), SecurityCare) },
            title = { Text(secTr(language, "Protect this phone", "Защитить этот телефон", "Bu telefonni himoyalash", "Бу телефонни ҳимоялаш"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(secTr(language, "Enter the current password for ID ${overview?.iumrahID.orEmpty()}. It is checked on the server and is not stored on this screen.", "Введите текущий пароль от ID ${overview?.iumrahID.orEmpty()}. Он проверяется на сервере и не сохраняется на этом экране.", "${overview?.iumrahID.orEmpty()} ID uchun joriy parolni kiriting. U serverda tekshiriladi va bu ekranda saqlanmaydi.", "${overview?.iumrahID.orEmpty()} ID учун жорий паролни киритинг. У серверда текширилади ва бу экранда сақланмайди."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = primaryPassword,
                        onValueChange = { primaryPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(secTr(language, "Current password", "Текущий пароль", "Joriy parol", "Жорий парол")) },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        primaryBusy = true
                        scope.launch {
                            runCatching { accountStore.claimPrimaryDevice(primaryPassword) }
                                .onSuccess { value -> overview = value; error = null; showPrimaryDialog = false }
                                .onFailure { throwable -> error = securityErrorMessage(language, throwable) }
                            primaryBusy = false
                        }
                    },
                    enabled = primaryPassword.length >= 8 && !primaryBusy,
                ) { if (primaryBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(secTr(language, "Confirm and protect", "Подтвердить и защитить", "Tasdiqlash va himoyalash", "Тасдиқлаш ва ҳимоялаш")) }
            },
            dismissButton = { TextButton(onClick = { showPrimaryDialog = false }, enabled = !primaryBusy) { Text(secTr(language, "Close", "Закрыть", "Yopish", "Ёпиш")) } },
        )
    }

    if (emailDialog) {
        AlertDialog(
            onDismissRequest = { if (!emailBusy) emailDialog = false },
            icon = { CupertinoIcon(CupertinoSymbol.Mail, null, Modifier.size(30.dp), SecurityBlue) },
            title = { Text(secTr(language, "Email security", "Безопасность почты", "Email xavfsizligi", "Email хавфсизлиги"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (emailChallenge == null) secTr(language, "We will send a verification code to this email.", "Мы отправим код подтверждения на эту почту.", "Bu emailga tasdiqlash kodini yuboramiz.", "Бу emailга тасдиқлаш кодини юборамиз.") else secTr(language, "Enter the verification code sent to $email.", "Введите код подтверждения, отправленный на $email.", "$email manziliga yuborilgan tasdiqlash kodini kiriting.", "$email манзилига юборилган тасдиқлаш кодини киритинг."),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (emailChallenge == null) {
                        OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                    } else {
                        OutlinedTextField(value = emailCode, onValueChange = { emailCode = it.filter(Char::isDigit).take(8) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(secTr(language, "Verification code", "Код подтверждения", "Tasdiqlash kodi", "Тасдиқлаш коди")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        emailBusy = true
                        scope.launch {
                            if (emailChallenge == null) {
                                runCatching { accountStore.startEmailVerification(email.trim(), language.localeTag) }
                                    .onSuccess { emailChallenge = it.challengeID; error = null }
                                    .onFailure { error = securityErrorMessage(language, it) }
                            } else {
                                runCatching { accountStore.confirmEmailVerification(emailChallenge!!, emailCode.trim()) }
                                    .onSuccess { error = null; emailDialog = false; reload() }
                                    .onFailure { error = securityErrorMessage(language, it) }
                            }
                            emailBusy = false
                        }
                    },
                    enabled = !emailBusy && if (emailChallenge == null) email.contains('@') else emailCode.length >= 4,
                ) { if (emailBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (emailChallenge == null) secTr(language, "Send code", "Отправить код", "Kod yuborish", "Код юбориш") else secTr(language, "Verify", "Подтвердить", "Tasdiqlash", "Тасдиқлаш")) }
            },
            dismissButton = { TextButton(onClick = { emailDialog = false }, enabled = !emailBusy) { Text(secTr(language, "Close", "Закрыть", "Yopish", "Ёпиш")) } },
        )
    }

    pendingTermination?.let { session ->
        AlertDialog(
            onDismissRequest = { if (workingSessionID == null) pendingTermination = null },
            icon = { CupertinoIcon(CupertinoSymbol.HandRaised, null, Modifier.size(30.dp), SecurityRed) },
            title = { Text(if (session.isCurrent) secTr(language, "End this session?", "Завершить этот сеанс?", "Bu seans tugatilsinmi?", "Бу сеанс тугатилсинми?") else secTr(language, "End session?", "Завершить сеанс?", "Seans tugatilsinmi?", "Сеанс тугатилсинми?"), fontWeight = FontWeight.Bold) },
            text = { Text(if (session.isCurrent) secTr(language, "You will need to sign in again.", "Для продолжения потребуется войти снова.", "Qayta kirish kerak bo‘ladi.", "Қайта кириш керак бўлади.") else secTr(language, "This device will immediately lose access to your account.", "Это устройство сразу потеряет доступ к Вашему аккаунту.", "Bu qurilma akkauntga kirish huquqini darhol yo‘qotadi.", "Бу қурилма аккаунтга кириш ҳуқуқини дарҳол йўқотади."), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    workingSessionID = session.id
                    scope.launch {
                        runCatching { accountStore.terminateSecuritySession(session.id) }
                            .onSuccess { signedOut ->
                                if (signedOut) chrome.back() else reload()
                            }
                            .onFailure { error = securityErrorMessage(language, it) }
                        pendingTermination = null
                        workingSessionID = null
                    }
                }) { Text(if (session.isCurrent) secTr(language, "Sign out this device", "Выйти на этом устройстве", "Bu qurilmadan chiqish", "Бу қурилмадан чиқиш") else secTr(language, "End session", "Завершить сеанс", "Seansni tugatish", "Сеансни тугатиш"), color = SecurityRed) }
            },
            dismissButton = { TextButton(onClick = { pendingTermination = null }) { Text(secTr(language, "Cancel", "Отмена", "Bekor qilish", "Бекор қилиш")) } },
        )
    }

    if (pendingTerminateOthers) {
        AlertDialog(
            onDismissRequest = { if (!terminatingOthers) pendingTerminateOthers = false },
            icon = { CupertinoIcon(CupertinoSymbol.HandRaised, null, Modifier.size(30.dp), SecurityRed) },
            title = { Text(secTr(language, "End all other sessions?", "Завершить все другие сеансы?", "Boshqa barcha seanslar tugatilsinmi?", "Бошқа барча сеанслар тугатилсинми?"), fontWeight = FontWeight.Bold) },
            text = { Text(secTr(language, "All other devices will lose access. This device will stay signed in.", "Все другие устройства потеряют доступ. На этом устройстве вход сохранится.", "Boshqa barcha qurilmalar kirish huquqini yo‘qotadi. Bu qurilmada kirish saqlanadi.", "Бошқа барча қурилмалар кириш ҳуқуқини йўқотади. Бу қурилмада кириш сақланади."), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(
                    enabled = !terminatingOthers,
                    onClick = {
                        val targets = overview?.sessions.orEmpty().filter { !it.isCurrent && it.canTerminate }
                        if (targets.isEmpty()) {
                            pendingTerminateOthers = false
                        } else {
                            terminatingOthers = true
                            scope.launch {
                                var failure: Throwable? = null
                                for (target in targets) {
                                    runCatching { accountStore.terminateSecuritySession(target.id) }
                                        .onFailure { if (failure == null) failure = it }
                                }
                                pendingTerminateOthers = false
                                terminatingOthers = false
                                reload()
                                failure?.let { error = securityErrorMessage(language, it) }
                            }
                        }
                    },
                ) {
                    if (terminatingOthers) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = SecurityRed)
                    else Text(secTr(language, "End other sessions", "Завершить другие сеансы", "Boshqa seanslarni tugatish", "Бошқа сеансларни тугатиш"), color = SecurityRed)
                }
            },
            dismissButton = { TextButton(onClick = { pendingTerminateOthers = false }, enabled = !terminatingOthers) { Text(secTr(language, "Cancel", "Отмена", "Bekor qilish", "Бекор қилиш")) } },
        )
    }
}

@Composable
private fun SecurityNavigationHeader(language: AppLanguage, chrome: AppChromeStore) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = { chrome.back() }, shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.ChevronLeft, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface) }
        }
        Text(secTr(language, "Security", "Безопасность", "Xavfsizlik", "Хавфсизлик"), Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(38.dp))
    }
}

@Composable
private fun SecurityHero(iumrahID: String?) {
    Box(
        Modifier.fillMaxWidth().height(246.dp).clip(RoundedCornerShape(32.dp)).background(Color.Black),
    ) {
        SecuritySphere(Modifier.align(Alignment.TopEnd).size(292.dp).padding(top = 0.dp))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(alpha = .98f), Color.Black.copy(alpha = .90f), Color.Black.copy(alpha = .38f), Color.Transparent),
                ),
            ),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CupertinoIcon(CupertinoSymbol.LockShield, null, Modifier.size(24.dp), SecurityCare)
                if (!iumrahID.isNullOrBlank()) Text("ID $iumrahID", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily, color = Color.White.copy(alpha = .64f))
            }
            Spacer(Modifier.weight(1f))
            Text("Iumrah\nSecurity", fontSize = 30.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun SecuritySphere(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "security-sphere")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "security-phase")
    Canvas(modifier) {
        val radius = size.minDimension * .34f
        val center = Offset(size.width * .62f, size.height * .42f)
        val ringCount = 18
        repeat(ringCount) { ring ->
            val latitude = -PI / 2 + PI * (ring + .5) / ringCount
            val cosLat = cos(latitude).toFloat()
            val sinLat = sin(latitude).toFloat()
            val count = maxOf(8, (42 * cosLat.coerceAtLeast(.18f)).toInt())
            repeat(count) { index ->
                val longitude = 2 * PI * (index.toDouble() / count + phase * .22 + ring * .013)
                val depth = (cos(longitude) * cosLat).toFloat()
                if (depth > -.28f) {
                    val x = center.x + (sin(longitude).toFloat() * cosLat * radius)
                    val y = center.y + (sinLat * radius)
                    val hue = ((ring.toFloat() / ringCount) + phase + index.toFloat() / count * .23f) % 1f
                    val color = Color.hsv(hue * 360f, .72f, 1f).copy(alpha = (.28f + (depth + .28f) * .44f).coerceIn(.18f, .88f))
                    drawCircle(color, radius = (1.2f + (depth + 1f) * 1.15f).coerceAtLeast(.8f), center = Offset(x, y))
                }
            }
        }
    }
}

@Composable
private fun SecurityCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 3.dp, border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .075f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

@Composable
private fun SecuritySectionTitle(icon: CupertinoSymbol, title: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecurityIconBadge(icon, tint, 38.dp, 15.dp)
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecurityStatusRow(icon: CupertinoSymbol, title: String, detail: String, tint: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SecurityIconBadge(icon, tint, 42.dp, 17.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detail, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SecurityProviderCard(
    language: AppLanguage,
    title: String,
    icon: CupertinoSymbol,
    linked: Boolean,
    iumrahID: String,
    provider: String,
    primaryDevice: Boolean,
    busy: Boolean,
    onConnect: () -> Unit,
) {
    SecurityCard {
        SecuritySectionTitle(icon, title, if (provider == "Google") SecurityBlue else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(14.dp))
        if (linked) {
            SecurityStatusRow(CupertinoSymbol.CheckCircle, secTr(language, "$provider is connected", "$provider подключён", "$provider ulangan", "$provider уланган"), secTr(language, "$provider signs in to this same iumrah ID — no second account is created.", "$provider выполняет вход в этот же iumrah ID — второй аккаунт не создаётся.", "$provider aynan shu iumrah ID’ga kiradi — ikkinchi akkaunt yaratilmaydi.", "$provider айнан шу iumrah ID’га киради — иккинчи аккаунт яратилмайди."), SecurityGreen)
        } else {
            Text(secTr(language, "Connect $provider to ID $iumrahID. After that you can sign in without typing the eight-digit ID or password.", "Подключите $provider к ID $iumrahID. После этого можно входить без ввода восьмизначного ID и пароля.", "$provider’ni $iumrahID ID’ga ulang. Shundan keyin sakkiz xonali ID va parolsiz kirishingiz mumkin.", "$provider’ни $iumrahID ID’га уланг. Шундан кейин саккиз хонали ID ва паролсиз киришингиз мумкин."), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            val enabled = primaryDevice && !busy
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .055f else .035f)) {
                Row(
                    Modifier.fillMaxWidth().height(50.dp).clickable(enabled = enabled, onClick = onConnect).padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CupertinoIcon(icon, null, Modifier.size(19.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .90f else .42f))
                    Text(secTr(language, "Continue with $provider", "Продолжить с $provider", "$provider orqali davom etish", "$provider орқали давом этиш"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .90f else .42f))
                    if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                }
            }
            if (!primaryDevice) {
                Spacer(Modifier.height(10.dp))
                SecurityLockedNote(language, secTr(language, "Only the primary device can connect a new sign-in method.", "Новый способ входа может подключить только основное устройство.", "Yangi kirish usulini faqat asosiy qurilma ulashi mumkin.", "Янги кириш усулини фақат асосий қурилма улаши мумкин."))
            }
        }
    }
}

@Composable
private fun SecuritySessionCard(language: AppLanguage, session: IumrahSecuritySession, working: Boolean, onTerminate: () -> Unit) {
    SecurityCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SecurityIconBadge(CupertinoSymbol.Device, if (session.platform.contains("android", true)) SecurityGreen else SecurityBlue, 48.dp, 21.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(securitySessionDisplayName(language, session), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (session.isCurrent) Text(secTr(language, "THIS DEVICE", "ЭТО УСТРОЙСТВО", "BU QURILMA", "БУ ҚУРИЛМА"), Modifier.clip(CircleShape).background(SecurityBlue.copy(alpha = .10f)).padding(horizontal = 6.dp, vertical = 4.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SecurityBlue)
                }
                val software = securitySessionSoftwareLine(session)
                if (software.isNotBlank()) Text(software, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(securitySessionLocationAndActivity(language, session), fontSize = 12.sp, color = if (session.isCurrent) SecurityGreen else SecurityGray, maxLines = 2)
            }
            if (session.isPrimary) CupertinoIcon(CupertinoSymbol.CrownFill, null, Modifier.size(18.dp), SecurityOrange)
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
        Spacer(Modifier.height(10.dp))
        if (session.canTerminate) {
            Row(Modifier.fillMaxWidth().clickable(enabled = !working, onClick = onTerminate).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                CupertinoIcon(CupertinoSymbol.HandRaised, null, Modifier.size(17.dp), SecurityRed)
                Spacer(Modifier.width(8.dp))
                Text(if (session.isCurrent) secTr(language, "End this session", "Завершить этот сеанс", "Bu seansni tugatish", "Бу сеансни тугатиш") else secTr(language, "End session", "Завершить сеанс", "Seansni tugatish", "Сеансни тугатиш"), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SecurityRed)
                if (working) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CupertinoIcon(CupertinoSymbol.Lock, null, Modifier.size(14.dp), SecurityGray)
                Text(secTr(language, "Managed by the primary device", "Управляется основным устройством", "Asosiy qurilma orqali boshqariladi", "Асосий қурилма орқали бошқарилади"), fontSize = 11.sp, color = SecurityGray)
            }
        }
    }
}

@Composable
private fun SecurityIconBadge(icon: CupertinoSymbol, tint: Color, size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    // iOS security status rows use the solid system icon badge, not a pastel chip.
    IumrahIconBadge(icon, tint, size = size, symbolSize = iconSize, cornerRadius = size * .30f)
}

@Composable
private fun SecurityPrimaryButton(title: String, icon: CupertinoSymbol, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)) {
        CupertinoIcon(icon, null, Modifier.size(18.dp), Color.White)
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecuritySecondaryButton(title: String, icon: CupertinoSymbol, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .055f else .03f)) {
        Row(Modifier.fillMaxWidth().height(50.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CupertinoIcon(icon, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .42f))
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .42f))
        }
    }
}

@Composable
private fun SecurityLockedNote(language: AppLanguage, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        CupertinoIcon(CupertinoSymbol.Lock, null, Modifier.size(13.dp), SecurityGray)
        Text(text, fontSize = 11.sp, lineHeight = 15.sp, color = SecurityGray)
    }
}

@Composable
private fun SecuritySessionGroupLabel(text: String) {
    Text(text, Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontSize = 10.sp, letterSpacing = .8.sp, fontWeight = FontWeight.SemiBold, color = SecurityGray)
}

private fun securitySessionDisplayName(language: AppLanguage, session: IumrahSecuritySession): String {
    val name = session.deviceName.trim()
    val model = session.model.trim()
    val generic = setOf("", "iPhone", "iPad", "Unknown device", "Apple device", "Android")
    if (name !in generic) return name
    if (session.platform.contains("ios", true) && model.startsWith("iPhone")) {
        friendlyAppleModelName(model)?.let { return it }
    }
    if (session.platform.contains("android", true) && model.isNotBlank() && !model.equals("android", true)) return model
    if (model.isNotBlank()) return model
    if (session.platform.contains("android", true)) return "Android"
    if (session.platform.contains("ios", true)) return if (name == "iPad") "iPad" else "iPhone"
    return secTr(language, "Unknown device", "Неизвестное устройство", "Noma’lum qurilma", "Номаълум қурилма")
}


private fun friendlyAppleModelName(identifier: String): String? = mapOf(
    "iPhone12,1" to "iPhone 11",
    "iPhone12,3" to "iPhone 11 Pro",
    "iPhone12,5" to "iPhone 11 Pro Max",
    "iPhone12,8" to "iPhone SE (2nd generation)",
    "iPhone13,1" to "iPhone 12 mini",
    "iPhone13,2" to "iPhone 12",
    "iPhone13,3" to "iPhone 12 Pro",
    "iPhone13,4" to "iPhone 12 Pro Max",
    "iPhone14,4" to "iPhone 13 mini",
    "iPhone14,5" to "iPhone 13",
    "iPhone14,2" to "iPhone 13 Pro",
    "iPhone14,3" to "iPhone 13 Pro Max",
    "iPhone14,6" to "iPhone SE (3rd generation)",
    "iPhone14,7" to "iPhone 14",
    "iPhone14,8" to "iPhone 14 Plus",
    "iPhone15,2" to "iPhone 14 Pro",
    "iPhone15,3" to "iPhone 14 Pro Max",
    "iPhone15,4" to "iPhone 15",
    "iPhone15,5" to "iPhone 15 Plus",
    "iPhone16,1" to "iPhone 15 Pro",
    "iPhone16,2" to "iPhone 15 Pro Max",
    "iPhone17,3" to "iPhone 16",
    "iPhone17,4" to "iPhone 16 Plus",
    "iPhone17,1" to "iPhone 16 Pro",
    "iPhone17,2" to "iPhone 16 Pro Max",
    "iPhone17,5" to "iPhone 16e",
    "iPhone18,3" to "iPhone 17",
    "iPhone18,1" to "iPhone 17 Pro",
    "iPhone18,2" to "iPhone 17 Pro Max",
    "iPhone18,4" to "iPhone Air",
    "iPhone18,5" to "iPhone 17e",
)[identifier]

private fun securitySessionSoftwareLine(session: IumrahSecuritySession): String {
    val platform = when {
        session.platform.contains("android", true) -> "Android"
        session.platform.contains("ios", true) -> "iOS"
        else -> session.platform.trim()
    }
    val os = listOf(platform, session.osVersion.trim()).filter { it.isNotBlank() }.joinToString(" ")
    val appVersion = session.appVersion.trim().substringBefore(" (").trim()
    val app = appVersion.takeIf { it.isNotBlank() }?.let { "iumrah $it" }.orEmpty()
    return listOf(os, app).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun securitySessionLocationAndActivity(language: AppLanguage, session: IumrahSecuritySession): String {
    val country = localizedCountry(language, session.country)
    val location = listOf(session.city.trim(), country).filter { it.isNotBlank() }.joinToString(", ")
    val activity = if (session.isCurrent) {
        secTr(language, "online", "в сети", "onlayn", "онлайн")
    } else {
        relativeSecurityDate(language, session.lastActiveAt)
    }
    return listOf(location, activity).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun localizedCountry(language: AppLanguage, raw: String): String {
    val value = raw.trim()
    if (value.length != 2) return value
    val locale = Locale.forLanguageTag(language.localeTag)
    return Locale("", value.uppercase(Locale.ROOT)).getDisplayCountry(locale).ifBlank { value }
}

private fun relativeSecurityDate(language: AppLanguage, raw: String): String {
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return raw
    val seconds = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> secTr(language, "just now", "только что", "hozirgina", "ҳозиргина")
        seconds < 3600 -> {
            val n = seconds / 60
            secTr(language, "$n min ago", "$n мин назад", "$n daqiqa oldin", "$n дақиқа олдин")
        }
        seconds < 86400 -> {
            val n = seconds / 3600
            secTr(language, "$n h ago", "$n ч назад", "$n soat oldin", "$n соат олдин")
        }
        seconds < 604800 -> {
            val n = seconds / 86400
            secTr(language, "$n d ago", "$n дн назад", "$n kun oldin", "$n кун олдин")
        }
        else -> raw.substringBefore('T')
    }
}

private fun securityErrorMessage(language: AppLanguage, throwable: Throwable): String {
    val code = when (throwable) {
        is APIException.Server -> throwable.serverMessage
        is APIException.Status -> if (throwable.code == 401) "SESSION_REVOKED" else "HTTP_${throwable.code}"
        else -> throwable.message.orEmpty()
    }
    return when (code) {
        "PRIMARY_DEVICE_REQUIRED" -> secTr(language, "Only the protected primary device can do this.", "Это действие доступно только на защищённом основном устройстве.", "Bu amal faqat himoyalangan asosiy qurilmada mavjud.", "Бу амал фақат ҳимояланган асосий қурилмада мавжуд.")
        "PRIMARY_DEVICE_ALREADY_PROTECTED" -> secTr(language, "Another primary device is already protecting this account.", "Этот аккаунт уже защищён другим основным устройством.", "Bu akkaunt boshqa asosiy qurilma bilan himoyalangan.", "Бу аккаунт бошқа асосий қурилма билан ҳимояланган.")
        "GOOGLE_ID_CONNECTED_TO_ANOTHER_ACCOUNT" -> secTr(language, "This Google account is already connected to another iumrah ID.", "Этот Google-аккаунт уже подключён к другому iumrah ID.", "Bu Google akkaunti boshqa iumrah ID’ga ulangan.", "Бу Google аккаунти бошқа iumrah ID’га уланган.")
        "GOOGLE_ID_ALREADY_CONNECTED" -> secTr(language, "A different Google account is already connected to this iumrah account.", "К этому аккаунту iumrah уже подключён другой Google-аккаунт.", "Bu iumrah akkauntiga boshqa Google akkaunti ulangan.", "Бу iumrah аккаунтига бошқа Google аккаунти уланган.")
        "GOOGLE_EMAIL_CONNECTED_TO_ANOTHER_ACCOUNT" -> secTr(language, "The email verified by Google already belongs to another iumrah account.", "Подтверждённая Google почта уже принадлежит другому аккаунту iumrah.", "Google tasdiqlagan email boshqa iumrah akkauntiga tegishli.", "Google тасдиқлаган email бошқа iumrah аккаунтига тегишли.")
        "GOOGLE_TOKEN_INVALID", "GOOGLE_TOKEN_REPLAYED" -> secTr(language, "Google authorization expired. Please try again.", "Подтверждение Google устарело. Попробуйте ещё раз.", "Google tasdig‘i eskirgan. Qayta urinib ko‘ring.", "Google тасдиғи эскирган. Қайта уриниб кўринг.")
        "EMAIL_INVALID" -> secTr(language, "Enter a valid email address.", "Введите корректный адрес электронной почты.", "To‘g‘ri email manzilini kiriting.", "Тўғри email манзилини киритинг.")
        "EMAIL_ALREADY_CONNECTED" -> secTr(language, "This email is already connected to another iumrah account.", "Эта почта уже подключена к другому аккаунту iumrah.", "Bu email boshqa iumrah akkauntiga ulangan.", "Бу email бошқа iumrah аккаунтига уланган.")
        "EMAIL_RATE_LIMITED" -> secTr(language, "Too many email requests. Please try again later.", "Слишком много запросов. Повторите отправку позже.", "Email so‘rovlari ko‘p. Keyinroq qayta urinib ko‘ring.", "Email сўровлари кўп. Кейинроқ қайта уриниб кўринг.")
        "VERIFICATION_CODE_INVALID" -> secTr(language, "The code is incorrect or expired. Request a new code.", "Код неверный или устарел. Запросите новый код.", "Kod noto‘g‘ri yoki muddati tugagan. Yangi kod so‘rang.", "Код нотўғри ёки муддати тугаган. Янги код сўранг.")
        "INVALID_CREDENTIALS" -> secTr(language, "The iumrah ID or password is incorrect.", "Неверный iumrah ID или пароль.", "iumrah ID yoki parol noto‘g‘ri.", "iumrah ID ёки парол нотўғри.")
        "ACCOUNT_TEMPORARILY_LOCKED" -> secTr(language, "Too many attempts. Try again in 15 minutes.", "Слишком много попыток. Повторите через 15 минут.", "Urinishlar ko‘p. 15 daqiqadan keyin qayta urinib ko‘ring.", "Уринишлар кўп. 15 дақиқадан кейин қайта уриниб кўринг.")
        "SESSION_REVOKED" -> secTr(language, "This session has ended. Sign in again to continue.", "Этот сеанс завершён. Войдите снова, чтобы продолжить.", "Bu seans tugagan. Davom etish uchun qayta kiring.", "Бу сеанс тугаган. Давом этиш учун қайта киринг.")
        else -> throwable.message?.takeIf { it.isNotBlank() } ?: secTr(language, "Security information is temporarily unavailable.", "Информация безопасности временно недоступна.", "Xavfsizlik ma’lumotlari vaqtincha mavjud emas.", "Хавфсизлик маълумотлари вақтинча мавжуд эмас.")
    }
}

