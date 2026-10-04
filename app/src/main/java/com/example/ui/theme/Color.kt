package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Rose (Warm Light) Theme Tokens — Section 6
val RoseBg = Color(0xFFFBF3EC)
val RoseSurface = Color(0xFFFFF9F4)
val RoseRaised = Color(0xFFFFFCF8)
val RoseMuted = Color(0xFFF4E6DC)
val RoseBorder = Color(0xFFE3CFC4)
val RoseText = Color(0xFF3B2A2E)
val RoseTextSecondary = Color(0xFF6B5359)
val RosePrimary = Color(0xFFB04468)
val RoseOnPrimary = Color(0xFFFFFFFF)
val RoseGoldAccent = Color(0xFF8C5E14)
val RoseSuccess = Color(0xFF236B42)
val RoseWarning = Color(0xFF8F5700)
val RoseError = Color(0xFFB3261E)

// Dark (Warm Slate/Charcoal) Theme Tokens — Section 6
val DarkBg = Color(0xFF1F1B1D)
val DarkSurface = Color(0xFF2A2528)
val DarkRaised = Color(0xFF352F33)
val DarkMuted = Color(0xFF3D353A)
val DarkBorder = Color(0xFF54494F)
val DarkText = Color(0xFFF3E9E4)
val DarkTextSecondary = Color(0xFFD0C0C6)
val DarkPrimary = Color(0xFFE58BA6)
val DarkOnPrimary = Color(0xFF2A1118)
val DarkGoldAccent = Color(0xFFE5B869)
val DarkSuccess = Color(0xFF7FD9A2)
val DarkWarning = Color(0xFFF5C26B)
val DarkError = Color(0xFFFF897D)

data class TeacherPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val muted: Color,
    val border: Color,
    val text: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryText: Color,
    val goldAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val onPrimaryContrastRatio: Double,
    val hasLowOnPrimaryContrast: Boolean
)

object ContrastUtils {

    val PRESET_CUSTOM_COLORS = listOf(
        "#B04468" to "وردي دافئ (الافتراضي)",
        "#8E3B46" to "عنابي كلاسيكي",
        "#2B6777" to "أزرق بترولي هادئ",
        "#3B6E5C" to "أخضر زيتوني راقٍ",
        "#6B4C8C" to "بنفسجي ملكي",
        "#9C5527" to "نحاسي صحراوي",
        "#F3D250" to "أصفر فاتح (للتجربة)"
    )

    fun parseHexColor(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length != 6) return null
        val rgb = cleaned.toLongOrNull(16) ?: return null
        return Color(0xFF000000L or rgb)
    }

    fun toHexString(color: Color): String {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        return String.format(java.util.Locale.US, "#%02X%02X%02X", r, g, b)
    }

    private fun channelLuminance(c: Float): Double {
        val v = c.toDouble().coerceIn(0.0, 1.0)
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun relativeLuminance(color: Color): Double {
        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * WCAG 2.1 contrast ratio between two colors (1.0 .. 21.0)
     */
    fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Section 6: Automatically picks white or near-black for --on-primary,
     * whichever has the higher WCAG contrast.
     */
    fun pickAutoOnPrimary(primary: Color): Pair<Color, Double> {
        val white = Color(0xFFFFFFFF)
        val nearBlack = Color(0xFF1F1619)
        val cWhite = contrastRatio(primary, white)
        val cBlack = contrastRatio(primary, nearBlack)
        return if (cWhite >= cBlack) {
            white to cWhite
        } else {
            nearBlack to cBlack
        }
    }

    /**
     * Adjusts primary color to have >= 4.5:1 contrast on surface for text/icons (--primary-text).
     */
    fun adjustPrimaryForSurfaceText(primary: Color, surface: Color, isDark: Boolean): Color {
        if (contrastRatio(primary, surface) >= 4.5) return primary
        var candidate = primary
        for (step in 1..20) {
            val factor = step * 0.05f
            candidate = if (isDark) {
                Color(
                    red = (primary.red + (1f - primary.red) * factor).coerceIn(0f, 1f),
                    green = (primary.green + (1f - primary.green) * factor).coerceIn(0f, 1f),
                    blue = (primary.blue + (1f - primary.blue) * factor).coerceIn(0f, 1f),
                    alpha = 1f
                )
            } else {
                Color(
                    red = (primary.red * (1f - factor)).coerceIn(0f, 1f),
                    green = (primary.green * (1f - factor)).coerceIn(0f, 1f),
                    blue = (primary.blue * (1f - factor)).coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            if (contrastRatio(candidate, surface) >= 4.5) {
                return candidate
            }
        }
        return if (isDark) DarkText else RoseText
    }

    fun buildPalette(theme: String, customPrimaryHex: String?): TeacherPalette {
        val isDark = theme == "dark"
        val basePrimary = parseHexColor(customPrimaryHex)
            ?: if (isDark) DarkPrimary else RosePrimary
        val (autoOnPrimary, ratio) = pickAutoOnPrimary(basePrimary)
        val surface = if (isDark) DarkSurface else RoseSurface
        val primaryText = adjustPrimaryForSurfaceText(basePrimary, surface, isDark)

        return if (isDark) {
            TeacherPalette(
                isDark = true,
                bg = DarkBg,
                surface = DarkSurface,
                raised = DarkRaised,
                muted = DarkMuted,
                border = DarkBorder,
                text = DarkText,
                textSecondary = DarkTextSecondary,
                primary = basePrimary,
                onPrimary = autoOnPrimary,
                primaryText = primaryText,
                goldAccent = DarkGoldAccent,
                success = DarkSuccess,
                warning = DarkWarning,
                error = DarkError,
                onPrimaryContrastRatio = ratio,
                hasLowOnPrimaryContrast = ratio < 4.5
            )
        } else {
            TeacherPalette(
                isDark = false,
                bg = RoseBg,
                surface = RoseSurface,
                raised = RoseRaised,
                muted = RoseMuted,
                border = RoseBorder,
                text = RoseText,
                textSecondary = RoseTextSecondary,
                primary = basePrimary,
                onPrimary = autoOnPrimary,
                primaryText = primaryText,
                goldAccent = RoseGoldAccent,
                success = RoseSuccess,
                warning = RoseWarning,
                error = RoseError,
                onPrimaryContrastRatio = ratio,
                hasLowOnPrimaryContrast = ratio < 4.5
            )
        }
    }
}
