package dev.ronova.gryphonis.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import dev.ronova.gryphonis.data.AppPalette

// ---- фирменные цвета «радуги» (тема Gryphonis и иконка приложения)
val GryphPink = Color(0xFFFF8FC7)
val GryphLilac = Color(0xFFCFA9F5)
val GryphSky = Color(0xFF7FCBFF)

/**
 * Что ещё зависит от темы, кроме цветов Material: переливается ли акцент (только фирменная тема)
 * и как красить папки. В однотонных темах папка — один цвет: задняя стенка темнее, передняя панель светлее.
 */
@Immutable
data class GryphExtras(
    val rainbow: Boolean,
    val folderBack: Color,
    val folderFront: Color,
)

private val DefaultExtras = GryphExtras(rainbow = false, folderBack = Color(0xFFC96A4E), folderFront = Color(0xFFEE8A70))

val LocalGryphExtras = staticCompositionLocalOf { DefaultExtras }

object GryphTheme {
    val extras: GryphExtras
        @Composable @ReadOnlyComposable get() = LocalGryphExtras.current
}

// =====================================================================================
// Пепел + коралл (тема по умолчанию)
// =====================================================================================

private val AshCoralDark = darkColorScheme(
    primary = Color(0xFFEE8A70),
    onPrimary = Color(0xFF3B1409),
    primaryContainer = Color(0xFF7A3A28),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFD9CABA),
    onSecondary = Color(0xFF382F25),
    secondaryContainer = Color(0xFF4A443D),
    onSecondaryContainer = Color(0xFFEFE4D7),
    tertiary = Color(0xFFE6B691),
    onTertiary = Color(0xFF3A2615),
    tertiaryContainer = Color(0xFF54402F),
    onTertiaryContainer = Color(0xFFFFE0C7),
    background = Color(0xFF262624),
    onBackground = Color(0xFFF2F0E8),
    surface = Color(0xFF262624),
    onSurface = Color(0xFFF2F0E8),
    surfaceVariant = Color(0xFF403E3A),
    onSurfaceVariant = Color(0xFFCCC8BC),
    outline = Color(0xFF99968B),
    outlineVariant = Color(0xFF4D4B46),
    surfaceContainerLowest = Color(0xFF1F1E1C),
    surfaceContainerLow = Color(0xFF2B2A28),
    surfaceContainer = Color(0xFF31302E),
    surfaceContainerHigh = Color(0xFF3A3936),
    surfaceContainerHighest = Color(0xFF454440),
)

// Кремово-жёлтая светлая с коралловым
private val CreamCoralLight = lightColorScheme(
    primary = Color(0xFFB5442A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8CA),
    onPrimaryContainer = Color(0xFF4A1405),
    secondary = Color(0xFF75664C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE2BA),
    onSecondaryContainer = Color(0xFF3A3015),
    tertiary = Color(0xFF8A5A2B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF6DDB8),
    onTertiaryContainer = Color(0xFF38200A),
    background = Color(0xFFFBF4DC),
    onBackground = Color(0xFF2A251C),
    surface = Color(0xFFFBF4DC),
    onSurface = Color(0xFF2A251C),
    surfaceVariant = Color(0xFFEDE3C2),
    onSurfaceVariant = Color(0xFF5A5242),
    outline = Color(0xFF857C62),
    outlineVariant = Color(0xFFD8CDA8),
    surfaceContainerLowest = Color(0xFFFFFCEE),
    surfaceContainerLow = Color(0xFFF6EFD3),
    surfaceContainer = Color(0xFFF1E9CB),
    surfaceContainerHigh = Color(0xFFEBE2C1),
    surfaceContainerHighest = Color(0xFFE5DBB8),
)

// =====================================================================================
// Фирменная Gryphonis: розово-лиловая с небесным
// =====================================================================================

private val BrandLight = lightColorScheme(
    primary = Color(0xFFB93A7E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8EA),
    onPrimaryContainer = Color(0xFF3E001F),
    secondary = Color(0xFF0A6C94),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC5E8FF),
    onSecondaryContainer = Color(0xFF001E2E),
    tertiary = Color(0xFF6B5BA8),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8DEFF),
    onTertiaryContainer = Color(0xFF230A5C),
    background = Color(0xFFFFF8F9),
    onBackground = Color(0xFF211A1D),
    surface = Color(0xFFFFF8F9),
    onSurface = Color(0xFF211A1D),
    surfaceVariant = Color(0xFFF4DDE5),
    onSurfaceVariant = Color(0xFF524349),
    outline = Color(0xFF85737A),
    outlineVariant = Color(0xFFD8C2C9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F4),
    surfaceContainer = Color(0xFFFCEAEF),
    surfaceContainerHigh = Color(0xFFF6E4E9),
    surfaceContainerHighest = Color(0xFFF0DEE3),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFFFB0D3),
    onPrimary = Color(0xFF620A3A),
    primaryContainer = Color(0xFF7F2252),
    onPrimaryContainer = Color(0xFFFFD8EA),
    secondary = Color(0xFF85CFF9),
    onSecondary = Color(0xFF00344B),
    secondaryContainer = Color(0xFF004C6B),
    onSecondaryContainer = Color(0xFFC5E8FF),
    tertiary = Color(0xFFCDBDFF),
    onTertiary = Color(0xFF38256F),
    tertiaryContainer = Color(0xFF503E87),
    onTertiaryContainer = Color(0xFFE8DEFF),
    background = Color(0xFF181216),
    onBackground = Color(0xFFEBDFE3),
    surface = Color(0xFF181216),
    onSurface = Color(0xFFEBDFE3),
    surfaceVariant = Color(0xFF50434A),
    onSurfaceVariant = Color(0xFFD4C2C9),
    outline = Color(0xFF9D8C93),
    outlineVariant = Color(0xFF50434A),
    surfaceContainerLowest = Color(0xFF130D11),
    surfaceContainerLow = Color(0xFF201A1E),
    surfaceContainer = Color(0xFF241E22),
    surfaceContainerHigh = Color(0xFF2F282D),
    surfaceContainerHighest = Color(0xFF3A3338),
)

// =====================================================================================
// Однотонные темы: один акцентный цвет, никаких переливов
// =====================================================================================

/** Ключевые цвета однотонной темы; остальное выводится из них. */
private class Mono(
    val accentDark: Color,
    val accentLight: Color,
    val bgDark: Color,
    val inkDark: Color,
    val bgLight: Color,
    val inkLight: Color,
)

private val OceanMono = Mono(
    accentDark = Color(0xFF6CB4F0), accentLight = Color(0xFF1B6FB5),
    bgDark = Color(0xFF13181E), inkDark = Color(0xFFE6ECF2),
    bgLight = Color(0xFFF3F8FC), inkLight = Color(0xFF14202B),
)
private val ForestMono = Mono(
    accentDark = Color(0xFF6FCB8A), accentLight = Color(0xFF2B7A4C),
    bgDark = Color(0xFF121A15), inkDark = Color(0xFFE3EDE6),
    bgLight = Color(0xFFF2F8F3), inkLight = Color(0xFF15251B),
)
private val VioletMono = Mono(
    accentDark = Color(0xFFB79CF2), accentLight = Color(0xFF6A48C2),
    bgDark = Color(0xFF17141D), inkDark = Color(0xFFEAE5F3),
    bgLight = Color(0xFFF7F4FC), inkLight = Color(0xFF20192E),
)
private val RoseMono = Mono(
    accentDark = Color(0xFFF58BB0), accentLight = Color(0xFFBE3F72),
    bgDark = Color(0xFF1C1316), inkDark = Color(0xFFF3E6EB),
    bgLight = Color(0xFFFDF3F6), inkLight = Color(0xFF2D1820),
)
private val AmberMono = Mono(
    accentDark = Color(0xFFF2B544), accentLight = Color(0xFF9A5F00),
    bgDark = Color(0xFF1A1710), inkDark = Color(0xFFF1EBDA),
    bgLight = Color(0xFFFCF7EA), inkLight = Color(0xFF2B2310),
)
private val GraphiteMono = Mono(
    accentDark = Color(0xFFDADADA), accentLight = Color(0xFF3B3B3B),
    bgDark = Color(0xFF141414), inkDark = Color(0xFFEDEDED),
    bgLight = Color(0xFFF5F5F5), inkLight = Color(0xFF1A1A1A),
)

/** Цвет текста/иконок на заливке [bg]: чёрный или белый — смотря что контрастнее. */
private fun onColorFor(bg: Color): Color {
    val l = bg.luminance()
    val black = (l + 0.05f) / 0.05f
    val white = 1.05f / (l + 0.05f)
    return if (black >= white) Color(0xFF1B1B1B) else Color.White
}

private fun monoScheme(m: Mono, dark: Boolean): ColorScheme {
    val bg = if (dark) m.bgDark else m.bgLight
    val ink = if (dark) m.inkDark else m.inkLight
    val accent = if (dark) m.accentDark else m.accentLight
    val container = lerp(bg, accent, if (dark) 0.30f else 0.20f)
    val onContainer = if (dark) lerp(accent, Color.White, 0.78f) else lerp(accent, Color.Black, 0.62f)
    val secondary = lerp(accent, ink, 0.28f)
    val tertiary = lerp(accent, ink, 0.48f)
    val secondaryContainer = lerp(bg, accent, if (dark) 0.18f else 0.13f)
    val tertiaryContainer = lerp(bg, accent, if (dark) 0.12f else 0.09f)
    val onSurfaceVariant = lerp(ink, bg, if (dark) 0.25f else 0.30f)

    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onColorFor(accent),
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = secondary,
            onSecondary = onColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = lerp(secondary, Color.White, 0.7f),
            tertiary = tertiary,
            onTertiary = onColorFor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = lerp(tertiary, Color.White, 0.7f),
            background = bg,
            onBackground = ink,
            surface = bg,
            onSurface = ink,
            surfaceVariant = lerp(bg, ink, 0.16f),
            onSurfaceVariant = onSurfaceVariant,
            outline = lerp(ink, bg, 0.52f),
            outlineVariant = lerp(bg, ink, 0.20f),
            surfaceContainerLowest = lerp(bg, Color.Black, 0.30f),
            surfaceContainerLow = lerp(bg, ink, 0.04f),
            surfaceContainer = lerp(bg, ink, 0.07f),
            surfaceContainerHigh = lerp(bg, ink, 0.11f),
            surfaceContainerHighest = lerp(bg, ink, 0.15f),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onColorFor(accent),
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = secondary,
            onSecondary = onColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = lerp(secondary, Color.Black, 0.6f),
            tertiary = tertiary,
            onTertiary = onColorFor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = lerp(tertiary, Color.Black, 0.6f),
            background = bg,
            onBackground = ink,
            surface = bg,
            onSurface = ink,
            surfaceVariant = lerp(bg, ink, 0.09f),
            onSurfaceVariant = onSurfaceVariant,
            outline = lerp(ink, bg, 0.50f),
            outlineVariant = lerp(bg, ink, 0.16f),
            surfaceContainerLowest = lerp(bg, Color.White, 0.65f),
            surfaceContainerLow = lerp(bg, ink, 0.03f),
            surfaceContainer = lerp(bg, ink, 0.055f),
            surfaceContainerHigh = lerp(bg, ink, 0.085f),
            surfaceContainerHighest = lerp(bg, ink, 0.115f),
        )
    }
}

/** Цветовая схема Material для выбранной палитры. */
fun schemeFor(palette: AppPalette, dark: Boolean): ColorScheme = when (palette) {
    AppPalette.DEFAULT -> if (dark) AshCoralDark else CreamCoralLight
    AppPalette.GRYPHONIS -> if (dark) BrandDark else BrandLight
    AppPalette.OCEAN -> monoScheme(OceanMono, dark)
    AppPalette.FOREST -> monoScheme(ForestMono, dark)
    AppPalette.VIOLET -> monoScheme(VioletMono, dark)
    AppPalette.ROSE -> monoScheme(RoseMono, dark)
    AppPalette.AMBER -> monoScheme(AmberMono, dark)
    AppPalette.GRAPHITE -> monoScheme(GraphiteMono, dark)
}

private fun extrasFor(scheme: ColorScheme, rainbow: Boolean): GryphExtras = GryphExtras(
    rainbow = rainbow,
    folderBack = lerp(scheme.primary, Color.Black, 0.22f),
    folderFront = lerp(scheme.primary, Color.White, 0.10f),
)

/** Цвета для предпросмотра палитры в настройках: фон, акцент. */
fun previewColors(palette: AppPalette, dark: Boolean): Pair<Color, Color> {
    val s = schemeFor(palette, dark)
    return s.surface to s.primary
}

@Composable
fun GryphonisTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    palette: AppPalette = AppPalette.DEFAULT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = if (useDynamic) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        schemeFor(palette, darkTheme)
    }
    val rainbow = !useDynamic && palette == AppPalette.GRYPHONIS
    val extras = remember(scheme, rainbow) { extrasFor(scheme, rainbow) }
    CompositionLocalProvider(LocalGryphExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
