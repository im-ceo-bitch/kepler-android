package com.iumrah.beta.ui.chat

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.iumrah.beta.core.design.IumrahFontFamily
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.media.AndroidImageCodec
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.chat.ChatService
import com.iumrah.beta.data.chat.IumrahPublicProfile
import com.iumrah.beta.models.booking.ChatMessage
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CareBlue = Color(0xFF007AFF)
private val CareBlueDark = Color(0xFF0A84FF)
private val CareGreen = Color(0xFF34C759)
private val CareRed = Color(0xFFFF3B30)

private enum class CareChatWallpaper(val key: String) {
    NONE("none"), PHOTO("photo"), DAWN("dawn"), SKY("sky"), WATER("water"), AURORA("aurora"), SAND("sand"), MAKKAH("makkah");

    val isVisual: Boolean get() = this != NONE
    fun title(language: AppLanguage): String = when (this) {
        NONE -> tr(language, "None", "Без фона", "Fonsiz", "Фонсиз")
        PHOTO -> tr(language, "Photo", "Фото", "Foto", "Фото")
        DAWN -> tr(language, "Color", "Цвет", "Rang", "Ранг")
        SKY -> tr(language, "Sky", "Небо", "Osmon", "Осмон")
        WATER -> tr(language, "Water", "Вода", "Suv", "Сув")
        AURORA -> tr(language, "Aurora", "Аврора", "Avrora", "Аврора")
        SAND -> tr(language, "Sand", "Песок", "Qum", "Қум")
        MAKKAH -> tr(language, "Makkah", "Мекка", "Makka", "Макка")
    }
}

@Stable
private class CareChatAppearanceController(
    context: Context,
    private val bookingID: String,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("iumrah_care_chat_v2", Context.MODE_PRIVATE)

    var wallpaper by mutableStateOf(
        CareChatWallpaper.entries.firstOrNull { it.key == prefs.getString("wallpaper.$bookingID", null) }
            ?: CareChatWallpaper.NONE
    )
        private set
    var customPhotoUri by mutableStateOf(prefs.getString("photo.$bookingID", null))
        private set
    var soundsEnabled by mutableStateOf(prefs.getBoolean("sounds", true))
        private set
    var hapticsEnabled by mutableStateOf(prefs.getBoolean("haptics", true))
        private set
    var founderConnected by mutableStateOf(prefs.getBoolean("founder.$bookingID", false))
        private set

    fun select(value: CareChatWallpaper) {
        if (value == CareChatWallpaper.PHOTO && customPhotoUri.isNullOrBlank()) return
        wallpaper = value
        prefs.edit().putString("wallpaper.$bookingID", value.key).apply()
    }

    fun setCustomPhoto(uri: Uri) {
        customPhotoUri = uri.toString()
        wallpaper = CareChatWallpaper.PHOTO
        prefs.edit().putString("photo.$bookingID", uri.toString()).putString("wallpaper.$bookingID", CareChatWallpaper.PHOTO.key).apply()
    }

    fun setSounds(value: Boolean) {
        soundsEnabled = value
        prefs.edit().putBoolean("sounds", value).apply()
    }

    fun setHaptics(value: Boolean) {
        hapticsEnabled = value
        prefs.edit().putBoolean("haptics", value).apply()
    }

    fun connectFounder() {
        founderConnected = true
        prefs.edit().putBoolean("founder.$bookingID", true).apply()
    }
}

@Composable
private fun rememberCareAppearance(bookingID: String): CareChatAppearanceController {
    val context = LocalContext.current
    return remember(bookingID) { CareChatAppearanceController(context, bookingID) }
}

@Composable
fun BookingChatScreen(
    bookingID: String,
    language: AppLanguage,
    bookingStore: BookingStore,
    chatService: ChatService,
    chrome: AppChromeStore,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val appearance = rememberCareAppearance(bookingID)
    val session = bookingStore.booking(bookingID)
    val dark = MaterialTheme.colorScheme.background.luminanceCompat() < .45f
    val page = if (dark) Color(0xFF000000) else Color(0xFFF7F7F8)
    val primary = if (appearance.wallpaper.isVisual) Color.White else MaterialTheme.colorScheme.onBackground
    val secondary = if (appearance.wallpaper.isVisual) Color.White.copy(alpha = .68f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .56f)
    val accent = if (dark) CareBlueDark else CareBlue

    var messages by remember(bookingID) { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var care by remember { mutableStateOf<IumrahPublicProfile?>(null) }
    var text by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }
    var isSendingPhoto by remember { mutableStateOf(false) }
    var pendingText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var failedDraft by remember { mutableStateOf<String?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var fullImagePath by remember { mutableStateOf<String?>(null) }
    var unseenCount by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    var composerFocused by remember { mutableStateOf(false) }

    val atBottom by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            total == 0 || ((listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 2)
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || session == null || isSendingPhoto || isSending) return@rememberLauncherForActivityResult
        isSendingPhoto = true
        error = null
        scope.launch {
            runCatching {
                val data = withContext(Dispatchers.IO) { AndroidImageCodec.jpeg(context.contentResolver, uri, 1600, 84) }
                chatService.sendPhoto(data, bookingID, bookingStore.headersFor(session))
            }.onSuccess { message ->
                messages = (messages + message).distinctBy { it.id }.sortedBy { it.createdAt }
                if (appearance.hapticsEnabled) IumrahHaptics.success(view)
                if (appearance.soundsEnabled) playCareTone(success = true)
                isSendingPhoto = false
            }.onFailure {
                error = it.message ?: L10n.text("error_chat_load", language)
                isSendingPhoto = false
                if (appearance.hapticsEnabled) IumrahHaptics.error(view)
            }
        }
    }

    LaunchedEffect(bookingID) {
        care = runCatching { chatService.loadCareProfile() }.getOrNull()
        var first = true
        while (true) {
            if (session != null) {
                runCatching { chatService.loadChat(bookingID, bookingStore.headersFor(session)) }
                    .onSuccess { fresh ->
                        val oldIDs = messages.mapTo(hashSetOf()) { it.id }
                        val newIncoming = fresh.count { !oldIDs.contains(it.id) && !isMine(it) }
                        if (!first && !atBottom && newIncoming > 0) unseenCount += newIncoming
                        messages = fresh.sortedBy { it.createdAt }
                        runCatching { chatService.markRead(bookingID, bookingStore.headersFor(session)) }
                        error = null
                        isLoading = false
                    }
                    .onFailure {
                        if (first) error = it.message ?: L10n.text("error_chat_load", language)
                        isLoading = false
                    }
            } else {
                isLoading = false
            }
            if (first && messages.isNotEmpty()) {
                first = false
                delay(80)
                listState.scrollToItem(messages.lastIndex)
            } else {
                first = false
            }
            delay(6_000)
        }
    }

    LaunchedEffect(messages.size, pendingText) {
        if (atBottom && messages.isNotEmpty()) {
            delay(40)
            listState.animateScrollToItem(messages.lastIndex)
            unseenCount = 0
        }
    }

    if (showProfile) {
        CareContactInfoScreen(
            bookingID = bookingID,
            language = language,
            profile = care,
            bookingNumber = session?.displayBookingNumber,
            appearance = appearance,
            onBack = { showProfile = false },
        )
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(page),
    ) {
        CareConversationBackground(
            wallpaper = appearance.wallpaper,
            customPhotoUri = appearance.customPhotoUri,
            modifier = Modifier.fillMaxSize(),
        )

        Column(Modifier.fillMaxSize()) {
            CareChatHeader(
                language = language,
                appearance = appearance,
                primary = primary,
                onBack = chrome::back,
                onProfile = { if (appearance.hapticsEnabled) IumrahHaptics.selection(view); showProfile = true },
            )

            Box(Modifier.weight(1f)) {
                if (isLoading && messages.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(top = 88.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = primary)
                        Spacer(Modifier.height(14.dp))
                        Text(L10n.text("chat_load", language), color = secondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                } else if (messages.isEmpty() && pendingText == null) {
                    CareEmptyState(language, primary, secondary)
                } else {
                    val presentations = remember(messages) { buildPresentations(messages, language) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 108.dp),
                    ) {
                        itemsIndexed(messages, key = { _, item -> item.id }) { index, message ->
                            val meta = presentations[index]
                            if (meta.showDateSeparator) {
                                CareDateSeparator(meta.dateText, secondary, appearance.wallpaper.isVisual)
                            }
                            CareChatMessageRow(
                                message = message,
                                meta = meta,
                                language = language,
                                wallpaperActive = appearance.wallpaper.isVisual,
                                accent = accent,
                                bookingID = bookingID,
                                bookingStore = bookingStore,
                                chatService = chatService,
                                onImage = { fullImagePath = it },
                            )
                            Spacer(Modifier.height(if (meta.groupEnd) 8.dp else 2.dp))
                        }
                        pendingText?.let { body ->
                            item("pending") {
                                CarePendingBubble(body, accent)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                if (!atBottom && (messages.isNotEmpty() || pendingText != null)) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 14.dp)) {
                        IumrahPressable(
                            onClick = {
                                scope.launch {
                                    if (appearance.hapticsEnabled) IumrahHaptics.selection(view)
                                    unseenCount = 0
                                    if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            cornerRadius = 99.dp,
                            background = if (appearance.wallpaper.isVisual) Color.White.copy(alpha = .18f) else MaterialTheme.colorScheme.surface,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CupertinoIcon(CupertinoSymbol.ChevronDown, null, Modifier.size(17.dp), primary)
                            }
                        }
                        if (unseenCount > 0) {
                            Text(
                                text = unseenCount.coerceAtMost(99).toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .graphicsLayer { translationX = 7.dp.toPx(); translationY = (-6).dp.toPx() }
                                    .background(accent, CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = if (composerFocused) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedVisibility(error != null) {
                CareErrorBar(
                    message = error.orEmpty(),
                    language = language,
                    primary = primary,
                    onRetry = {
                        error = null
                        failedDraft?.takeIf { it.isNotBlank() }?.let { text = it }
                        focusRequester.requestFocus()
                    },
                )
            }

            CareComposer(
                language = language,
                value = text,
                onValueChange = { text = it.take(3000) },
                focused = composerFocused,
                onFocused = { composerFocused = it },
                focusRequester = focusRequester,
                wallpaperActive = appearance.wallpaper.isVisual,
                primary = primary,
                accent = accent,
                busy = isSending || isSendingPhoto,
                sendingPhoto = isSendingPhoto,
                onPhoto = { photoLauncher.launch("image/*") },
                onSend = {
                    val body = text.trim()
                    val current = session
                    if (body.isBlank() || current == null || isSending || isSendingPhoto) return@CareComposer
                    text = ""
                    pendingText = body
                    failedDraft = body
                    isSending = true
                    error = null
                    if (appearance.hapticsEnabled) IumrahHaptics.soft(view)
                    if (appearance.soundsEnabled) playCareTone(success = true)
                    scope.launch {
                        runCatching { chatService.send(body, bookingID, bookingStore.headersFor(current)) }
                            .onSuccess { message ->
                                messages = (messages + message).distinctBy { it.id }.sortedBy { it.createdAt }
                                pendingText = null
                                failedDraft = null
                                isSending = false
                                if (appearance.hapticsEnabled) IumrahHaptics.success(view)
                            }
                            .onFailure { cause ->
                                pendingText = null
                                text = body
                                isSending = false
                                error = cause.message ?: L10n.text("error_chat_load", language)
                                if (appearance.hapticsEnabled) IumrahHaptics.error(view)
                                if (appearance.soundsEnabled) playCareTone(success = false)
                            }
                    }
                },
            )
        }
    }

    fullImagePath?.let { path ->
        CareFullscreenAttachment(
            path = path,
            bookingID = bookingID,
            bookingStore = bookingStore,
            chatService = chatService,
            onDismiss = { fullImagePath = null },
        )
    }
}

@Composable
private fun CareChatHeader(
    language: AppLanguage,
    appearance: CareChatAppearanceController,
    primary: Color,
    onBack: () -> Unit,
    onProfile: () -> Unit,
) {
    val glass = if (appearance.wallpaper.isVisual) Color.Black.copy(alpha = .18f) else MaterialTheme.colorScheme.background.copy(alpha = .96f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(glass)
            .statusBarsPadding()
            .height(48.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IumrahPressable(
            onClick = onBack,
            modifier = Modifier.size(40.dp),
            cornerRadius = 99.dp,
            background = Color.Transparent,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.ChevronLeft, "Back", Modifier.size(20.dp), primary)
            }
        }

        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onProfile).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CareAvatar(30.dp)
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("iumrah Care", color = primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(8.dp), primary)
                }
                Text(tr(language, "Support", "Поддержка", "Yordam", "Ёрдам"), color = primary.copy(alpha = .62f), fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
            }
        }

        IumrahPressable(
            onClick = onProfile,
            modifier = Modifier.size(40.dp),
            cornerRadius = 99.dp,
            background = Color.Transparent,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.InfoCircle, "Care information", Modifier.size(18.dp), primary)
            }
        }
    }
}

@Composable
private fun CareEmptyState(language: AppLanguage, primary: Color, secondary: Color) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 78.dp, start = 18.dp, end = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        CareAvatar(92.dp, shadow = true)
        Spacer(Modifier.height(16.dp))
        Text(
            L10n.text("chat_empty_title", language),
            color = primary,
            fontSize = 28.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.35).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            L10n.text("chat_empty_body", language),
            color = secondary,
            fontSize = 17.sp,
            lineHeight = 23.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 26.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Color.White.copy(alpha = .14f))
                .border(.7.dp, primary.copy(alpha = .08f), RoundedCornerShape(99.dp))
                .padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CupertinoIcon(CupertinoSymbol.HeartFill, null, Modifier.size(12.dp), primary.copy(alpha = .82f))
            Text(
                tr(language, "We are here throughout your trip", "Мы рядом на протяжении всей поездки", "Safaringiz davomida yoningizdamiz", "Сафарингиз давомида ёнингиздамиз"),
                color = primary.copy(alpha = .82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private data class ChatPresentation(
    val isMine: Boolean,
    val groupStart: Boolean,
    val groupEnd: Boolean,
    val showDelivery: Boolean,
    val showDateSeparator: Boolean,
    val dateText: String,
    val timeText: String,
)

private fun buildPresentations(messages: List<ChatMessage>, language: AppLanguage): List<ChatPresentation> = messages.mapIndexed { index, message ->
    val mine = isMine(message)
    val current = parseInstant(message.createdAt)
    val previous = messages.getOrNull(index - 1)
    val next = messages.getOrNull(index + 1)
    val previousDate = previous?.let { parseInstant(it.createdAt) }
    val nextDate = next?.let { parseInstant(it.createdAt) }
    val showDate = index == 0 || dayOf(current) != dayOf(previousDate)
    val nextShowsDate = next != null && dayOf(nextDate) != dayOf(current)
    val start = previous == null || isMine(previous) != mine || secondsBetween(previousDate, current) > 120 || showDate
    val end = next == null || isMine(next) != mine || secondsBetween(current, nextDate) > 120 || nextShowsDate
    val lastMine = mine && messages.drop(index + 1).none(::isMine)
    ChatPresentation(
        isMine = mine,
        groupStart = start,
        groupEnd = end,
        showDelivery = lastMine,
        showDateSeparator = showDate,
        dateText = dateLabel(current, language),
        timeText = timeLabel(current),
    )
}

@Composable
private fun CareDateSeparator(label: String, color: Color, wallpaperActive: Boolean) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = color,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = if (wallpaperActive) Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(Color.Black.copy(alpha = .14f))
                .border(.6.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(99.dp))
                .padding(horizontal = 11.dp, vertical = 6.dp)
            else Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CareChatMessageRow(
    message: ChatMessage,
    meta: ChatPresentation,
    language: AppLanguage,
    wallpaperActive: Boolean,
    accent: Color,
    bookingID: String,
    bookingStore: BookingStore,
    chatService: ChatService,
    onImage: (String) -> Unit,
) {
    val incomingBackground = if (wallpaperActive) Color.White.copy(alpha = .92f) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (meta.isMine) Color.White else if (wallpaperActive) Color.Black else MaterialTheme.colorScheme.onSurface
    val topMine = if (meta.groupStart) 19.5.dp else 6.dp
    val topIncoming = if (meta.groupStart) 19.5.dp else 6.dp
    val shape = if (meta.isMine) {
        RoundedCornerShape(topStart = 19.5.dp, topEnd = topMine, bottomStart = 19.5.dp, bottomEnd = if (meta.groupEnd) 6.dp else 19.5.dp)
    } else {
        RoundedCornerShape(topStart = topIncoming, topEnd = 19.5.dp, bottomStart = if (meta.groupEnd) 6.dp else 19.5.dp, bottomEnd = 19.5.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (meta.groupStart) 4.dp else 0.dp),
        horizontalAlignment = if (meta.isMine) Alignment.End else Alignment.Start,
    ) {
        val bubbleColor = if (meta.isMine) accent.copy(alpha = if (wallpaperActive) .96f else 1f) else incomingBackground
        Row(verticalAlignment = Alignment.Bottom) {
            if (!meta.isMine && meta.groupEnd) CareBubbleTail(color = bubbleColor, mine = false)

            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(shape)
                    .background(bubbleColor)
                    .then(if (!meta.isMine) Modifier.border(.55.dp, if (wallpaperActive) Color.White.copy(alpha = .16f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .045f), shape) else Modifier)
                    .padding(
                        start = if (meta.groupEnd && !meta.isMine) 16.dp else 12.dp,
                        end = if (meta.groupEnd && meta.isMine) 16.dp else 12.dp,
                        top = if (message.messageType == "image" && message.body.isBlank()) 4.dp else 8.dp,
                        bottom = if (message.messageType == "image" && message.body.isBlank()) 4.dp else 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val attachment = message.attachmentURL?.takeIf { it.isNotBlank() }
                if (message.messageType == "image" && attachment != null) {
                    AuthenticatedChatImage(
                        path = attachment,
                        bookingID = bookingID,
                        bookingStore = bookingStore,
                        chatService = chatService,
                        modifier = Modifier.widthIn(max = 258.dp).clip(RoundedCornerShape(14.dp)).clickable { onImage(attachment) },
                    )
                }
                if (message.body.isNotBlank()) {
                    Text(message.body, color = textColor, fontSize = 17.sp, lineHeight = 22.sp)
                }
            }

            if (meta.isMine && meta.groupEnd) CareBubbleTail(color = bubbleColor, mine = true)
        }

        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (meta.isMine && meta.showDelivery && meta.groupEnd) {
                Text(
                    if (message.readByStaff == true) tr(language, "Read", "Прочитано", "O‘qildi", "Ўқилди") else tr(language, "Delivered", "Доставлено", "Yetkazildi", "Етказилди"),
                    color = if (wallpaperActive) Color.White.copy(alpha = .82f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .50f),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                meta.timeText,
                color = if (wallpaperActive) Color.White.copy(alpha = .82f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .50f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = IumrahFontFamily,
            )
        }
    }
}

@Composable
private fun CareBubbleTail(color: Color, mine: Boolean) {
    Canvas(Modifier.width(7.dp).height(14.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            if (mine) {
                moveTo(0f, 0f)
                quadraticBezierTo(size.width * .18f, size.height * .72f, size.width, size.height)
                quadraticBezierTo(size.width * .42f, size.height * .96f, 0f, size.height * .72f)
            } else {
                moveTo(size.width, 0f)
                quadraticBezierTo(size.width * .82f, size.height * .72f, 0f, size.height)
                quadraticBezierTo(size.width * .58f, size.height * .96f, size.width, size.height * .72f)
            }
            close()
        }
        drawPath(path, color)
    }
}

@Composable
private fun AuthenticatedChatImage(
    path: String,
    bookingID: String,
    bookingStore: BookingStore,
    chatService: ChatService,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
) {
    val session = bookingStore.booking(bookingID)
    var image by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path, session) {
        if (session == null) return@LaunchedEffect
        runCatching {
            withContext(Dispatchers.IO) {
                chatService.loadAttachment(path, bookingStore.headersFor(session)).let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
        }.onSuccess { image = it }.onFailure { failed = true }
    }
    val resolvedModifier = if (fullScreen) {
        modifier.fillMaxSize()
    } else if (image != null) {
        modifier.aspectRatio((image!!.width.toFloat() / image!!.height.toFloat()).coerceIn(.42f, 2.2f))
    } else {
        modifier.width(226.dp).height(156.dp)
    }
    Box(resolvedModifier.background(Color.Black.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
        when {
            image != null -> Image(image!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            failed -> CupertinoIcon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(22.dp), Color.Gray)
            else -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun CarePendingBubble(body: String, accent: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(19.5.dp, 19.5.dp, 6.dp, 19.5.dp)).background(accent.copy(alpha = .98f)).padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(body, color = Color.White, fontSize = 16.5.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(timeLabel(Instant.now()), color = Color.White.copy(alpha = .72f), fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.4.dp, color = Color.White.copy(alpha = .76f))
            }
        }
    }
}

@Composable
private fun CareComposer(
    language: AppLanguage,
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    onFocused: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    wallpaperActive: Boolean,
    primary: Color,
    accent: Color,
    busy: Boolean,
    sendingPhoto: Boolean,
    onPhoto: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        IumrahPressable(
            onClick = onPhoto,
            modifier = Modifier.size(44.dp),
            cornerRadius = 99.dp,
            background = if (wallpaperActive) Color.White.copy(alpha = .10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f),
            enabled = !busy,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (sendingPhoto) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.6.dp, color = primary)
                else CupertinoIcon(CupertinoSymbol.Plus, "Add photo", Modifier.size(19.dp), primary)
            }
        }

        val surface = if (wallpaperActive) {
            Color.White.copy(alpha = if (focused) .10f else .045f)
        } else if (focused) {
            accent.copy(alpha = .07f)
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = .02f)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp, max = 86.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(surface)
                .border(.65.dp, if (wallpaperActive) Color.White.copy(alpha = .18f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .08f), RoundedCornerShape(20.dp))
                .padding(start = 13.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { onFocused(it.isFocused) }.padding(vertical = 5.dp),
                textStyle = TextStyle(color = primary, fontSize = 16.5.sp, lineHeight = 21.sp),
                maxLines = 3,
                decorationBox = { inner ->
                    Box {
                        if (value.isBlank()) Text(L10n.text("chat_placeholder", language), color = primary.copy(alpha = .46f), fontSize = 16.5.sp)
                        inner()
                    }
                },
            )

            val canSend = value.trim().isNotEmpty() && !busy
            if (canSend || busy) {
                IumrahPressable(
                    onClick = onSend,
                    modifier = Modifier.size(30.dp),
                    cornerRadius = 99.dp,
                    background = accent,
                    enabled = canSend,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (busy && !sendingPhoto) CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 1.4.dp, color = Color.White)
                        else CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(14.dp).graphicsLayer { rotationZ = -45f }, Color.White)
                    }
                }
            } else {
                CupertinoIcon(CupertinoSymbol.SignalWave, null, Modifier.size(30.dp).padding(7.dp), primary.copy(alpha = .66f))
            }
        }
    }
}

@Composable
private fun CareErrorBar(message: String, language: AppLanguage, primary: Color, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = .96f)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CupertinoIcon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(19.dp), CareRed)
        Text(message, color = primary, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        Text(
            L10n.text("chat_retry", language),
            color = primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onRetry).padding(4.dp),
        )
    }
}

@Composable
private fun CareContactInfoScreen(
    bookingID: String,
    language: AppLanguage,
    profile: IumrahPublicProfile?,
    bookingNumber: String?,
    appearance: CareChatAppearanceController,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var section by remember { mutableStateOf(0) }
    val dark = MaterialTheme.colorScheme.background.luminanceCompat() < .45f
    val primary = if (appearance.wallpaper.isVisual) Color.White else MaterialTheme.colorScheme.onBackground
    val secondary = if (appearance.wallpaper.isVisual) Color.White.copy(alpha = .68f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .56f)

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            appearance.setCustomPhoto(uri)
            if (appearance.hapticsEnabled) IumrahHaptics.selection(view)
        }
    }

    Box(Modifier.fillMaxSize()) {
        CareConversationBackground(appearance.wallpaper, appearance.customPhotoUri, Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IumrahPressable(onClick = onBack, modifier = Modifier.size(40.dp), cornerRadius = 99.dp, background = Color.Transparent) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.ChevronLeft, "Back", Modifier.size(20.dp), primary) }
                }
                Text("iumrah Care", color = primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text(
                    tr(language, "Done", "Готово", "Tayyor", "Тайёр"),
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 34.dp),
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CareAvatar(92.dp, shadow = true)
                        Spacer(Modifier.height(12.dp))
                        Text("iumrah Care", color = primary, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.55).sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr(language, "Support for every stage of your journey", "Поддержка на всех этапах вашей поездки", "Safaringizning barcha bosqichlarida yordam", "Сафарингизнинг барча босқичларида ёрдам"),
                            color = secondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                            CareProfileAction(tr(language, "Call", "Позвонить", "Qo‘ng‘iroq", "Қўнғироқ"), CupertinoSymbol.Phone, primary, true) {
                                openExternal(context, Uri.parse("tel:+998508898845"), Intent.ACTION_DIAL)
                            }
                            CareProfileAction("Telegram", CupertinoSymbol.Send, primary, !profile?.telegram.isNullOrBlank()) {
                                val raw = profile?.telegram.orEmpty().trim().removePrefix("@")
                                if (raw.isNotBlank()) openExternal(context, Uri.parse("https://t.me/$raw"))
                            }
                            CareProfileAction("WhatsApp", CupertinoSymbol.Message, primary, !profile?.whatsapp.isNullOrBlank()) {
                                val raw = profile?.whatsapp.orEmpty().filter(Char::isDigit)
                                if (raw.isNotBlank()) openExternal(context, Uri.parse("https://wa.me/$raw"))
                            }
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    CareSegmentedControl(
                        selected = section,
                        left = tr(language, "Info", "Сведения", "Ma’lumot", "Маълумот"),
                        right = tr(language, "Background", "Фон", "Fon", "Фон"),
                        primary = primary,
                        visual = appearance.wallpaper.isVisual,
                        onSelect = { section = it; if (appearance.hapticsEnabled) IumrahHaptics.selection(view) },
                    )
                    Spacer(Modifier.height(18.dp))
                }

                item {
                    AnimatedContent(
                        targetState = section,
                        transitionSpec = {
                            if (targetState > initialState) (slideInHorizontally { it / 8 } + fadeIn()).togetherWith(slideOutHorizontally { -it / 8 } + fadeOut())
                            else (slideInHorizontally { -it / 8 } + fadeIn()).togetherWith(slideOutHorizontally { it / 8 } + fadeOut())
                        },
                        label = "care-profile-section",
                    ) { target ->
                        if (target == 0) {
                            CareInfoSection(language, bookingNumber, appearance, primary, secondary, view)
                        } else {
                            CareBackgroundSection(language, appearance, primary, { photoPicker.launch(it) }, view)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CareProfileAction(title: String, icon: CupertinoSymbol, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IumrahPressable(
            onClick = onClick,
            modifier = Modifier.size(30.dp),
            cornerRadius = 99.dp,
            background = Color.White.copy(alpha = .11f),
            enabled = enabled,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(icon, null, Modifier.size(18.dp), if (enabled) color else color.copy(alpha = .42f)) }
        }
        Text(title, color = if (enabled) color else color.copy(alpha = .42f), fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun CareSegmentedControl(selected: Int, left: String, right: String, primary: Color, visual: Boolean, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .width(258.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (visual) Color.White.copy(alpha = .12f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .06f))
            .padding(2.dp),
    ) {
        listOf(left, right).forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected == index) if (visual) Color.White.copy(alpha = .18f) else MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = primary, fontSize = 13.sp, fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CareInfoSection(
    language: AppLanguage,
    bookingNumber: String?,
    appearance: CareChatAppearanceController,
    primary: Color,
    secondary: Color,
    view: android.view.View,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val founderShape = RoundedCornerShape(22.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(founderShape)
                .background(Color.White.copy(alpha = if (appearance.wallpaper.isVisual) .11f else .68f))
                .border(.7.dp, primary.copy(alpha = .08f), founderShape)
                .clickable(enabled = !appearance.founderConnected) {
                    if (appearance.hapticsEnabled) IumrahHaptics.selection(view)
                    appearance.connectFounder()
                }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(if (appearance.founderConnected) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.PlusPerson, null, Modifier.size(19.dp), if (appearance.founderConnected) CareGreen else primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (appearance.founderConnected) tr(language, "Abdulaziz is connected", "Абдулазиз подключён", "Abdulaziz ulandi", "Абдулазиз уланди") else tr(language, "Connect Abdulaziz", "Подключить Абдулазиза", "Abdulazizni ulash", "Абдулазизни улаш"),
                    color = primary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (appearance.founderConnected) tr(language, "We connected your chat with the founder’s chat as well. He can now see your conversation too.", "Мы соединили ваш чат одновременно с чатом основателя, и он тоже видит вашу переписку.", "Chatingizni asoschining chatiga ham uladik. Endi u ham yozishmalaringizni ko‘radi.", "Чатингизни асосчининг чатига ҳам уладик. Энди у ҳам ёзишмаларингизни кўради.") else tr(language, "The founder will personally review your trip once more.", "Основатель сам ещё раз проверит вашу поездку.", "Asoschi safaringizni yana bir bor shaxsan tekshiradi.", "Асосчи сафарингизни яна бир бор шахсан текширади."),
                    color = secondary,
                    fontSize = 13.2.sp,
                    lineHeight = 18.sp,
                )
            }
            if (!appearance.founderConnected) CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), secondary)
        }

        val glassShape = RoundedCornerShape(29.dp)
        Column(
            Modifier.fillMaxWidth().clip(glassShape).background(Color.White.copy(alpha = if (appearance.wallpaper.isVisual) .10f else .66f)).border(.7.dp, primary.copy(alpha = .07f), glassShape).padding(horizontal = 14.dp),
        ) {
            CareSettingToggle(tr(language, "Chat sounds", "Звуки чата", "Chat tovushlari", "Чат товушлари"), CupertinoSymbol.Speaker, appearance.soundsEnabled, primary) { appearance.setSounds(it) }
            HorizontalDivider(color = primary.copy(alpha = .09f), modifier = Modifier.padding(start = 52.dp))
            CareSettingToggle(tr(language, "Haptics", "Виброотклик", "Haptika", "Ҳаптика"), CupertinoSymbol.HandRaised, appearance.hapticsEnabled, primary) { appearance.setHaptics(it) }
        }

        if (!bookingNumber.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().clip(glassShape).background(Color.White.copy(alpha = if (appearance.wallpaper.isVisual) .10f else .66f)).border(.7.dp, primary.copy(alpha = .07f), glassShape).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    CupertinoIcon(CupertinoSymbol.SuitcaseFill, null, Modifier.size(15.dp), primary)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tr(language, "Booking", "Бронирование", "Bron", "Брон"), color = secondary, fontSize = 12.sp)
                    Text(bookingNumber, color = primary, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily)
                }
            }
        }
    }
}

@Composable
private fun CareSettingToggle(title: String, icon: CupertinoSymbol, checked: Boolean, primary: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
            CupertinoIcon(icon, null, Modifier.size(15.dp), primary)
        }
        Text(title, color = primary, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = CareGreen, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun CareBackgroundSection(
    language: AppLanguage,
    appearance: CareChatAppearanceController,
    primary: Color,
    openPhoto: (Array<String>) -> Unit,
    view: android.view.View,
) {
    val top = listOf(CareChatWallpaper.NONE, CareChatWallpaper.PHOTO, CareChatWallpaper.DAWN, CareChatWallpaper.SKY, CareChatWallpaper.WATER, CareChatWallpaper.AURORA)
    Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(76.dp),
            modifier = Modifier.fillMaxWidth().height(210.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            userScrollEnabled = false,
        ) {
            items(top) { wallpaper ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        Box(
                            Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (wallpaper == CareChatWallpaper.PHOTO && appearance.customPhotoUri.isNullOrBlank()) openPhoto(arrayOf("image/*"))
                                    else appearance.select(wallpaper)
                                    if (appearance.hapticsEnabled) IumrahHaptics.selection(view)
                                }
                                .border(if (appearance.wallpaper == wallpaper) 3.dp else .8.dp, Color.White.copy(alpha = if (appearance.wallpaper == wallpaper) 1f else .18f), CircleShape),
                        ) {
                            CareWallpaperPreview(wallpaper, appearance.customPhotoUri, Modifier.fillMaxSize())
                        }
                        if (appearance.wallpaper == wallpaper) {
                            Box(Modifier.align(Alignment.BottomEnd).size(25.dp).clip(CircleShape).background(CareGreen), contentAlignment = Alignment.Center) {
                                CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(11.dp), Color.White)
                            }
                        }
                    }
                    Text(wallpaper.title(language), color = primary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Text(tr(language, "Suggestions", "Предложения", "Takliflar", "Таклифлар"), color = primary, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
            val suggestions = listOf(CareChatWallpaper.MAKKAH, CareChatWallpaper.SAND, CareChatWallpaper.AURORA, CareChatWallpaper.WATER)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(480.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false,
            ) {
                items(suggestions) { wallpaper ->
                    Box(
                        Modifier
                            .height(224.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .clickable { appearance.select(wallpaper); if (appearance.hapticsEnabled) IumrahHaptics.selection(view) }
                            .border(if (appearance.wallpaper == wallpaper) 2.dp else .8.dp, Color.White.copy(alpha = if (appearance.wallpaper == wallpaper) .9f else .15f), RoundedCornerShape(30.dp)),
                    ) {
                        CareWallpaperPreview(wallpaper, appearance.customPhotoUri, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .48f)), startY = 110f)))
                        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(wallpaper.title(language), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (appearance.wallpaper == wallpaper) {
                                Box(Modifier.size(25.dp).clip(CircleShape).background(CareGreen), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(11.dp), Color.White) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CareConversationBackground(wallpaper: CareChatWallpaper, customPhotoUri: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        when (wallpaper) {
            CareChatWallpaper.NONE -> Unit
            CareChatWallpaper.PHOTO -> if (!customPhotoUri.isNullOrBlank()) AsyncImage(Uri.parse(customPhotoUri), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            CareChatWallpaper.MAKKAH -> Image(painterResource(R.drawable.iumrah_makkah_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            CareChatWallpaper.SAND -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFE8C58B), Color(0xFFD49C5D), Color(0xFFF1D7A6)))))
            CareChatWallpaper.SKY -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF62A7E8), Color(0xFFB9DDF4), Color(0xFFEAF5FB)))))
            CareChatWallpaper.DAWN -> AnimatedCareGradient(listOf(Color(0xFF6046B5), Color(0xFFEA758B), Color(0xFFF6BB6C)))
            CareChatWallpaper.WATER -> AnimatedCareGradient(listOf(Color(0xFF087EA4), Color(0xFF30B9B5), Color(0xFF9CE2D5)))
            CareChatWallpaper.AURORA -> AnimatedCareGradient(listOf(Color(0xFF141B4D), Color(0xFF5F4FD3), Color(0xFF33D1A0), Color(0xFF4A8CE9)))
        }
        if (wallpaper.isVisual) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .045f), Color.Transparent, Color.Black.copy(alpha = .11f)))))
        }
    }
}

@Composable
private fun AnimatedCareGradient(colors: List<Color>) {
    val transition = rememberInfiniteTransition(label = "care-wallpaper")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(7000), RepeatMode.Reverse), label = "phase")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.linearGradient(colors, start = Offset(size.width * phase * .45f, 0f), end = Offset(size.width * (1f - phase * .25f), size.height)))
        drawCircle(colors.last().copy(alpha = .22f), radius = size.minDimension * .55f, center = Offset(size.width * (.2f + .6f * phase), size.height * .35f))
    }
}

@Composable
private fun CareWallpaperPreview(wallpaper: CareChatWallpaper, customPhotoUri: String?, modifier: Modifier) {
    Box(modifier) {
        when (wallpaper) {
            CareChatWallpaper.NONE -> Box(Modifier.fillMaxSize().background(Color(0xFFEFEFF1)))
            CareChatWallpaper.PHOTO -> if (!customPhotoUri.isNullOrBlank()) AsyncImage(Uri.parse(customPhotoUri), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = .88f), Color.LightGray.copy(alpha = .34f)))), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Grid, null, Modifier.size(22.dp), Color.DarkGray.copy(alpha = .66f)) }
            CareChatWallpaper.MAKKAH -> Image(painterResource(R.drawable.iumrah_makkah_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            CareChatWallpaper.SAND -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFE7C58C), Color(0xFFD49B5D), Color(0xFFF2D7A8)))))
            CareChatWallpaper.SKY -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF5FA5E7), Color(0xFFB7DDF5), Color(0xFFE9F5FB)))))
            CareChatWallpaper.DAWN -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF6550B9), Color(0xFFE87791), Color(0xFFF6BC6C)))))
            CareChatWallpaper.WATER -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF087FA5), Color(0xFF36BDB6), Color(0xFFA5E4D8)))))
            CareChatWallpaper.AURORA -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF151B4C), Color(0xFF604FD3), Color(0xFF35D3A1), Color(0xFF4B8DE9)))))
        }
    }
}

@Composable
private fun CareAvatar(size: androidx.compose.ui.unit.Dp, shadow: Boolean = false) {
    Image(
        painter = painterResource(R.drawable.care_chat_avatar),
        contentDescription = "iumrah Care",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .then(if (shadow) Modifier.shadow(16.dp, CircleShape, clip = false) else Modifier)
            .clip(CircleShape)
            .border(.8.dp, Color.White.copy(alpha = .62f), CircleShape),
    )
}

@Composable
private fun CareFullscreenAttachment(
    path: String,
    bookingID: String,
    bookingStore: BookingStore,
    chatService: ChatService,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AuthenticatedChatImage(path, bookingID, bookingStore, chatService, Modifier.fillMaxSize().padding(12.dp), fullScreen = true)
            IumrahPressable(onClick = onDismiss, modifier = Modifier.statusBarsPadding().padding(14.dp).size(42.dp).align(Alignment.TopEnd), cornerRadius = 99.dp, background = Color.White.copy(alpha = .16f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Close, "Close", Modifier.size(18.dp), Color.White) }
            }
        }
    }
}

private fun isMine(message: ChatMessage): Boolean = message.senderType.equals("client", true) || message.senderType.equals("pilgrim", true)
private fun parseInstant(raw: String): Instant = runCatching { Instant.parse(raw) }.getOrElse { Instant.EPOCH }
private fun dayOf(instant: Instant?): LocalDate? = instant?.atZone(ZoneId.systemDefault())?.toLocalDate()
private fun secondsBetween(a: Instant?, b: Instant?): Long = if (a == null || b == null) Long.MAX_VALUE else kotlin.math.abs(b.epochSecond - a.epochSecond)
private fun timeLabel(instant: Instant): String = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(instant)
private fun dateLabel(instant: Instant, language: AppLanguage): String {
    val date = dayOf(instant) ?: return ""
    val today = LocalDate.now()
    if (date == today) return tr(language, "Today", "Сегодня", "Bugun", "Бугун")
    if (date == today.minusDays(1)) return tr(language, "Yesterday", "Вчера", "Kecha", "Кеча")
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(language.locale).format(date)
}

private fun openExternal(context: Context, uri: Uri, action: String = Intent.ACTION_VIEW) {
    runCatching { context.startActivity(Intent(action, uri)) }
}

private fun playCareTone(success: Boolean) {
    runCatching {
        ToneGenerator(AudioManager.STREAM_SYSTEM, 32).apply {
            startTone(if (success) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_NACK, 70)
            release()
        }
    }
}

private fun tr(language: AppLanguage, en: String, ru: String, uz: String, cyrl: String): String = when (language) {
    AppLanguage.ENGLISH -> en
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cyrl
}

private fun Color.luminanceCompat(): Float {
    fun channel(value: Float): Float = if (value <= .03928f) value / 12.92f else Math.pow(((value + .055f) / 1.055f).toDouble(), 2.4).toFloat()
    return .2126f * channel(red) + .7152f * channel(green) + .0722f * channel(blue)
}
