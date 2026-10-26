package com.iumrah.beta.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.settings.AppAppearance
import com.iumrah.beta.R

/**
 * Android mirror of Sources/Core/DesignSystem.swift from the current iOS repository.
 *
 * iOS is the visual source of truth.  Keep these values synchronized with
 * IumrahDesign rather than introducing Android-only geometry.
 */
object IumrahDesign {
    val PagePadding = 18.dp
    val CardRadius = 28.dp
    val HeroRadius = 34.dp
    val CompactRadius = 19.dp
    val ControlHeight = 56.dp
    val GlassIconSize = 46.dp
}

object IumrahBookingStatusVisual {
    fun color(status: String): Color = when (status.uppercase()) {
        "NEW", "AVAILABILITY_CHECK" -> IumrahColors.SystemYellow
        "PAYMENT_PENDING" -> IumrahColors.SystemOrange
        "PAID", "BOOKING_CONFIRMED" -> IumrahColors.SystemGreen
        "DOCUMENTS_READY", "READY_TO_TRAVEL" -> IumrahColors.SystemTeal
        "IN_TRIP" -> IumrahColors.SystemBlue
        "COMPLETED" -> IumrahColors.SystemIndigo
        "CANCELLED" -> IumrahColors.SystemRed
        else -> IumrahColors.SystemOrange
    }
}

object IumrahColors {
    // UIColor.systemBackground / grouped surfaces translated to deterministic ARGB.
    val LightPage = Color(0xFFF2F2F7) // UIColor.systemGroupedBackground (light)
    val LightCard = Color(0xFFFFFFFF)
    val LightRaised = Color(0xFFF2F2F7)
    val LightRaisedStrong = Color(0xFFE5E5EA)

    val DarkPage = Color(0xFF000000)
    val DarkCard = Color(0xFF1C1C1E)
    val DarkRaised = Color(0xFF2C2C2E)
    val DarkRaisedStrong = Color(0xFF3A3A3C)

    val Graphite = Color(0xFF17191D)
    // iOS signature header and active navigation surface: 0.035 sRGB linear fraction.
    val SignatureBlack = Color(0xFF090909)
    val SoftGraphite = Color(0xFF2A2B2F)
    val CareDark = Color(0xFF0E2422)
    val CareLight = Color(0xFF74A187)

    val SystemBlue = Color(0xFF007AFF)
    val SystemIndigo = Color(0xFF5856D6)
    val SystemOrange = Color(0xFFFF9500)
    val SystemTeal = Color(0xFF30B0C7)
    val SystemPink = Color(0xFFFF2D55)
    val SystemCyan = Color(0xFF32ADE6)
    val SystemGreen = Color(0xFF34C759)
    val SystemPurple = Color(0xFFAF52DE)
    val SystemYellow = Color(0xFFFFCC00)
    val SystemRed = Color(0xFFFF3B30)
    val SystemGray = Color(0xFF8E8E93)
}

/**
 * Compatibility name retained because many existing Compose files already import it.
 * Values are intentionally the exact current iOS metrics, not a separate Android system.
 */
object IumrahGalaxyMetrics {
    val ScreenHorizontal = IumrahDesign.PagePadding
    val ScreenTop = 12.dp
    val SectionGap = 30.dp
    val ContentGap = 14.dp

    val RadiusSmall = 14.dp
    val RadiusControl = IumrahDesign.CompactRadius
    val RadiusButton = IumrahDesign.CompactRadius
    val RadiusTile = 22.dp
    val RadiusCard = IumrahDesign.CardRadius
    val RadiusLarge = IumrahDesign.HeroRadius

    val TouchTarget = IumrahDesign.GlassIconSize
    val ControlHeight = IumrahDesign.ControlHeight
    val PrimaryButtonHeight = IumrahDesign.ControlHeight
}

private val LightColors = lightColorScheme(
    background = IumrahColors.LightPage,
    surface = IumrahColors.LightCard,
    surfaceVariant = IumrahColors.LightRaised,
    onBackground = Color.Black,
    onSurface = Color.Black,
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = IumrahColors.CareDark,
    onSecondary = Color.White,
    outline = Color.Black.copy(alpha = .075f),
    outlineVariant = Color.Black.copy(alpha = .055f),
)

private val DarkColors = darkColorScheme(
    background = IumrahColors.DarkPage,
    surface = IumrahColors.DarkCard,
    surfaceVariant = IumrahColors.DarkRaised,
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color(0xFFF5F5F5),
    onPrimary = Color(0xFF121316),
    secondary = IumrahColors.CareLight,
    onSecondary = Color(0xFF0E1714),
    outline = Color.White.copy(alpha = .075f),
    outlineVariant = Color.White.copy(alpha = .055f),
)

// Cross-platform iumrah type family: bundled OFL Lato. Never fall back to
// OEM rounded fonts when an Android device is configured differently.
// Same two visual faces as iOS: Regular for normal/medium text,
// Bold for semibold headings, buttons, prices and emphasis.
// Keep weight aliases so existing Compose fontWeight requests resolve predictably.
val IumrahFontFamily = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_regular, FontWeight.Medium),
    Font(R.font.lato_bold, FontWeight.SemiBold),
    Font(R.font.lato_bold, FontWeight.Bold),
)

private val IumrahTypography = Typography(
    displayLarge = TextStyle(
                fontSize = 38.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1.0).sp,
    ),
    headlineLarge = TextStyle(
                fontSize = 31.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
                fontSize = 28.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.7).sp,
    ),
    headlineSmall = TextStyle(
                fontSize = 23.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.45).sp,
    ),
    titleLarge = TextStyle(
                fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
                fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
                fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
                fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
                fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
                fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
                fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = TextStyle(
                fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

// All Material components inherit the same font, not just bare Text() calls.
private val UnifiedTypography = IumrahTypography.copy(
    displayLarge = IumrahTypography.displayLarge.copy(fontFamily = IumrahFontFamily),
    displayMedium = IumrahTypography.displayMedium.copy(fontFamily = IumrahFontFamily),
    displaySmall = IumrahTypography.displaySmall.copy(fontFamily = IumrahFontFamily),
    headlineLarge = IumrahTypography.headlineLarge.copy(fontFamily = IumrahFontFamily),
    headlineMedium = IumrahTypography.headlineMedium.copy(fontFamily = IumrahFontFamily),
    headlineSmall = IumrahTypography.headlineSmall.copy(fontFamily = IumrahFontFamily),
    titleLarge = IumrahTypography.titleLarge.copy(fontFamily = IumrahFontFamily),
    titleMedium = IumrahTypography.titleMedium.copy(fontFamily = IumrahFontFamily),
    titleSmall = IumrahTypography.titleSmall.copy(fontFamily = IumrahFontFamily),
    bodyLarge = IumrahTypography.bodyLarge.copy(fontFamily = IumrahFontFamily),
    bodyMedium = IumrahTypography.bodyMedium.copy(fontFamily = IumrahFontFamily),
    bodySmall = IumrahTypography.bodySmall.copy(fontFamily = IumrahFontFamily),
    labelLarge = IumrahTypography.labelLarge.copy(fontFamily = IumrahFontFamily),
    labelMedium = IumrahTypography.labelMedium.copy(fontFamily = IumrahFontFamily),
    labelSmall = IumrahTypography.labelSmall.copy(fontFamily = IumrahFontFamily),
)

private val IumrahShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(IumrahGalaxyMetrics.RadiusSmall),
    medium = RoundedCornerShape(IumrahDesign.CompactRadius),
    large = RoundedCornerShape(IumrahDesign.CardRadius),
    extraLarge = RoundedCornerShape(IumrahDesign.HeroRadius),
)

@Composable
fun IumrahTheme(
    appearance: AppAppearance = AppAppearance.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance) {
        AppAppearance.SYSTEM -> systemDark
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = UnifiedTypography,
        shapes = IumrahShapes,
    ) {
        // Many parity screens set explicit size/weight but intentionally omit fontFamily.
        // This provider makes Lato global without forcing one global text size.
        ProvideTextStyle(value = TextStyle(fontFamily = IumrahFontFamily)) {
            content()
        }
    }
}
