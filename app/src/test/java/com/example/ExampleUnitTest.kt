package com.example

import com.example.domain.*
import com.example.i18n.ArStrings
import com.example.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `T18 date and time formatting uses 0-9 digits and Algerian months`() {
        val formatted = DateTimeUtils.formatAlgerianDate("2026-01-15")
        assertEquals("15 جانفي 2026", formatted)
        assertEquals("4 أكتوبر 2026", DateTimeUtils.formatAlgerianDate("2026-10-04"))
        assertNull(DateTimeUtils.validateSchoolYearLabel("2026-2027"))
        assertNotNull(DateTimeUtils.validateSchoolYearLabel("2026-2028"))
        assertTrue(DateTimeUtils.isEndAfterStart("08:00", "09:00"))
        assertFalse(DateTimeUtils.isEndAfterStart("09:00", "08:00"))
        assertFalse(DateTimeUtils.isEndAfterStart("08:00", "08:00"))
    }

    @Test
    fun `D14 Arabic normalization strips tashkeel unifies hamzas ta marbuta and digits`() {
        val a = ArabicUtils.normalizeArabic("أَحْمَدُ بْنُ فاطِمَة ١٢٣")
        val b = ArabicUtils.normalizeArabic("احمد بن فاطمه 123")
        assertEquals(a, b)
        assertEquals("هدي", ArabicUtils.normalizeArabic("هُدَى"))
    }

    @Test
    fun `T30 T31 T32 T33 T34 T35 T37 GradingEngine pure calculations and edge cases`() {
        val defaultConfig = GradeTermConfig(
            schoolYearId = "y1",
            termId = "T1",
            ca2Active = true,
            ca2Required = true,
            as2Active = false,
            as2Required = false,
            weights = GradeWeights(1.0, 1.0, 2.0)
        )

        // T30: Score 0 counts as 0, never treated as missing
        val zeroRec = GradeRecord(
            gradebookId = "gb1",
            schoolYearId = "y1",
            classId = "c1",
            studentId = "s1",
            termId = "T1",
            orderIndex = 1,
            ca1 = 0.0,
            ca2 = 10.0,
            as1 = 0.0,
            exam = 10.0
        )
        val zeroEval = GradingEngine.evaluate(zeroRec, defaultConfig)
        assertTrue(zeroEval.isComplete)
        // caAvg = (0+10)/2 = 5, asAvg = 0, exam = 10 -> (5*1 + 0*1 + 10*2)/4 = 25/4 = 6.25
        assertEquals(6.25, zeroEval.finalAverage!!, 0.001)

        // T31 & T32: Missing required field -> no final average, names missing field
        val incompleteRec = zeroRec.copy(exam = null)
        val incEval = GradingEngine.evaluate(incompleteRec, defaultConfig)
        assertFalse(incEval.isComplete)
        assertNull(incEval.finalAverage)
        assertTrue(incEval.missingMessage.contains("الاختبار"))

        // T33: Optional active field (ca2Active = true, ca2Required = false)
        val optionalCa2Cfg = defaultConfig.copy(ca2Required = false)
        val recWithoutCa2 = zeroRec.copy(ca1 = 14.0, ca2 = null, as1 = 12.0, exam = 15.0)
        val optEval = GradingEngine.evaluate(recWithoutCa2, optionalCa2Cfg)
        assertTrue(optEval.isComplete)
        // caAvg = 14, asAvg = 12, exam = 15 -> (14 + 12 + 30)/4 = 14.0
        assertEquals(14.0, optEval.finalAverage!!, 0.001)

        // T34: Custom weights (2 / 2 / 4) and zero-weight category
        val zeroWeightCaCfg = defaultConfig.copy(weights = GradeWeights(0.0, 1.0, 2.0))
        val recMissingCa = zeroRec.copy(ca1 = null, ca2 = null, as1 = 12.0, exam = 15.0)
        val zwEval = GradingEngine.evaluate(recMissingCa, zeroWeightCaCfg)
        assertTrue(zwEval.isComplete)
        // (12*1 + 15*2)/3 = 42/3 = 14.0
        assertEquals(14.0, zwEval.finalAverage!!, 0.001)

        // T35: Invalid weights blocked
        assertNotNull(GradingEngine.validateWeights(GradeWeights(-1.0, 1.0, 2.0)))
        assertNotNull(GradingEngine.validateWeights(GradeWeights(0.0, 0.0, 0.0)))
    }

    @Test
    fun `T36 Statistics and T40 CSV export with BOM and injection guard`() {
        val cfg = GradeTermConfig(schoolYearId = "y1", termId = "T1")
        val s1 = Student(id = "s1", schoolYearId = "y1", classId = "c1", fullName = "تلميذ نشط", orderIndex = 1)
        val s2 = Student(id = "s2", schoolYearId = "y1", classId = "c1", fullName = "تلميذ مؤرشف", orderIndex = 2, isArchived = true)
        val s3 = Student(id = "s3", schoolYearId = "y1", classId = "c1", fullName = "=SUM(A1:A2)", orderIndex = 3)
        val studentsMap = mapOf(s1.id to s1, s2.id to s2, s3.id to s3)

        val r1 = GradeRecord(gradebookId = "gb", schoolYearId = "y1", classId = "c1", studentId = "s1", termId = "T1", orderIndex = 1, ca1 = 14.0, ca2 = 14.0, as1 = 12.0, exam = 15.0)
        val r2 = GradeRecord(gradebookId = "gb", schoolYearId = "y1", classId = "c1", studentId = "s2", termId = "T1", orderIndex = 2, ca1 = 8.0, ca2 = 8.0, as1 = 9.0, exam = 8.5)
        val r3 = GradeRecord(gradebookId = "gb", schoolYearId = "y1", classId = "c1", studentId = "s3", termId = "T1", orderIndex = 3, ca1 = 0.0, ca2 = null, as1 = null, exam = null)

        val stats = GradingEngine.computeStatistics(listOf(r1, r2, r3), studentsMap, cfg)
        assertNotNull(stats)
        assertEquals(2, stats!!.completeCount)
        assertEquals(3, stats.totalStudentsCount)
        assertEquals(1, stats.archivedIncludedCount)
        assertEquals(1, stats.passCount)
        assertEquals(1, stats.failCount)
        assertEquals(50.0, stats.passRate, 0.001)

        // Empty statistics when no complete record exists
        val emptyStats = GradingEngine.computeStatistics(listOf(r3), studentsMap, cfg)
        assertNull(emptyStats)

        // T40: CSV export
        val cls = SchoolClass(id = "c1", schoolYearId = "y1", level = "الأولى متوسط", name = "م 1")
        val csv = GradingEngine.exportGradebookCsv(
            schoolYearLabel = "2026-2027",
            schoolClass = cls,
            termId = "T1",
            config = cfg,
            students = listOf(s1, s2, s3),
            recordsByStudentId = mapOf(s1.id to r1, s2.id to r2, s3.id to r3),
            delimiter = ';',
            decimalMark = ','
        )
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("تلميذ مؤرشف (مؤرشف)"))
        assertTrue(csv.contains("'=SUM(A1:A2)")) // Formula injection guard
        assertFalse(csv.contains(ArStrings.PRIVATE_NOTE_NOTICE))
    }

    @Test
    fun `T56 WCAG contrast ratios for Rose and Dark themes meet AA requirements`() {
        val rosePalette = ContrastUtils.buildPalette("rose", null)
        assertTrue(ContrastUtils.contrastRatio(rosePalette.text, rosePalette.bg) >= 4.5)
        assertTrue(ContrastUtils.contrastRatio(rosePalette.text, rosePalette.surface) >= 4.5)
        assertTrue(ContrastUtils.contrastRatio(rosePalette.primaryText, rosePalette.surface) >= 4.5)
        assertFalse(rosePalette.hasLowOnPrimaryContrast)

        val darkPalette = ContrastUtils.buildPalette("dark", null)
        assertTrue(ContrastUtils.contrastRatio(darkPalette.text, darkPalette.bg) >= 4.5)
        assertTrue(ContrastUtils.contrastRatio(darkPalette.text, darkPalette.surface) >= 4.5)
        assertTrue(ContrastUtils.contrastRatio(darkPalette.primaryText, darkPalette.surface) >= 4.5)
        assertFalse(darkPalette.hasLowOnPrimaryContrast)
    }
}
