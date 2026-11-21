/*
 * Theme.kt (Aditya)
 *
 * Look and feel from our Figma: colours, fonts, shapes and the app theme. Components live in
 * Buttons.kt, Cards.kt, Inputs.kt, Bars.kt and Formats.kt.
 *
 * Outstanding issues: none known.
 */
package com.carbon.events

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colour tokens from the Figma file (names match the Figma variables). */
object EventsColors {
    val Primary = Color(0xFFB9A9FF)
    val PrimaryPressed = Color(0xFF9D8BF0)
    val OnPrimary = Color(0xFF2A2160)
    val PrimaryContainer = Color(0xFF3E3A8A)
    val OnPrimaryContainer = Color(0xFFDCD3FA)
    val Coral = Color(0xFFFFB4A6)
    val OnCoral = Color(0xFF3A2C6E)
    val CoralContainer = Color(0xFF4E2F5C)
    val Green = Color(0xFF7CE0A8)
    val GreenContainer = Color(0xFF1F4B45)
    val Amber = Color(0xFFFFC46B)
    val AmberContainer = Color(0xFF4A3B2A)
    val Star = Color(0xFFFFB020)
    val Danger = Color(0xFFF2687A)
    val DangerContainer = Color(0xFF5B2750)
    val Background = Color(0xFF25225E)
    val Surface = Color(0xFF2D2A6E)
    val SurfaceSoft = Color(0xFF37347F)
    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFC4BEEB)
    val TextTertiary = Color(0xFF9690C8)
    val Outline = Color(0xFF4A4790)
    val Scrim = Color(0xFF0B1220)

    // Twilight sky gradient and bars
    val SkyTop = Color(0xFF121846)
    val SkyMid = Color(0xFF3B3585)
    val SkyGlow = Color(0xFF8C6AB2)
    val Horizon = Color(0xFFE99B92)
    val Bar = Color(0xFF5A4B9C)
    val OnBar = Color(0xB8FFFFFF)
    val Pill = Color(0x52FFB4A6)
    val Tint = Color(0x33FFFFFF)
    val TintStroke = Color(0x47FFFFFF)
    val TextSoft = Color(0xC7FFFFFF)

    // Aurora ribbons (from the Scene artwork)
    val AuroraGreen = Color(0xFF63D2A9)
    val AuroraTeal = Color(0xFF4FB3C9)
}

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) =
    Font(R.font.plus_jakarta_sans, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Plus Jakarta Sans, the body and UI font. */
val Jakarta = FontFamily(
    jakarta(FontWeight.Normal), jakarta(FontWeight.Medium), jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold), jakarta(FontWeight.ExtraBold),
)

/** Fraunces SemiBold (WONK on, SOFT off), the heading font. */
@OptIn(ExperimentalTextApi::class)
val Fraunces = FontFamily(
    Font(
        R.font.fraunces, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600), FontVariation.Setting("WONK", 1f), FontVariation.Setting("SOFT", 0f),
        ),
    ),
)

/** Text styles measured from the Figma screens (size / line height in sp). */
object EventsType {
    /** Greeting name, e.g. "Adam" on Explore: Fraunces 34/38. */
    val Display = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 38.sp)
    /** Owner dashboard name: Fraunces 30/34. */
    val DisplaySmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp)
    /** Welcome headline: Fraunces 28/36. */
    val Hero = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp)
    /** Screen titles ("Profile", "My Lotteries", campsite name on details): Fraunces 22/30. */
    val Headline = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp)
    /** Section headers, top-bar titles, dialog titles: Fraunces 18/26. */
    val Title = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp)
    /** Wordmark "Events" on splash: Plus Jakarta ExtraBold 34/36. */
    val Wordmark = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 36.sp)
    val TitleSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp)
    val Body = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp)
    val BodyStrong = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 22.sp)
    val BodySmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
    val BodySmallStrong = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    val Label = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
    val LabelSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
    val Greeting = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
    val Overline = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp)
}

/** Material typography mapped onto the Events styles. */
val EventsTypography = Typography(
    displaySmall = EventsType.Display,
    headlineMedium = EventsType.Headline,
    titleLarge = EventsType.Title,
    titleMedium = EventsType.TitleSmall,
    bodyLarge = EventsType.Body,
    bodyMedium = EventsType.Body,
    bodySmall = EventsType.BodySmall,
    labelLarge = EventsType.Label,
    labelMedium = EventsType.LabelSmall,
    labelSmall = EventsType.Caption,
)

/** Corner radii from Figma (radius/sm, md, lg, xl). */
object EventsShapes {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(24.dp)
    val XLarge = RoundedCornerShape(28.dp)
    val Pill = RoundedCornerShape(999.dp)
}

private val scheme = darkColorScheme(
    primary = EventsColors.Primary,
    onPrimary = EventsColors.OnPrimary,
    primaryContainer = EventsColors.PrimaryContainer,
    onPrimaryContainer = EventsColors.OnPrimaryContainer,
    secondary = EventsColors.Coral,
    onSecondary = EventsColors.OnCoral,
    secondaryContainer = EventsColors.CoralContainer,
    onSecondaryContainer = EventsColors.Coral,
    tertiary = EventsColors.Green,
    background = EventsColors.Background,
    onBackground = EventsColors.Text,
    surface = EventsColors.Surface,
    onSurface = EventsColors.Text,
    surfaceVariant = EventsColors.SurfaceSoft,
    onSurfaceVariant = EventsColors.TextSecondary,
    surfaceContainer = EventsColors.Surface,
    surfaceContainerHigh = EventsColors.SurfaceSoft,
    surfaceContainerHighest = EventsColors.SurfaceSoft,
    outline = EventsColors.Outline,
    outlineVariant = EventsColors.Outline,
    error = EventsColors.Danger,
    errorContainer = EventsColors.DangerContainer,
    scrim = EventsColors.Scrim,
)

/** Applies the Events twilight theme to [content]. */
@Composable
fun EventsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = EventsTypography,
        shapes = Shapes(small = EventsShapes.Small, medium = EventsShapes.Medium, large = EventsShapes.Large),
        content = content,
    )
}

/** Horizontal page margin used by every Figma screen. */
val PageMargin = 16.dp

/** One of the custom icons exported from the Figma icon set. */
@Composable
fun EventsIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, tint: Color = EventsColors.Text, size: Dp = 22.dp, contentDescription: String? = null) {
    Icon(painterResource(icon), contentDescription, modifier.size(size), tint = tint)
}
