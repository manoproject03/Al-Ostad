package com.example.domain

import com.example.i18n.ArStrings
import kotlin.math.round

object GradingEngine {

    data class GradeEvaluation(
        val isComplete: Boolean,
        val finalAverage: Double?,
        val caAverage: Double?,
        val asAverage: Double?,
        val examScore: Double?,
        val missingFields: List<String>
    ) {
        val missingMessage: String
            get() = if (missingFields.isEmpty()) "" else "${ArStrings.MISSING_SCORES_PREFIX}${missingFields.joinToString("، ")}"
    }

    data class DistributionBin(
        val label: String,
        val minInclusive: Double,
        val maxLimit: Double,
        val count: Int
    )

    data class ClassStatistics(
        val completeCount: Int,
        val totalStudentsCount: Int,
        val archivedIncludedCount: Int,
        val maxAverage: Double,
        val minAverage: Double,
        val classAverage: Double,
        val passCount: Int,
        val failCount: Int,
        val passRate: Double,
        val bins: List<DistributionBin>
    )

    fun isFilled(v: Double?): Boolean = v != null

    /**
     * D8 Rounding: compute with full precision,
     * round2(x) = Math.round((x + Number.EPSILON) * 100) / 100
     */
    fun round2(x: Double): Double {
        if (!x.isFinite()) return 0.0
        return round((x + 1e-12) * 100.0) / 100.0
    }

    /**
     * Validates category weights per D8:
     * - Finite and >= 0
     * - Sum > 0
     */
    fun validateWeights(weights: GradeWeights): String? {
        val list = listOf(weights.ca, weights.asWeight, weights.exam)
        if (list.any { !it.isFinite() }) {
            return "معاملات الفئات يجب أن تكون أرقامًا صحيحة أو عشرية صالحة."
        }
        if (list.any { it < 0.0 }) {
            return "لا يمكن أن يكون معامل أي فئة سالبًا (يجب أن يكون 0 أو أكبر)."
        }
        val sum = weights.ca + weights.asWeight + weights.exam
        if (sum <= 0.0) {
            return "مجموع معاملات الفئات يجب أن يكون أكبر من الصفر."
        }
        return null
    }

    /**
     * Evaluates a single student's grade record according to D8 & D9:
     * - Fixed required fields (when category weight > 0): CA1, Assignment 1, Exam.
     * - Configurable per school-year + term: CA2 active/required, Assignment 2 active/required.
     * - A category average = mean of its active fields that have a value.
     * - A required active field must be filled for the result to be complete;
     *   an active-but-optional field counts only when filled.
     * - Values of a deactivated field are preserved in storage but ignored in calculation.
     * - A category with weight 0 is excluded from numerator and denominator and does not block completeness.
     */
    fun evaluate(record: GradeRecord, config: GradeTermConfig): GradeEvaluation {
        if (validateWeights(config.weights) != null) {
            return GradeEvaluation(
                isComplete = false,
                finalAverage = null,
                caAverage = null,
                asAverage = null,
                examScore = null,
                missingFields = emptyList()
            )
        }

        val missing = mutableListOf<String>()

        // 1. Continuous Assessment (التقويم المستمر)
        val caUsed = config.weights.ca > 0.0
        var caAvg: Double? = null
        if (caUsed) {
            if (!isFilled(record.ca1)) {
                missing.add("التقويم المستمر 1")
            }
            if (config.ca2Active && config.ca2Required && !isFilled(record.ca2)) {
                missing.add("التقويم المستمر 2")
            }
            val activeCaValues = mutableListOf<Double>()
            record.ca1?.let { activeCaValues.add(it) }
            if (config.ca2Active) {
                record.ca2?.let { activeCaValues.add(it) }
            }
            if (activeCaValues.isNotEmpty()) {
                caAvg = activeCaValues.sum() / activeCaValues.size.toDouble()
            }
        }

        // 2. Assignments (الفروض)
        val asUsed = config.weights.asWeight > 0.0
        var asAvg: Double? = null
        if (asUsed) {
            if (!isFilled(record.as1)) {
                missing.add("الفرض 1")
            }
            if (config.as2Active && config.as2Required && !isFilled(record.as2)) {
                missing.add("الفرض 2")
            }
            val activeAsValues = mutableListOf<Double>()
            record.as1?.let { activeAsValues.add(it) }
            if (config.as2Active) {
                record.as2?.let { activeAsValues.add(it) }
            }
            if (activeAsValues.isNotEmpty()) {
                asAvg = activeAsValues.sum() / activeAsValues.size.toDouble()
            }
        }

        // 3. Exam (الاختبار)
        val examUsed = config.weights.exam > 0.0
        var examVal: Double? = null
        if (examUsed) {
            if (!isFilled(record.exam)) {
                missing.add("الاختبار")
            } else {
                examVal = record.exam
            }
        }

        if (missing.isNotEmpty()) {
            return GradeEvaluation(
                isComplete = false,
                finalAverage = null,
                caAverage = caAvg,
                asAverage = asAvg,
                examScore = examVal,
                missingFields = missing
            )
        }

        var weightedSum = 0.0
        var weightTotal = 0.0

        if (caUsed && caAvg != null) {
            weightedSum += config.weights.ca * caAvg
            weightTotal += config.weights.ca
        }
        if (asUsed && asAvg != null) {
            weightedSum += config.weights.asWeight * asAvg
            weightTotal += config.weights.asWeight
        }
        if (examUsed && examVal != null) {
            weightedSum += config.weights.exam * examVal
            weightTotal += config.weights.exam
        }

        if (weightTotal <= 0.0) {
            return GradeEvaluation(
                isComplete = false,
                finalAverage = null,
                caAverage = caAvg,
                asAverage = asAvg,
                examScore = examVal,
                missingFields = emptyList()
            )
        }

        val finalRounded = round2(weightedSum / weightTotal)
        return GradeEvaluation(
            isComplete = true,
            finalAverage = finalRounded,
            caAverage = caAvg,
            asAverage = asAvg,
            examScore = examVal,
            missingFields = emptyList()
        )
    }

    /**
     * Builds the human-readable Arabic formula string (Section 8.6)
     */
    fun buildFormulaDescription(config: GradeTermConfig): String {
        val parts = mutableListOf<String>()
        val w = config.weights
        val totalW = w.ca + w.asWeight + w.exam
        if (w.ca > 0.0) {
            val label = if (config.ca2Active) "متوسط التقويم المستمر" else "التقويم المستمر 1"
            parts.add("$label × ${ArabicUtils.formatWesternNumber(w.ca)}")
        }
        if (w.asWeight > 0.0) {
            val label = if (config.as2Active) "متوسط الفروض" else "الفرض 1"
            parts.add("$label × ${ArabicUtils.formatWesternNumber(w.asWeight)}")
        }
        if (w.exam > 0.0) {
            parts.add("الاختبار × ${ArabicUtils.formatWesternNumber(w.exam)}")
        }
        if (parts.isEmpty() || totalW <= 0.0) {
            return "الصيغة غير مكتملة (مجموع المعاملات يساوي 0)"
        }
        return "المعدل = (${parts.joinToString(" + ")}) ÷ ${ArabicUtils.formatWesternNumber(totalW)}"
    }

    /**
     * D9 & D10 Statistics calculation:
     * - Only complete records are included.
     * - Archived students in the class are included if their record is complete;
     *   their count is tracked in archivedIncludedCount.
     * - Pass/fail (>= 10) uses the rounded finalAverage so display and classification agree.
     * - Returns null if completeCount == 0 (never shows zeros!).
     */
    fun computeStatistics(
        records: List<GradeRecord>,
        studentsById: Map<String, Student>,
        config: GradeTermConfig
    ): ClassStatistics? {
        val validStudentRecords = records.filter { studentsById.containsKey(it.studentId) }
        val totalStudentsCount = validStudentRecords.size
        if (totalStudentsCount == 0) return null

        val completedPairs = mutableListOf<Pair<Student, Double>>()
        for (rec in validStudentRecords) {
            val student = studentsById[rec.studentId] ?: continue
            val eval = evaluate(rec, config)
            val avg = eval.finalAverage
            if (eval.isComplete && avg != null) {
                completedPairs.add(student to avg)
            }
        }

        if (completedPairs.isEmpty()) return null

        val averages = completedPairs.map { it.second }
        val completeCount = averages.size
        val archivedIncludedCount = completedPairs.count { it.first.isArchived }
        val maxAverage = round2(averages.maxOrNull() ?: 0.0)
        val minAverage = round2(averages.minOrNull() ?: 0.0)
        val classAverage = round2(averages.sum() / completeCount.toDouble())
        val passCount = averages.count { it >= 10.0 }
        val failCount = completeCount - passCount
        val passRate = round2((passCount.toDouble() * 100.0) / completeCount.toDouble())

        val bin0to5 = averages.count { it >= 0.0 && it < 5.0 }
        val bin5to10 = averages.count { it >= 5.0 && it < 10.0 }
        val bin10to15 = averages.count { it >= 10.0 && it < 15.0 }
        val bin15to20 = averages.count { it >= 15.0 && it <= 20.0 }

        val bins = listOf(
            DistributionBin("0 إلى <5", 0.0, 5.0, bin0to5),
            DistributionBin("5 إلى <10", 5.0, 10.0, bin5to10),
            DistributionBin("10 إلى <15", 10.0, 15.0, bin10to15),
            DistributionBin("15 إلى 20", 15.0, 20.0, bin15to20)
        )

        return ClassStatistics(
            completeCount = completeCount,
            totalStudentsCount = totalStudentsCount,
            archivedIncludedCount = archivedIncludedCount,
            maxAverage = maxAverage,
            minAverage = minAverage,
            classAverage = classAverage,
            passCount = passCount,
            failCount = failCount,
            passRate = passRate,
            bins = bins
        )
    }

    /**
     * D13 CSV Export:
     * - UTF-8 with BOM (\uFEFF)
     * - Delimiter `;` (default) or `,`
     * - Decimal mark `.` (default) or `,`
     * - Header block with school year, class, term, formula, weights; blank row; table of visible columns
     * - Incomplete final average = empty cell; 0 exported as 0; null exported empty
     * - Formula injection guard: prefixes text cells starting with = + - @ with '
     * - Archived students marked « (مؤرشف)»
     * - Never includes private notes or lesson observations
     */
    fun exportGradebookCsv(
        schoolYearLabel: String,
        schoolClass: SchoolClass,
        termId: String,
        config: GradeTermConfig,
        students: List<Student>,
        recordsByStudentId: Map<String, GradeRecord>,
        delimiter: Char = ';',
        decimalMark: Char = '.'
    ): String {
        fun formatNum(v: Double?): String {
            if (v == null) return ""
            val str = ArabicUtils.formatWesternNumber(v)
            return if (decimalMark == ',') str.replace('.', ',') else str
        }

        fun escapeCell(raw: String, isTextCell: Boolean): String {
            if (raw.isEmpty()) return ""
            val guarded = if (isTextCell && (raw.startsWith("=") || raw.startsWith("+") || raw.startsWith("-") || raw.startsWith("@"))) {
                "'$raw"
            } else {
                raw
            }
            val needsQuotes = guarded.contains(delimiter) || guarded.contains('"') || guarded.contains('\n') || guarded.contains('\r')
            return if (needsQuotes) {
                "\"${guarded.replace("\"", "\"\"")}\""
            } else {
                guarded
            }
        }

        fun rowOf(cells: List<Pair<String, Boolean>>): String =
            cells.joinToString(delimiter.toString()) { (value, isText) -> escapeCell(value, isText) }

        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM

        val termLabel = Gradebook.termLabel(termId)
        val formulaStr = buildFormulaDescription(config)
        val weightsStr = "التقويم المستمر=${formatNum(config.weights.ca)} | الفروض=${formatNum(config.weights.asWeight)} | الاختبار=${formatNum(config.weights.exam)}"

        sb.appendLine(rowOf(listOf(("السنة الدراسية" to true), (schoolYearLabel to true))))
        sb.appendLine(rowOf(listOf(("القسم" to true), (schoolClass.fullTitle to true))))
        sb.appendLine(rowOf(listOf(("الفصل" to true), (termLabel to true))))
        sb.appendLine(rowOf(listOf(("الصيغة" to true), (formulaStr to true))))
        sb.appendLine(rowOf(listOf(("المعاملات" to true), (weightsStr to true))))
        sb.appendLine() // Blank row

        val headers = mutableListOf<Pair<String, Boolean>>()
        headers.add("الرقم" to true)
        headers.add("الاسم واللقب" to true)
        headers.add("التقويم المستمر 1" to true)
        if (config.ca2Active) headers.add("التقويم المستمر 2" to true)
        headers.add("الفرض 1" to true)
        if (config.as2Active) headers.add("الفرض 2" to true)
        headers.add("الاختبار" to true)
        headers.add("المعدل الفصلي" to true)
        if (config.visibleColumns.absences) headers.add("الغيابات" to true)
        if (config.visibleColumns.behaviour) headers.add("السلوك" to true)
        if (config.visibleColumns.materials) headers.add("إحضار الأدوات" to true)
        if (config.visibleColumns.notebook) headers.add("تنظيم الكراس" to true)
        headers.add("الملاحظات" to true)
        sb.appendLine(rowOf(headers))

        val sortedStudents = students.sortedWith(compareBy<Student> { it.orderIndex }.thenBy { it.fullName })
        for (student in sortedStudents) {
            val rec = recordsByStudentId[student.id]
            val eval = if (rec != null) evaluate(rec, config) else null
            val displayName = if (student.isArchived) "${student.fullName} (مؤرشف)" else student.fullName

            val row = mutableListOf<Pair<String, Boolean>>()
            row.add(student.orderIndex.toString() to false)
            row.add(displayName to true)
            row.add(formatNum(rec?.ca1) to false)
            if (config.ca2Active) row.add(formatNum(rec?.ca2) to false)
            row.add(formatNum(rec?.as1) to false)
            if (config.as2Active) row.add(formatNum(rec?.as2) to false)
            row.add(formatNum(rec?.exam) to false)
            // Incomplete final average = empty cell
            val finalCell = if (eval != null && eval.isComplete && eval.finalAverage != null) {
                formatNum(eval.finalAverage)
            } else {
                ""
            }
            row.add(finalCell to false)

            if (config.visibleColumns.absences) {
                row.add((rec?.absences?.toString() ?: "") to false)
            }
            if (config.visibleColumns.behaviour) {
                row.add((rec?.behaviour ?: "") to true)
            }
            if (config.visibleColumns.materials) {
                row.add((rec?.materials ?: "") to true)
            }
            if (config.visibleColumns.notebook) {
                row.add((rec?.notebook ?: "") to true)
            }
            row.add((rec?.remarks ?: "") to true)

            sb.appendLine(rowOf(row))
        }

        return sb.toString()
    }
}
