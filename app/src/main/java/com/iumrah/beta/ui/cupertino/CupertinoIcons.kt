package com.iumrah.beta.ui.cupertino

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.iumrah.beta.R

/**
 * iumrah's shared semantic icon names. EVERY displayed icon uses an original
 * Flutter CupertinoIcons typeface glyph (MIT), not hand-drawn Compose paths.
 *
 * This is the native Jetpack Compose equivalent of the Flutter asset package:
 * Flutter's Dart dependency cannot be added to a native Android/Kotlin app.
 * The binary font + the glyph table are installed automatically by
 * scripts/install-cupertino-assets.py before an Android Gradle build.
 *
 * Apple SF Symbols are NOT copied into Android (Apple SDK licensing).
 */
enum class CupertinoSymbol {
    Home, Hotel, Suitcase, SuitcaseFill, TrayFill, Heart, HeartFill, PersonCircle,
    Menu, ChevronRight, ChevronLeft, ArrowRight,
    ChatBubble, Phone, Send, Lock, LockShield, ShieldCheck,
    CreditCard, UndoCircle, HandRaised, IdentityCard,
    Gear, Globe, HalfCircle, Bell, BellSignal, BellBadge,
    Persons, Wallet, Airplane, AirplaneTakeoff, AirplaneLand, Location, Building,
    CheckCircle, CheckCircleFill, ExclamationCircle, Passport, Key,
    SignOut, Sun, SunMaxFill, Moon, MoonStarsFill, Device, Mail, Apple,
    InfoCircle, Message, TrashSlash, Document, NumberSquare,
    CalendarClock, Hourglass, Close, Copy, Eye, EyeSlash, Sparkles, CupSaucerFill,
    Route, Car, Refresh, PlusPerson, Lightbulb, Checkmark, SignalWave,
    Star, StarFill, Sliders, Speaker, SpeakerSlash, ChevronDown, ChevronUp, Plus, Minus,
    ArrowUpRight, ArrowUp, ArrowDown, ArrowLeftRight, Paperclip, Bed, Play, ForkKnife, Share, LeafFill, Checklist, CrownFill, Grid,
    Calendar, Clock, Map, QrCode, Gift, WarningTriangle, Photo, Pencil, Person, Microphone, PackageBox, Trash,
    Download, LockOpen, SimCard, Safari, Book, Timer, Seatbelt, Bag, Ellipsis,
    Search, QuestionCircle, ChartBar, Cart, Ticket, SosCircle, ArrowUpDown,
    Wifi, WifiSlash, WifiWarning, Sunrise, CloudSun, Scan, Link,
    HomeFill, BellFill, BellSlashFill, InfoCircleFill, Tray, WarningTriangleFill, PhotoFill,
}


@Composable
fun CupertinoIcon(
    symbol: CupertinoSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val font = remember(context) {
        ResourcesCompat.getFont(context, R.font.cupertino_icons)
            ?: error("The official CupertinoIcons font was not installed")
    }
    val glyph = remember(symbol) {
        val codepoint = CupertinoGlyphCodepoints.bySymbol.getValue(symbol)
        String(Character.toChars(codepoint))
    }
    val resolvedTint = if (tint == Color.Unspecified) LocalContentColor.current else tint
    val accessible = if (contentDescription.isNullOrBlank()) modifier
        else modifier.semantics { this.contentDescription = contentDescription.orEmpty() }
    // Canvas is only the text drawing surface. No icon outlines are constructed
    // with Android/Compose paths, circles, strokes or procedural geometry.
    val paint = remember(font) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = font
            textAlign = Paint.Align.CENTER
            isFilterBitmap = true
        }
    }
    Canvas(modifier = accessible) {
        paint.color = resolvedTint.toArgb()
        paint.textSize = size.minDimension
        val metrics = paint.fontMetrics
        val baseline = size.height / 2f - (metrics.ascent + metrics.descent) / 2f
        drawIntoCanvas { it.nativeCanvas.drawText(glyph, size.width / 2f, baseline, paint) }
    }
}

/** Compatibility shim for existing Compose screens that call Icon(CupertinoSymbol, ...). */
@Composable
fun Icon(
    imageVector: CupertinoSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = Color.Unspecified,
) = CupertinoIcon(imageVector, contentDescription, modifier, tint)

/** SF Symbols' systemName vocabulary is preserved as a cross-platform semantic API. */
@Composable
fun CupertinoSfIcon(
    systemName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = Color.Unspecified,
) = CupertinoIcon(CupertinoSfCatalog.resolve(systemName), contentDescription, modifier, tint)
