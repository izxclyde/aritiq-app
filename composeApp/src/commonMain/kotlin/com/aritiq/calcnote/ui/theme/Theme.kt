package com.aritiq.calcnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * CalcNote paper-themed Material 3 theme.
 *
 * Light mode: Classic notebook aesthetic
 *   - Cream background with subtle warmth
 *   - Warm brown text for excellent readability
 *   - Muted, earthy accent colors
 *   - Serif headers, monospace editor, sans-serif body
 *   - Soft, diffuse shadows
 *
 * Dark mode: Warm sepia notebook
 *   - Deep brown/sepia background
 *   - Cream text for comfortable reading
 *   - Adjusted accents for dark backgrounds
 *   - Same font choices, adapted for dark mode
 */
@Composable
fun CalcNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkPaperColorScheme else LightPaperColorScheme
    val typography = PaperTypography
    val shapes = PaperShapes

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}

// MARK: - Shapes

val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// MARK: - Typography

/**
 * Paper-themed typography using system fonts:
 * - Display/Headline: Serif (Playfair Display-like) for titles and headers
 * - Body: Sans-serif (Inter-like) for body text
 * - Label: Sans-serif for UI labels
 * - Editor: Monospace (JetBrains Mono-like) for the editor
 */
val PaperTypography = Typography(
    // Display styles - Serif for dramatic titles
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = -0.25.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),

    // Headline styles - Serif for section headers
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),

    // Title styles - Serif for card titles
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // Body styles - Sans-serif for body text
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),

    // Label styles - Sans-serif for UI labels
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

// MARK: - Editor Text Style

/**
 * Text style for the editor (BasicTextField).
 * Monospace font with comfortable line height for coding/note-taking.
 */
@Composable
fun editorTextStyle(): TextStyle {
    return TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    )
}

// MARK: - Color Schemes

private val LightPaperColorScheme = lightColorScheme(
    // Primary - deep teal for actions and accents
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB),
    onPrimaryContainer = Color(0xFF00201E),

    // Secondary - warm orange for highlights
    secondary = Color(0xFFE65100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0B2),
    onSecondaryContainer = Color(0xFF3E2723),

    // Tertiary - soft purple for variety
    tertiary = Color(0xFF6A1B9A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1BEE7),
    onTertiaryContainer = Color(0xFF311B92),

    // Error - muted red
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFFB71C1C),

    // Background - cream paper
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF3E2723),

    // Surface - slightly warmer than background for cards
    surface = Color(0xFFF5F3F0),
    onSurface = Color(0xFF3E2723),

    // Surface variants for elevated surfaces
    surfaceContainer = Color(0xFFEEEBE6),
    surfaceContainerLow = Color(0xFFF5F3F0),
    surfaceContainerHigh = Color(0xFFE8E5E0),
    surfaceContainerHighest = Color(0xFFE0DCD7),

    // Outline - warm brown for borders
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFD7CCC8),

    // Inverse
    inverseSurface = Color(0xFF3E2723),
    inverseOnSurface = Color(0xFFF5F5DC),
    inversePrimary = Color(0xFF80CBC4),

    // Scrim
    scrim = Color(0xFF3E2723),

    // Surface tint
    surfaceTint = Color(0xFF00695C),
)

private val DarkPaperColorScheme = darkColorScheme(
    // Primary - lighter teal for dark mode
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFFB2DFDB),

    // Secondary - lighter orange for dark mode
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFFE65100),
    onSecondaryContainer = Color(0xFFFFE0B2),

    // Tertiary - lighter purple for dark mode
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF311B92),
    tertiaryContainer = Color(0xFF4A148C),
    onTertiaryContainer = Color(0xFFE1BEE7),

    // Error - lighter red for dark mode
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF3E2723),
    errorContainer = Color(0xFFB71C1C),
    onErrorContainer = Color(0xFFFFCDD2),

    // Background - dark brown/sepia paper
    background = Color(0xFF3E2723),
    onBackground = Color(0xFFF5F5DC),

    // Surface - slightly lighter than background
    surface = Color(0xFF4E342E),
    onSurface = Color(0xFFF5F5DC),

    // Surface variants — lightened so elevated cards are visibly distinct from the dark
    // sepia background. Each step is ~1.5-2.6:1 against background (above the 1.5:1
    // elevation-perception threshold) while keeping text contrast >= 4.78:1 (AA).
    surfaceContainerLow = Color(0xFF5E4038),
    surfaceContainer = Color(0xFF6E4D43),
    surfaceContainerHigh = Color(0xFF7B564A),
    surfaceContainerHighest = Color(0xFF886356),

    // Outline - warm brown for borders
    outline = Color(0xFFBCAAA4),
    outlineVariant = Color(0xFF8D6E63),

    // Inverse
    inverseSurface = Color(0xFFF5F5DC),
    inverseOnSurface = Color(0xFF3E2723),
    inversePrimary = Color(0xFF00695C),

    // Scrim
    scrim = Color.Black,

    // Surface tint
    surfaceTint = Color(0xFF80CBC4),
)

/**
 * Access the paper color scheme from anywhere in the compose tree.
 */
@Composable
fun paperColorScheme() = MaterialTheme.colorScheme