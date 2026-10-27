package com.iumrah.beta.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.core.settings.AppSettingsStore
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.media.LoopingRawVideo
import kotlinx.coroutines.launch

private val UserDataRed = Color(0xFFFF3B30)
private val UserDataGreen = Color(0xFF34C759)

@Composable
fun IumrahUserDataScreen(
    language: AppLanguage,
    accountStore: IumrahAccountStore,
    bookingStore: BookingStore,
    settingsStore: AppSettingsStore,
    chrome: AppChromeStore,
) {
    val accountState by accountStore.state.collectAsState()
    val bookingState by bookingStore.state.collectAsState()
    val settings by settingsStore.state.collectAsState()
    val profile = accountState.account
    val scope = rememberCoroutineScope()

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var telegram by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var nationality by remember { mutableStateOf("") }
    var emergencyName by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var emergencyRelation by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.iumrahID, settings.isLoaded) {
        firstName = profile?.firstName?.takeIf { it.isNotBlank() } ?: settings.firstName
        lastName = profile?.lastName?.takeIf { it.isNotBlank() } ?: settings.lastName
        phone = formatPhoneInput(profile?.phone?.takeIf { it.isNotBlank() } ?: settings.phone)
        email = profile?.email?.takeIf { it.isNotBlank() } ?: settings.email
        telegram = profile?.telegram?.takeIf { it.isNotBlank() } ?: settings.telegram
        whatsapp = formatPhoneInput(profile?.whatsapp?.takeIf { it.isNotBlank() } ?: settings.whatsapp)
        dateOfBirth = displayDate(settings.dateOfBirth)
        gender = settings.gender
        nationality = settings.nationality
        emergencyName = settings.emergencyName
        emergencyPhone = formatPhoneInput(settings.emergencyPhone)
        emergencyRelation = settings.emergencyRelation
    }

    val kycTrip = bookingState.sessions
        .filterNot { it.effectiveStatus.uppercase() in setOf("COMPLETED", "CANCELLED") }
        .minByOrNull { it.booking.input.startDate }
        ?: bookingState.sessions.firstOrNull()

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
    ) {
        UserDataNavigationBar(language, chrome)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 12.dp, bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(292.dp).clip(RoundedCornerShape(32.dp)).background(Color.Black)
                    .border(.8.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(32.dp)),
            ) {
                LoopingRawVideo("iumrah_security_identity", Modifier.fillMaxSize(), muted = true)
            }

            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CupertinoIcon(CupertinoSymbol.LockShield, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                    Text("iumrah Security", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                }
                Text(userDataText(language, "Ваши данные для бронирования", "Your booking profile", "Bron uchun ma’lumotlaringiz", "Брон учун маълумотларингиз"), fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
                Text(
                    userDataText(
                        language,
                        "Храните личные и контактные данные в одном профиле. iumrah использует их для будущих авиабилетов, отелей и поездок, чтобы Вам не приходилось вводить одно и то же заново.",
                        "Keep your personal and contact details in one profile. iumrah reuses them for future flights, hotels and trips, so you do not have to enter the same information again.",
                        "Shaxsiy va aloqa ma’lumotlaringizni bitta profilda saqlang. iumrah ularni keyingi aviachiptalar, mehmonxonalar va safarlarda qayta ishlatadi.",
                        "Шахсий ва алоқа маълумотларингизни битта профилда сақланг. iumrah уларни кейинги авиачипталар, меҳмонхоналар ва сафарларда қайта ишлатади.",
                    ),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                )
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = UserDataRed.copy(alpha = .075f),
                border = androidx.compose.foundation.BorderStroke(1.dp, UserDataRed.copy(alpha = .20f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(UserDataRed.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                        CupertinoIcon(CupertinoSymbol.ShieldCheck, null, Modifier.size(20.dp), UserDataRed)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(userDataText(language, "Важно", "Important", "Muhim", "Муҳим"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = UserDataRed)
                        Text(
                            userDataText(language, "Введите имя, фамилию и личные данные точно так, как они указаны в паспорте. Эти значения будут использоваться при оформлении услуг поездки.", "Enter your first name, last name and personal details exactly as they appear in your passport. These values are reused when iumrah prepares travel services.", "Ism, familiya va shaxsiy ma’lumotlarni pasportdagidek aniq kiriting. Bu ma’lumotlar safar xizmatlarini rasmiylashtirishda ishlatiladi.", "Исм, фамилия ва шахсий маълумотларни паспортдагидек аниқ киритинг. Бу маълумотлар сафар хизматларини расмийлаштиришда ишлатилади."),
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            }

            UserDataCard {
                UserDataSectionHeader(language, CupertinoSymbol.IdentityCard, "Паспортный профиль", "Passport profile", "Pasport profili", "Паспорт профили", "Владелец аккаунта", "Account owner", "Akkaunt egasi", "Аккаунт эгаси")
                UserDataField(userDataText(language, "Имя", "First name", "Ism", "Исм"), userDataText(language, "Как в паспорте", "Exactly as in passport", "Pasportdagidek", "Паспортдагидек"), firstName) { firstName = it }
                UserDataField(userDataText(language, "Фамилия", "Last name", "Familiya", "Фамилия"), userDataText(language, "Как в паспорте", "Exactly as in passport", "Pasportdagidek", "Паспортдагидек"), lastName) { lastName = it }
            }

            UserDataCard {
                UserDataSectionHeader(language, CupertinoSymbol.PersonCircle, "Личные данные", "Personal details", "Shaxsiy ma’lumotlar", "Шахсий маълумотлар", "Сохраняются в Вашем профиле", "Saved to your owner profile", "Profilingizda saqlanadi", "Профилингизда сақланади")
                UserDataField(userDataText(language, "Дата рождения", "Date of birth", "Tug‘ilgan sana", "Туғилган сана"), "DD.MM.YYYY", dateOfBirth, KeyboardType.Number) { dateOfBirth = formatDisplayDateInput(it) }
                Text(userDataText(language, "Пол", "Gender", "Jins", "Жинс"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .055f)).padding(2.dp)) {
                    listOf("male" to userDataText(language, "Мужской", "Male", "Erkak", "Эркак"), "female" to userDataText(language, "Женский", "Female", "Ayol", "Аёл")).forEach { (value, label) ->
                        val active = gender == value
                        Surface(onClick = { gender = value }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(12.dp), color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent) {
                            Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 13.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium) }
                        }
                    }
                }
                UserDataField(userDataText(language, "Гражданство", "Nationality", "Fuqarolik", "Фуқаролик"), userDataText(language, "Например, Uzbekistan", "For example, Uzbekistan", "Masalan, Uzbekistan", "Масалан, Uzbekistan"), nationality) { nationality = it }
            }

            UserDataCard {
                UserDataSectionHeader(language, CupertinoSymbol.Phone, "Контакты и экстренная связь", "Contacts & emergency", "Aloqa va favqulodda kontakt", "Алоқа ва фавқулодда контакт", "Используются для бронирований и поддержки", "Used for bookings and support", "Bron va yordam uchun ishlatiladi", "Брон ва ёрдам учун ишлатилади")
                UserDataField(userDataText(language, "Номер телефона", "Phone", "Telefon", "Телефон"), "+998 90 123 45 67", phone, KeyboardType.Phone) { phone = formatPhoneInput(it) }
                UserDataField("Email", "name@example.com", email, KeyboardType.Email) { email = it }
                UserDataField("Telegram", "@username", telegram) { telegram = it }
                UserDataField("WhatsApp", "+998 90 123 45 67", whatsapp, KeyboardType.Phone) { whatsapp = formatPhoneInput(it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = .07f))
                Text(userDataText(language, "Экстренный контакт", "Emergency contact", "Favqulodda kontakt", "Фавқулодда контакт"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                UserDataField(userDataText(language, "Имя контакта", "Contact name", "Kontakt ismi", "Контакт исми"), "", emergencyName) { emergencyName = it }
                UserDataField(userDataText(language, "Экстренный номер телефона", "Emergency phone", "Favqulodda telefon", "Фавқулодда телефон"), "+998 90 123 45 67", emergencyPhone, KeyboardType.Phone) { emergencyPhone = formatPhoneInput(it) }
                UserDataField(userDataText(language, "Кем приходится", "Relationship", "Qarindoshlik", "Қариндошлик"), userDataText(language, "Например, отец", "For example, father", "Masalan, ota", "Масалан, ота"), emergencyRelation) { emergencyRelation = it }
            }

            UserDataCard {
                UserDataSectionHeader(language, CupertinoSymbol.LockShield, "iumrah Security", "iumrah Security", "iumrah Security", "iumrah Security", "Статус подтверждения личности", "Identity verification status", "Shaxsni tasdiqlash holati", "Шахсни тасдиқлаш ҳолати")
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .045f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(UserDataGreen.copy(alpha = .10f)), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(18.dp), UserDataGreen) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (profile != null) userDataText(language, "Профиль аккаунта активен", "Account profile active", "Akkaunt profili faol", "Аккаунт профили фаол") else userDataText(language, "Локальный профиль", "Local profile", "Mahalliy profil", "Маҳаллий профил"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(profile?.iumrahID?.let { "iumrah ID · $it" } ?: userDataText(language, "Войдите в аккаунт для синхронизации", "Sign in to sync", "Sinxronlash uchun kiring", "Синхронлаш учун киринг"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                    }
                }
                if (kycTrip != null) {
                    IumrahPressable(
                        onClick = { chrome.openAccountKyc(kycTrip.id) },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        cornerRadius = 18.dp,
                        background = MaterialTheme.colorScheme.onBackground.copy(alpha = .06f),
                        shadowElevation = 0.dp,
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            CupertinoIcon(CupertinoSymbol.Passport, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .70f))
                            Spacer(Modifier.width(10.dp))
                            Text(userDataText(language, "Открыть KYC поездки", "Open trip KYC", "Safar KYC sini ochish", "Сафар KYC сини очиш"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .35f))
                        }
                    }
                } else {
                    Text(userDataText(language, "Фото паспорта и финальная KYC-проверка станут доступны, когда бронирование перейдёт к этапу данных паломника.", "Passport photo and final KYC verification will become available when a booking reaches the pilgrim-details stage.", "Pasport rasmi va yakuniy KYC tekshiruvi bron ziyoratchi ma’lumotlari bosqichiga o‘tganda ochiladi.", "Паспорт расми ва якуний KYC текшируви брон зиёратчи маълумотлари босқичига ўтганда очилади."), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                }
            }

            message?.let {
                Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (messageSuccess) UserDataGreen else UserDataRed, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Button(
                onClick = {
                    if (firstName.isBlank() || lastName.isBlank()) return@Button
                    saving = true; message = null
                    val cleanPhone = normalizedPhone(phone); val cleanEmail = email.trim(); val cleanTelegram = telegram.trim(); val cleanWhatsapp = normalizedPhone(whatsapp)
                    val cleanEmergencyPhone = normalizedPhone(emergencyPhone)
                    settingsStore.updateOwnerDetails(firstName.trim(), lastName.trim(), cleanPhone, cleanEmail, cleanTelegram, cleanWhatsapp, isoDate(dateOfBirth).orEmpty(), gender, nationality.trim(), emergencyName.trim(), cleanEmergencyPhone, emergencyRelation.trim())
                    scope.launch {
                        runCatching {
                            if (profile != null) accountStore.updateProfile(firstName.trim(), lastName.trim(), cleanPhone, cleanEmail, cleanTelegram, cleanWhatsapp)
                        }.onSuccess {
                            messageSuccess = true
                            message = userDataText(language, "Ваши данные сохранены.", "Your details are saved.", "Ma’lumotlaringiz saqlandi.", "Маълумотларингиз сақланди.")
                        }.onFailure {
                            messageSuccess = false
                            message = userDataText(language, "Не удалось синхронизировать профиль. Локальные данные сохранены.", "The profile could not be synced. Your local details remain saved.", "Profilni sinxronlab bo‘lmadi. Mahalliy ma’lumotlar saqlandi.", "Профилни синхронлаб бўлмади. Маҳаллий маълумотлар сақланди.")
                        }
                        saving = false
                    }
                },
                enabled = firstName.isNotBlank() && lastName.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background),
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.background)
                else CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(18.dp), MaterialTheme.colorScheme.background)
                Spacer(Modifier.width(9.dp))
                Text(userDataText(language, "Сохранить Ваши данные", "Save your details", "Ma’lumotlarni saqlash", "Маълумотларни сақлаш"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun UserDataNavigationBar(language: AppLanguage, chrome: AppChromeStore) {
    Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp)) {
        IumrahPressable(onClick = chrome::back, modifier = Modifier.align(Alignment.CenterStart).size(40.dp), cornerRadius = 20.dp, background = MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), shadowElevation = 0.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.ChevronLeft, "Back", Modifier.size(17.dp), MaterialTheme.colorScheme.onBackground) }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(userDataText(language, "Ваши данные", "Your details", "Ma’lumotlaringiz", "Маълумотларингиз"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("iumrah Security · Profile", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
        }
    }
}

@Composable
private fun UserDataCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 2.dp, border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .055f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun UserDataSectionHeader(language: AppLanguage, icon: CupertinoSymbol, ru: String, en: String, uz: String, cy: String, subRu: String, subEn: String, subUz: String, subCy: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .055f)), contentAlignment = Alignment.Center) { CupertinoIcon(icon, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .68f)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(userDataText(language, ru, en, uz, cy), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(userDataText(language, subRu, subEn, subUz, subCy), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
        }
    }
}

@Composable
private fun UserDataField(label: String, placeholder: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            singleLine = true,
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder, fontSize = 14.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .045f),
                unfocusedContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .045f),
                focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .18f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .05f),
            ),
        )
    }
}

private fun formatDisplayDateInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(8)
    return buildString {
        digits.forEachIndexed { index, c ->
            append(c)
            if ((index == 1 || index == 3) && index != digits.lastIndex) append('.')
        }
    }
}

private fun displayDate(iso: String): String {
    val parts = iso.trim().split('-')
    return if (parts.size == 3 && parts[0].length == 4) "${parts[2]}.${parts[1]}.${parts[0]}" else iso
}

private fun isoDate(display: String): String? {
    val parts = display.trim().split('.')
    if (parts.size != 3) return null
    val day = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val year = parts[2].toIntOrNull() ?: return null
    return runCatching { java.time.LocalDate.of(year, month, day).toString() }.getOrNull()
}

private fun normalizedPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.isBlank()) "" else "+$digits"
}

private fun formatPhoneInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(15)
    return if (digits.isBlank()) "" else "+$digits"
}

private fun userDataText(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}

@Composable
fun IumrahUserDataEntryCardAndroid(language: AppLanguage, profileName: String, onClick: () -> Unit) {
    IumrahPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        background = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .055f)), contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.IdentityCard, null, Modifier.size(22.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .72f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(userDataText(language, "Ваши данные", "Your details", "Ma’lumotlaringiz", "Маълумотларингиз"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    profileName.takeIf { it.isNotBlank() } ?: userDataText(language, "Данные для будущих бронирований", "Details for future bookings", "Keyingi bronlar uchun ma’lumotlar", "Кейинги бронлар учун маълумотлар"),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                    maxLines = 2,
                )
            }
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .34f))
        }
    }
}
