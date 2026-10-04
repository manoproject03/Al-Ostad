package com.example.print

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.*
import com.example.i18n.ArStrings
import java.io.File

data class PrintJobSpec(
    val title: String,
    val isLandscape: Boolean,
    val exclusionNotice: String? = null,
    val htmlBody: String,
    val previewSections: List<PrintPreviewSection>
)

data class PrintPreviewSection(
    val heading: String,
    val metaLines: List<String> = emptyList(),
    val rows: List<Pair<String, String>> = emptyList(),
    val tableHeaders: List<String> = emptyList(),
    val tableRows: List<List<String>> = emptyList()
)

object PrintAndExportHelper {

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    /**
     * D12 & 8.5: Lesson plan printing (one plan or date range).
     * Only status «محضّر» or «أُنجز» are printable; drafts and postponed plans are excluded
     * with an explicit count and explanation.
     * Never prints private notes or lesson observations.
     */
    fun buildLessonPlansPrintSpec(
        selectedPlans: List<LessonPlan>,
        classesById: Map<String, SchoolClass>,
        profile: TeacherProfile,
        schoolYearLabel: String
    ): PrintJobSpec {
        val printable = selectedPlans.filter {
            it.status == ArStrings.STATUS_PREPARED || it.status == ArStrings.STATUS_COMPLETED
        }.sortedWith(compareBy<LessonPlan> { it.date }.thenBy { it.startTime })

        val excludedCount = selectedPlans.size - printable.size
        val exclusionNotice = if (excludedCount > 0) {
            "تم استبعاد $excludedCount تحضير لأن حالتها «مسودة» أو «أُجّل» (تُطبع فقط الحصص ذات الحالة «محضّر» أو «أُنجز»)."
        } else null

        val sections = printable.map { plan ->
            val clsTitle = classesById[plan.classId]?.fullTitle ?: ""
            val meta = buildList {
                if (profile.institution.isNotBlank()) add("المؤسسة: ${profile.institution}")
                if (profile.professionalName.isNotBlank()) add("الأستاذ(ة): ${profile.professionalName}")
                add("السنة الدراسية: $schoolYearLabel")
                add("القسم: $clsTitle")
                add("التاريخ: ${DateTimeUtils.formatAlgerianDate(plan.date, includeWeekday = true)}")
                add("التوقيت: ${DateTimeUtils.formatTimeRange(plan.startTime, plan.endTime)}")
                add("الحالة: ${plan.status}")
            }
            val fieldRows = buildList {
                add("عنوان الدرس" to plan.title)
                if (plan.learningSegment.isNotBlank()) add("المقطع التعلمي" to plan.learningSegment)
                if (plan.activityName.isNotBlank()) add("الميدان / النشاط" to plan.activityName)
                if (plan.targetCompetence.isNotBlank()) add("الكفاءة المستهدفة" to plan.targetCompetence)
                if (plan.procedure.isNotBlank()) add("سيرورة العمل" to plan.procedure)
                if (plan.materials.isNotBlank()) add("الوسائل التعليمية" to plan.materials)
                if (plan.evaluation.isNotBlank()) add("التقويم" to plan.evaluation)
                if (plan.homework.isNotBlank()) add("الواجب المنزلي" to plan.homework)
                if (plan.notes.isNotBlank()) add("الملاحظات البيداغوجية" to plan.notes)
            }
            PrintPreviewSection(
                heading = "بطاقة تحضير درس — ${plan.title}",
                metaLines = meta,
                rows = fieldRows
            )
        }

        val htmlCards = sections.joinToString("<div class='page-break'></div>") { sec ->
            val metaHtml = sec.metaLines.joinToString(" • ") { escapeHtml(it) }
            val rowsHtml = sec.rows.joinToString("") { (k, v) ->
                "<tr><th>${escapeHtml(k)}</th><td dir='auto'>${escapeHtml(v).replace("\n", "<br/>")}</td></tr>"
            }
            """
            <section class="print-card">
                <h2>${escapeHtml(sec.heading)}</h2>
                <p class="meta">$metaHtml</p>
                <table>$rowsHtml</table>
            </section>
            """.trimIndent()
        }

        val fullHtml = wrapHtmlDocument(
            title = "طباعة التحضير اليومي",
            isLandscape = false,
            bodyContent = htmlCards.ifBlank { "<p>لا توجد حصص قابلة للطباعة (محضّر أو أُنجز) في النطاق المحدد.</p>" }
        )

        return PrintJobSpec(
            title = "طباعة مذكرات التحضير (A4 عمودي)",
            isLandscape = false,
            exclusionNotice = exclusionNotice,
            htmlBody = fullHtml,
            previewSections = sections
        )
    }

    /**
     * D12 & 8.6: Gradebook A4 Landscape print.
     * Shows visible columns, formula, term, class, school year; «—» for incomplete finals;
     * never prints private notes or lesson observations.
     */
    fun buildGradebookPrintSpec(
        schoolYearLabel: String,
        schoolClass: SchoolClass,
        termId: String,
        config: GradeTermConfig,
        students: List<Student>,
        recordsByStudentId: Map<String, GradeRecord>,
        profile: TeacherProfile
    ): PrintJobSpec {
        val termLabel = Gradebook.termLabel(termId)
        val formulaStr = GradingEngine.buildFormulaDescription(config)
        val headers = buildList {
            add("الرقم")
            add("الاسم واللقب")
            add("ت. مستمر 1")
            if (config.ca2Active) add("ت. مستمر 2")
            add("الفرض 1")
            if (config.as2Active) add("الفرض 2")
            add("الاختبار")
            add("المعدل الفصلي")
            if (config.visibleColumns.absences) add("الغيابات")
            if (config.visibleColumns.behaviour) add("السلوك")
            if (config.visibleColumns.materials) add("الأدوات")
            if (config.visibleColumns.notebook) add("الكراس")
            add("الملاحظات")
        }

        val sortedStudents = students.sortedWith(compareBy<Student> { it.orderIndex }.thenBy { it.fullName })
        val tableRows = sortedStudents.map { st ->
            val rec = recordsByStudentId[st.id]
            val eval = if (rec != null) GradingEngine.evaluate(rec, config) else null
            val nameDisplay = if (st.isArchived) "${st.fullName} (مؤرشف)" else st.fullName
            buildList {
                add(st.orderIndex.toString())
                add(nameDisplay)
                add(rec?.ca1?.let { ArabicUtils.formatWesternNumber(it) } ?: "—")
                if (config.ca2Active) add(rec?.ca2?.let { ArabicUtils.formatWesternNumber(it) } ?: "—")
                add(rec?.as1?.let { ArabicUtils.formatWesternNumber(it) } ?: "—")
                if (config.as2Active) add(rec?.as2?.let { ArabicUtils.formatWesternNumber(it) } ?: "—")
                add(rec?.exam?.let { ArabicUtils.formatWesternNumber(it) } ?: "—")
                add(if (eval != null && eval.isComplete && eval.finalAverage != null) {
                    ArabicUtils.formatWesternNumber(eval.finalAverage)
                } else "—")
                if (config.visibleColumns.absences) add(rec?.absences?.toString() ?: "—")
                if (config.visibleColumns.behaviour) add(rec?.behaviour?.ifBlank { "—" } ?: "—")
                if (config.visibleColumns.materials) add(rec?.materials?.ifBlank { "—" } ?: "—")
                if (config.visibleColumns.notebook) add(rec?.notebook?.ifBlank { "—" } ?: "—")
                add(rec?.remarks?.ifBlank { "" } ?: "")
            }
        }

        val metaLines = buildList {
            if (profile.institution.isNotBlank()) add("المؤسسة: ${profile.institution}")
            if (profile.professionalName.isNotBlank()) add("الأستاذ(ة): ${profile.professionalName}")
            add("السنة الدراسية: $schoolYearLabel")
            add("القسم: ${schoolClass.fullTitle}")
            add("الفصل: $termLabel")
            add(formulaStr)
        }

        val section = PrintPreviewSection(
            heading = "كشف النقاط — ${schoolClass.fullTitle} ($termLabel)",
            metaLines = metaLines,
            tableHeaders = headers,
            tableRows = tableRows
        )

        val thHtml = headers.joinToString("") { "<th>${escapeHtml(it)}</th>" }
        val trHtml = tableRows.joinToString("") { row ->
            "<tr>" + row.joinToString("") { cell -> "<td>${escapeHtml(cell)}</td>" } + "</tr>"
        }
        val bodyHtml = """
            <section class="print-card">
                <h2>${escapeHtml(section.heading)}</h2>
                <p class="meta">${metaLines.joinToString(" • ") { escapeHtml(it) }}</p>
                <table>
                    <thead><tr>$thHtml</tr></thead>
                    <tbody>$trHtml</tbody>
                </table>
            </section>
        """.trimIndent()

        return PrintJobSpec(
            title = "طباعة كشف النقاط (A4 أفقي)",
            isLandscape = true,
            htmlBody = wrapHtmlDocument(section.heading, isLandscape = true, bodyContent = bodyHtml),
            previewSections = listOf(section)
        )
    }

    /**
     * D18 & 8.8: Professional profile print view prints ONLY filled fields, no empty sections.
     */
    fun buildProfilePrintSpec(
        profile: TeacherProfile,
        activeYearClasses: List<SchoolClass>,
        activeYearLabel: String
    ): PrintJobSpec {
        val filledRows = buildList {
            if (profile.professionalName.isNotBlank()) add("الاسم المهني" to profile.professionalName)
            if (profile.institution.isNotBlank()) add("المؤسسة التعليمية" to profile.institution)
            if (profile.employmentStatus.isNotBlank()) add("الوضعية المهنية" to profile.employmentStatus)
            if (profile.specialization.isNotBlank()) add("مادة التخصص" to profile.specialization)
            if (profile.qualifications.isNotBlank()) add("المؤهلات والشهادات" to profile.qualifications)
            if (profile.appointmentDate.isNotBlank()) add("تاريخ التعيين" to profile.appointmentDate)
            if (profile.rank.isNotBlank()) add("الرتبة والدرجة" to profile.rank)
            if (profile.inspectionHistory.isNotBlank()) add("سجل التفتيش والندوات" to profile.inspectionHistory)
            if (profile.promotionDetails.isNotBlank()) add("معلومات الترقية" to profile.promotionDetails)
            if (profile.optionalIdentifier.isNotBlank()) add("المعرّف المهني الاختياري" to profile.optionalIdentifier)
            val nonArchived = activeYearClasses.filter { !it.isArchived }
            if (nonArchived.isNotEmpty()) {
                add("الأقسام المسندة ($activeYearLabel)" to nonArchived.joinToString("، ") { it.fullTitle })
            }
        }

        val section = PrintPreviewSection(
            heading = "البطاقة المهنية للأستاذ(ة)",
            metaLines = if (activeYearLabel.isNotBlank()) listOf("السنة الدراسية: $activeYearLabel") else emptyList(),
            rows = filledRows
        )

        val rowsHtml = filledRows.joinToString("") { (k, v) ->
            "<tr><th>${escapeHtml(k)}</th><td dir='auto'>${escapeHtml(v).replace("\n", "<br/>")}</td></tr>"
        }
        val bodyHtml = """
            <section class="print-card">
                <h2>البطاقة المهنية للأستاذ(ة)</h2>
                ${if (filledRows.isEmpty()) "<p>لم يتم ملء أي حقل بعد.</p>" else "<table>$rowsHtml</table>"}
            </section>
        """.trimIndent()

        return PrintJobSpec(
            title = "طباعة البطاقة المهنية (A4 عمودي)",
            isLandscape = false,
            htmlBody = wrapHtmlDocument("البطاقة المهنية", isLandscape = false, bodyContent = bodyHtml),
            previewSections = listOf(section)
        )
    }

    private fun wrapHtmlDocument(title: String, isLandscape: Boolean, bodyContent: String): String {
        val pageOrientation = if (isLandscape) "A4 landscape" else "A4 portrait"
        return """
            <!DOCTYPE html>
            <html lang="ar" dir="rtl">
            <head>
                <meta charset="UTF-8" />
                <title>${escapeHtml(title)}</title>
                <style>
                    @page { size: $pageOrientation; margin: 15mm; }
                    body {
                        font-family: "Noto Sans Arabic", "Segoe UI", Tahoma, sans-serif;
                        background: #FFFFFF;
                        color: #1A1A1A;
                        direction: rtl;
                        margin: 0;
                        padding: 0;
                        line-height: 1.7;
                    }
                    h2 {
                        margin: 0 0 8px 0;
                        font-size: 18pt;
                        color: #111111;
                        border-bottom: 2px solid #333333;
                        padding-bottom: 6px;
                    }
                    .meta {
                        font-size: 10.5pt;
                        color: #333333;
                        margin-bottom: 12px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 16px;
                        font-size: 10.5pt;
                    }
                    th, td {
                        border: 1px solid #333333;
                        padding: 6px 8px;
                        text-align: right;
                        vertical-align: top;
                    }
                    th {
                        background: #F2F2F2;
                        font-weight: bold;
                        width: 24%;
                    }
                    thead th {
                        width: auto;
                    }
                    .page-break {
                        page-break-after: always;
                        height: 16px;
                    }
                </style>
            </head>
            <body>
                <div id="print-root">
                    $bodyContent
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun triggerAndroidSystemPrint(context: Context, spec: PrintJobSpec) {
        try {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                        ?: return
                    val adapter = view.createPrintDocumentAdapter(spec.title)
                    val mediaSize = if (spec.isLandscape) {
                        PrintAttributes.MediaSize.ISO_A4.asLandscape()
                    } else {
                        PrintAttributes.MediaSize.ISO_A4.asPortrait()
                    }
                    val attrs = PrintAttributes.Builder()
                        .setMediaSize(mediaSize)
                        .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager.print(spec.title, adapter, attrs)
                }
            }
            webView.loadDataWithBaseURL(null, spec.htmlBody, "text/html", "UTF-8", null)
        } catch (_: Exception) {
            // Ignored in headless test environments
        }
    }

    fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readTextFromUri(context: Context, uri: Uri, maxBytes: Long = 50L * 1024L * 1024L): Result<String> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > maxBytes) {
                    return Result.failure(IllegalArgumentException("حجم الملف يتجاوز الحد الأقصى المسموح (50 ميغابايت)."))
                }
                Result.success(String(bytes, Charsets.UTF_8))
            } ?: Result.failure(IllegalArgumentException("تعذّر فتح الملف المختار."))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("تعذّرت قراءة الملف: ${e.message ?: ""}"))
        }
    }

    fun saveExportCopyToInternalExportsDir(context: Context, fileName: String, content: String): File? {
        return try {
            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeText(content, Charsets.UTF_8)
            file
        } catch (_: Exception) {
            null
        }
    }

    fun shareTextContent(context: Context, title: String, fileName: String, content: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, content)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (_: Exception) {
        }
    }
}

/**
 * Dedicated #print-root modal showing white paper A4 preview with dark text and no navigation/chrome,
 * plus a button to invoke Android's system A4 PDF printer.
 */
@Composable
fun PrintPreviewDialog(
    spec: PrintJobSpec,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("print_root_dialog"),
            color = Color(0xFFF5F5F5),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Non-printed top toolbar for the preview dialog
                Surface(
                    color = Color(0xFF2B2326),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = spec.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (spec.isLandscape) "معاينة ورق A4 أفقي — خلفية بيضاء ونص داكن للطباعة" else "معاينة ورق A4 عمودي — خلفية بيضاء ونص داكن للطباعة",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFE0D4D8)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { PrintAndExportHelper.triggerAndroidSystemPrint(context, spec) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB04468),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("طباعة / حفظ PDF")
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق المعاينة",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                if (spec.exclusionNotice != null) {
                    Surface(
                        color = Color(0xFFFFF4E0),
                        border = BorderStroke(1.dp, Color(0xFFD99B26)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = spec.exclusionNotice,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF5C3D00),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // White A4 paper sheet (#print-root)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (spec.previewSections.isEmpty()) {
                        Surface(
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لا توجد سجلات قابلة للطباعة (محضّر أو أُنجز) في الاختيار الحالي.",
                                color = Color(0xFF1A1A1A),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        spec.previewSections.forEach { section ->
                            Surface(
                                color = Color.White,
                                contentColor = Color(0xFF111111),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color(0xFF333333)),
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = section.heading,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111111)
                                    )
                                    HorizontalDivider(color = Color(0xFF333333), thickness = 1.5.dp)

                                    if (section.metaLines.isNotEmpty()) {
                                        Text(
                                            text = section.metaLines.joinToString("  •  "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF222222),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Key-value rows (Lesson Plan or Profile)
                                    if (section.rows.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White)
                                        ) {
                                            section.rows.forEach { (k, v) ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Text(
                                                        text = "$k:",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF111111),
                                                        modifier = Modifier.width(135.dp)
                                                    )
                                                    Text(
                                                        text = v,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = Color(0xFF111111),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                HorizontalDivider(color = Color(0xFFDDDDDD))
                                            }
                                        }
                                    }

                                    // Table (Gradebook A4 Landscape)
                                    if (section.tableHeaders.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFEEEEEE))
                                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                                            ) {
                                                section.tableHeaders.forEachIndexed { idx, th ->
                                                    Text(
                                                        text = th,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF111111),
                                                        modifier = Modifier.weight(if (idx == 1) 2f else 1f)
                                                    )
                                                }
                                            }
                                            section.tableRows.forEach { tr ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                                ) {
                                                    tr.forEachIndexed { idx, cell ->
                                                        Text(
                                                            text = cell,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = Color(0xFF111111),
                                                            modifier = Modifier.weight(if (idx == 1) 2f else 1f)
                                                        )
                                                    }
                                                }
                                                HorizontalDivider(color = Color(0xFFCCCCCC))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
