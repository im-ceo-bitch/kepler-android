package com.iumrah.beta.ui.home

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.models.account.IumrahFriendGift
import com.iumrah.beta.models.account.IumrahFriendsDashboard
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IumrahGiftCardsScreen(
    language: AppLanguage,
    accountStore: IumrahAccountStore,
    chrome: AppChromeStore,
) {
    val accountState by accountStore.state.collectAsState()
    var dashboard by remember { mutableStateOf<IumrahFriendsDashboard?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showShareSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (!accountState.isAuthenticated) { dashboard = null; loading = false; return }
        loading = true
        scope.launch {
            runCatching { accountStore.friendsDashboard() }
                .onSuccess { value -> dashboard = value; selectedId = value.gifts.firstOrNull { it.isAvailable }?.id; error = null }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(accountState.account?.iumrahID) { load() }
    val available = dashboard?.gifts?.filter { it.isAvailable }.orEmpty()
    val selected = available.firstOrNull { it.id == selectedId } ?: available.firstOrNull()
    Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        GiftBackgroundGlow(selected?.position ?: available.firstOrNull()?.position ?: 1)
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                IumrahPressable(onClick = chrome::back, modifier = Modifier.size(40.dp), cornerRadius = 20.dp, background = Color.White.copy(alpha = .10f), shadowElevation = 0.dp) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.ChevronLeft, "Back", Modifier.size(17.dp), Color.White) }
                }
                Spacer(Modifier.weight(1f))
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (loading && dashboard == null) {
                    Spacer(Modifier.height(120.dp)); CircularProgressIndicator(color = Color.White)
                } else {
                    GiftWelcomeCarousel(available, dashboard == null, language)
                    Spacer(Modifier.height(25.dp))
                    Text(giftText(language, "Добро пожаловать в", "Welcome to", "Xush kelibsiz", "Хуш келибсиз"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .58f))
                    Spacer(Modifier.height(7.dp))
                    Text("iumrah Gift Card", fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.9).sp, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        giftText(language, "Подарите близкому Gift Card на $100 для первого подходящего бронирования Умры.", "Share a $100 Gift Card with someone close to you for their first eligible Umrah booking.", "Yaqin insoningizga birinchi mos Umrah broni uchun $100 Gift Card ulashing.", "Яқин инсонингизга биринчи мос Умра брони учун $100 Gift Card улашинг."),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = .62f),
                        modifier = Modifier.padding(horizontal = 34.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                    if (accountState.isAuthenticated) {
                        Button(
                            onClick = { if (available.isNotEmpty()) { selectedId = available.firstOrNull()?.id; showShareSheet = true } },
                            enabled = available.isNotEmpty() && !(loading && dashboard == null),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black, disabledContainerColor = Color.White.copy(alpha = .48f), disabledContentColor = Color.Black.copy(alpha = .55f)),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                        ) {
                            CupertinoIcon(CupertinoSymbol.Share, null, Modifier.size(16.dp), if (available.isNotEmpty()) Color.Black else Color.Black.copy(alpha = .55f))
                            Spacer(Modifier.width(8.dp))
                            Text(giftText(language, "Поделиться Gift Card", "Share Gift Card", "Gift Card ulashish", "Gift Card улашиш"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = { chrome.navigate(AppTab.ACCOUNT) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                        ) { Text(giftText(language, "Открыть аккаунт iumrah", "Open iumrah account", "iumrah akkauntini ochish", "iumrah аккаунтини очиш"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    error?.let { Text(it, color = Color(0xFFFFCC00), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)) }
                    Spacer(Modifier.height(18.dp))
                    if (accountState.isAuthenticated && dashboard != null) {
                        Text(giftText(language, "Доступно: ${available.size} из 3", "${available.size} of 3 available", "3 tadan ${available.size} tasi mavjud", "3 тадан ${available.size} таси мавжуд"), color = Color.White.copy(alpha = .55f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if ((dashboard?.availableCreditUsd ?: 0.0) > 0) {
                            Text("iumrah Balance: $${(dashboard?.availableCreditUsd ?: 0.0).toInt()}", color = Color.White.copy(alpha = .42f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    if (showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false; load() },
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            GiftShareSheet(
                language = language,
                gifts = available,
                selectedId = selectedId,
                onSelect = { selectedId = it },
                sender = accountState.account?.displayName.orEmpty(),
                onClose = { showShareSheet = false; load() },
            )
        }
    }
}

@Composable
private fun GiftBackgroundGlow(position: Int) {
    val image = giftImage(position)
    Image(
        painter = painterResource(image),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().height(360.dp),
        contentScale = ContentScale.Crop,
        alpha = .19f,
    )
    Box(Modifier.fillMaxWidth().height(420.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .55f), Color.Black))))
}

@Composable
private fun GiftWelcomeCarousel(gifts: List<IumrahFriendGift>, loadingSkeleton: Boolean, language: AppLanguage) {
    val positions = if (loadingSkeleton) listOf(1, 2, 3) else gifts.map { it.position }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (positions.isEmpty()) {
            Box(Modifier.width(238.dp).height(350.dp).clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(34.dp), Color.White)
                    Text(giftText(language, "Все отправлены", "All shared", "Hammasi ulashildi", "Ҳаммаси улашилди"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(giftText(language, "Сейчас нет доступных Gift Cards", "No Gift Cards available right now", "Hozir mavjud Gift Card yo‘q", "Ҳозир мавжуд Gift Card йўқ"), color = Color.White.copy(alpha = .56f), fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        } else positions.forEach { position ->
            GiftWelcomeArtwork(position, language, Modifier.width(238.dp).height(350.dp))
        }
    }
}

@Composable
private fun GiftWelcomeArtwork(position: Int, language: AppLanguage, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).border(.7.dp, Color.White.copy(alpha = .15f), RoundedCornerShape(26.dp))) {
        Image(painterResource(giftImage(position)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .58f)))))
        Column(Modifier.fillMaxSize().padding(17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) { Text("iumrah", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("$100", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Text("Gift Card", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
            Text(giftText(language, "Для Умры", "For Umrah", "Umrah uchun", "Умра учун"), color = Color.White.copy(alpha = .78f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GiftShareSheet(
    language: AppLanguage,
    gifts: List<IumrahFriendGift>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    sender: String,
    onClose: () -> Unit,
) {
    val selected = gifts.firstOrNull { it.id == selectedId } ?: gifts.firstOrNull()
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(giftText(language, "Выберите Gift Card", "Choose a Gift Card", "Gift Card tanlang", "Gift Card танланг"), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp, textAlign = TextAlign.Center)
                Text(giftText(language, "Выберите одну карту и отправьте её через системное меню «Поделиться».", "Choose one card, then send it with the system Share menu.", "Bitta kartani tanlang va tizimdagi ulashish menyusi orqali yuboring.", "Битта картани танланг ва тизимдаги улашиш менюси орқали юборинг."), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
            }
            IumrahPressable(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).size(38.dp), cornerRadius = 19.dp, background = MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), shadowElevation = 0.dp) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground) }
            }
        }
        if (gifts.isEmpty()) {
            Column(Modifier.padding(vertical = 56.dp, horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(38.dp), MaterialTheme.colorScheme.onBackground)
                Text(giftText(language, "Нет доступных Gift Cards", "No Gift Cards available", "Mavjud Gift Card yo‘q", "Мавжуд Gift Card йўқ"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(giftText(language, "Карты, которые больше недоступны, исчезают из этого списка.", "Cards that are no longer available are removed from this list.", "Endi mavjud bo‘lmagan kartalar bu ro‘yxatdan yo‘qoladi.", "Энди мавжуд бўлмаган карталар бу рўйхатдан йўқолади."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f), textAlign = TextAlign.Center)
            }
        } else {
            GiftPassCarousel(gifts, selected?.id, onSelect, language)
            if (selected != null) GiftSelectedActionsLight(language, selected, sender)
        }
    }
}

@Composable
private fun GiftPassCarousel(gifts: List<IumrahFriendGift>, selectedId: String?, onSelect: (String) -> Unit, language: AppLanguage) {
    val cards = if (gifts.isEmpty()) listOf<IumrahFriendGift?>(null) else gifts.map { it }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 64.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        cards.forEach { gift ->
            if (gift == null) {
                Box(Modifier.width(238.dp).height(350.dp).clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(34.dp), Color.White)
                        Text(giftText(language, "Все отправлены", "All shared", "Hammasi ulashildi", "Ҳаммаси улашилди"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                val selected = selectedId == gift.id || (selectedId == null && gifts.firstOrNull()?.id == gift.id)
                IumrahPressable(onClick = { onSelect(gift.id) }, modifier = Modifier.width(238.dp), cornerRadius = 27.dp, background = Color.Transparent, shadowElevation = 0.dp) {
                    GiftCardArtwork(gift, language, Modifier.fillMaxWidth().height(350.dp).then(if (selected) Modifier.border(2.2.dp, Color.White.copy(alpha = .92f), RoundedCornerShape(27.dp)) else Modifier))
                }
            }
        }
    }
}

@Composable
private fun GiftCardArtwork(gift: IumrahFriendGift, language: AppLanguage, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(27.dp)).border(.7.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(27.dp))) {
        Image(painterResource(giftImage(gift.position)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .04f), Color.Transparent, Color.Black.copy(alpha = .68f)))))
        Column(Modifier.fillMaxSize().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                Text("iumrah", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("$100", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text("Gift Card", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.65).sp)
            Text(giftText(language, "$100 на Умру", "$100 toward Umrah", "Umrah uchun $100", "Умра учун $100"), color = Color.White.copy(alpha = .82f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("#${gift.position}", color = Color.White.copy(alpha = .60f), fontSize = 10.sp, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun GiftSelectedActionsLight(language: AppLanguage, gift: IumrahFriendGift, sender: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .055f), RoundedCornerShape(20.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(giftText(language, "Код Gift Card", "Gift code", "Gift Card kodi", "Gift Card коди"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), fontSize = 10.sp)
                Text(gift.code, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily)
            }
            IumrahPressable(onClick = { clipboard.setText(AnnotatedString(gift.code)) }, modifier = Modifier.size(38.dp), cornerRadius = 19.dp, background = MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), shadowElevation = 0.dp) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Copy, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground) }
            }
        }
        Button(
            onClick = {
                val name = sender.trim().ifBlank { "iumrah" }
                val text = giftText(language,
                    "🎁 $name отправил(а) Вам iumrah Gift Card на $100 для первого подходящего бронирования Умры. Откройте iumrah и примените код ${gift.code}. https://iumrah.app",
                    "🎁 $name sent you an iumrah Gift Card worth $100 toward your first eligible Umrah booking. Open iumrah and use code ${gift.code}. https://iumrah.app",
                    "🎁 $name Sizga birinchi mos Umrah broniga $100 iumrah Gift Card yubordi. iumrah ni oching va ${gift.code} kodini kiriting. https://iumrah.app",
                    "🎁 $name Сизга биринчи мос Умра бронига $100 iumrah Gift Card юборди. iumrah ни очинг ва ${gift.code} кодини киритинг. https://iumrah.app")
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
                context.startActivity(Intent.createChooser(intent, "iumrah Gift Card"))
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(19.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background),
        ) {
            CupertinoIcon(CupertinoSymbol.Share, null, Modifier.size(17.dp), MaterialTheme.colorScheme.background)
            Spacer(Modifier.width(8.dp))
            Text(giftText(language, "Поделиться Gift Card", "Share Gift Card", "Gift Card ulashish", "Gift Card улашиш"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun giftImage(position: Int): Int = when (((position - 1) % 3 + 3) % 3) {
    0 -> R.drawable.gift_card_together
    1 -> R.drawable.gift_card_kaaba
    else -> R.drawable.gift_card_journey
}
private fun giftText(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
