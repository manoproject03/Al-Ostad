package com.example.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.components.*
import com.example.domain.*
import com.example.i18n.ArStrings
import com.example.print.PrintAndExportHelper
import com.example.print.PrintJobSpec
import com.example.print.PrintPreviewDialog
import com.example.storage.DatabaseSnapshot
import com.example.storage.TeacherRepository
import com.example.ui.theme.LocalTeacherPalette

@Composable
fun DailyPlannerScreen(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    viewedYearId: String,
    initialPlanToOpen: LessonPlan? = null,
    initialDate: String? = null,
    onConsumedInitialPlan: () -> Unit = {}
) {
    val palette = LocalTeacherPalette.current
    var selectedDate by remember(initialDate) {
        mutableStateOf(initialDate ?: DateTimeUtils.todayDateString())
    }
    var viewMode by remember { mutableStateOf("daily") } // "daily" | "weekly"
    var editingPlan by remember { mutableStateOf<LessonPlan?>(null) }
    var quickObsPlan by remember { mutableStateOf<LessonPlan?>(null) }
    var copyingPlan by remember { mutableStateOf<LessonPlan?>(null) }
    var deletingPlan by remember { mutableStateOf<LessonPlan?>(null) }
    var showCreateManualDialog by remember { mutableStateOf(false) }
    var showPrintRangeDialog by remember { mutableStateOf(false) }
    var activePrintSpec by remember { mutableStateOf<PrintJobSpec?>(null) }

    LaunchedEffect(initialPlanToOpen) {
        if (initialPlanToOpen != null) {
            selectedDate = initialPlanToOpen.date
            editingPlan = initialPlanToOpen
            onConsumedInitialPlan()
        }
    }

    val yearClasses = remember(snapshot.classes, viewedYearId) {
        snapshot.classes.filter { it.schoolYearId == viewedYearId }
    }
    val activeClasses = remember(yearClasses) { yearClasses.filter { !it.isArchived } }
    val classById = remember(yearClasses) { yearClasses.associateBy { it.id } }
    val yearLabel = snapshot.schoolYears.find { it.id == viewedYearId }?.label ?: ""

    val dayIndex = DateTimeUtils.getDayIndexFromSunday(selectedDate)
    val savedPlansForDate = remember(snapshot.lessonPlans, viewedYearId, selectedDate) {
        snapshot.lessonPlans
            .filter { it.schoolYearId == viewedYearId && it.date == selectedDate }
            .sortedBy { it.startTime }
    }

    // D20: Timetable slots for that weekday that have no plan yet
    val unlinkedSlotsForDate = remember(snapshot.timetableEntries, savedPlansForDate, viewedYearId, dayIndex, classById) {
        if (dayIndex !in 0..4) {
            emptyList()
        } else {
            val plannedSlotIds = savedPlansForDate.mapNotNull { it.timetableEntryId }.toSet()
            snapshot.timetableEntries
                .filter {
                    it.schoolYearId == viewedYearId &&
                        !it.isArchived &&
                        it.day == dayIndex &&
                        it.id !in plannedSlotIds &&
                        classById[it.classId]?.isArchived == false
                }
                .sortedBy { it.startTime }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("daily_planner_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header & Primary Actions
        SectionHeader(
            title = "المفكرة والتحضير اليومي",
            subtitle = "إعداد المذكرات البيداغوجية اليومية والأسبوعية"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showCreateManualDialog = true },
                enabled = activeClasses.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("add_manual_lesson_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إضافة تحضير", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(
                onClick = { showPrintRangeDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("طباعة المذكرات", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        // Daily vs Weekly segmented toggle
        ScrollablePillRow(
            items = listOf(
                "daily" to "العرض اليومي",
                "weekly" to "العرض الأسبوعي (الحصص المحفوظة)"
            ),
            selectedValue = viewMode,
            onSelect = { viewMode = it },
            iconFor = { mode ->
                if (mode == "daily") Icons.Outlined.Today else Icons.Outlined.ViewWeek
            }
        )

        // Date navigation bar
        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { selectedDate = DateTimeUtils.addDays(selectedDate, -1) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("اليوم السابق", maxLines = 1)
                    }
                    TextButton(
                        onClick = { selectedDate = DateTimeUtils.todayDateString() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "اليوم (${DateTimeUtils.formatAlgerianDate(DateTimeUtils.todayDateString())})",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = { selectedDate = DateTimeUtils.addDays(selectedDate, 1) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("اليوم التالي", maxLines = 1)
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                AlgerianDateField(
                    label = "التاريخ المحدد",
                    value = selectedDate,
                    onValueChange = { selectedDate = it }
                )
            }
        }

        if (viewMode == "daily") {
            // 1. Saved Lesson Plans for selectedDate
            Text(
                text = "مذكرات التحضير المحفوظة ليوم ${DateTimeUtils.formatAlgerianDate(selectedDate, includeWeekday = true)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.text
            )
            if (savedPlansForDate.isEmpty() && unlinkedSlotsForDate.isEmpty()) {
                TeacherEmptyState(
                    icon = Icons.Outlined.MenuBook,
                    title = "لا توجد حصص أو تحضيرات لهذا اليوم",
                    description = if (activeClasses.isEmpty()) {
                        "أضيفي قسمًا أولًا من صفحة الأقسام لتتمكني من إنشاء مذكرات التحضير."
                    } else {
                        "يمكنكِ إضافة تحضير يدويًا أو فتح حصة من التوقيت الأسبوعي."
                    },
                    actionLabel = if (activeClasses.isNotEmpty()) "إضافة تحضير جديد" else null,
                    onAction = if (activeClasses.isNotEmpty()) ({ showCreateManualDialog = true }) else null
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    savedPlansForDate.forEach { plan ->
                        val obsCount = snapshot.lessonObservations.count { it.lessonPlanId == plan.id }
                        LessonPlanCard(
                            plan = plan,
                            schoolClass = classById[plan.classId],
                            observationCount = obsCount,
                            onEdit = { editingPlan = plan },
                            onQuickObs = { quickObsPlan = plan },
                            onCopy = { copyingPlan = plan },
                            onPrintSingle = {
                                activePrintSpec = PrintAndExportHelper.buildLessonPlansPrintSpec(
                                    selectedPlans = listOf(plan),
                                    classesById = classById,
                                    profile = snapshot.profile,
                                    schoolYearLabel = yearLabel
                                )
                            },
                            onDelete = { deletingPlan = plan }
                        )
                    }
                }
            }

            // 2. Unlinked Timetable Slots for that weekday (D20)
            if (unlinkedSlotsForDate.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "حصص مبرمجة في التوقيت الأسبوعي (لم يُفتح لها تحضير بعد)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    unlinkedSlotsForDate.forEach { slot ->
                        val cls = classById[slot.classId]
                        Surface(
                            color = palette.raised,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "حصة من التوقيت: ${cls?.fullTitle ?: ""}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (slot.isSample || cls?.isSample == true) {
                                            SampleBadge()
                                        }
                                    }
                                    Text(
                                        text = "${DateTimeUtils.formatTimeRange(slot.startTime, slot.endTime)} • ${slot.period}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textSecondary
                                    )
                                }
                                Button(
                                    onClick = {
                                        repository.openOrCreateLessonFromSlot(slot.id, selectedDate)
                                            .onSuccess { editingPlan = it }
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("daily_open_slot_${slot.id}")
                                ) {
                                    Text("افتح الحصة")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Weekly view: stacked day cards for the 5 school days around selectedDate, saved plans only (8.5)
            val currentDayIdx = DateTimeUtils.getDayIndexFromSunday(selectedDate)
            val sundayDate = DateTimeUtils.addDays(selectedDate, -currentDayIdx)
            val weekDates = (0..4).map { offset -> offset to DateTimeUtils.addDays(sundayDate, offset) }

            Text(
                text = "الحصص المحفوظة خلال الأسبوع الدراسي (من ${DateTimeUtils.formatAlgerianDate(weekDates.first().second)} إلى ${DateTimeUtils.formatAlgerianDate(weekDates.last().second)})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            weekDates.forEach { (dIdx, dStr) ->
                val dayName = ArStrings.SCHOOL_DAYS.getOrElse(dIdx) { "" }
                val dayPlans = snapshot.lessonPlans
                    .filter { it.schoolYearId == viewedYearId && it.date == dStr }
                    .sortedBy { it.startTime }

                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$dayName — ${DateTimeUtils.formatAlgerianDate(dStr)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryText
                        )
                        if (dayPlans.isEmpty()) {
                            Text(
                                text = "لا توجد تحضيرات محفوظة لهذا اليوم.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                        } else {
                            dayPlans.forEach { plan ->
                                val obsCount = snapshot.lessonObservations.count { it.lessonPlanId == plan.id }
                                LessonPlanCard(
                                    plan = plan,
                                    schoolClass = classById[plan.classId],
                                    observationCount = obsCount,
                                    onEdit = { editingPlan = plan },
                                    onQuickObs = { quickObsPlan = plan },
                                    onCopy = { copyingPlan = plan },
                                    onPrintSingle = {
                                        activePrintSpec = PrintAndExportHelper.buildLessonPlansPrintSpec(
                                            selectedPlans = listOf(plan),
                                            classesById = classById,
                                            profile = snapshot.profile,
                                            schoolYearLabel = yearLabel
                                        )
                                    },
                                    onDelete = { deletingPlan = plan }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create manual lesson plan dialog
    if (showCreateManualDialog && activeClasses.isNotEmpty()) {
        CreateManualLessonDialog(
            activeClasses = activeClasses,
            initialDate = selectedDate,
            onDismiss = { showCreateManualDialog = false },
            onCreated = { draft ->
                showCreateManualDialog = false
                editingPlan = draft
            }
        )
    }

    // Full Lesson Plan Editor Dialog
    if (editingPlan != null) {
        val currentPlan = snapshot.lessonPlans.find { it.id == editingPlan!!.id } ?: editingPlan!!
        LessonPlanEditorDialog(
            plan = currentPlan,
            schoolClass = classById[currentPlan.classId],
            activityTemplates = snapshot.activityTemplates.sortedBy { it.orderIndex },
            repository = repository,
            onOpenQuickObservation = {
                val latest = repository.snapshot.value.lessonPlans.find { it.id == currentPlan.id } ?: currentPlan
                editingPlan = null
                quickObsPlan = latest
            },
            onDismiss = { editingPlan = null }
        )
    }

    // Quick Observation Dialog (D21)
    if (quickObsPlan != null) {
        val plan = snapshot.lessonPlans.find { it.id == quickObsPlan!!.id } ?: quickObsPlan!!
        QuickObservationDialog(
            plan = plan,
            schoolClass = classById[plan.classId],
            snapshot = snapshot,
            repository = repository,
            onDismiss = { quickObsPlan = null }
        )
    }

    // Copy Lesson Plan Dialog (D20)
    if (copyingPlan != null && activeClasses.isNotEmpty()) {
        CopyLessonPlanDialog(
            sourcePlan = copyingPlan!!,
            activeClasses = activeClasses,
            repository = repository,
            onDismiss = { copyingPlan = null },
            onCopySaved = { newCopy ->
                copyingPlan = null
                selectedDate = newCopy.date
                editingPlan = newCopy
            }
        )
    }

    // Delete Lesson Plan Confirmation (D6)
    if (deletingPlan != null) {
        val plan = deletingPlan!!
        val deps = repository.getLessonDependents(plan.id)
        val obsWarning = if (deps.observationCount > 0) {
            "\nتنبيه: سيؤدي حذف هذا التحضير إلى حذف ${deps.observationCount} ملاحظة حصة مرتبطة به في نفس العملية."
        } else ""
        ConfirmActionDialog(
            title = "حذف مذكرة التحضير",
            message = "هل أنتِ متأكدة من حذف تحضير «${plan.title.ifBlank { "بدون عنوان" }}» بتاريخ ${DateTimeUtils.formatAlgerianDate(plan.date)}؟$obsWarning",
            confirmLabel = "تأكيد الحذف",
            onConfirm = {
                repository.deleteLessonPlan(plan.id)
                deletingPlan = null
            },
            onDismiss = { deletingPlan = null }
        )
    }

    // Print Date Range Dialog (D12)
    if (showPrintRangeDialog) {
        PrintLessonRangeDialog(
            initialDate = selectedDate,
            onDismiss = { showPrintRangeDialog = false },
            onConfirmRange = { startD, endD ->
                showPrintRangeDialog = false
                val inRange = snapshot.lessonPlans.filter {
                    it.schoolYearId == viewedYearId && it.date >= startD && it.date <= endD
                }
                activePrintSpec = PrintAndExportHelper.buildLessonPlansPrintSpec(
                    selectedPlans = inRange,
                    classesById = classById,
                    profile = snapshot.profile,
                    schoolYearLabel = yearLabel
                )
            }
        )
    }

    // A4 Print Preview Modal (#print-root)
    if (activePrintSpec != null) {
        PrintPreviewDialog(
            spec = activePrintSpec!!,
            onDismiss = { activePrintSpec = null }
        )
    }
}

@Composable
private fun LessonPlanCard(
    plan: LessonPlan,
    schoolClass: SchoolClass?,
    observationCount: Int = 0,
    onEdit: () -> Unit,
    onQuickObs: () -> Unit,
    onCopy: () -> Unit,
    onPrintSingle: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lesson_plan_card_${plan.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = schoolClass?.fullTitle ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryText
                    )
                    if (plan.isSample || schoolClass?.isSample == true) {
                        SampleBadge()
                    }
                    if (schoolClass?.isArchived == true) {
                        ArchivedBadge()
                    }
                }
                LessonStatusChip(status = plan.status)
            }

            Text(
                text = plan.title.ifBlank { "حصة بدون عنوان بعد" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.text
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${DateTimeUtils.formatTimeRange(plan.startTime, plan.endTime)} • النشاط: ${plan.activityName.ifBlank { "غير محدد" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (observationCount > 0) {
                    Surface(
                        color = palette.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "ملاحظات: $observationCount",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Postponement info side-by-side (Section 8.5)
            if (plan.status == ArStrings.STATUS_POSTPONED && plan.postponement != null) {
                Surface(
                    color = palette.warning.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.warning.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "سبب التأجيل: ${plan.postponement.reason}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "التاريخ الأصلي: ${DateTimeUtils.formatAlgerianDate(plan.date)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textSecondary
                            )
                            if (!plan.postponement.suggestedDate.isNullOrBlank()) {
                                Text(
                                    text = "التاريخ المقترح: ${DateTimeUtils.formatAlgerianDate(plan.postponement.suggestedDate)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.primaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.border.copy(alpha = 0.6f))

            // Primary Actions row (never wraps or squeezes on narrow screens)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onEdit,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("edit_lesson_btn_${plan.id}")
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("تحرير المذكرة", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onQuickObs,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("quick_obs_btn_${plan.id}")
                ) {
                    Icon(Icons.Outlined.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("وضع الحصة السريعة", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            // Secondary Actions row (Copy, Print, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("copy_lesson_btn_${plan.id}")
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("نسخ")
                }
                TextButton(
                    onClick = onPrintSingle,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("طباعة")
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.error),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("حذف")
                }
            }
        }
    }
}

@Composable
private fun CreateManualLessonDialog(
    activeClasses: List<SchoolClass>,
    initialDate: String,
    onDismiss: () -> Unit,
    onCreated: (LessonPlan) -> Unit
) {
    var selectedClassId by remember { mutableStateOf(activeClasses.first().id) }
    var date by remember { mutableStateOf(initialDate) }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("09:00") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة تحضير جديد", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val selClass = activeClasses.find { it.id == selectedClassId } ?: activeClasses.first()
                DropdownRowSelector(
                    title = "القسم",
                    selectedLabel = selClass.fullTitle,
                    options = activeClasses.map { it.id to it.fullTitle },
                    onSelect = { selectedClassId = it }
                )
                AlgerianDateField(
                    label = "تاريخ الحصة",
                    value = date,
                    onValueChange = { date = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Time24Field(
                        label = "وقت البداية",
                        value = startTime,
                        onValueChange = { startTime = it },
                        modifier = Modifier.weight(1f)
                    )
                    Time24Field(
                        label = "وقت النهاية",
                        value = endTime,
                        onValueChange = { endTime = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!DateTimeUtils.isEndAfterStart(startTime, endTime)) {
                        errorMsg = "وقت النهاية يجب أن يكون بعد وقت البداية."
                    } else {
                        val cls = activeClasses.find { it.id == selectedClassId } ?: activeClasses.first()
                        val draft = LessonPlan(
                            id = newStableId(),
                            schoolYearId = cls.schoolYearId,
                            classId = cls.id,
                            timetableEntryId = null,
                            date = date,
                            startTime = startTime,
                            endTime = endTime,
                            status = ArStrings.STATUS_DRAFT
                        )
                        onCreated(draft)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("متابعة لتحرير المذكرة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun LessonPlanEditorDialog(
    plan: LessonPlan,
    schoolClass: SchoolClass?,
    activityTemplates: List<ActivityTemplate>,
    repository: TeacherRepository,
    onOpenQuickObservation: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    var status by remember(plan.id) { mutableStateOf(plan.status) }
    var title by remember(plan.id) { mutableStateOf(plan.title) }
    var learningSegment by remember(plan.id) { mutableStateOf(plan.learningSegment) }
    var activityId by remember(plan.id) { mutableStateOf(plan.activityId) }
    var activityName by remember(plan.id) {
        mutableStateOf(plan.activityName.ifBlank { activityTemplates.firstOrNull()?.name ?: "" })
    }
    var useCustomActivity by remember(plan.id) {
        mutableStateOf(plan.activityId == null && plan.activityName.isNotBlank() && activityTemplates.none { it.name == plan.activityName })
    }
    var targetCompetence by remember(plan.id) { mutableStateOf(plan.targetCompetence) }
    var procedure by remember(plan.id) { mutableStateOf(plan.procedure) }
    var materials by remember(plan.id) { mutableStateOf(plan.materials) }
    var evaluation by remember(plan.id) { mutableStateOf(plan.evaluation) }
    var homework by remember(plan.id) { mutableStateOf(plan.homework) }
    var notes by remember(plan.id) { mutableStateOf(plan.notes) }
    var postponeReason by remember(plan.id) { mutableStateOf(plan.postponement?.reason ?: "") }
    var postponeSuggestedDate by remember(plan.id) {
        mutableStateOf(plan.postponement?.suggestedDate ?: DateTimeUtils.addDays(plan.date, 7))
    }
    var hasSuggestedDate by remember(plan.id) { mutableStateOf(plan.postponement?.suggestedDate != null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun attemptSave(closeOnSuccess: Boolean) {
        val updated = plan.copy(
            status = status,
            title = title,
            learningSegment = learningSegment,
            activityId = if (useCustomActivity) null else activityId,
            activityName = activityName,
            targetCompetence = targetCompetence,
            procedure = procedure,
            materials = materials,
            evaluation = evaluation,
            homework = homework,
            notes = notes,
            postponement = if (status == ArStrings.STATUS_POSTPONED) {
                PostponementInfo(
                    reason = postponeReason.trim(),
                    suggestedDate = if (hasSuggestedDate) postponeSuggestedDate else null
                )
            } else null
        )
        val res = repository.saveLessonPlan(updated)
        if (res.isSuccess) {
            validationError = null
            if (closeOnSuccess) onDismiss()
        } else {
            validationError = res.exceptionOrNull()?.message
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("lesson_editor_dialog"),
            color = palette.bg,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Surface(
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "مذكرة تحضير درس — ${schoolClass?.fullTitle ?: ""}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryText
                            )
                            Text(
                                text = "${DateTimeUtils.formatAlgerianDate(plan.date, includeWeekday = true)} • ${DateTimeUtils.formatTimeRange(plan.startTime, plan.endTime)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textSecondary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { attemptSave(closeOnSuccess = true) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("save_lesson_plan_btn")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("حفظ")
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق")
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (validationError != null) {
                        Surface(
                            color = palette.error.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, palette.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = validationError!!,
                                color = palette.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Quick Observation button inside the plan (8.5)
                    OutlinedButton(
                        onClick = {
                            // Save current draft fields if valid, then open quick observation
                            val res = repository.saveLessonPlan(
                                plan.copy(
                                    title = title,
                                    activityName = activityName,
                                    learningSegment = learningSegment
                                )
                            )
                            if (res.isSuccess) {
                                onOpenQuickObservation()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("inside_plan_quick_obs_btn")
                    ) {
                        Icon(Icons.Outlined.FactCheck, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("فتح «وضع الحصة السريعة» (حضور / غياب / مشاركة / ملاحظة)")
                    }

                    // Status selector (مسودة · محضّر · أُنجز · أُجّل)
                    Text(
                        text = "حالة الحصة",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val statuses = listOf(
                        ArStrings.STATUS_DRAFT,
                        ArStrings.STATUS_PREPARED,
                        ArStrings.STATUS_COMPLETED,
                        ArStrings.STATUS_POSTPONED
                    )
                    ScrollablePillRow(
                        items = statuses.map { it to it },
                        selectedValue = status,
                        onSelect = {
                            status = it
                            validationError = null
                        },
                        testTagPrefix = "status_chip"
                    )

                    // Postponement fields when status == أُجّل (Section 8.5 & T24)
                    if (status == ArStrings.STATUS_POSTPONED) {
                        Surface(
                            color = palette.warning.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, palette.warning),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "بيانات التأجيل (لا يغيّر التوقيت الأسبوعي ولا ينشئ حصة بديلة تلقائيًا)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.warning
                                )
                                OutlinedTextField(
                                    value = postponeReason,
                                    onValueChange = {
                                        postponeReason = it
                                        validationError = null
                                    },
                                    label = { Text("سبب التأجيل (إلزامي)") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("postpone_reason_input"),
                                    singleLine = true
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = hasSuggestedDate,
                                        onCheckedChange = { hasSuggestedDate = it }
                                    )
                                    Text("تحديد تاريخ مقترح للتعويض (اختياري)")
                                }
                                if (hasSuggestedDate) {
                                    AlgerianDateField(
                                        label = "التاريخ المقترح (التاريخ الأصلي: ${DateTimeUtils.formatAlgerianDate(plan.date)})",
                                        value = postponeSuggestedDate,
                                        onValueChange = { postponeSuggestedDate = it }
                                    )
                                }
                            }
                        }
                    }

                    // Lesson Fields
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            validationError = null
                        },
                        label = { Text("عنوان الدرس (إلزامي عند اختيار محضّر أو أُنجز)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lesson_title_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = learningSegment,
                        onValueChange = { learningSegment = it },
                        label = { Text("المقطع التعلمي") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Activity selector (templates or custom «نشاط آخر»)
                    val templateOptions = activityTemplates.map { (it.id as String?) to it.name } +
                        (null to "نشاط آخر (كتابة حرة)")
                    DropdownRowSelector(
                        title = "الميدان / النشاط",
                        selectedLabel = if (useCustomActivity) "نشاط آخر: $activityName" else activityName.ifBlank { "اختاري النشاط" },
                        options = templateOptions,
                        onSelect = { chosenId ->
                            if (chosenId == null) {
                                useCustomActivity = true
                                activityId = null
                            } else {
                                useCustomActivity = false
                                activityId = chosenId
                                activityName = activityTemplates.find { it.id == chosenId }?.name ?: ""
                            }
                        }
                    )
                    if (useCustomActivity) {
                        OutlinedTextField(
                            value = activityName,
                            onValueChange = { activityName = it },
                            label = { Text("اكتبي اسم النشاط") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = targetCompetence,
                        onValueChange = { targetCompetence = it },
                        label = { Text("الكفاءة المستهدفة / مؤشرات الكفاءة") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = procedure,
                        onValueChange = { procedure = it },
                        label = { Text("سيرورة العمل (وضعيات التعلم والأنشطة)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    OutlinedTextField(
                        value = materials,
                        onValueChange = { materials = it },
                        label = { Text("الوسائل التعليمية والسندات") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = evaluation,
                        onValueChange = { evaluation = it },
                        label = { Text("التقويم المرحلي والختامي") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = homework,
                        onValueChange = { homework = it },
                        label = { Text("الواجب المنزلي / التكليف") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("الملاحظات البيداغوجية") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        }
    }
}

/**
 * D21: Quick observation dialog inside a lesson plan.
 * Only active students of the lesson's class are listed.
 * Attendance (حضور/غياب mutually exclusive), participation boolean (disabled while absent),
 * dated short note (<= 300 chars). Saves immediately via runSave and never affects grades!
 */
@Composable
fun QuickObservationDialog(
    plan: LessonPlan,
    schoolClass: SchoolClass?,
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    onDismiss: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    val activeStudents = remember(snapshot.students, plan.classId) {
        snapshot.students
            .filter { it.classId == plan.classId && !it.isArchived }
            .sortedWith(compareBy<Student> { it.orderIndex }.thenBy { it.fullName })
    }
    val obsByStudentId = remember(snapshot.lessonObservations, plan.id) {
        snapshot.lessonObservations
            .filter { it.lessonPlanId == plan.id }
            .associateBy { it.studentId }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("quick_observation_dialog"),
            color = palette.bg,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "وضع الحصة السريعة — ${schoolClass?.fullTitle ?: ""}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryText
                            )
                            Text(
                                text = "ملاحظات الحصة مستقلة تمامًا عن النقاط ولا تغيّر المعدل الفصلي.",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textSecondary
                            )
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("إغلاق")
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeStudents.isEmpty()) {
                        TeacherEmptyState(
                            icon = Icons.Outlined.PeopleOutline,
                            title = "لا يوجد تلاميذ نشطون في هذا القسم",
                            description = "أضيفي التلاميذ إلى القسم لتسجيل الحضور والمشاركة والملاحظات السريعة."
                        )
                    } else {
                        // Quick summary & "Mark all present" helper row
                        val presentCount = activeStudents.count { obsByStudentId[it.id]?.attendance == "present" }
                        val absentCount = activeStudents.count { obsByStudentId[it.id]?.attendance == "absent" }
                        val participatedCount = activeStudents.count { obsByStudentId[it.id]?.participated == true }

                        Surface(
                            color = palette.raised,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "الحضور: $presentCount • الغياب: $absentCount • المشاركة: $participatedCount",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.text,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        activeStudents.forEach { st ->
                                            val cur = obsByStudentId[st.id]
                                            if (cur?.attendance == null) {
                                                repository.saveLessonObservation(
                                                    lessonPlanId = plan.id,
                                                    studentId = st.id,
                                                    attendance = "present",
                                                    participated = cur?.participated == true,
                                                    note = cur?.note ?: ""
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("تسجيل حضور البقية", maxLines = 1)
                                }
                            }
                        }

                        activeStudents.forEach { student ->
                            val currentObs = obsByStudentId[student.id]
                            val attendance = currentObs?.attendance
                            val participated = currentObs?.participated == true
                            var noteText by remember(student.id, currentObs?.note) {
                                mutableStateOf(currentObs?.note ?: "")
                            }

                            Surface(
                                color = palette.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("obs_student_row_${student.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${student.orderIndex}. ${student.fullName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = attendance == "present",
                                            onClick = {
                                                val nextAtt = if (attendance == "present") null else "present"
                                                repository.saveLessonObservation(
                                                    lessonPlanId = plan.id,
                                                    studentId = student.id,
                                                    attendance = nextAtt,
                                                    participated = participated,
                                                    note = noteText
                                                )
                                            },
                                            label = { Text("حضور", maxLines = 1) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = palette.success.copy(alpha = 0.16f),
                                                selectedLabelColor = palette.success,
                                                selectedLeadingIconColor = palette.success
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("obs_present_${student.id}")
                                        )

                                        FilterChip(
                                            selected = attendance == "absent",
                                            onClick = {
                                                val nextAtt = if (attendance == "absent") null else "absent"
                                                repository.saveLessonObservation(
                                                    lessonPlanId = plan.id,
                                                    studentId = student.id,
                                                    attendance = nextAtt,
                                                    participated = if (nextAtt == "absent") false else participated,
                                                    note = noteText
                                                )
                                            },
                                            label = { Text("غياب", maxLines = 1) },
                                            leadingIcon = {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = palette.error.copy(alpha = 0.16f),
                                                selectedLabelColor = palette.error,
                                                selectedLeadingIconColor = palette.error
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("obs_absent_${student.id}")
                                        )

                                        FilterChip(
                                            selected = participated && attendance != "absent",
                                            enabled = attendance != "absent",
                                            onClick = {
                                                repository.saveLessonObservation(
                                                    lessonPlanId = plan.id,
                                                    studentId = student.id,
                                                    attendance = attendance ?: "present",
                                                    participated = !participated,
                                                    note = noteText
                                                )
                                            },
                                            label = { Text("مشاركة", maxLines = 1) },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.StarOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = palette.primary.copy(alpha = 0.16f),
                                                selectedLabelColor = palette.primaryText,
                                                selectedLeadingIconColor = palette.primaryText
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("obs_participated_${student.id}")
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = noteText,
                                            onValueChange = { noteText = it.take(300) },
                                            label = {
                                                val dateSuffix = currentObs?.noteAt?.let { " (بتاريخ ${DateTimeUtils.formatAlgerianDate(it)})" } ?: ""
                                                Text("ملاحظة قصيرة مؤرخة (≤ 300 حرف)$dateSuffix")
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("obs_note_input_${student.id}"),
                                            singleLine = true
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                repository.saveLessonObservation(
                                                    lessonPlanId = plan.id,
                                                    studentId = student.id,
                                                    attendance = attendance,
                                                    participated = participated,
                                                    note = noteText
                                                )
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("obs_save_note_${student.id}")
                                        ) {
                                            Text("حفظ الملاحظة")
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

@Composable
private fun CopyLessonPlanDialog(
    sourcePlan: LessonPlan,
    activeClasses: List<SchoolClass>,
    repository: TeacherRepository,
    onDismiss: () -> Unit,
    onCopySaved: (LessonPlan) -> Unit
) {
    var targetClassId by remember {
        mutableStateOf(
            activeClasses.find { it.id != sourcePlan.classId }?.id
                ?: activeClasses.first().id
        )
    }
    var targetDate by remember { mutableStateOf(sourcePlan.date) }
    var targetStart by remember { mutableStateOf(sourcePlan.startTime) }
    var targetEnd by remember { mutableStateOf(sourcePlan.endTime) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نسخ التحضير إلى قسم أو تاريخ آخر", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "الأصل: ${sourcePlan.title.ifBlank { "بدون عنوان" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val chosenClass = activeClasses.find { it.id == targetClassId } ?: activeClasses.first()
                DropdownRowSelector(
                    title = "القسم المستهدف",
                    selectedLabel = chosenClass.fullTitle,
                    options = activeClasses.map { it.id to it.fullTitle },
                    onSelect = { targetClassId = it }
                )
                AlgerianDateField(
                    label = "تاريخ الحصة المنسوخة",
                    value = targetDate,
                    onValueChange = { targetDate = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Time24Field(
                        label = "وقت البداية",
                        value = targetStart,
                        onValueChange = { targetStart = it },
                        modifier = Modifier.weight(1f)
                    )
                    Time24Field(
                        label = "وقت النهاية",
                        value = targetEnd,
                        onValueChange = { targetEnd = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = repository.copyLessonPlan(
                        sourcePlanId = sourcePlan.id,
                        targetClassId = targetClassId,
                        targetDate = targetDate,
                        targetStartTime = targetStart,
                        targetEndTime = targetEnd
                    )
                    if (res.isSuccess) {
                        onCopySaved(res.getOrThrow())
                    } else {
                        errorMsg = res.exceptionOrNull()?.message
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_copy_lesson_btn")
            ) {
                Text("حفظ النسخة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
private fun PrintLessonRangeDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onConfirmRange: (String, String) -> Unit
) {
    var startDate by remember { mutableStateOf(initialDate) }
    var endDate by remember { mutableStateOf(initialDate) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("طباعة مذكرات التحضير (نطاق تاريخ)", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "تُطبع فقط المذكرات ذات الحالة «محضّر» أو «أُنجز» على ورق A4 عمودي بخلفية بيضاء، دون الملاحظات الخاصة أو ملاحظات الحصة.",
                    style = MaterialTheme.typography.bodyMedium
                )
                AlgerianDateField(
                    label = "من تاريخ",
                    value = startDate,
                    onValueChange = { startDate = it }
                )
                AlgerianDateField(
                    label = "إلى تاريخ",
                    value = endDate,
                    onValueChange = { endDate = it }
                )
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (endDate < startDate) {
                        errorMsg = "تاريخ النهاية يجب أن يكون مساويًا لتاريخ البداية أو بعده."
                    } else {
                        onConfirmRange(startDate, endDate)
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("معاينة وطباعة")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إلغاء")
            }
        }
    )
}
