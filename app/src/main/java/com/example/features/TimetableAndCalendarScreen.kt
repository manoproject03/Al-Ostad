package com.example.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.components.*
import com.example.domain.*
import com.example.i18n.ArStrings
import com.example.storage.DatabaseSnapshot
import com.example.storage.TeacherRepository
import com.example.ui.theme.LocalTeacherPalette

@Composable
fun TimetableAndCalendarScreen(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    viewedYearId: String,
    initialAction: String? = null,
    onConsumedInitialAction: () -> Unit = {},
    onOpenLessonPlan: (LessonPlan) -> Unit
) {
    val palette = LocalTeacherPalette.current
    var selectedSection by remember { mutableStateOf("timetable") } // "timetable" | "calendar" | "assessments"
    var selectedDayIndex by remember {
        mutableIntStateOf(DateTimeUtils.getDayIndexFromSunday(DateTimeUtils.todayDateString()).coerceIn(0, 4))
    }

    var editingTimetable by remember { mutableStateOf<TimetableEntry?>(null) }
    var showAddTimetableDialog by remember { mutableStateOf(false) }
    var deletingTimetable by remember { mutableStateOf<TimetableEntry?>(null) }

    var editingCalendarEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showAddCalendarDialog by remember { mutableStateOf(false) }
    var deletingCalendarEvent by remember { mutableStateOf<CalendarEvent?>(null) }

    var editingAssessment by remember { mutableStateOf<AssessmentEvent?>(null) }
    var showAddAssessmentDialog by remember { mutableStateOf(false) }
    var deletingAssessment by remember { mutableStateOf<AssessmentEvent?>(null) }

    LaunchedEffect(initialAction) {
        when (initialAction) {
            "add_timetable" -> {
                selectedSection = "timetable"
                showAddTimetableDialog = true
                onConsumedInitialAction()
            }
            "add_assessment", "assessments" -> {
                selectedSection = "assessments"
                if (initialAction == "add_assessment") showAddAssessmentDialog = true
                onConsumedInitialAction()
            }
            "calendar" -> {
                selectedSection = "calendar"
                onConsumedInitialAction()
            }
            "timetable" -> {
                selectedSection = "timetable"
                onConsumedInitialAction()
            }
        }
    }

    val yearClasses = remember(snapshot.classes, viewedYearId) {
        snapshot.classes.filter { it.schoolYearId == viewedYearId }
    }
    val activeClasses = remember(yearClasses) { yearClasses.filter { !it.isArchived } }
    val classById = remember(yearClasses) { yearClasses.associateBy { it.id } }

    val yearTimetable = remember(snapshot.timetableEntries, viewedYearId, classById) {
        snapshot.timetableEntries
            .filter {
                it.schoolYearId == viewedYearId &&
                    !it.isArchived &&
                    classById[it.classId]?.isArchived == false
            }
            .sortedWith(compareBy<TimetableEntry> { it.day }.thenBy { it.startTime })
    }

    val yearCalendarEvents = remember(snapshot.calendarEvents, viewedYearId) {
        snapshot.calendarEvents
            .filter { it.schoolYearId == viewedYearId }
            .sortedBy { it.startDate }
    }

    val yearAssessments = remember(snapshot.assessments, viewedYearId) {
        snapshot.assessments
            .filter { it.schoolYearId == viewedYearId }
            .sortedBy { it.date }
    }

    // Calculate current school week dates for «افتح الحصة» from timetable
    val today = DateTimeUtils.todayDateString()
    val todayDayIdx = DateTimeUtils.getDayIndexFromSunday(today)
    val sundayDate = DateTimeUtils.addDays(today, -todayDayIdx)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 768.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("schedule_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "التوقيت الأسبوعي والرزنامة",
                subtitle = "تنظيم الحصص الأسبوعية، الفروض والاختبارات، والعطل والمناسبات الرسمية"
            )

            // Section Tabs: التوقيت الأسبوعي | الفروض والاختبارات | العطل والمناسبات
            ScrollablePillRow(
                items = listOf(
                    "timetable" to "التوقيت الأسبوعي",
                    "assessments" to "الفروض والاختبارات",
                    "calendar" to "العطل والمناسبات"
                ),
                selectedValue = selectedSection,
                onSelect = { selectedSection = it },
                testTagPrefix = "tab_schedule"
            )

            when (selectedSection) {
                "timetable" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "حصص التوقيت الأسبوعي (الأحد – الخميس)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddTimetableDialog = true },
                            enabled = activeClasses.isNotEmpty(),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("add_timetable_entry_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة حصة")
                        }
                    }

                    if (activeClasses.isEmpty()) {
                        TeacherEmptyState(
                            icon = Icons.Outlined.Groups,
                            title = "أضيفي قسمًا نشطًا أولًا",
                            description = "يرتبط كل توقيت أسبوعي بقسم حقيقي موجود في السنة الدراسية."
                        )
                    } else if (isWideScreen) {
                        // >= 768dp: Full weekly grid (Section 8.4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArStrings.SCHOOL_DAYS.forEachIndexed { dayIdx, dayName ->
                                val dayEntries = yearTimetable.filter { it.day == dayIdx }
                                val slotDate = DateTimeUtils.addDays(sundayDate, dayIdx)
                                Surface(
                                    color = palette.surface,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = dayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.primaryText
                                        )
                                        if (dayEntries.isEmpty()) {
                                            Text(
                                                text = "لا توجد حصص",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = palette.textSecondary
                                            )
                                        } else {
                                            dayEntries.forEach { entry ->
                                                TimetableSlotItemCard(
                                                    entry = entry,
                                                    schoolClass = classById[entry.classId],
                                                    onOpenSlot = {
                                                        repository.openOrCreateLessonFromSlot(entry.id, slotDate)
                                                            .onSuccess { onOpenLessonPlan(it) }
                                                    },
                                                    onEdit = { editingTimetable = entry },
                                                    onDelete = { deletingTimetable = entry }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Phone (< 768dp): One day at a time with easy day switching (Section 8.4)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ArStrings.SCHOOL_DAYS.forEachIndexed { idx, dName ->
                                FilterChip(
                                    selected = selectedDayIndex == idx,
                                    onClick = { selectedDayIndex = idx },
                                    label = { Text(dName) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("day_chip_$idx")
                                )
                            }
                        }

                        val dayEntries = yearTimetable.filter { it.day == selectedDayIndex }
                        val slotDate = DateTimeUtils.addDays(sundayDate, selectedDayIndex)
                        if (dayEntries.isEmpty()) {
                            TeacherEmptyState(
                                icon = Icons.Outlined.Schedule,
                                title = "لا توجد حصص مسجلة ليوم ${ArStrings.SCHOOL_DAYS[selectedDayIndex]}",
                                description = "أضيفي حصة جديدة لهذا اليوم وحددي القسم والتوقيت.",
                                actionLabel = "إضافة حصة",
                                onAction = { showAddTimetableDialog = true }
                            )
                        } else {
                            dayEntries.forEach { entry ->
                                TimetableSlotItemCard(
                                    entry = entry,
                                    schoolClass = classById[entry.classId],
                                    onOpenSlot = {
                                        repository.openOrCreateLessonFromSlot(entry.id, slotDate)
                                            .onSuccess { onOpenLessonPlan(it) }
                                    },
                                    onEdit = { editingTimetable = entry },
                                    onDelete = { deletingTimetable = entry }
                                )
                            }
                        }
                    }
                }

                "assessments" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "رزنامة الفروض والاختبارات",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddAssessmentDialog = true },
                            enabled = activeClasses.isNotEmpty(),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("add_assessment_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة فرض أو اختبار")
                        }
                    }

                    if (yearAssessments.isEmpty()) {
                        TeacherEmptyState(
                            icon = Icons.Outlined.Assignment,
                            title = "لم تتم إضافة فروض أو اختبارات بعد",
                            description = "سجّلي مواعيد الفروض والاختبارات وتواريخ تصحيحها لتظهر تذكيراتها في صفحة «نهاري».",
                            actionLabel = if (activeClasses.isNotEmpty()) "إضافة فرض أو اختبار" else null,
                            onAction = if (activeClasses.isNotEmpty()) ({ showAddAssessmentDialog = true }) else null
                        )
                    } else {
                        yearAssessments.forEach { ass ->
                            val clsNames = ass.classIds.mapNotNull { classById[it]?.fullTitle }.joinToString("، ")
                            Surface(
                                color = palette.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                                imageVector = if (ass.kind == "اختبار") Icons.Outlined.MenuBook else Icons.Outlined.Assignment,
                                                contentDescription = null,
                                                tint = palette.primaryText
                                            )
                                            Text(
                                                text = "${ass.kind}: ${ass.title}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (ass.isSample) SampleBadge()
                                        }
                                        Row {
                                            IconButton(
                                                onClick = { editingAssessment = ass },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(Icons.Outlined.Edit, contentDescription = "تعديل")
                                            }
                                            IconButton(
                                                onClick = { deletingAssessment = ass },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = palette.error)
                                            }
                                        }
                                    }
                                    Text(
                                        text = "الأقسام المعنية: $clsNames",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "تاريخ الإجراء: ${DateTimeUtils.formatAlgerianDate(ass.date)} • تاريخ التصحيح: ${DateTimeUtils.formatAlgerianDate(ass.correctionDate)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textSecondary
                                    )
                                    if (ass.notes.isNotBlank()) {
                                        Text(
                                            text = "ملاحظات: ${ass.notes}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                "calendar" -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "العطل والمناسبات الدينية والوطنية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddCalendarDialog = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("add_calendar_event_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إضافة عطلة أو مناسبة")
                        }
                    }

                    if (yearCalendarEvents.isEmpty()) {
                        TeacherEmptyState(
                            icon = Icons.Outlined.Event,
                            title = "الرزنامة المدرسية فارغة",
                            description = "أضيفي عطل الخريف والشتاء والربيع والمناسبات الدينية والوطنية يدويًا حسب الرزنامة المعتمدة لديكِ.",
                            actionLabel = "إضافة عطلة أو مناسبة",
                            onAction = { showAddCalendarDialog = true }
                        )
                    } else {
                        yearCalendarEvents.forEach { ev ->
                            Surface(
                                color = palette.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${ev.kindArabic} — ${ev.title}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.primaryText
                                            )
                                            Text(
                                                text = if (ev.startDate == ev.endDate) {
                                                    DateTimeUtils.formatAlgerianDate(ev.startDate, includeWeekday = true)
                                                } else {
                                                    "من ${DateTimeUtils.formatAlgerianDate(ev.startDate)} إلى ${DateTimeUtils.formatAlgerianDate(ev.endDate)}"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = palette.text
                                            )
                                        }
                                        Row {
                                            IconButton(
                                                onClick = { editingCalendarEvent = ev },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(Icons.Outlined.Edit, contentDescription = "تعديل")
                                            }
                                            IconButton(
                                                onClick = { deletingCalendarEvent = ev },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = palette.error)
                                            }
                                        }
                                    }
                                    if (ev.notes.isNotBlank()) {
                                        Text(
                                            text = ev.notes,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Timetable Dialog (with overlap warning and explicit «حفظ رغم التداخل»)
    if ((showAddTimetableDialog || editingTimetable != null) && activeClasses.isNotEmpty()) {
        TimetableEntryDialog(
            existing = editingTimetable,
            initialDay = selectedDayIndex,
            activeClasses = activeClasses,
            allClassesById = classById,
            repository = repository,
            onDismiss = {
                showAddTimetableDialog = false
                editingTimetable = null
            }
        )
    }

    // D6: Delete or Archive Timetable Entry Dialog
    if (deletingTimetable != null) {
        val tt = deletingTimetable!!
        val deps = repository.getTimetableDependents(tt.id)
        ConfirmActionDialog(
            title = if (deps.canDeletePermanently) "حذف حصة التوقيت" else "أرشفة حصة التوقيت",
            message = if (deps.canDeletePermanently) {
                "هل أنتِ متأكدة من حذف هذه الحصة (${tt.startTime} - ${tt.endTime}) من التوقيت الأسبوعي؟"
            } else {
                "توجد ${deps.lessonPlanCount} مذكرة تحضير مرتبطة بهذه الحصة؛ لذلك سيتم أرشفتها وإخفاؤها من جدول التوقيت مع الحفاظ على سجل الحصص السابقة."
            },
            confirmLabel = if (deps.canDeletePermanently) "حذف الحصة" else "أرشفة الحصة",
            onConfirm = {
                repository.archiveOrDeleteTimetableEntry(tt.id)
                deletingTimetable = null
            },
            onDismiss = { deletingTimetable = null }
        )
    }

    // Add / Edit Calendar Event Dialog
    if (showAddCalendarDialog || editingCalendarEvent != null) {
        CalendarEventDialog(
            existing = editingCalendarEvent,
            repository = repository,
            viewedYearId = viewedYearId,
            onDismiss = {
                showAddCalendarDialog = false
                editingCalendarEvent = null
            }
        )
    }

    if (deletingCalendarEvent != null) {
        ConfirmActionDialog(
            title = "حذف حدث الرزنامة",
            message = "هل أنتِ متأكدة من حذف «${deletingCalendarEvent!!.title}»؟",
            confirmLabel = "تأكيد الحذف",
            onConfirm = {
                repository.deleteCalendarEvent(deletingCalendarEvent!!.id)
                deletingCalendarEvent = null
            },
            onDismiss = { deletingCalendarEvent = null }
        )
    }

    // Add / Edit Assessment Dialog
    if ((showAddAssessmentDialog || editingAssessment != null) && activeClasses.isNotEmpty()) {
        AssessmentDialog(
            existing = editingAssessment,
            activeClasses = activeClasses,
            repository = repository,
            viewedYearId = viewedYearId,
            onDismiss = {
                showAddAssessmentDialog = false
                editingAssessment = null
            }
        )
    }

    if (deletingAssessment != null) {
        ConfirmActionDialog(
            title = "حذف الفرض / الاختبار",
            message = "هل أنتِ متأكدة من حذف «${deletingAssessment!!.title}»؟",
            confirmLabel = "تأكيد الحذف",
            onConfirm = {
                repository.deleteAssessment(deletingAssessment!!.id)
                deletingAssessment = null
            },
            onDismiss = { deletingAssessment = null }
        )
    }
}

@Composable
private fun TimetableSlotItemCard(
    entry: TimetableEntry,
    schoolClass: SchoolClass?,
    onOpenSlot: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    Surface(
        color = palette.raised,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("timetable_card_${entry.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        color = palette.text
                    )
                    if (entry.isSample || schoolClass?.isSample == true) {
                        SampleBadge()
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "تعديل الحصة")
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف الحصة", tint = palette.error)
                    }
                }
            }
            Text(
                text = "${DateTimeUtils.formatTimeRange(entry.startTime, entry.endTime)} • الفترة ${entry.period}",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            Button(
                onClick = onOpenSlot,
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = 48.dp)
                    .testTag("timetable_open_slot_${entry.id}")
            ) {
                Text("افتح الحصة")
            }
        }
    }
}

@Composable
private fun TimetableEntryDialog(
    existing: TimetableEntry?,
    initialDay: Int,
    activeClasses: List<SchoolClass>,
    allClassesById: Map<String, SchoolClass>,
    repository: TeacherRepository,
    onDismiss: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    var day by remember { mutableIntStateOf(existing?.day ?: initialDay.coerceIn(0, 4)) }
    var classId by remember {
        mutableStateOf(
            existing?.classId?.takeIf { id -> activeClasses.any { it.id == id } }
                ?: activeClasses.first().id
        )
    }
    var startTime by remember { mutableStateOf(existing?.startTime ?: "08:00") }
    var endTime by remember { mutableStateOf(existing?.endTime ?: "09:00") }
    var period by remember {
        mutableStateOf(existing?.period ?: DateTimeUtils.suggestPeriod("08:00"))
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val selectedClass = activeClasses.find { it.id == classId } ?: activeClasses.first()
    val overlaps = remember(selectedClass.schoolYearId, day, startTime, endTime, existing?.id) {
        if (DateTimeUtils.isEndAfterStart(startTime, endTime)) {
            repository.findTimetableOverlaps(
                schoolYearId = selectedClass.schoolYearId,
                day = day,
                startTime = startTime,
                endTime = endTime,
                ignoreEntryId = existing?.id
            )
        } else emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "إضافة حصة إلى التوقيت" else "تعديل حصة التوقيت",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownRowSelector(
                    title = "اليوم الدراسي",
                    selectedLabel = ArStrings.SCHOOL_DAYS.getOrElse(day) { "الأحد" },
                    options = ArStrings.SCHOOL_DAYS.mapIndexed { idx, name -> idx to name },
                    onSelect = { day = it }
                )
                DropdownRowSelector(
                    title = "القسم",
                    selectedLabel = selectedClass.fullTitle,
                    options = activeClasses.map { it.id to it.fullTitle },
                    onSelect = { classId = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Time24Field(
                        label = "وقت البداية",
                        value = startTime,
                        onValueChange = {
                            startTime = it
                            period = DateTimeUtils.suggestPeriod(it)
                            errorMsg = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Time24Field(
                        label = "وقت النهاية",
                        value = endTime,
                        onValueChange = {
                            endTime = it
                            errorMsg = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                DropdownRowSelector(
                    title = "الفترة",
                    selectedLabel = period,
                    options = listOf(
                        ArStrings.PERIOD_MORNING to ArStrings.PERIOD_MORNING,
                        ArStrings.PERIOD_AFTERNOON to ArStrings.PERIOD_AFTERNOON
                    ),
                    onSelect = { period = it }
                )

                // Overlap inline warning naming the conflicting entry (Section 8.4 & T15)
                if (overlaps.isNotEmpty()) {
                    val firstConflict = overlaps.first()
                    val conflictClassTitle = allClassesById[firstConflict.classId]?.fullTitle ?: "قسم آخر"
                    Surface(
                        color = palette.warning.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.warning),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("timetable_overlap_warning")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = palette.warning)
                            Text(
                                text = "تنبيه تداخل زمني: يتقاطع هذا الوقت مع حصة «$conflictClassTitle» (${firstConflict.startTime} - ${firstConflict.endTime}).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.text
                            )
                        }
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = palette.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("timetable_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (overlaps.isNotEmpty()) {
                    Button(
                        onClick = {
                            val res = repository.saveTimetableEntry(
                                existingId = existing?.id,
                                classId = classId,
                                day = day,
                                startTime = startTime,
                                endTime = endTime,
                                period = period,
                                allowOverlap = true
                            )
                            if (res.isSuccess) onDismiss()
                            else errorMsg = res.exceptionOrNull()?.message
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.warning,
                            contentColor = palette.bg
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("save_despite_overlap_btn")
                    ) {
                        Text("حفظ رغم التداخل")
                    }
                } else {
                    Button(
                        onClick = {
                            val res = repository.saveTimetableEntry(
                                existingId = existing?.id,
                                classId = classId,
                                day = day,
                                startTime = startTime,
                                endTime = endTime,
                                period = period,
                                allowOverlap = false
                            )
                            if (res.isSuccess) onDismiss()
                            else errorMsg = res.exceptionOrNull()?.message
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("save_timetable_btn")
                    ) {
                        Text("حفظ")
                    }
                }
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
private fun CalendarEventDialog(
    existing: CalendarEvent?,
    repository: TeacherRepository,
    viewedYearId: String,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(existing?.kind ?: "break") }
    var season by remember { mutableStateOf(existing?.season ?: "autumn") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var startDate by remember { mutableStateOf(existing?.startDate ?: DateTimeUtils.todayDateString()) }
    var endDate by remember { mutableStateOf(existing?.endDate ?: DateTimeUtils.todayDateString()) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "إضافة عطلة أو مناسبة" else "تعديل العطلة / المناسبة",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownRowSelector(
                    title = "النوع",
                    selectedLabel = when (kind) {
                        "break" -> "عطلة مدرسية"
                        "religious" -> "مناسبة دينية"
                        else -> "مناسبة وطنية"
                    },
                    options = listOf(
                        "break" to "عطلة مدرسية",
                        "religious" to "مناسبة دينية",
                        "national" to "مناسبة وطنية"
                    ),
                    onSelect = { kind = it }
                )
                if (kind == "break") {
                    DropdownRowSelector(
                        title = "الفصل / الموسم",
                        selectedLabel = when (season) {
                            "autumn" -> "عطلة الخريف"
                            "winter" -> "عطلة الشتاء"
                            else -> "عطلة الربيع"
                        },
                        options = listOf(
                            "autumn" to "عطلة الخريف",
                            "winter" to "عطلة الشتاء",
                            "spring" to "عطلة الربيع"
                        ),
                        onSelect = { season = it }
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMsg = null
                    },
                    label = { Text("العنوان (مثال: عطلة الخريف / أول نوفمبر)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                AlgerianDateField(
                    label = "تاريخ البداية",
                    value = startDate,
                    onValueChange = { startDate = it }
                )
                AlgerianDateField(
                    label = "تاريخ النهاية",
                    value = endDate,
                    onValueChange = { endDate = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = repository.saveCalendarEvent(
                        existingId = existing?.id,
                        kind = kind,
                        season = if (kind == "break") season else null,
                        title = title,
                        startDate = startDate,
                        endDate = endDate,
                        notes = notes,
                        schoolYearId = viewedYearId
                    )
                    if (res.isSuccess) onDismiss()
                    else errorMsg = res.exceptionOrNull()?.message
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("حفظ")
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
private fun AssessmentDialog(
    existing: AssessmentEvent?,
    activeClasses: List<SchoolClass>,
    repository: TeacherRepository,
    viewedYearId: String,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(existing?.kind ?: "فرض") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    val selectedClassIds = remember {
        mutableStateListOf<String>().apply {
            if (existing != null) addAll(existing.classIds)
            else add(activeClasses.first().id)
        }
    }
    var date by remember { mutableStateOf(existing?.date ?: DateTimeUtils.todayDateString()) }
    var correctionDate by remember {
        mutableStateOf(existing?.correctionDate ?: DateTimeUtils.addDays(DateTimeUtils.todayDateString(), 4))
    }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "إضافة فرض أو اختبار" else "تعديل الفرض / الاختبار",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == "فرض",
                        onClick = { kind = "فرض" },
                        label = { Text("فرض محروس") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = kind == "اختبار",
                        onClick = { kind = "اختبار" },
                        label = { Text("اختبار فصلي") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMsg = null
                    },
                    label = { Text("العنوان (مثال: الفرض الأول للفصل الأول)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("assessment_title_input"),
                    singleLine = true
                )
                Text(
                    text = "الأقسام المعنية (واحد أو أكثر):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                activeClasses.forEach { cls ->
                    val checked = cls.id in selectedClassIds
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (isChecked) selectedClassIds.add(cls.id)
                                else selectedClassIds.remove(cls.id)
                            }
                        )
                        Text(cls.fullTitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                AlgerianDateField(
                    label = "تاريخ الإجراء",
                    value = date,
                    onValueChange = {
                        date = it
                        if (correctionDate < it) correctionDate = it
                    }
                )
                AlgerianDateField(
                    label = "تاريخ التصحيح",
                    value = correctionDate,
                    onValueChange = { correctionDate = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = repository.saveAssessment(
                        existingId = existing?.id,
                        kind = kind,
                        title = title,
                        classIds = selectedClassIds.toList(),
                        date = date,
                        correctionDate = correctionDate,
                        notes = notes,
                        schoolYearId = viewedYearId
                    )
                    if (res.isSuccess) onDismiss()
                    else errorMsg = res.exceptionOrNull()?.message
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_assessment_btn")
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إلغاء")
            }
        }
    )
}
