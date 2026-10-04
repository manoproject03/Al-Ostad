package com.example.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.DateTimeUtils
import com.example.domain.SaveIndicatorState
import com.example.domain.SaveStatus
import com.example.i18n.ArStrings
import com.example.ui.theme.LocalTeacherPalette
import java.util.Locale

/**
 * Original inline vector logo: Open notebook + reed pen (قلم القصب) + ink drop detail (Section 6).
 */
@Composable
fun PlannerLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val palette = LocalTeacherPalette.current
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "شعار تطبيق الأستاذ: دفتر مفتوح وقلم قصب" }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Outer soft gold ring
        drawCircle(
            color = palette.goldAccent.copy(alpha = 0.28f),
            radius = w * 0.49f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Warm circular medallion background
        drawCircle(
            color = palette.primary,
            radius = w * 0.45f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Open notebook pages
        val bookPath = Path().apply {
            moveTo(w * 0.22f, h * 0.33f)
            quadraticTo(w * 0.36f, h * 0.28f, w * 0.50f, h * 0.35f)
            quadraticTo(w * 0.64f, h * 0.28f, w * 0.78f, h * 0.33f)
            lineTo(w * 0.78f, h * 0.67f)
            quadraticTo(w * 0.64f, h * 0.62f, w * 0.50f, h * 0.69f)
            quadraticTo(w * 0.36f, h * 0.62f, w * 0.22f, h * 0.67f)
            close()
        }
        drawPath(path = bookPath, color = Color(0xFFFFFCF8))

        // Center spine line
        drawLine(
            color = palette.primary,
            start = Offset(w * 0.50f, h * 0.35f),
            end = Offset(w * 0.50f, h * 0.69f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )

        // Subtle notebook lines on right page
        drawLine(
            color = palette.primary.copy(alpha = 0.28f),
            start = Offset(w * 0.56f, h * 0.44f),
            end = Offset(w * 0.72f, h * 0.43f),
            strokeWidth = w * 0.022f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = palette.primary.copy(alpha = 0.28f),
            start = Offset(w * 0.56f, h * 0.53f),
            end = Offset(w * 0.70f, h * 0.52f),
            strokeWidth = w * 0.022f,
            cap = StrokeCap.Round
        )

        // Reed pen (قلم القصب) diagonal across notebook
        val penPath = Path().apply {
            moveTo(w * 0.68f, h * 0.20f)
            lineTo(w * 0.75f, h * 0.27f)
            lineTo(w * 0.43f, h * 0.61f)
            lineTo(w * 0.35f, h * 0.63f)
            lineTo(w * 0.37f, h * 0.55f)
            close()
        }
        drawPath(path = penPath, color = Color(0xFFE5B869))

        // Warm gold ink drop flourish at tip
        drawCircle(
            color = Color(0xFFF6C973),
            radius = w * 0.042f,
            center = Offset(w * 0.32f, h * 0.70f)
        )
    }
}

/**
 * Decorative planner flourish used in onboarding and the home welcome card (Section 6).
 */
@Composable
fun PlannerCornerOrnament(
    modifier: Modifier = Modifier,
    tint: Color = LocalTeacherPalette.current.goldAccent.copy(alpha = 0.18f)
) {
    Canvas(modifier = modifier.size(96.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = tint,
            radius = w * 0.42f,
            center = Offset(w * 0.3f, h * 0.3f),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = tint,
            radius = w * 0.26f,
            center = Offset(w * 0.3f, h * 0.3f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

/**
 * Editorial section header with an RTL vertical accent bar and optional trailing content.
 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val palette = LocalTeacherPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.primary)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.text
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * Horizontally scrollable row of FilterChips / Segmented Pills so chips never squeeze or wrap letter-by-letter on narrow screens or large fonts.
 */
@Composable
fun <T> ScrollablePillRow(
    items: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    iconFor: ((T) -> ImageVector?)? = null,
    testTagPrefix: String? = null
) {
    val palette = LocalTeacherPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (value, label) ->
            val selected = value == selectedValue
            val icon = iconFor?.invoke(value)
            val chipMod = if (testTagPrefix != null) {
                Modifier
                    .heightIn(min = 48.dp)
                    .testTag("${testTagPrefix}_$value")
            } else {
                Modifier.heightIn(min = 48.dp)
            }
            FilterChip(
                selected = selected,
                onClick = { onSelect(value) },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                },
                leadingIcon = if (icon != null) {
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.primary,
                    selectedLabelColor = palette.onPrimary,
                    selectedLeadingIconColor = palette.onPrimary,
                    containerColor = palette.surface,
                    labelColor = palette.text
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = if (selected) palette.primary else palette.border
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = chipMod
            )
        }
    }
}

/**
 * D7: Save indicator in the top app bar showing icon + text:
 * «جارٍ الحفظ…» -> «تم الحفظ» or «تعذّر الحفظ»
 */
@Composable
fun SaveIndicatorBadge(
    state: SaveIndicatorState,
    modifier: Modifier = Modifier
) {
    var showErrorDetails by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = state.status != SaveStatus.IDLE,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val palette = LocalTeacherPalette.current
        val (icon, bgColor, contentColor, text) = when (state.status) {
            SaveStatus.SAVING -> Quadruple(
                Icons.Outlined.Sync,
                palette.muted,
                palette.text,
                ArStrings.SAVE_SAVING
            )
            SaveStatus.SAVED -> Quadruple(
                Icons.Filled.CheckCircle,
                palette.success.copy(alpha = 0.14f),
                palette.success,
                ArStrings.SAVE_SAVED
            )
            SaveStatus.FAILED -> Quadruple(
                Icons.Filled.ErrorOutline,
                palette.error.copy(alpha = 0.16f),
                palette.error,
                ArStrings.SAVE_FAILED
            )
            SaveStatus.IDLE -> Quadruple(
                Icons.Filled.CheckCircle,
                Color.Transparent,
                palette.textSecondary,
                ""
            )
        }

        Surface(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .clickable(enabled = state.status == SaveStatus.FAILED) {
                    showErrorDetails = true
                }
                .testTag("save_indicator_badge")
                .semantics { contentDescription = state.message.ifBlank { text } },
            color = bgColor,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, contentColor.copy(alpha = 0.38f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }

    if (showErrorDetails && state.status == SaveStatus.FAILED) {
        AlertDialog(
            onDismissRequest = { showErrorDetails = false },
            icon = {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = LocalTeacherPalette.current.error
                )
            },
            title = {
                Text(
                    text = ArStrings.SAVE_FAILED,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = state.errorReason ?: state.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showErrorDetails = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("حسنًا")
                }
            }
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * D11: Sample tag «تجريبية» (icon + text)
 */
@Composable
fun SampleBadge(modifier: Modifier = Modifier) {
    val palette = LocalTeacherPalette.current
    Surface(
        modifier = modifier.testTag("sample_badge"),
        color = palette.goldAccent.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, palette.goldAccent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Science,
                contentDescription = null,
                tint = palette.goldAccent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = ArStrings.TAG_SAMPLE,
                style = MaterialTheme.typography.labelMedium,
                color = palette.goldAccent,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * D6: Archived tag «مؤرشف» (icon + text)
 */
@Composable
fun ArchivedBadge(
    modifier: Modifier = Modifier,
    text: String = ArStrings.TAG_ARCHIVED
) {
    val palette = LocalTeacherPalette.current
    Surface(
        modifier = modifier.testTag("archived_badge"),
        color = palette.muted,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Inventory2,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Section 8.5 & 7: Lesson status chip with icon + text (never color alone):
 * مسودة · محضّر · أُنجز · أُجّل
 */
@Composable
fun LessonStatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalTeacherPalette.current
    val (icon, tint, bg) = when (status) {
        ArStrings.STATUS_PREPARED -> Triple(
            Icons.Outlined.BookmarkAdded,
            palette.primaryText,
            palette.primary.copy(alpha = 0.14f)
        )
        ArStrings.STATUS_COMPLETED -> Triple(
            Icons.Filled.CheckCircleOutline,
            palette.success,
            palette.success.copy(alpha = 0.14f)
        )
        ArStrings.STATUS_POSTPONED -> Triple(
            Icons.Outlined.EventRepeat,
            palette.warning,
            palette.warning.copy(alpha = 0.16f)
        )
        else -> Triple(
            Icons.Outlined.EditNote,
            palette.textSecondary,
            palette.muted
        )
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * D4: Accessible custom Algerian DateField (day / month with Algerian names / year selects).
 * Outputs YYYY-MM-DD using Western digits 0-9.
 */
@Composable
fun AlgerianDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTeacherPalette.current
    val parsed = DateTimeUtils.parseYyyyMmDd(value)
        ?: DateTimeUtils.parseYyyyMmDd(DateTimeUtils.todayDateString())
        ?: Triple(2026, 10, 4)
    val year = parsed.first
    val month = parsed.second
    val day = parsed.third

    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = palette.text,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, palette.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = palette.raised,
                contentColor = palette.text
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = palette.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = DateTimeUtils.formatAlgerianDate(value, includeWeekday = true),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
            }
        }
    }

    if (showDialog) {
        var selYear by remember(value) { mutableIntStateOf(year) }
        var selMonth by remember(value) { mutableIntStateOf(month) }
        var selDay by remember(value) { mutableIntStateOf(day) }

        val currentMaxDay = DateTimeUtils.daysInMonth(selYear, selMonth)
        if (selDay > currentMaxDay) selDay = currentMaxDay

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.text
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = palette.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = DateTimeUtils.formatAlgerianDate(
                                DateTimeUtils.formatYyyyMmDd(selYear, selMonth, selDay),
                                includeWeekday = true
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.primaryText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    DropdownRowSelector(
                        title = "اليوم",
                        selectedLabel = String.format(Locale.US, "%d", selDay),
                        options = (1..currentMaxDay).map { d -> d to String.format(Locale.US, "%d", d) },
                        onSelect = { selDay = it }
                    )

                    DropdownRowSelector(
                        title = "الشهر (الأشهر الجزائرية)",
                        selectedLabel = ArStrings.ALGERIAN_MONTHS.getOrElse(selMonth - 1) { "" },
                        options = ArStrings.ALGERIAN_MONTHS.mapIndexed { idx, mName ->
                            (idx + 1) to "${idx + 1} - $mName"
                        },
                        onSelect = { selMonth = it }
                    )

                    DropdownRowSelector(
                        title = "السنة",
                        selectedLabel = String.format(Locale.US, "%d", selYear),
                        options = (2020..2035).map { y -> y to String.format(Locale.US, "%d", y) },
                        onSelect = { selYear = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val validDay = selDay.coerceIn(1, DateTimeUtils.daysInMonth(selYear, selMonth))
                        onValueChange(DateTimeUtils.formatYyyyMmDd(selYear, selMonth, validDay))
                        showDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("تأكيد التاريخ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

/**
 * D4: Accessible custom 24h TimeField (hour / minute selects outputting HH:mm with 0-9 digits).
 */
@Composable
fun Time24Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTeacherPalette.current
    val parsed = DateTimeUtils.parseHhMm(value) ?: (8 to 0)
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = palette.text,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, palette.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = palette.raised,
                contentColor = palette.text
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = palette.primaryText,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = String.format(Locale.US, "%02d:%02d", parsed.first, parsed.second),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.text
                )
            }
        }
    }

    if (showDialog) {
        var selHour by remember(value) { mutableIntStateOf(parsed.first) }
        var selMin by remember(value) { mutableIntStateOf(parsed.second) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = palette.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "التوقيت المختار: ${String.format(Locale.US, "%02d:%02d", selHour, selMin)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.primaryText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    DropdownRowSelector(
                        title = "الساعة (نظام 24 سا)",
                        selectedLabel = String.format(Locale.US, "%02d", selHour),
                        options = (6..20).map { h -> h to String.format(Locale.US, "%02d", h) },
                        onSelect = { selHour = it }
                    )
                    DropdownRowSelector(
                        title = "الدقيقة",
                        selectedLabel = String.format(Locale.US, "%02d", selMin),
                        options = listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55).map { m ->
                            m to String.format(Locale.US, "%02d", m)
                        },
                        onSelect = { selMin = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onValueChange(DateTimeUtils.formatHhMm(selHour, selMin))
                        showDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("تأكيد الوقت")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun <T> DropdownRowSelector(
    title: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalTeacherPalette.current
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = palette.textSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.raised,
                    contentColor = palette.text
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                options.forEach { (value, labelText) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TeacherEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalTeacherPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(palette.primary.copy(alpha = 0.12f))
                    .border(1.dp, palette.primary.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.primaryText,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.text,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = true
) {
    val palette = LocalTeacherPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isDestructive) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                contentDescription = null,
                tint = if (isDestructive) palette.error else palette.primaryText
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) palette.error else palette.primary,
                    contentColor = if (isDestructive) Color.White else palette.onPrimary
                )
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("إلغاء")
            }
        }
    )
}
