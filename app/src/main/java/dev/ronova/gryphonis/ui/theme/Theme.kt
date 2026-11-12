package dev.ronova.gryphonis.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val GryphPink = Color(0xFFFF8FC7)
val GryphLilac = Color(0xFFCFA9F5)
val GryphSky = Color(0xFF7FCBFF)

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

@Composable
fun GryphonisTheme(darkTheme: Boolean, dynamicColor: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
