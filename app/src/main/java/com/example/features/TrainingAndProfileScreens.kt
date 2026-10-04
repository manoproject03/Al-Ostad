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
import com.example.print.PrintAndExportHelper
import com.example.print.PrintJobSpec
import com.example.print.PrintPreviewDialog
import com.example.storage.DatabaseSnapshot
import com.example.storage.TeacherRepository
import com.example.ui.theme.LocalTeacherPalette

@Composable
fun TrainingAndSeminarsScreen(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    viewedYearId: String,
    initialTab: String? = null,
    onConsumedInitialTab: () -> Unit = {}
) {
    val palette = LocalTeacherPalette.current
    var selectedTab by remember { mutableStateOf("notes") } // "notes" | "seminars" | "eduCalendar"
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<TrainingNote?>(null) }
    var deletingNote by remember { mutableStateOf<TrainingNote?>(null) }

    var showAddSeminarDialog by remember { mutableStateOf(false) }
    var editingSeminar by remember { mutableStateOf<SeminarRecord?>(null) }
    var deletingSeminar by remember { mutableStateOf<SeminarRecord?>(null) }

    var showAddEduCalendarDialog by remember { mutableStateOf(false) }
    var editingEduCalendar by remember { mutableStateOf<EduCalendarRecord?>(null) }
    var deletingEduCalendar by remember { mutableStateOf<EduCalendarRecord?>(null) }

    LaunchedEffect(initialTab) {
        if (initialTab in listOf("notes", "seminars", "eduCalendar")) {
            selectedTab = initialTab!!
            onConsumedInitialTab()
        }
    }

    val categories = remember(snapshot.trainingCategories) {
        snapshot.trainingCategories.sortedBy { it.orderIndex }
    }

    val yearNotes = remember(snapshot.trainingNotes, viewedYearId, searchQuery, selectedCategoryFilter) {
        val normQ = ArabicUtils.normalizeArabic(searchQuery)
        snapshot.trainingNotes
            .filter { it.schoolYearId == viewedYearId }
            .filter { selectedCategoryFilter == null || it.categoryName == selectedCategoryFilter }
            .filter {
                normQ.isEmpty() ||
                    ArabicUtils.normalizeArabic(it.title).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.text).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.categoryName).contains(normQ)
            }
            .sortedByDescending { it.date }
    }

    val yearSeminars = remember(snapshot.seminars, viewedYearId, searchQuery) {
        val normQ = ArabicUtils.normalizeArabic(searchQuery)
        snapshot.seminars
            .filter { it.schoolYearId == viewedYearId }
            .filter {
                normQ.isEmpty() ||
                    ArabicUtils.normalizeArabic(it.title).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.location).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.facilitator).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.mainIdeas).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.recommendations).contains(normQ)
            }
            .sortedByDescending { it.date }
    }

    val yearEduRecords = remember(snapshot.eduCalendarRecords, viewedYearId, searchQuery) {
        val normQ = ArabicUtils.normalizeArabic(searchQuery)
        snapshot.eduCalendarRecords
            .filter { it.schoolYearId == viewedYearId }
            .filter {
                normQ.isEmpty() ||
                    ArabicUtils.normalizeArabic(it.typeArabic).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.appliedLesson).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.teacherName).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.level).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.topic).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.location).contains(normQ) ||
                    ArabicUtils.normalizeArabic(it.internshipType).contains(normQ)
            }
            .sortedByDescending { it.date }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("training_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "التكوين والندوات والرزنامة التربوية",
            subtitle = "توثيق الملاحظات التكوينية، الندوات الداخلية والخارجية، وسجل الرزنامة التربوية"
        )

        // Tabs: ملاحظات التكوين | الندوات | الرزنامة التربوية
        ScrollablePillRow(
            items = listOf(
                "notes" to "ملاحظات التكوين (${yearNotes.size})",
                "seminars" to "الندوات (${yearSeminars.size})",
                "eduCalendar" to "الرزنامة التربوية (${yearEduRecords.size})"
            ),
            selectedValue = selectedTab,
            onSelect = { selectedTab = it },
            testTagPrefix = "tab_training"
        )

        // Local search bar for active tab
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("بحث في هذا القسم") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("training_local_search_input"),
            singleLine = true
        )

        when (selectedTab) {
            "notes" -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DropdownRowSelector(
                        title = "تصفية حسب الفئة",
                        selectedLabel = selectedCategoryFilter ?: "جميع فئات التكوين",
                        options = listOf((null as String?) to "جميع فئات التكوين") +
                            categories.map { (it.name as String?) to it.name },
                        onSelect = { selectedCategoryFilter = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { showAddNoteDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("add_training_note_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة ملاحظة تكوين")
                    }
                }

                if (yearNotes.isEmpty()) {
                    TeacherEmptyState(
                        icon = Icons.Outlined.School,
                        title = "لا توجد ملاحظات تكوين مسجلة",
                        description = "دوّني ملاحظاتكِ التكوينية في تعليمية المادة، التشريع المدرسي، هندسة التكوين وغيرها للرجوع إليها بسهولة.",
                        actionLabel = "إضافة ملاحظة تكوين",
                        onAction = { showAddNoteDialog = true }
                    )
                } else {
                    yearNotes.forEach { note ->
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("training_note_card_${note.id}")
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
                                    Surface(
                                        color = palette.primary.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = note.categoryName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = palette.primaryText,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { editingNote = note }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "تعديل")
                                        }
                                        IconButton(onClick = { deletingNote = note }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = palette.error)
                                        }
                                    }
                                }
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.text
                                )
                                Text(
                                    text = DateTimeUtils.formatAlgerianDate(note.date, includeWeekday = true),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textSecondary
                                )
                                if (note.text.isNotBlank()) {
                                    Text(
                                        text = note.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.text
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "seminars" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل الندوات (الداخلية والخارجية)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { showAddSeminarDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_seminar_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة ندوة")
                    }
                }

                if (yearSeminars.isEmpty()) {
                    TeacherEmptyState(
                        icon = Icons.Outlined.Forum,
                        title = "لا توجد ندوات مسجلة بعد",
                        description = "وثّقي الندوات الداخلية والخارجية، المؤطر أو المفتش، أهم الأفكار، والتوصيات البيداغوجية.",
                        actionLabel = "إضافة ندوة",
                        onAction = { showAddSeminarDialog = true }
                    )
                } else {
                    yearSeminars.forEach { sem ->
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seminar_card_${sem.id}")
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
                                    Text(
                                        text = "${sem.kindArabic} — ${sem.title}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryText,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row {
                                        IconButton(onClick = { editingSeminar = sem }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "تعديل")
                                        }
                                        IconButton(onClick = { deletingSeminar = sem }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = palette.error)
                                        }
                                    }
                                }
                                Text(
                                    text = "التاريخ: ${DateTimeUtils.formatAlgerianDate(sem.date)} • المكان: ${sem.location.ifBlank { "—" }} • المؤطر/المفتش: ${sem.facilitator.ifBlank { "—" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                                if (sem.mainIdeas.isNotBlank()) {
                                    Text(
                                        text = "الأفكار الأساسية: ${sem.mainIdeas}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.text
                                    )
                                }
                                if (sem.recommendations.isNotBlank()) {
                                    Text(
                                        text = "التوصيات: ${sem.recommendations}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.text
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "eduCalendar" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الرزنامة التربوية (ندوات تربوية، أيام دراسية، تربصات)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { showAddEduCalendarDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("add_edu_cal_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة سجل")
                    }
                }

                if (yearEduRecords.isEmpty()) {
                    TeacherEmptyState(
                        icon = Icons.Outlined.EventNote,
                        title = "لا توجد سجلات في الرزنامة التربوية",
                        description = "أضيفي الندوات التربوية التطبيقية، الأيام الدراسية، والتربصات التكوينية.",
                        actionLabel = "إضافة سجل",
                        onAction = { showAddEduCalendarDialog = true }
                    )
                } else {
                    yearEduRecords.forEach { rec ->
                        Surface(
                            color = palette.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edu_cal_card_${rec.id}")
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
                                    Text(
                                        text = "${rec.typeArabic} — ${DateTimeUtils.formatAlgerianDate(rec.date)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryText
                                    )
                                    Row {
                                        IconButton(onClick = { editingEduCalendar = rec }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "تعديل")
                                        }
                                        IconButton(onClick = { deletingEduCalendar = rec }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = palette.error)
                                        }
                                    }
                                }
                                when (rec.type) {
                                    "pedagogicalSeminar" -> {
                                        Text("الموضوع: ${rec.topic}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "الدرس التطبيقي: ${rec.appliedLesson.ifBlank { "—" }} • الأستاذ(ة) المطبق(ة): ${rec.teacherName.ifBlank { "—" }} • المستوى: ${rec.level.ifBlank { "—" }}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = palette.textSecondary
                                        )
                                    }
                                    "studyDay" -> {
                                        Text("الموضوع: ${rec.topic}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("المكان: ${rec.location.ifBlank { "—" }}", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                    }
                                    "internship" -> {
                                        Text("نوع التربص: ${rec.internshipType}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("المكان: ${rec.location.ifBlank { "—" }}", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs for Training Note
    if (showAddNoteDialog || editingNote != null) {
        TrainingNoteDialog(
            existing = editingNote,
            categories = categories,
            repository = repository,
            viewedYearId = viewedYearId,
            onDismiss = {
                showAddNoteDialog = false
                editingNote = null
            }
        )
    }

    if (deletingNote != null) {
        ConfirmActionDialog(
            title = "حذف ملاحظة التكوين",
            message = "هل أنتِ متأكدة من حذف «${deletingNote!!.title}»؟",
            confirmLabel = "تأكيد الحذف",
            onConfirm = {
                repository.deleteTrainingNote(deletingNote!!.id)
                deletingNote = null
            },
            onDismiss = { deletingNote = null }
        )
    }

    // Dialogs for Seminar
    if (showAddSeminarDialog || editingSeminar != null) {
        SeminarDialog(
            existing = editingSeminar,
            repository = repository,
            viewedYearId = viewedYearId,
            onDismiss = {
                showAddSeminarDialog = false
                editingSeminar = null
            }
        )
    }

    if (deletingSeminar != null) {
        ConfirmActionDialog(
            title = "حذف الندوة",
            message = "هل أنتِ متأكدة من حذف ندوة «${deletingSeminar!!.title}»؟",
            confirmLabel = "تأكيد الحذف",
            onConfirm = {
                repository.deleteSeminar(deletingSeminar!!.id)
                deletingSeminar = null
            },
            onDismiss = { deletingSeminar = null }
        )
    }

    // Dialogs for Educational Calendar
    if (showAddEduCalendarDialog || editingEduCalendar != null) {
        EduCalendarDialog(
            existing = editingEduCalendar,
            repository = repository,
            viewedYearId = viewedYearId,
            onDismiss = {
                showAddEduCalendarDialog = false
                editingEduCalendar = null
            }
        )
    }

    if (deletingEduCalendar != null) {
        ConfirmActionDialog(
            title = "حذف سجل الرزنامة التربوية",
            message = "هل أنتِ متأكدة من حذف هذا السجل (${deletingEduCalendar!!.typeArabic})؟",
            confirmLabel = "تأكيد الحذف",
            onConfirm = {
                repository.deleteEduCalendarRecord(deletingEduCalendar!!.id)
                deletingEduCalendar = null
            },
            onDismiss = { deletingEduCalendar = null }
        )
    }
}

@Composable
private fun TrainingNoteDialog(
    existing: TrainingNote?,
    categories: List<TrainingCategory>,
    repository: TeacherRepository,
    viewedYearId: String,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: DateTimeUtils.todayDateString()) }
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var selectedCategoryName by remember {
        mutableStateOf(existing?.categoryName ?: categories.firstOrNull()?.name ?: "التشريع المدرسي")
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "إضافة ملاحظة تكوين" else "تعديل ملاحظة التكوين",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownRowSelector(
                    title = "فئة التكوين",
                    selectedLabel = selectedCategoryName,
                    options = categories.map { it.name to it.name },
                    onSelect = { selectedCategoryName = it }
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMsg = null
                    },
                    label = { Text("عنوان الملاحظة") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("training_note_title_input"),
                    singleLine = true
                )
                AlgerianDateField(
                    label = "التاريخ",
                    value = date,
                    onValueChange = { date = it }
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("محتوى الملاحظة التكوينية") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("training_note_text_input"),
                    minLines = 4
                )
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val catId = categories.find { it.name == selectedCategoryName }?.id
                    val res = repository.saveTrainingNote(
                        existingId = existing?.id,
                        title = title,
                        date = date,
                        text = text,
                        categoryId = catId,
                        categoryName = selectedCategoryName,
                        schoolYearId = viewedYearId
                    )
                    if (res.isSuccess) onDismiss()
                    else errorMsg = res.exceptionOrNull()?.message
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_training_note_btn")
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
private fun SeminarDialog(
    existing: SeminarRecord?,
    repository: TeacherRepository,
    viewedYearId: String,
    onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(existing?.kind ?: "internal") }
    var date by remember { mutableStateOf(existing?.date ?: DateTimeUtils.todayDateString()) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var facilitator by remember { mutableStateOf(existing?.facilitator ?: "") }
    var mainIdeas by remember { mutableStateOf(existing?.mainIdeas ?: "") }
    var recommendations by remember { mutableStateOf(existing?.recommendations ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "إضافة ندوة" else "تعديل الندوة",
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
                        selected = kind == "internal",
                        onClick = { kind = "internal" },
                        label = { Text("ندوة داخلية") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = kind == "external",
                        onClick = { kind = "external" },
                        label = { Text("ندوة خارجية") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMsg = null
                    },
                    label = { Text("عنوان الندوة") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("seminar_title_input"),
                    singleLine = true
                )
                AlgerianDateField(
                    label = "التاريخ",
                    value = date,
                    onValueChange = { date = it }
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("المكان / المؤسسة") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = facilitator,
                    onValueChange = { facilitator = it },
                    label = { Text("المؤطر / المفتش") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = mainIdeas,
                    onValueChange = { mainIdeas = it },
                    label = { Text("أهم الأفكار والمحاور") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = recommendations,
                    onValueChange = { recommendations = it },
                    label = { Text("التوصيات والتوجيهات") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = repository.saveSeminar(
                        existingId = existing?.id,
                        kind = kind,
                        date = date,
                        title = title,
                        location = location,
                        facilitator = facilitator,
                        mainIdeas = mainIdeas,
                        recommendations = recommendations,
                        schoolYearId = viewedYearId
                    )
                    if (res.isSuccess) onDismiss()
                    else errorMsg = res.exceptionOrNull()?.message
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_seminar_btn")
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
private fun EduCalendarDialog(
    existing: EduCalendarRecord?,
    repository: TeacherRepository,
    viewedYearId: String,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(existing?.type ?: "pedagogicalSeminar") }
    var date by remember { mutableStateOf(existing?.date ?: DateTimeUtils.todayDateString()) }
    var appliedLesson by remember { mutableStateOf(existing?.appliedLesson ?: "") }
    var teacherName by remember { mutableStateOf(existing?.teacherName ?: "") }
    var level by remember { mutableStateOf(existing?.level ?: ArStrings.SCHOOL_LEVELS.first()) }
    var topic by remember { mutableStateOf(existing?.topic ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var internshipType by remember { mutableStateOf(existing?.internshipType ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "إضافة سجل في الرزنامة التربوية" else "تعديل سجل الرزنامة التربوية",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DropdownRowSelector(
                    title = "نوع النشاط التربوي",
                    selectedLabel = when (type) {
                        "pedagogicalSeminar" -> "ندوة تربوية"
                        "studyDay" -> "يوم دراسي"
                        else -> "تربص"
                    },
                    options = listOf(
                        "pedagogicalSeminar" to "ندوة تربوية",
                        "studyDay" to "يوم دراسي",
                        "internship" to "تربص"
                    ),
                    onSelect = {
                        type = it
                        errorMsg = null
                    }
                )
                AlgerianDateField(
                    label = "التاريخ",
                    value = date,
                    onValueChange = { date = it }
                )

                when (type) {
                    "pedagogicalSeminar" -> {
                        OutlinedTextField(
                            value = topic,
                            onValueChange = { topic = it; errorMsg = null },
                            label = { Text("موضوع الندوة التربوية") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = appliedLesson,
                            onValueChange = { appliedLesson = it; errorMsg = null },
                            label = { Text("الدرس التطبيقي") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = teacherName,
                            onValueChange = { teacherName = it },
                            label = { Text("الأستاذ(ة) المطبق(ة) أو المشارك(ة)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        DropdownRowSelector(
                            title = "المستوى الدراسي",
                            selectedLabel = level,
                            options = ArStrings.SCHOOL_LEVELS.map { it to it },
                            onSelect = { level = it }
                        )
                    }
                    "studyDay" -> {
                        OutlinedTextField(
                            value = topic,
                            onValueChange = { topic = it; errorMsg = null },
                            label = { Text("موضوع اليوم الدراسي") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("المكان") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    "internship" -> {
                        OutlinedTextField(
                            value = internshipType,
                            onValueChange = { internshipType = it; errorMsg = null },
                            label = { Text("نوع التربص") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("المكان") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = repository.saveEduCalendarRecord(
                        existingId = existing?.id,
                        type = type,
                        date = date,
                        appliedLesson = appliedLesson,
                        teacherName = teacherName,
                        level = level,
                        topic = topic,
                        location = location,
                        internshipType = internshipType,
                        schoolYearId = viewedYearId
                    )
                    if (res.isSuccess) onDismiss()
                    else errorMsg = res.exceptionOrNull()?.message
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_edu_cal_btn")
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

/**
 * Section 8.8 & D18: Professional Profile «بطاقتي المهنية»
 */
@Composable
fun ProfessionalProfileScreen(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository
) {
    val palette = LocalTeacherPalette.current
    val prof = snapshot.profile
    var professionalName by remember(prof) { mutableStateOf(prof.professionalName) }
    var institution by remember(prof) { mutableStateOf(prof.institution) }
    var employmentStatus by remember(prof) { mutableStateOf(prof.employmentStatus) }
    var specialization by remember(prof) { mutableStateOf(prof.specialization) }
    var qualifications by remember(prof) { mutableStateOf(prof.qualifications) }
    var appointmentDate by remember(prof) { mutableStateOf(prof.appointmentDate) }
    var rank by remember(prof) { mutableStateOf(prof.rank) }
    var inspectionHistory by remember(prof) { mutableStateOf(prof.inspectionHistory) }
    var promotionDetails by remember(prof) { mutableStateOf(prof.promotionDetails) }
    var optionalIdentifier by remember(prof) { mutableStateOf(prof.optionalIdentifier) }
    var activePrintSpec by remember { mutableStateOf<PrintJobSpec?>(null) }

    val activeYearId = snapshot.meta.activeSchoolYearId
    val activeYearLabel = snapshot.schoolYears.find { it.id == activeYearId }?.label ?: ""
    val activeYearClasses = remember(snapshot.classes, activeYearId) {
        snapshot.classes.filter { it.schoolYearId == activeYearId && !it.isArchived }
    }

    fun persistCurrentProfile(): TeacherProfile {
        val next = prof.copy(
            professionalName = professionalName,
            institution = institution,
            employmentStatus = employmentStatus,
            specialization = specialization,
            qualifications = qualifications,
            appointmentDate = appointmentDate,
            rank = rank,
            inspectionHistory = inspectionHistory,
            promotionDetails = promotionDetails,
            optionalIdentifier = optionalIdentifier
        )
        repository.saveProfile(next)
        return next
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("profile_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(
            title = "بطاقتي المهنية",
            subtitle = "البيانات الإدارية والمهنية الخاصة بالأستاذ(ة) للطباعة الرسمية بصيغة A4"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    val latest = persistCurrentProfile()
                    activePrintSpec = PrintAndExportHelper.buildProfilePrintSpec(
                        profile = latest,
                        activeYearClasses = activeYearClasses,
                        activeYearLabel = activeYearLabel
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("print_profile_btn")
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("طباعة البطاقة A4")
            }
            Button(
                onClick = { persistCurrentProfile() },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("save_profile_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("حفظ البطاقة")
            }
        }

        // Privacy notice (Section 8.8)
        Surface(
            color = palette.raised,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = palette.primaryText)
                Text(
                    text = "${ArStrings.PROFILE_LOCAL_NOTICE} جميع الحقول اختيارية وتُطبع فقط الحقول المملوءة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }

        // D18: "Assigned classes" computed from active year's classes and shown read-only ONLY if classes exist
        if (activeYearClasses.isNotEmpty()) {
            Surface(
                color = palette.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "الأقسام المسندة في السنة النشطة ($activeYearLabel)",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeYearClasses.joinToString(" ، ") { it.fullTitle },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text
                    )
                }
            }
        }

        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = professionalName,
                    onValueChange = { professionalName = it },
                    label = { Text("الاسم المهني للأستاذ(ة)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = institution,
                    onValueChange = { institution = it },
                    label = { Text("المؤسسة التعليمية (المتوسطة)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_institution_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = employmentStatus,
                    onValueChange = { employmentStatus = it },
                    label = { Text("الوضعية المهنية (مرسمة / متربصة / متعاقدة)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = specialization,
                    onValueChange = { specialization = it },
                    label = { Text("مادة التخصص") },
                    placeholder = { Text("اللغة العربية وآدابها") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = qualifications,
                    onValueChange = { qualifications = it },
                    label = { Text("المؤهلات العلمية والشهادات") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = appointmentDate,
                    onValueChange = { appointmentDate = it },
                    label = { Text("تاريخ أول تعيين") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = rank,
                    onValueChange = { rank = it },
                    label = { Text("الرتبة والدرجة الحالية") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_rank_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = inspectionHistory,
                    onValueChange = { inspectionHistory = it },
                    label = { Text("سجل الزيارات التفتيشية والنقاط البيداغوجية") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = promotionDetails,
                    onValueChange = { promotionDetails = it },
                    label = { Text("تفاصيل الترقية في الدرجات والرتب") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = optionalIdentifier,
                    onValueChange = { optionalIdentifier = it },
                    label = { Text("معرّف مهني اختياري (إن رغبتِ)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }
    }

    if (activePrintSpec != null) {
        PrintPreviewDialog(
            spec = activePrintSpec!!,
            onDismiss = { activePrintSpec = null }
        )
    }
}
