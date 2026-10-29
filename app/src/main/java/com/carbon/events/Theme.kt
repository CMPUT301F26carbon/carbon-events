/*
 * Theme.kt (Aditya)
 *
 * Look and feel from our Figma: colours, fonts, buttons, cards, tags, chips,
 * text fields, the nav bar, dialogs and the date picker field.
 */
package com.carbon.events

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

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

/** Button sizes from the Figma Button component. */
enum class ButtonSize(val height: Dp, val padding: Dp, val icon: Dp) { LARGE(52.dp, 28.dp, 20.dp), SMALL(40.dp, 18.dp, 18.dp) }

@Composable
private fun EventsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    container: Color,
    content: Color,
    @DrawableRes icon: Int?,
    enabled: Boolean,
    loading: Boolean,
    size: ButtonSize,
    border: BorderStroke? = null,
    glow: Color? = null,
) {
    val shadowMod = if (glow != null && enabled && size == ButtonSize.LARGE) {
        Modifier.shadow(12.dp, EventsShapes.Pill, ambientColor = glow, spotColor = glow)
    } else Modifier
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.then(shadowMod).height(size.height),
        shape = EventsShapes.Pill,
        border = border,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = container.copy(alpha = container.alpha * 0.45f), disabledContentColor = content.copy(alpha = 0.6f),
        ),
        contentPadding = PaddingValues(horizontal = size.padding),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                EventsIcon(icon, tint = content, size = size.icon)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = if (size == ButtonSize.LARGE) EventsType.Label else EventsType.LabelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Primary button: coral #FFB4A6 with plum text and a soft coral glow. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, enabled: Boolean = true, loading: Boolean = false, size: ButtonSize = ButtonSize.LARGE) =
    EventsButton(text, onClick, modifier, EventsColors.Coral, EventsColors.OnCoral, icon, enabled, loading, size, glow = Color(0x59E57373))

/** Tonal (frosted) button: white 20% with a 14% white outline. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, enabled: Boolean = true, size: ButtonSize = ButtonSize.LARGE) =
    EventsButton(text, onClick, modifier, EventsColors.Tint, EventsColors.Text, icon, enabled, false, size, BorderStroke(1.dp, Color(0x24FFFFFF)))

/** Destructive button: solid red with white text. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, loading: Boolean = false, size: ButtonSize = ButtonSize.LARGE) =
    EventsButton(text, onClick, modifier, EventsColors.Danger, Color.White, icon, true, loading, size, glow = Color(0x4DF2667A))

/** Text-only button (e.g. "I already have an account", lottery "Edit"). */
@Composable
fun TextLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = EventsColors.Primary) {
    Text(
        text, style = EventsType.LabelSmall, color = color,
        modifier = modifier.clip(EventsShapes.Pill).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

/** Round glass icon button: 44dp, #121846 at 38% with a 16% white outline, 22dp icon. */
@Composable
fun GlassIconButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, badge: Boolean = false) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x61121846))
            .border(1.dp, Color(0x29FFFFFF), CircleShape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        EventsIcon(icon, contentDescription = contentDescription)
        if (badge) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 11.dp).size(8.dp).clip(CircleShape).background(EventsColors.Coral))
    }
}

/** Dusk surface card: #2D2A6E, radius 24, no outline. */
@Composable
fun DuskCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    color: Color = EventsColors.Surface,
    shape: RoundedCornerShape = EventsShapes.Large,
    borderColor: Color? = null,
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(color)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Tag tones from the Figma Tag component. */
enum class TagTone(val bg: Color, val fg: Color) {
    BLUE(EventsColors.PrimaryContainer, EventsColors.OnPrimaryContainer),
    GREEN(EventsColors.GreenContainer, EventsColors.Green),
    AMBER(EventsColors.AmberContainer, EventsColors.Amber),
    CORAL(EventsColors.CoralContainer, Color(0xFFFFC2B6)),
    RED(EventsColors.DangerContainer, EventsColors.Danger),
    GREY(EventsColors.SurfaceSoft, EventsColors.TextSecondary),
    SOLID(EventsColors.Primary, EventsColors.OnPrimary),
}

/** Tag: 24dp pill, Plus Jakarta Bold 11 uppercase with 0.8 letter spacing. */
@Composable
fun StatusTag(text: String, tone: TagTone, modifier: Modifier = Modifier) {
    Box(modifier.height(24.dp).clip(EventsShapes.Pill).background(tone.bg).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), style = EventsType.Overline, color = tone.fg, maxLines = 1)
    }
}

/** Chip styles used across the Figma screens. */
enum class ChipStyle {
    /** Frosted chip on the sky (Explore vibes, search filters). */
    GLASS,
    /** Coral chip (selected vibe / selected tag). */
    CORAL,
    /** Dusk chip on a card (Chip component, default state). */
    DUSK,
    /** Lavender chip (Chip component, selected state). */
    SELECTED,
}

/** Chip: 36dp pill, 16dp icon, Plus Jakarta SemiBold 12. */
@Composable
fun EventsChip(text: String, style: ChipStyle, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    val (bg, fg, stroke) = when (style) {
        ChipStyle.GLASS -> Triple(EventsColors.Tint, EventsColors.Text, EventsColors.TintStroke)
        ChipStyle.CORAL -> Triple(EventsColors.Coral, EventsColors.OnCoral, null)
        ChipStyle.DUSK -> Triple(EventsColors.Surface, EventsColors.Text, EventsColors.Outline)
        ChipStyle.SELECTED -> Triple(EventsColors.Primary, EventsColors.OnPrimary, null)
    }
    Row(
        modifier
            .height(36.dp)
            .clip(EventsShapes.Pill)
            .background(bg)
            .then(if (stroke != null) Modifier.border(1.dp, stroke, EventsShapes.Pill) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = if (icon != null) 14.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) EventsIcon(icon, tint = fg, size = 16.dp)
        Text(text, style = EventsType.LabelSmall, color = fg, maxLines = 1)
    }
}

/** Stat tile (details): #37347F, radius 16, value SemiBold 14 with 16dp icon, label Medium 11. */
@Composable
fun InfoTile(@DrawableRes icon: Int, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(EventsShapes.Medium).background(EventsColors.SurfaceSoft).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EventsIcon(icon, tint = EventsColors.TextSecondary, size = 16.dp)
            Text(value, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(label, style = EventsType.Caption, color = EventsColors.TextSecondary, maxLines = 1)
    }
}

/** Label / value row: 16dp icon, Regular 12 label, SemiBold 12 value (lottery card rows). */
@Composable
fun DetailRow(@DrawableRes icon: Int, label: String, value: String, labelColor: Color = EventsColors.OnPrimaryContainer) {
    Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EventsIcon(icon, tint = labelColor, size = 16.dp)
        Text(label, style = EventsType.BodySmall, color = labelColor, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value, style = EventsType.BodySmallStrong, color = EventsColors.Text, textAlign = TextAlign.End, maxLines = 1)
    }
}

/** Icon + text meta line (location, guests, email): 16dp icon, Regular 12. */
@Composable
fun MetaItem(@DrawableRes icon: Int, text: String, color: Color = EventsColors.TextSecondary, modifier: Modifier = Modifier, iconTint: Color = color) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EventsIcon(icon, tint = iconTint, size = 16.dp)
        Text(text, style = EventsType.BodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Section header: Fraunces SemiBold 18/26, with an optional trailing action. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = EventsType.Title, color = EventsColors.Text, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Stepper: 36dp circles (#3E3A8A minus, #B9A9FF plus), SemiBold 16 value, 14dp gaps. */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int, max: Int, label: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = EventsType.BodyStrong, color = EventsColors.Text)
            if (supporting != null) Text(supporting, style = EventsType.BodySmall, color = EventsColors.TextSoft)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepButton(R.drawable.ic_remove, "Decrease $label", value > min, EventsColors.PrimaryContainer, EventsColors.Text) { onChange(value - 1) }
            Text("$value", style = EventsType.TitleSmall, color = EventsColors.Text, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
            StepButton(R.drawable.ic_add, "Increase $label", value < max, EventsColors.Primary, EventsColors.OnPrimary) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepButton(@DrawableRes icon: Int, description: String, enabled: Boolean, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { EventsIcon(icon, tint = fg.copy(alpha = if (enabled) 1f else 0.5f), size = 18.dp, contentDescription = description) }
}

/**
 * Field (Figma "Field" component): SemiBold 12 label at 78% white, then a 52dp input
 * (#37347F, radius 16, 8% white outline) with Regular 14 text and an optional 20dp icon.
 */
@Composable
fun EventsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    @DrawableRes leadingIcon: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = EventsType.LabelSmall, color = EventsColors.TextSoft)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = EventsType.Body.copy(color = EventsColors.Text),
            cursorBrush = SolidColor(EventsColors.Coral),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (singleLine) 52.dp else 96.dp)
                        .clip(EventsShapes.Medium)
                        .background(EventsColors.SurfaceSoft)
                        .border(1.dp, if (error != null) EventsColors.Danger else Color(0x14FFFFFF), EventsShapes.Medium)
                        .padding(start = 16.dp, end = 14.dp, top = if (singleLine) 0.dp else 14.dp, bottom = if (singleLine) 0.dp else 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (leadingIcon != null) EventsIcon(leadingIcon, tint = EventsColors.TextSecondary, size = 20.dp)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) Text(placeholder, style = EventsType.Body, color = EventsColors.TextTertiary)
                        inner()
                    }
                }
            },
        )
        if (error != null) Text(error, style = EventsType.Caption, color = EventsColors.Danger, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * Segmented control (Figma "Segmented"): 50dp frosted pill with 4dp padding; the selected
 * segment is a Dusk (#2D2A6E) pill, labels SemiBold 12.
 */
@Composable
fun SegmentedControl(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, tagPrefix: String = "tab_") {
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(EventsShapes.Pill)
            .background(EventsColors.Tint)
            .border(1.dp, EventsColors.TintStroke, EventsShapes.Pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(EventsShapes.Pill)
                    .background(if (i == selected) EventsColors.Surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .then(Modifier.semanticsTag("$tagPrefix$i")),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = EventsType.LabelSmall, color = if (i == selected) EventsColors.Text else EventsColors.TextSoft, maxLines = 1) }
        }
    }
}

/** Shorthand for a test tag (keeps call sites short). */
fun Modifier.semanticsTag(tag: String): Modifier = this.testTag(tag)

/** Friendly empty state with an icon tile, title and message. */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(EventsColors.PrimaryContainer), contentAlignment = Alignment.Center) {
            EventsIcon(icon, size = 28.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = EventsType.Title, color = EventsColors.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = EventsType.BodySmall, color = EventsColors.TextSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Avatar: #4E2F5C circle with a Dusk outline and white SemiBold initials. */
@Composable
fun Avatar(initials: String, modifier: Modifier = Modifier, size: Dp = 36.dp, bg: Color = EventsColors.CoralContainer, fg: Color? = null) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).border(if (size > 40.dp) 3.dp else 2.dp, EventsColors.Surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials.ifBlank { "?" }, style = if (size > 40.dp) EventsType.TitleSmall else EventsType.LabelSmall, color = fg ?: if (size > 40.dp) EventsColors.Coral else EventsColors.Text)
    }
}

/** Icon tile used in settings rows: 36dp, radius 12, #3E3A8A. */
@Composable
fun IconTile(@DrawableRes icon: Int, modifier: Modifier = Modifier, bg: Color = EventsColors.PrimaryContainer, tint: Color = EventsColors.Text) {
    Box(modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) { EventsIcon(icon, tint = tint, size = 20.dp) }
}

/** A row reserved for content that hugs the bottom of a column. */
@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

/** One destination in the bottom navigation bar. */
data class NavItem(val route: String, val label: String, @param:DrawableRes val icon: Int, val raised: Boolean = false)

private val BarShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * The Dusk Violet nav bar from the Figma "Nav Bar" component. A [NavItem.raised] item is
 * the 52dp coral-to-orchid gradient button with a 3dp bar-coloured ring, sitting 1dp
 * below the bar's top edge (it does not stick out above the bar).
 */
@Composable
fun EventsNavBar(items: List<NavItem>, selectedRoute: String?, onSelect: (NavItem) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .shadow(24.dp, BarShape, ambientColor = Color(0x471F1459), spotColor = Color(0x471F1459))
            .clip(BarShape)
            .background(EventsColors.Bar)
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(80.dp).padding(horizontal = 8.dp)) {
            for (item in items) {
                if (item.raised) RaisedItem(item) { onSelect(item) } else TabItem(item, item.route == selectedRoute) { onSelect(item) }
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .height(80.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClickLabel = item.label, onClick = onClick)
            .testTag("nav_${item.label}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) EventsColors.Pill else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { EventsIcon(item.icon, tint = if (selected) Color.White else EventsColors.OnBar, size = 24.dp) }
        Spacer(Modifier.height(4.dp))
        Text(item.label, style = if (selected) EventsType.LabelSmall else EventsType.Caption, color = if (selected) Color.White else EventsColors.OnBar, maxLines = 1)
    }
}

@Composable
private fun RowScope.RaisedItem(item: NavItem, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .height(80.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = item.label, onClick = onClick)
            .testTag("nav_${item.label}")
            .padding(top = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .shadow(10.dp, CircleShape, ambientColor = Color(0x594D1F66), spotColor = Color(0x594D1F66))
                .size(52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFF59A92), Color(0xFFC77DB5))))
                .border(3.dp, EventsColors.Bar, CircleShape),
            contentAlignment = Alignment.Center,
        ) { EventsIcon(item.icon, tint = Color.White, size = 24.dp) }
        Spacer(Modifier.height(3.dp))
        Text(item.label, style = EventsType.Caption, color = EventsColors.OnBar)
    }
}

/** Top bar: 44dp glass back (or close) button and a Fraunces 18/26 title, 8dp apart. */
@Composable
fun BackTopBar(
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_arrow_back,
    actions: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(horizontal = PageMargin).padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(icon, if (icon == R.drawable.ic_close) "Close" else "Back", onBack)
        if (title != null) {
            Spacer(Modifier.width(8.dp))
            Text(title, style = EventsType.Title, color = EventsColors.Text, modifier = Modifier.weight(1f), maxLines = 1)
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

/**
 * Frosted search bar (56dp, white 20% fill, 28% outline, 22dp icon, Regular 14 text).
 * With [onClick] it is a button (Explore); otherwise an editable field (Search).
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search campsites…",
    onClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(EventsShapes.Pill)
            .background(EventsColors.Tint)
            .border(1.dp, EventsColors.TintStroke, EventsShapes.Pill)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Search", onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EventsIcon(R.drawable.ic_search, size = 22.dp)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, style = EventsType.Body, color = EventsColors.TextSoft, maxLines = 1)
            if (onClick == null) {
                BasicTextField(
                    value = query, onValueChange = onQueryChange, singleLine = true,
                    textStyle = EventsType.Body.copy(color = EventsColors.Text), cursorBrush = SolidColor(EventsColors.Coral),
                    modifier = Modifier.fillMaxWidth().testTag("search_field").then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                )
            }
        }
        if (onClick == null && query.isNotEmpty()) {
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClickLabel = "Clear search") { onQueryChange("") }, contentAlignment = Alignment.Center) {
                EventsIcon(R.drawable.ic_close, size = 18.dp, tint = EventsColors.TextSecondary, contentDescription = "Clear search")
            }
        }
    }
}

/** Bottom action bar (Figma "Action Bar"): 88dp Dusk Violet, 24dp top corners, 16dp padding, 12dp gaps. */
@Composable
fun ActionBar(modifier: Modifier = Modifier, header: (@Composable () -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(24.dp, BarShape, ambientColor = Color(0x471F1459), spotColor = Color(0x471F1459))
            .clip(BarShape)
            .background(EventsColors.Bar)
            .navigationBarsPadding()
            .padding(horizontal = PageMargin, vertical = 18.dp),
    ) {
        header?.invoke()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/**
 * Dialog (Figma): 312dp, Dusk surface, radius 28, 24dp padding, 12dp gaps. The icon tile is
 * a 56dp lavender circle, or for destructive actions a 52dp red-tinted rounded tile. Title
 * Fraunces 18/26, message Regular 12/18, then a tonal cancel and a primary / danger confirm.
 */
@Composable
fun ConfirmDialog(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissText: String = "Cancel",
    loading: Boolean = false,
    extra: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(312.dp)
                .clip(EventsShapes.XLarge)
                .background(EventsColors.Surface)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp)
                .testTag("confirm_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (destructive) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(EventsColors.Danger.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    EventsIcon(icon, tint = EventsColors.Danger, size = 26.dp)
                }
            } else {
                Box(Modifier.size(56.dp).clip(CircleShape).background(EventsColors.PrimaryContainer), contentAlignment = Alignment.Center) {
                    EventsIcon(icon, size = 28.dp)
                }
            }
            Text(title, style = EventsType.Title, color = EventsColors.Text, textAlign = TextAlign.Center)
            Text(message, style = EventsType.BodySmall, color = EventsColors.TextSecondary, textAlign = TextAlign.Center)
            extra?.invoke()
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(dismissText, onDismiss, Modifier.weight(1f))
                if (destructive) DangerButton(confirmText, onConfirm, Modifier.weight(1f), loading = loading)
                else PrimaryButton(confirmText, onConfirm, Modifier.weight(1f), loading = loading)
            }
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.CANADA)
private val dayTimeFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.CANADA)

/** "Oct 16" style date for epoch [millis]. */
fun formatDay(millis: Long): String = dayFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "Oct 16, 9:30 PM" style date and time for epoch [millis]. */
fun formatDayTime(millis: Long): String = dayTimeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** "Oct 1 – Oct 15" style range. */
fun formatRange(start: Long, end: Long): String = "${formatDay(start)} – ${formatDay(end)}"

/** Short relative time like the Figma notifications: "2m", "1h", "Yesterday", "Oct 4". */
fun relativeTime(millis: Long, now: Long): String {
    val diff = now - millis
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        day == today -> "${diff / 3_600_000}h"
        day == today.minusDays(1) -> "Yesterday"
        day.isAfter(today.minusDays(7)) -> day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CANADA)
        else -> formatDay(millis)
    }
}

/** True when [millis] falls on the same calendar day as [now]. */
fun isToday(millis: Long, now: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
}

/** Short countdown text, e.g. "Closes in 3 days", "Closes in 2 min". */
fun closesIn(lottery: Lottery, now: Long): String {
    val left = lottery.registrationEnd - now
    return when {
        left <= 0 -> "Registration closed"
        left < 3_600_000 -> "Closes in ${ceil(left / 60_000.0).toInt()} min"
        left < 86_400_000 -> "Closes in ${ceil(left / 3_600_000.0).toInt()} h"
        else -> "Closes in ${ceil(left / 86_400_000.0).toInt()} days"
    }
}

/** The status tag for a lottery at time [now]. */
@Composable
fun LotteryStatusTag(lottery: Lottery?, now: Long, modifier: Modifier = Modifier, openTone: TagTone = TagTone.GREEN) {
    when {
        lottery == null -> StatusTag("No lottery", TagTone.GREY, modifier)
        lottery.status == LotteryStatus.DRAWN -> StatusTag("Winners drawn", TagTone.CORAL, modifier)
        lottery.isUpcoming(now) -> StatusTag("Opens ${formatDay(lottery.registrationStart)}", TagTone.AMBER, modifier)
        lottery.isRegistrationOpen(now) -> StatusTag(closesIn(lottery, now), openTone, modifier)
        else -> StatusTag("Drawing winners", TagTone.AMBER, modifier)
    }
}

/** Rating line: star, SemiBold 12 score, Regular 12 count in tertiary. */
@Composable
fun RatingLine(rating: Double, count: Int, countLabel: String = "($count)") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EventsIcon(R.drawable.ic_star, size = 16.dp, tint = EventsColors.Star)
        Text("%.1f".format(rating), style = EventsType.BodySmallStrong, color = EventsColors.Text)
        Text(countLabel, style = EventsType.BodySmall, color = EventsColors.TextTertiary)
    }
}

/**
 * Campsite list card (Figma search result): 104dp tall, 84dp thumbnail (radius 16),
 * name SemiBold 14, location and "Max N" with rating, chevron. Summary for US 01.01.04.
 */
@Composable
fun CampsiteListCard(campsite: Campsite, onClick: () -> Unit, modifier: Modifier = Modifier, thumb: Dp = 84.dp, status: (@Composable () -> Unit)? = null) {
    DuskCard(
        modifier.fillMaxWidth().testTag("campsite_card_${campsite.id}"),
        onClick = onClick,
        padding = PaddingValues(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (thumb < 80.dp) 12.dp else 14.dp)) {
            Box(Modifier.size(thumb).clip(EventsShapes.Medium)) { CampsiteImage(campsite.id, campsite.imageBase64) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(campsite.name, style = EventsType.BodyStrong, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MetaItem(R.drawable.ic_location, campsite.location)
                if (status != null) {
                    status()
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetaItem(R.drawable.ic_group, "Max ${campsite.maxOccupancy}")
                        if (campsite.ratingCount > 0) RatingLine(campsite.rating, campsite.ratingCount)
                    }
                }
            }
            EventsIcon(R.drawable.ic_chevron_right, tint = EventsColors.TextSecondary, size = 20.dp)
        }
    }
}

/** Featured lottery card from the Explore carousel: 290x282, radius 24, scene art, info panel. */
@Composable
fun FeaturedLotteryCard(campsite: Campsite, lottery: Lottery, now: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(width = 290.dp, height = 282.dp).clip(EventsShapes.Large).background(EventsColors.Surface).testTag("featured_${campsite.id}")) {
        CampsiteImage(campsite.id, campsite.imageBase64, Modifier.fillMaxSize())
        LotteryStatusTag(lottery, now, Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 14.dp), openTone = TagTone.SOLID)
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .fillMaxWidth()
                .clip(EventsShapes.Medium)
                .background(EventsColors.Bar)
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(campsite.name, style = EventsType.TitleSmall, color = EventsColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Figma: coral meta icons; the guest count never truncates, the location gives way.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetaItem(R.drawable.ic_location, campsite.location, EventsColors.TextSoft, Modifier.weight(1f, fill = false), iconTint = EventsColors.Coral)
                    MetaItem(R.drawable.ic_group, "Up to ${campsite.maxOccupancy}", EventsColors.TextSoft, iconTint = EventsColors.Coral)
                }
            }
            PrimaryButton("View", onClick, size = ButtonSize.SMALL)
        }
    }
}

/** Carousel dots: active 18x6, inactive 6x6 at 35% white, 6dp apart. */
@Composable
fun CarouselDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(width = if (i == current) 18.dp else 6.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(if (i == current) Color.White else Color(0x59FFFFFF)))
        }
    }
}

/** Thin divider (#4A4790) used inside cards. */
@Composable
fun CardDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(EventsColors.Outline))
}

/** A quick-pick option shown under a [DateTimeField]. */
data class TimePreset(val label: String, val millis: () -> Long)

/**
 * A tappable field showing a chosen date and time.
 *
 * @param value chosen time in epoch ms, or null.
 * @param presets quick choices shown as chips.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    label: String,
    value: Long?,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    presets: List<TimePreset> = emptyList(),
    testTag: String = label,
) {
    var showDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    Column(modifier.fillMaxWidth()) {
        Text(label, style = EventsType.LabelSmall, color = EventsColors.TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(EventsShapes.Medium).background(EventsColors.SurfaceSoft)
                .border(1.dp, if (error != null) EventsColors.Danger else Color.Transparent, EventsShapes.Medium)
                .clickable { showDate = true }.padding(horizontal = 14.dp).testTag("field_$testTag"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EventsIcon(R.drawable.ic_calendar, tint = EventsColors.TextSecondary, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(value?.let { formatDayTime(it) } ?: "Pick date & time", style = EventsType.Body, color = if (value == null) EventsColors.TextTertiary else EventsColors.Text)
        }
        if (presets.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p -> EventsChip(p.label, ChipStyle.DUSK, { onChange(p.millis()) }, Modifier.testTag("preset_${testTag}_${p.label}")) }
            }
        }
        if (error != null) Text(error, style = EventsType.Caption, color = EventsColors.Danger, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }

    if (showDate) {
        val initial = value ?: System.currentTimeMillis()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = Instant.ofEpochMilli(initial).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton({
                    dateState.selectedDateMillis?.let { pickedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("Next", color = EventsColors.Coral) }
            },
            dismissButton = { TextButton({ showDate = false }) { Text("Cancel", color = EventsColors.TextSecondary) } },
        ) { DatePicker(dateState) }
    }
    pickedDate?.let { date ->
        val start = value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() } ?: LocalTime.of(9, 0)
        val timeState = rememberTimePickerState(start.hour, start.minute, is24Hour = false)
        Dialog(onDismissRequest = { pickedDate = null }) {
            Column(Modifier.clip(EventsShapes.XLarge).background(EventsColors.Surface).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Pick a time", style = EventsType.Title, color = EventsColors.Text)
                Spacer(Modifier.height(12.dp))
                TimePicker(timeState)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ pickedDate = null }) { Text("Cancel", color = EventsColors.TextSecondary) }
                    TextButton({
                        onChange(date.atTime(timeState.hour, timeState.minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        pickedDate = null
                    }) { Text("Done", color = EventsColors.Coral) }
                }
            }
        }
    }
}
