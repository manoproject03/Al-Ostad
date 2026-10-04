package com.example.domain

import com.example.i18n.ArStrings
import java.util.Calendar
import java.util.Locale

object ArabicUtils {
    private val TASHKEEL_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun toWesternDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Normalizes Arabic and Latin text for search and duplicate detection (D14):
     * - Strips tashkeel and tatweel
     * - Unifies أ إ آ ٱ -> ا, ى -> ي, ة -> ه, ؤ -> و, ئ -> ي
     * - Maps Arabic-Indic digits to 0-9
     * - Collapses whitespace and trims
     * - Case-folds Latin characters
     */
    fun normalizeArabic(text: String): String {
        if (text.isBlank()) return ""
        val withoutTashkeel = TASHKEEL_REGEX.replace(text, "")
        val sb = StringBuilder(withoutTashkeel.length)
        for (ch in withoutTashkeel) {
            when (ch) {
                '\u0640' -> {} // strip tatweel
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ى' -> sb.append('ي')
                'ة' -> sb.append('ه')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return WHITESPACE_REGEX.replace(sb.toString(), " ").trim()
    }

    /**
     * Parses a grade score input:
     * - Empty/blank -> ScoreParseResult.Empty (stores null, never 0!)
     * - Normalizes Arabic-Indic digits and decimal comma
     * - Rejects non-numeric or < 0 or > 20 with clear Arabic message
     */
    sealed class ScoreParseResult {
        data object Empty : ScoreParseResult()
        data class Valid(val value: Double) : ScoreParseResult()
        data class Invalid(val errorAr: String) : ScoreParseResult()
    }

    fun parseScoreInput(raw: String): ScoreParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ScoreParseResult.Empty
        val normalized = toWesternDigits(trimmed).replace('،', '.').replace(',', '.')
        val num = normalized.toDoubleOrNull()
            ?: return ScoreParseResult.Invalid("أدخلي قيمة رقمية صحيحة بين 0 و 20 (مثال: 14.5).")
        if (!num.isFinite()) {
            return ScoreParseResult.Invalid("القيمة الرقمية غير صالحة.")
        }
        if (num < 0.0 || num > 20.0) {
            return ScoreParseResult.Invalid("النقطة يجب أن تكون بين 0 و 20.")
        }
        return ScoreParseResult.Valid(num)
    }

    fun formatWesternNumber(value: Double, maxDecimals: Int = 2): String {
        val rounded = GradingEngine.round2(value)
        val asLong = rounded.toLong()
        return if (rounded == asLong.toDouble()) {
            String.format(Locale.US, "%d", asLong)
        } else {
            String.format(Locale.US, "%.${maxDecimals}f", rounded)
                .trimEnd('0')
                .trimEnd('.')
        }
    }
}

object DateTimeUtils {
    private val DATE_REGEX = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val TIME_REGEX = Regex("^(\\d{2}):(\\d{2})$")
    private val SCHOOL_YEAR_REGEX = Regex("^(\\d{4})-(\\d{4})$")

    var testFixedTodayDate: String? = null
    var testFixedNowHour: Int? = null

    fun todayDateString(): String {
        testFixedTodayDate?.let { return it }
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return formatYyyyMmDd(y, m, d)
    }

    fun currentHour24(): Int {
        testFixedNowHour?.let { return it }
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }

    fun currentTimeString(): String {
        val cal = Calendar.getInstance()
        val h = testFixedNowHour ?: cal.get(Calendar.HOUR_OF_DAY)
        val min = cal.get(Calendar.MINUTE)
        return String.format(Locale.US, "%02d:%02d", h, min)
    }

    fun nowIsoUtc(): String {
        val cal = Calendar.getInstance( java.util.TimeZone.getTimeZone("UTC") )
        return String.format(
            Locale.US,
            "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND),
            cal.get(Calendar.MILLISECOND)
        )
    }

    fun formatYyyyMmDd(year: Int, month: Int, day: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    fun formatHhMm(hour: Int, minute: Int): String =
        String.format(Locale.US, "%02d:%02d", hour, minute)

    fun parseYyyyMmDd(dateStr: String): Triple<Int, Int, Int>? {
        val normalized = ArabicUtils.toWesternDigits(dateStr.trim())
        val match = DATE_REGEX.matchEntire(normalized) ?: return null
        val y = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        val d = match.groupValues[3].toIntOrNull() ?: return null
        if (y < 1970 || y > 2100 || m !in 1..12) return null
        val maxDay = daysInMonth(y, m)
        if (d !in 1..maxDay) return null
        return Triple(y, m, d)
    }

    fun isValidDate(dateStr: String): Boolean = parseYyyyMmDd(dateStr) != null

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
        else -> 30
    }

    fun parseHhMm(timeStr: String): Pair<Int, Int>? {
        val normalized = ArabicUtils.toWesternDigits(timeStr.trim())
        val match = TIME_REGEX.matchEntire(normalized) ?: return null
        val h = match.groupValues[1].toIntOrNull() ?: return null
        val m = match.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return Pair(h, m)
    }

    fun isValidTime(timeStr: String): Boolean = parseHhMm(timeStr) != null

    fun timeToMinutes(timeStr: String): Int? {
        val (h, m) = parseHhMm(timeStr) ?: return null
        return h * 60 + m
    }

    /**
     * Returns true if endTime is strictly after startTime
     */
    fun isEndAfterStart(startTime: String, endTime: String): Boolean {
        val s = timeToMinutes(startTime) ?: return false
        val e = timeToMinutes(endTime) ?: return false
        return e > s
    }

    fun suggestPeriod(startTime: String): String {
        val mins = timeToMinutes(startTime) ?: return ArStrings.PERIOD_MORNING
        return if (mins < 12 * 60 + 30) ArStrings.PERIOD_MORNING else ArStrings.PERIOD_AFTERNOON
    }

    /**
     * Formats YYYY-MM-DD using Western digits 0-9 and Algerian month names:
     * e.g., "2026-10-04" -> "4 أكتوبر 2026"
     */
    fun formatAlgerianDate(dateStr: String, includeWeekday: Boolean = false): String {
        val (y, m, d) = parseYyyyMmDd(dateStr) ?: return dateStr
        val monthName = ArStrings.ALGERIAN_MONTHS.getOrElse(m - 1) { "" }
        val base = String.format(Locale.US, "%d %s %d", d, monthName, y)
        return if (includeWeekday) {
            val weekdayName = getWeekdayArabicName(dateStr)
            "$weekdayName $base"
        } else {
            base
        }
    }

    /**
     * Formats time range as «من 08:00 إلى 09:30» to avoid RTL bidi reversal
     */
    fun formatTimeRange(startTime: String, endTime: String): String {
        val s = ArabicUtils.toWesternDigits(startTime)
        val e = ArabicUtils.toWesternDigits(endTime)
        return "من \u2066$s\u2069 إلى \u2066$e\u2069"
    }

    /**
     * Returns school day index:
     * 0 = الأحد (Sunday), 1 = الاثنين (Monday), 2 = الثلاثاء (Tuesday),
     * 3 = الأربعاء (Wednesday), 4 = الخميس (Thursday),
     * 5 = الجمعة (Friday), 6 = السبت (Saturday)
     */
    fun getDayIndexFromSunday(dateStr: String): Int {
        val (y, m, d) = parseYyyyMmDd(dateStr) ?: return 0
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, d, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 0
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            else -> 0
        }
    }

    fun getWeekdayArabicName(dateStr: String): String {
        return when (getDayIndexFromSunday(dateStr)) {
            0 -> "الأحد"
            1 -> "الاثنين"
            2 -> "الثلاثاء"
            3 -> "الأربعاء"
            4 -> "الخميس"
            5 -> "الجمعة"
            6 -> "السبت"
            else -> "الأحد"
        }
    }

    fun addDays(dateStr: String, deltaDays: Int): String {
        val (y, m, d) = parseYyyyMmDd(dateStr) ?: return dateStr
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, d, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, deltaDays)
        return formatYyyyMmDd(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun daysBetween(startDate: String, endDate: String): Int {
        val (y1, m1, d1) = parseYyyyMmDd(startDate) ?: return 0
        val (y2, m2, d2) = parseYyyyMmDd(endDate) ?: return 0
        val c1 = Calendar.getInstance().apply {
            set(y1, m1 - 1, d1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val c2 = Calendar.getInstance().apply {
            set(y2, m2 - 1, d2, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffMillis = c2.timeInMillis - c1.timeInMillis
        return kotlin.math.round(diffMillis / (1000.0 * 60 * 60 * 24)).toInt()
    }

    /**
     * Validates school year label YYYY-YYYY where second = first + 1 (e.g., 2026-2027)
     */
    fun validateSchoolYearLabel(label: String): String? {
        val normalized = ArabicUtils.toWesternDigits(label.trim())
        val match = SCHOOL_YEAR_REGEX.matchEntire(normalized)
            ?: return "صيغة السنة الدراسية يجب أن تكون على الشكل YYYY-YYYY (مثال: 2026-2027)."
        val y1 = match.groupValues[1].toIntOrNull() ?: return "سنة البداية غير صالحة."
        val y2 = match.groupValues[2].toIntOrNull() ?: return "سنة النهاية غير صالحة."
        if (y2 != y1 + 1) {
            return "السنة الثانية يجب أن تلي السنة الأولى مباشرة (مثال: $y1-${y1 + 1})."
        }
        if (y1 !in 2000..2100) {
            return "يرجى إدخال سنة دراسية بين 2000 و 2100."
        }
        return null
    }

    fun greetingForCurrentHour(): String {
        val h = currentHour24()
        return if (h in 5..12) "صباح الخير والعطاء" else "مساء الخير والتألق"
    }
}
