package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val NotoSansArabicFontFamily = FontFamily(
    Font(R.font.noto_sans_arabic, FontWeight.Normal),
    Font(R.font.noto_sans_arabic, FontWeight.SemiBold),
    Font(R.font.noto_sans_arabic, FontWeight.Bold)
)

val ArefRuqaaFontFamily = FontFamily(
    Font(R.font.aref_ruqaa, FontWeight.Bold)
)

/**
 * Builds Arabic typography with line-height >= 1.7 and zero letter-spacing on Arabic (Section 6).
 * Supports "normal" (16sp base) and "large" (20sp base = 1.25x scale).
 */
fun buildTeacherTypography(isLargeFont: Boolean): Typography {
    val scale = if (isLargeFont) 1.22f else 1.0f
    return Typography(
        displayLarge = TextStyle(
            fontFamily = ArefRuqaaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (30f * scale).sp,
            lineHeight = (52f * scale).sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = NotoSansArabicFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (24f * scale).sp,
            lineHeight = (42f * scale).sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = NotoSansArabicFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (20f * scale).sp,
            lineHeight = (35f * scale).sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = NotoSansArabicFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (18f * scale).sp,
            lineHeight = (31f * scale).sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = NotoSansArabicFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (16f * scale).sp,
            lineHeight = (28f * scale).sp,
            letterSpacing = 0.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = NotoSansArabicFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (16f * scale).sp,
            lineHeight = (28f * scale).sp,
            letterSpacing = 0.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = NotoSansArabicFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14.5f * scale).sp,
            lineHeight = (25f * scale).sp,
            letterSpacing = 0.sp
        ),
        labelLarge = TextStyle(
            fontFamily = NotoSansArabicFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (14f * scale).sp,
            lineHeight = (24f * scale).sp,
            letterSpacing = 0.sp
        ),
        labelMedium = TextStyle(
            fontFamily = NotoSansArabicFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (12.5f * scale).sp,
            lineHeight = (22f * scale).sp,
            letterSpacing = 0.sp
        )
    )
}

val Typography = buildTeacherTypography(isLargeFont = false)
