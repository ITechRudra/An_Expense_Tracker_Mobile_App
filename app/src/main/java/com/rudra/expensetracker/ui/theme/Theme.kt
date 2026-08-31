package com.rudra.expensetracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rudra.expensetracker.data.prefs.ThemeMode

// A deep green primary reads as "money" without the alarm of red/green ledgers,
// and leaves red free to mean exactly one thing in this app: an expense.
private val Green40 = Color(0xFF2E6B4F)
private val Green80 = Color(0xFF9BD5B4)
private val Sand40 = Color(0xFF6B5E2E)
private val Sand80 = Color(0xFFD5C89B)

val ExpenseRed = Color(0xFFC5372C)
val IncomeGreen = Color(0xFF1E7A45)
val ExpenseRedDark = Color(0xFFFF8A80)
val IncomeGreenDark = Color(0xFF7BD9A3)

private val LightColors = lightColorScheme(
    primary = Green40,
    secondary = Sand40,
    tertiary = Color(0xFF3A5F7D),
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    secondary = Sand80,
    tertiary = Color(0xFFA6C8E8),
)

private val AppTypography = Typography(
    // Amounts carry the screen, so the display styles are tightened and
    // weighted rather than left at the airy Material defaults.
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ExpenseTrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}

/** Direction colours that stay legible against either scheme. */
@Composable
fun expenseColor(): Color = if (isDarkTheme()) ExpenseRedDark else ExpenseRed

@Composable
fun incomeColor(): Color = if (isDarkTheme()) IncomeGreenDark else IncomeGreen

@Composable
private fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminanceIsDark()

private fun Color.luminanceIsDark(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5
