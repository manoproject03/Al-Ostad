package com.example.features

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
fun ClassesAndGradebookScreen(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    viewedYearId: String,
    initialClassId: String? = null,
    initialStudentId: String? = null,
    onConsumedInitialTarget: () -> Unit = {},
    onOpenGradeSettings: () -> Unit = {}
) {
    var selectedClassId by remember(initialClassId) { mutableStateOf(initialClassId) }
    var selectedTabInClass by remember { mutableStateOf("students") } // "students" | "gradebook"
    var studentIdToInspect by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialClassId, initialStudentId) {
        if (initialClassId != null) {
            selectedClassId = initialClassId
            if (initialStudentId != null) {
                selectedTabInClass = "students"
                studentIdToInspect = initialStudentId
            }
            onConsumedInitialTarget()
        }
    }

    val currentClass = snapshot.classes.find { it.id == selectedClassId && it.schoolYearId == viewedYearId }

    if (currentClass == null) {
        ClassesListOverview(
            snapshot = snapshot,
            repository = repository,
            viewedYearId = viewedYearId,
            onSelectClass = { clsId, initialSection ->
                selectedClassId = clsId
                selectedTabInClass = initialSection
            }
        )
    } else {
        BackHandler { selectedClassId = null }
        ClassDetailView(
            schoolClass = currentClass,
            snapshot = snapshot,
            repository = repository,
            selectedSection = selectedTabInClass,
            onSelectSection = { selectedTabInClass = it },
            initialStudentIdToInspect = studentIdToInspect,
            onConsumedInspectStudent = { studentIdToInspect = null },
            onBackToClasses = { selectedClassId = null },
            onOpenGradeSettings = onOpenGradeSettings
        )
    }
}

@Composable
private fun ClassesListOverview(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    viewedYearId: String,
    onSelectClass: (String, String) -> Unit
) {
    val palette = LocalTeacherPalette.current
    val yearClasses = remember(snapshot.classes, viewedYearId) {
        snapshot.classes.filter { it.schoolYearId == viewedYearId }
    }
    val activeClasses = remember(yearClasses) { yearClasses.filter { !it.isArchived } }
    val archivedClasses = remember(yearClasses) { yearClasses.filter { it.isArchived } }

    var showAddClassDialog by remember { mutableStateOf(false) }
    var editingClass by remember { mutableStateOf<SchoolClass?>(null) }
    var deletingOrArchivingClass by remember { mutableStateOf<SchoolClass?>(null) }
    var showArchivedClasses by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("classes_overview_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "الأقسام والتنقيط",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.text
            )
            Button(
                onClick = { showAddClassDialog = true },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("add_class_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إضافة قسم")
            }
        }

        if (activeClasses.isEmpty()) {
            TeacherEmptyState(
                icon = Icons.Outlined.Groups,
                title = "لا توجد أقسام نشطة في هذه السنة الدراسية",
                description = "أضيفي أقسامكِ لتسجيل التلاميذ، ربط الحصص في التوقيت، واستعمال دفتر التنقيط.",
                actionLabel = "إضافة قسم جديد",
                onAction = { showAddClassDialog = true }
            )
        } else {
            activeClasses.forEach { cls ->
                ClassSummaryCard(
                    schoolClass = cls,
                    snapshot = snapshot,
                    onOpenStudents = { onSelectClass(cls.id, "students") },
                    onOpenGradebook = { onSelectClass(cls.id, "gradebook") },
                    onEdit = { editingClass = cls },
                    onArchiveOrDelete = { deletingOrArchivingClass = cls },
                    onRestoreArchived = null
                )
            }
        }

        // Archived classes in a collapsed section (Section 8.3)
        if (archivedClasses.isNotEmpty()) {
            Surface(
                color = palette.raised,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showArchivedClasses = !showArchivedClasses }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ArchivedBadge(text = "الأقسام المؤرشفة (${archivedClasses.size})")
                        }
                        Icon(
                            imageVector = if (showArchivedClasses) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "عرض أو إخفاء الأقسام المؤرشفة"
                        )
                    }
                    if (showArchivedClasses) {
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            archivedClasses.forEach { cls ->
                                ClassSummaryCard(
                                    schoolClass = cls,
                                    snapshot = snapshot,
                                    onOpenStudents = { onSelectClass(cls.id, "students") },
                                    onOpenGradebook = { onSelectClass(cls.id, "gradebook") },
                                    onEdit = { editingClass = cls },
                                    onArchiveOrDelete = { deletingOrArchivingClass = cls },
                                    onRestoreArchived = {
                                        repository.setClassArchived(cls.id, false)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddClassDialog) {
        ClassEditDialog(
            existingClass = null,
            repository = repository,
            onDismiss = { showAddClassDialog = false }
        )
    }

    if (editingClass != null) {
        ClassEditDialog(
            existingClass = editingClass,
            repository = repository,
            onDismiss = { editingClass = null }
        )
    }

    // D6: Class Archive vs Permanent Delete dialog
    if (deletingOrArchivingClass != null) {
        val cls = deletingOrArchivingClass!!
        val deps = repository.getClassDependents(cls.id)
        AlertDialog(
            onDismissRequest = { deletingOrArchivingClass = null },
            title = {
                Text(
                    text = "إدارة القسم: ${cls.fullTitle}",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (deps.hasHistoryBlockingPermanentDelete) {
                        Text(
                            text = "هذا القسم يحتوي على سجل بيداغوجي تاريخي (تحضيرات: ${deps.lessonPlanCount}، ملاحظات حصة: ${deps.observationCount}، فروض/اختبارات: ${deps.assessmentCount}، نقاط مسجلة: ${deps.nonEmptyGradeCount}).",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "حفاظًا على سلامة البيانات التاريخية، الحذف النهائي غير متاح لهذا القسم. يمكنكِ أرشفته لإخفائه من القوائم اليومية مع إمكانية استعادته في أي وقت.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    } else {
                        Text(
                            text = "ننصح بأرشفة القسم بدل حذفه حتى تتمكني من استعادته لاحقًا.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "إذا اخترتِ الحذف النهائي فسيتم أيضًا حذف: ${deps.studentCount} تلميذ، و ${deps.timetableCount} حصة من التوقيت الأسبوعي مرتبطة بهذا القسم.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.error
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!cls.isArchived) {
                        Button(
                            onClick = {
                                repository.setClassArchived(cls.id, true)
                                deletingOrArchivingClass = null
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("archive_class_confirm_btn")
                        ) {
                            Text("أرشفة القسم (مستحسن)")
                        }
                    }
                    if (!deps.hasHistoryBlockingPermanentDelete) {
                        OutlinedButton(
                            onClick = {
                                repository.deleteClassPermanently(cls.id)
                                deletingOrArchivingClass = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.error),
                            border = BorderStroke(1.dp, palette.error),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("permanent_delete_class_btn")
                        ) {
                            Text("حذف نهائي")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingOrArchivingClass = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun ClassSummaryCard(
    schoolClass: SchoolClass,
    snapshot: DatabaseSnapshot,
    onOpenStudents: () -> Unit,
    onOpenGradebook: () -> Unit,
    onEdit: () -> Unit,
    onArchiveOrDelete: () -> Unit,
    onRestoreArchived: (() -> Unit)?
) {
    val palette = LocalTeacherPalette.current
    val activeStudentsCount = remember(snapshot.students, schoolClass.id) {
        snapshot.students.count { it.classId == schoolClass.id && !it.isArchived }
    }
    val latestLesson = remember(snapshot.lessonPlans, schoolClass.id) {
        snapshot.lessonPlans
            .filter { it.classId == schoolClass.id }
            .maxWithOrNull(compareBy<LessonPlan> { it.date }.thenBy { it.startTime })
    }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("class_card_${schoolClass.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    Text(
                        text = schoolClass.fullTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    if (schoolClass.isSample) {
                        SampleBadge()
                    }
                    if (schoolClass.isArchived) {
                        ArchivedBadge()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "تعديل القسم")
                    }
                    if (onRestoreArchived != null) {
                        TextButton(
                            onClick = onRestoreArchived,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("استعادة")
                        }
                    }
                    IconButton(
                        onClick = onArchiveOrDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("manage_delete_class_${schoolClass.id}")
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "أرشفة أو حذف القسم", tint = palette.error)
                    }
                }
            }

            Text(
                text = "عدد التلاميذ النشطين: $activeStudentsCount",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.primaryText
            )

            Text(
                text = if (latestLesson != null) {
                    "آخر تحضير مسجل: ${latestLesson.title.ifBlank { latestLesson.activityName.ifBlank { "حصة" } }} (${DateTimeUtils.formatAlgerianDate(latestLesson.date)})"
                } else {
                    "لا يوجد تحضير مسجل لهذا القسم بعد."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenStudents,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("open_class_students_${schoolClass.id}")
                ) {
                    Icon(Icons.Outlined.People, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("التلاميذ")
                }
                OutlinedButton(
                    onClick = onOpenGradebook,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("open_class_gradebook_${schoolClass.id}")
                ) {
                    Icon(Icons.Outlined.Grading, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("التنقيط")
                }
            }
        }
    }
}

@Composable
private fun ClassDetailView(
    schoolClass: SchoolClass,
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    selectedSection: String,
    onSelectSection: (String) -> Unit,
    initialStudentIdToInspect: String?,
    onConsumedInspectStudent: () -> Unit,
    onBackToClasses: () -> Unit,
    onOpenGradeSettings: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    val classStudents = remember(snapshot.students, schoolClass.id) {
        snapshot.students
            .filter { it.classId == schoolClass.id }
            .sortedWith(compareBy<Student> { it.orderIndex }.thenBy { it.fullName })
    }
    var showAddStudentDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("class_detail_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header with back button, class title, and sample/archived badges
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
                IconButton(
                    onClick = onBackToClasses,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("back_to_classes_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "العودة إلى قائمة الأقسام"
                    )
                }
                Text(
                    text = schoolClass.fullTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.text
                )
                if (schoolClass.isSample) SampleBadge()
                if (schoolClass.isArchived) ArchivedBadge()
            }
        }

        // Section tabs: «التلاميذ» and «التنقيط» (Section 8.3)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSection == "students",
                onClick = { onSelectSection("students") },
                label = { Text("التلاميذ (${classStudents.count { !it.isArchived }})") },
                leadingIcon = { Icon(Icons.Outlined.People, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("tab_class_students")
            )
            FilterChip(
                selected = selectedSection == "gradebook",
                onClick = { onSelectSection("gradebook") },
                label = { Text("التنقيط") },
                leadingIcon = { Icon(Icons.Outlined.Grading, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("tab_class_gradebook")
            )
        }

        if (selectedSection == "students") {
            ClassStudentsSection(
                schoolClass = schoolClass,
                classStudents = classStudents,
                snapshot = snapshot,
                repository = repository,
                initialStudentIdToInspect = initialStudentIdToInspect,
                onConsumedInspectStudent = onConsumedInspectStudent,
                onRequestAddStudent = { showAddStudentDialog = true }
            )
        } else {
            // Section 8.3: «التنقيط» opens only for a class with >= 1 student;
            // otherwise an empty state with a working «إضافة تلميذ» action.
            if (classStudents.isEmpty()) {
                TeacherEmptyState(
                    icon = Icons.Outlined.PersonAdd,
                    title = "لا يوجد تلاميذ مسجلون في هذا القسم بعد",
                    description = "أضيفي تلميذًا واحدًا على الأقل لفتح دفتر التنقيط وحساب المعدلات الفصلية.",
                    actionLabel = "إضافة تلميذ",
                    onAction = { showAddStudentDialog = true }
                )
            } else {
                ClassGradebookSection(
                    schoolClass = schoolClass,
                    classStudents = classStudents,
                    snapshot = snapshot,
                    repository = repository,
                    onOpenGradeSettings = onOpenGradeSettings
                )
            }
        }
    }

    if (showAddStudentDialog) {
        AddOrEditStudentDialog(
            schoolClass = schoolClass,
            existingStudent = null,
            repository = repository,
            onDismiss = { showAddStudentDialog = false }
        )
    }
}

@Composable
private fun ClassStudentsSection(
    schoolClass: SchoolClass,
    classStudents: List<Student>,
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    initialStudentIdToInspect: String?,
    onConsumedInspectStudent: () -> Unit,
    onRequestAddStudent: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    var searchQuery by remember { mutableStateOf("") }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var inspectingStudent by remember { mutableStateOf<Student?>(null) }
    var deletingOrArchivingStudent by remember { mutableStateOf<Student?>(null) }
    var showBatchAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialStudentIdToInspect, classStudents) {
        if (initialStudentIdToInspect != null) {
            val found = classStudents.find { it.id == initialStudentIdToInspect }
            if (found != null) {
                inspectingStudent = found
            }
            onConsumedInspectStudent()
        }
    }

    val filteredStudents = remember(classStudents, searchQuery) {
        val norm = ArabicUtils.normalizeArabic(searchQuery)
        if (norm.isEmpty()) {
            classStudents
        } else {
            classStudents.filter { ArabicUtils.normalizeArabic(it.fullName).contains(norm) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("بحث في تلاميذ القسم") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("class_student_search_input"),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onRequestAddStudent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("add_student_btn")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("إضافة تلميذ", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            OutlinedButton(
                onClick = { showBatchAddDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("لصق قائمة أسماء", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        if (filteredStudents.isEmpty()) {
            TeacherEmptyState(
                icon = Icons.Outlined.PeopleOutline,
                title = if (searchQuery.isNotBlank()) "لا يوجد تلميذ يطابق البحث" else "قائمة التلاميذ فارغة",
                description = "أضيفي تلاميذ القسم فرديًا أو عبر لصق القائمة.",
                actionLabel = "إضافة تلميذ",
                onAction = onRequestAddStudent
            )
        } else {
            filteredStudents.forEach { student ->
                val studentObsCount = snapshot.lessonObservations.count { it.studentId == student.id }
                val hasPrivateNote = snapshot.studentPrivateNotes.any { it.studentId == student.id && it.text.isNotBlank() }

                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_item_${student.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                Surface(
                                    color = palette.primary.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${student.orderIndex}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = student.fullName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.text
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (hasPrivateNote) {
                                    Surface(
                                        color = palette.muted,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, palette.border)
                                    ) {
                                        Text(
                                            text = "ملاحظة خاصة",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.textSecondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (student.isSample) SampleBadge()
                                if (student.isArchived) ArchivedBadge()
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { inspectingStudent = student },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("student_details_btn_${student.id}")
                            ) {
                                Icon(Icons.Outlined.Badge, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (studentObsCount > 0) "بطاقة التلميذ ($studentObsCount)" else "بطاقة وملاحظة خاصة",
                                    maxLines = 1
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { editingStudent = student },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = "تعديل اسم التلميذ")
                                }
                                if (student.isArchived) {
                                    TextButton(
                                        onClick = { repository.setStudentArchived(student.id, false) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("استعادة")
                                    }
                                }
                                IconButton(
                                    onClick = { deletingOrArchivingStudent = student },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("student_delete_or_archive_btn_${student.id}")
                                ) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "أرشفة أو حذف التلميذ", tint = palette.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingStudent != null) {
        AddOrEditStudentDialog(
            schoolClass = schoolClass,
            existingStudent = editingStudent,
            repository = repository,
            onDismiss = { editingStudent = null }
        )
    }

    if (showBatchAddDialog) {
        var batchText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBatchAddDialog = false },
            title = { Text("لصق قائمة تلاميذ (${schoolClass.fullTitle})") },
            text = {
                OutlinedTextField(
                    value = batchText,
                    onValueChange = { batchText = it },
                    label = { Text("اسم واحد في كل سطر") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    minLines = 4
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lines = batchText.split("\n", "\r\n").map { it.trim() }.filter { it.isNotEmpty() }
                        if (lines.isNotEmpty()) {
                            repository.addBatchStudents(schoolClass.id, lines)
                            showBatchAddDialog = false
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("حفظ القائمة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchAddDialog = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Student details dialog with private note (D5 & 8.3)
    if (inspectingStudent != null) {
        val st = inspectingStudent!!
        var privateNoteText by remember(st.id) {
            mutableStateOf(repository.getStudentPrivateNote(st.id))
        }
        val studentObs = remember(snapshot.lessonObservations, st.id) {
            snapshot.lessonObservations.filter { it.studentId == st.id }
        }
        val presentTimes = studentObs.count { it.attendance == "present" }
        val absentTimes = studentObs.count { it.attendance == "absent" }
        val partTimes = studentObs.count { it.participated }

        AlertDialog(
            onDismissRequest = { inspectingStudent = null },
            title = {
                Text(
                    text = "بطاقة التلميذ: ${st.fullName}",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "القسم: ${schoolClass.fullTitle} • الرقم الترتيبي: ${st.orderIndex}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    if (studentObs.isNotEmpty()) {
                        Surface(
                            color = palette.raised,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "ملخص ملاحظات الحصص: حضور ($presentTimes) • غياب ($absentTimes) • مشاركة ($partTimes)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primaryText
                                )
                                studentObs.filter { it.note.isNotBlank() }.takeLast(2).forEach { obs ->
                                    Text(
                                        text = "• ${obs.note}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.textSecondary
                                    )
                                }
                            }
                        }
                    }
                    Surface(
                        color = palette.muted,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = ArStrings.PRIVATE_NOTE_NOTICE,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    OutlinedTextField(
                        value = privateNoteText,
                        onValueChange = { privateNoteText = it },
                        label = { Text("الملاحظة الخاصة بالتلميذ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_private_note_input"),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.saveStudentPrivateNote(st.id, privateNoteText)
                        inspectingStudent = null
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("save_private_note_btn")
                ) {
                    Text("حفظ الملاحظة الخاصة")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { inspectingStudent = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إغلاق")
                }
            }
        )
    }

    // D6: Student Archive vs Permanent Delete dialog
    if (deletingOrArchivingStudent != null) {
        val st = deletingOrArchivingStudent!!
        val deps = repository.getStudentDependents(st.id)
        AlertDialog(
            onDismissRequest = { deletingOrArchivingStudent = null },
            title = { Text("إدارة سجل التلميذ: ${st.fullName}", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!deps.canDeletePermanently) {
                        Text(
                            text = "لا يمكن حذف هذا التلميذ نهائيًا لوجود ملاحظات حصة (${deps.observationCount}) أو نقاط مسجلة (${deps.nonEmptyGradeCount}) مرتبطة به.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "استخدمي «أرشفة التلميذ» للحفاظ على السجل التاريخي وإمكانية استعادته في أي وقت.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    } else {
                        val noteInfo = if (deps.hasPrivateNote) "وملاحظته الخاصة " else ""
                        val emptyRowsInfo = if (deps.emptyGradeRowCount > 0) "و ${deps.emptyGradeRowCount} سطر تنقيط فارغ " else ""
                        Text(
                            text = "يمكنكِ أرشفة التلميذ (مستحسن) أو حذفه نهائيًا. الحذف النهائي سيزيل التلميذ ${noteInfo}${emptyRowsInfo}بشكل نهائي.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!st.isArchived) {
                        Button(
                            onClick = {
                                repository.setStudentArchived(st.id, true)
                                deletingOrArchivingStudent = null
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("archive_student_confirm_btn")
                        ) {
                            Text("أرشفة التلميذ")
                        }
                    }
                    if (deps.canDeletePermanently) {
                        OutlinedButton(
                            onClick = {
                                repository.deleteStudentPermanently(st.id)
                                deletingOrArchivingStudent = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.error),
                            border = BorderStroke(1.dp, palette.error),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("permanent_delete_student_btn")
                        ) {
                            Text("حذف نهائي")
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingOrArchivingStudent = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun AddOrEditStudentDialog(
    schoolClass: SchoolClass,
    existingStudent: Student?,
    repository: TeacherRepository,
    onDismiss: () -> Unit
) {
    var fullName by remember { mutableStateOf(existingStudent?.fullName ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingStudent == null) "إضافة تلميذ إلى ${schoolClass.fullTitle}" else "تعديل اسم التلميذ",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        errorMsg = null
                    },
                    label = { Text("الاسم واللقب") },
                    isError = errorMsg != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_dialog_name_input"),
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
                    val res = repository.addOrUpdateStudent(existingStudent?.id, schoolClass.id, fullName)
                    if (res.isSuccess) {
                        onDismiss()
                    } else {
                        errorMsg = res.exceptionOrNull()?.message
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("student_dialog_save_btn")
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
 * Section 8.6: Gradebook «التنقيط»
 */
@Composable
private fun ClassGradebookSection(
    schoolClass: SchoolClass,
    classStudents: List<Student>,
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    onOpenGradeSettings: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    val context = LocalContext.current
    var selectedTermId by remember { mutableStateOf("T1") }
    var studentFilterQuery by remember { mutableStateOf("") }
    var showCsvExportDialog by remember { mutableStateOf(false) }
    var activePrintSpec by remember { mutableStateOf<PrintJobSpec?>(null) }

    LaunchedEffect(schoolClass.id, selectedTermId) {
        repository.ensureGradebookForClassTerm(schoolClass.id, selectedTermId)
    }

    val config = remember(snapshot.gradeTermConfigs, schoolClass.schoolYearId, selectedTermId) {
        snapshot.gradeTermConfigs.find {
            it.schoolYearId == schoolClass.schoolYearId && it.termId == selectedTermId
        } ?: GradeTermConfig(schoolYearId = schoolClass.schoolYearId, termId = selectedTermId)
    }

    val gradebook = remember(snapshot.gradebooks, schoolClass.id, selectedTermId) {
        snapshot.gradebooks.find {
            it.classId == schoolClass.id && it.termId == selectedTermId
        }
    }

    val recordsForGradebook = remember(snapshot.gradeRecords, gradebook?.id) {
        if (gradebook == null) emptyList()
        else snapshot.gradeRecords.filter { it.gradebookId == gradebook.id }
    }
    val recordsByStudentId = remember(recordsForGradebook) {
        recordsForGradebook.associateBy { it.studentId }
    }
    val studentsById = remember(classStudents) {
        classStudents.associateBy { it.id }
    }

    val stats = remember(recordsForGradebook, studentsById, config) {
        GradingEngine.computeStatistics(recordsForGradebook, studentsById, config)
    }

    val filteredStudents = remember(classStudents, studentFilterQuery) {
        val norm = ArabicUtils.normalizeArabic(studentFilterQuery)
        if (norm.isEmpty()) classStudents
        else classStudents.filter { ArabicUtils.normalizeArabic(it.fullName).contains(norm) }
    }

    val schoolYearLabel = snapshot.schoolYears.find { it.id == schoolClass.schoolYearId }?.label ?: ""

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideTable = maxWidth >= 768.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Term Switcher (الفصل الأول / الفصل الثاني / الفصل الثالث)
            ScrollablePillRow(
                items = listOf(
                    "T1" to ArStrings.TERM_1,
                    "T2" to ArStrings.TERM_2,
                    "T3" to ArStrings.TERM_3
                ),
                selectedValue = selectedTermId,
                onSelect = { selectedTermId = it },
                testTagPrefix = "term_chip"
            )

            // Export & Print actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showCsvExportDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("export_csv_btn")
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تصدير CSV", fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        activePrintSpec = PrintAndExportHelper.buildGradebookPrintSpec(
                            schoolYearLabel = schoolYearLabel,
                            schoolClass = schoolClass,
                            termId = selectedTermId,
                            config = config,
                            students = classStudents,
                            recordsByStudentId = recordsByStudentId,
                            profile = snapshot.profile
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("print_gradebook_btn")
                ) {
                    Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("طباعة كشف A4", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            // Live Formula Banner + Suggestion Note (Section 8.6)
            Surface(
                color = palette.raised,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = GradingEngine.buildFormulaDescription(config),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryText,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gradebook_formula_text")
                        )
                        TextButton(
                            onClick = onOpenGradeSettings,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إعدادات التنقيط")
                        }
                    }
                    Text(
                        text = ArStrings.FORMULA_SUGGESTION_NOTE,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary
                    )
                }
            }

            // Student search inside gradebook
            OutlinedTextField(
                value = studentFilterQuery,
                onValueChange = { studentFilterQuery = it },
                label = { Text("بحث عن تلميذ في كشف النقاط") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Responsive Gradebook Input: Wide Table (>=768dp) or Student Cards (<768dp)
            if (isWideTable) {
                WideGradebookTable(
                    students = filteredStudents,
                    recordsByStudentId = recordsByStudentId,
                    config = config,
                    repository = repository
                )
            } else {
                filteredStudents.forEach { student ->
                    val rec = recordsByStudentId[student.id]
                    if (rec != null) {
                        StudentGradeCard(
                            student = student,
                            record = rec,
                            config = config,
                            repository = repository
                        )
                    }
                }
            }

            // Statistics Panel (Section 8.6 & D9, D10, T36)
            GradebookStatisticsSection(stats = stats)
        }
    }

    if (showCsvExportDialog) {
        CsvExportDialog(
            schoolYearLabel = schoolYearLabel,
            schoolClass = schoolClass,
            termId = selectedTermId,
            config = config,
            students = classStudents,
            recordsByStudentId = recordsByStudentId,
            context = context,
            onDismiss = { showCsvExportDialog = false }
        )
    }

    if (activePrintSpec != null) {
        PrintPreviewDialog(
            spec = activePrintSpec!!,
            onDismiss = { activePrintSpec = null }
        )
    }
}

@Composable
private fun StudentGradeCard(
    student: Student,
    record: GradeRecord,
    config: GradeTermConfig,
    repository: TeacherRepository
) {
    val palette = LocalTeacherPalette.current
    val eval = remember(record, config) { GradingEngine.evaluate(record, config) }

    var ca1Text by remember(record.id, record.ca1) {
        mutableStateOf(record.ca1?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
    }
    var ca2Text by remember(record.id, record.ca2) {
        mutableStateOf(record.ca2?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
    }
    var as1Text by remember(record.id, record.as1) {
        mutableStateOf(record.as1?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
    }
    var as2Text by remember(record.id, record.as2) {
        mutableStateOf(record.as2?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
    }
    var examText by remember(record.id, record.exam) {
        mutableStateOf(record.exam?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
    }
    var absencesText by remember(record.id, record.absences) {
        mutableStateOf(record.absences?.toString() ?: "")
    }
    var behaviourText by remember(record.id, record.behaviour) { mutableStateOf(record.behaviour) }
    var materialsText by remember(record.id, record.materials) { mutableStateOf(record.materials) }
    var notebookText by remember(record.id, record.notebook) { mutableStateOf(record.notebook) }
    var remarksText by remember(record.id, record.remarks) { mutableStateOf(record.remarks) }
    var fieldError by remember(record.id) { mutableStateOf<String?>(null) }

    fun applyScoreEdit(fieldName: String, rawValue: String) {
        when (val parsed = ArabicUtils.parseScoreInput(rawValue)) {
            is ArabicUtils.ScoreParseResult.Invalid -> {
                fieldError = "$fieldName: ${parsed.errorAr}"
            }
            is ArabicUtils.ScoreParseResult.Empty -> {
                fieldError = null
                val next = when (fieldName) {
                    "ca1" -> record.copy(ca1 = null)
                    "ca2" -> record.copy(ca2 = null)
                    "as1" -> record.copy(as1 = null)
                    "as2" -> record.copy(as2 = null)
                    "exam" -> record.copy(exam = null)
                    else -> record
                }
                repository.saveGradeRecord(next)
            }
            is ArabicUtils.ScoreParseResult.Valid -> {
                fieldError = null
                val next = when (fieldName) {
                    "ca1" -> record.copy(ca1 = parsed.value)
                    "ca2" -> record.copy(ca2 = parsed.value)
                    "as1" -> record.copy(as1 = parsed.value)
                    "as2" -> record.copy(as2 = parsed.value)
                    "exam" -> record.copy(exam = parsed.value)
                    else -> record
                }
                repository.saveGradeRecord(next)
            }
        }
    }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("grade_card_${student.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Student orderIndex + fullName + Final Average
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
                        text = "${student.orderIndex}. ${student.fullName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    if (student.isSample) SampleBadge()
                    if (student.isArchived) ArchivedBadge()
                }

                // Final Average Pill (D9: «—» when incomplete)
                Surface(
                    color = if (eval.isComplete && eval.finalAverage != null) {
                        if (eval.finalAverage >= 10.0) palette.success.copy(alpha = 0.15f)
                        else palette.warning.copy(alpha = 0.16f)
                    } else palette.muted,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Text(
                        text = "المعدل: " + if (eval.isComplete && eval.finalAverage != null) {
                            ArabicUtils.formatWesternNumber(eval.finalAverage)
                        } else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.text,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("final_avg_${student.id}")
                    )
                }
            }

            if (!eval.isComplete && eval.missingFields.isNotEmpty()) {
                Text(
                    text = eval.missingMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.testTag("missing_fields_${student.id}")
                )
            }

            if (fieldError != null) {
                Text(
                    text = fieldError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.error,
                    modifier = Modifier.testTag("score_error_${student.id}")
                )
            }

            // Score Inputs Row 1: CA1, CA2 (if active)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScoreInputField(
                    label = "التقويم المستمر 1",
                    value = ca1Text,
                    onValueChange = {
                        ca1Text = it
                        applyScoreEdit("ca1", it)
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "input_ca1_${student.id}"
                )
                if (config.ca2Active) {
                    ScoreInputField(
                        label = if (config.ca2Required) "التقويم المستمر 2" else "التقويم المستمر 2 (اختياري)",
                        value = ca2Text,
                        onValueChange = {
                            ca2Text = it
                            applyScoreEdit("ca2", it)
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "input_ca2_${student.id}"
                    )
                }
            }

            // Score Inputs Row 2: AS1, AS2 (if active), Exam
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScoreInputField(
                    label = "الفرض 1",
                    value = as1Text,
                    onValueChange = {
                        as1Text = it
                        applyScoreEdit("as1", it)
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "input_as1_${student.id}"
                )
                if (config.as2Active) {
                    ScoreInputField(
                        label = if (config.as2Required) "الفرض 2" else "الفرض 2 (اختياري)",
                        value = as2Text,
                        onValueChange = {
                            as2Text = it
                            applyScoreEdit("as2", it)
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "input_as2_${student.id}"
                    )
                }
                ScoreInputField(
                    label = "الاختبار",
                    value = examText,
                    onValueChange = {
                        examText = it
                        applyScoreEdit("exam", it)
                    },
                    modifier = Modifier.weight(1f),
                    testTag = "input_exam_${student.id}"
                )
            }

            // Optional non-grade columns (D8: never affect calculation!)
            if (config.visibleColumns.absences || config.visibleColumns.behaviour ||
                config.visibleColumns.materials || config.visibleColumns.notebook
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (config.visibleColumns.absences) {
                        OutlinedTextField(
                            value = absencesText,
                            onValueChange = { raw ->
                                absencesText = raw
                                val cleaned = ArabicUtils.toWesternDigits(raw.trim())
                                if (cleaned.isEmpty()) {
                                    repository.saveGradeRecord(record.copy(absences = null))
                                } else {
                                    val num = cleaned.toIntOrNull()
                                    if (num != null && num in 0..999) {
                                        repository.saveGradeRecord(record.copy(absences = num))
                                    }
                                }
                            },
                            label = { Text("الغيابات") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    if (config.visibleColumns.behaviour) {
                        OutlinedTextField(
                            value = behaviourText,
                            onValueChange = {
                                behaviourText = it.take(30)
                                repository.saveGradeRecord(record.copy(behaviour = behaviourText))
                            },
                            label = { Text("السلوك") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (config.visibleColumns.materials) {
                        OutlinedTextField(
                            value = materialsText,
                            onValueChange = {
                                materialsText = it.take(30)
                                repository.saveGradeRecord(record.copy(materials = materialsText))
                            },
                            label = { Text("إحضار الأدوات") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    if (config.visibleColumns.notebook) {
                        OutlinedTextField(
                            value = notebookText,
                            onValueChange = {
                                notebookText = it.take(30)
                                repository.saveGradeRecord(record.copy(notebook = notebookText))
                            },
                            label = { Text("تنظيم الكراس") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            // Remarks
            OutlinedTextField(
                value = remarksText,
                onValueChange = {
                    remarksText = it.take(200)
                    repository.saveGradeRecord(record.copy(remarks = remarksText))
                },
                label = { Text("الملاحظات التقديرية (≤ 200 حرف)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
private fun ScoreInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.testTag(testTag),
        singleLine = true
    )
}

@Composable
private fun WideGradebookTable(
    students: List<Student>,
    recordsByStudentId: Map<String, GradeRecord>,
    config: GradeTermConfig,
    repository: TeacherRepository
) {
    val palette = LocalTeacherPalette.current
    val horizontalScroll = rememberScrollState()

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wide_gradebook_table")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            students.forEach { student ->
                val rec = recordsByStudentId[student.id]
                if (rec != null) {
                    val eval = GradingEngine.evaluate(rec, config)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sticky RTL right column (Student # and Name)
                        Row(
                            modifier = Modifier
                                .width(210.dp)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${student.orderIndex}. ${student.fullName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (student.isArchived) ArchivedBadge()
                        }

                        // Scrollable numeric columns
                        Row(
                            modifier = Modifier.horizontalScroll(horizontalScroll),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var ca1 by remember(rec.id, rec.ca1) {
                                mutableStateOf(rec.ca1?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
                            }
                            var ca2 by remember(rec.id, rec.ca2) {
                                mutableStateOf(rec.ca2?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
                            }
                            var as1 by remember(rec.id, rec.as1) {
                                mutableStateOf(rec.as1?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
                            }
                            var as2 by remember(rec.id, rec.as2) {
                                mutableStateOf(rec.as2?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
                            }
                            var exam by remember(rec.id, rec.exam) {
                                mutableStateOf(rec.exam?.let { ArabicUtils.formatWesternNumber(it) } ?: "")
                            }

                            ScoreInputField(
                                label = "ت.م 1",
                                value = ca1,
                                onValueChange = {
                                    ca1 = it
                                    when (val p = ArabicUtils.parseScoreInput(it)) {
                                        is ArabicUtils.ScoreParseResult.Empty -> repository.saveGradeRecord(rec.copy(ca1 = null))
                                        is ArabicUtils.ScoreParseResult.Valid -> repository.saveGradeRecord(rec.copy(ca1 = p.value))
                                        else -> {}
                                    }
                                },
                                modifier = Modifier.width(105.dp),
                                testTag = "input_ca1_${student.id}"
                            )
                            if (config.ca2Active) {
                                ScoreInputField(
                                    label = "ت.م 2",
                                    value = ca2,
                                    onValueChange = {
                                        ca2 = it
                                        when (val p = ArabicUtils.parseScoreInput(it)) {
                                            is ArabicUtils.ScoreParseResult.Empty -> repository.saveGradeRecord(rec.copy(ca2 = null))
                                            is ArabicUtils.ScoreParseResult.Valid -> repository.saveGradeRecord(rec.copy(ca2 = p.value))
                                            else -> {}
                                        }
                                    },
                                    modifier = Modifier.width(105.dp),
                                    testTag = "input_ca2_${student.id}"
                                )
                            }
                            ScoreInputField(
                                label = "فرض 1",
                                value = as1,
                                onValueChange = {
                                    as1 = it
                                    when (val p = ArabicUtils.parseScoreInput(it)) {
                                        is ArabicUtils.ScoreParseResult.Empty -> repository.saveGradeRecord(rec.copy(as1 = null))
                                        is ArabicUtils.ScoreParseResult.Valid -> repository.saveGradeRecord(rec.copy(as1 = p.value))
                                        else -> {}
                                    }
                                },
                                modifier = Modifier.width(105.dp),
                                testTag = "input_as1_${student.id}"
                            )
                            if (config.as2Active) {
                                ScoreInputField(
                                    label = "فرض 2",
                                    value = as2,
                                    onValueChange = {
                                        as2 = it
                                        when (val p = ArabicUtils.parseScoreInput(it)) {
                                            is ArabicUtils.ScoreParseResult.Empty -> repository.saveGradeRecord(rec.copy(as2 = null))
                                            is ArabicUtils.ScoreParseResult.Valid -> repository.saveGradeRecord(rec.copy(as2 = p.value))
                                            else -> {}
                                        }
                                    },
                                    modifier = Modifier.width(105.dp),
                                    testTag = "input_as2_${student.id}"
                                )
                            }
                            ScoreInputField(
                                label = "اختبار",
                                value = exam,
                                onValueChange = {
                                    exam = it
                                    when (val p = ArabicUtils.parseScoreInput(it)) {
                                        is ArabicUtils.ScoreParseResult.Empty -> repository.saveGradeRecord(rec.copy(exam = null))
                                        is ArabicUtils.ScoreParseResult.Valid -> repository.saveGradeRecord(rec.copy(exam = p.value))
                                        else -> {}
                                    }
                                },
                                modifier = Modifier.width(105.dp),
                                testTag = "input_exam_${student.id}"
                            )

                            Column(
                                modifier = Modifier
                                    .width(130.dp)
                                    .padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = "المعدل: " + if (eval.isComplete && eval.finalAverage != null) {
                                        ArabicUtils.formatWesternNumber(eval.finalAverage)
                                    } else "—",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("final_avg_${student.id}")
                                )
                                if (!eval.isComplete && eval.missingFields.isNotEmpty()) {
                                    Text(
                                        text = eval.missingMessage,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = palette.textSecondary,
                                        modifier = Modifier.testTag("missing_fields_${student.id}")
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = palette.border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

/**
 * Section 8.6 & T36: Statistics panel ONLY if >= 1 complete average exists.
 * Includes Canvas bar chart + accessible table + exact count notice.
 */
@Composable
private fun GradebookStatisticsSection(
    stats: GradingEngine.ClassStatistics?
) {
    val palette = LocalTeacherPalette.current
    if (stats == null) {
        TeacherEmptyState(
            icon = Icons.Outlined.BarChart,
            title = "لا توجد معدلات مكتملة بعد لحساب إحصاءات القسم",
            description = "تظهر الإحصاءات وتوزيع المعدلات تلقائيًا بمجرد اكتمال نقاط تلميذ واحد على الأقل في هذا الفصل (لا تُعرض إحصاءات صفرية أو مؤقتة)."
        )
        return
    }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gradebook_statistics_panel")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "إحصاءات القسم وتوزيع المعدلات",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.primaryText
            )

            Text(
                text = ArStrings.statsIncludedSummary(
                    completeCount = stats.completeCount,
                    totalCount = stats.totalStudentsCount,
                    archivedCount = stats.archivedIncludedCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.testTag("stats_included_summary")
            )

            // Key indicators grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricBox("معدل القسم", ArabicUtils.formatWesternNumber(stats.classAverage), Modifier.weight(1f))
                StatMetricBox("أعلى معدل", ArabicUtils.formatWesternNumber(stats.maxAverage), Modifier.weight(1f))
                StatMetricBox("أقل معدل", ArabicUtils.formatWesternNumber(stats.minAverage), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMetricBox("المعدلات ≥ 10", stats.passCount.toString(), Modifier.weight(1f))
                StatMetricBox("المعدلات < 10", stats.failCount.toString(), Modifier.weight(1f))
                StatMetricBox("نسبة النجاح", "${ArabicUtils.formatWesternNumber(stats.passRate)}%", Modifier.weight(1f))
            }

            // Real Canvas Bar Chart for the 4 bins (0–<5, 5–<10, 10–<15, 15–20)
            Text(
                text = "توزيع المعدلات حسب الفئات",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val maxBinCount = (stats.bins.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(vertical = 8.dp)
            ) {
                val w = size.width
                val h = size.height
                val barCount = stats.bins.size
                val slotWidth = w / barCount
                val barWidth = slotWidth * 0.55f

                stats.bins.forEachIndexed { index, bin ->
                    val ratio = bin.count.toFloat() / maxBinCount.toFloat()
                    val barHeight = (h * 0.78f * ratio).coerceAtLeast(6f)
                    val left = index * slotWidth + (slotWidth - barWidth) / 2f
                    val top = h - barHeight
                    drawRoundRect(
                        color = if (bin.minInclusive >= 10.0) palette.success else palette.primary,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }

            // Accessible Distribution Table alongside chart
            Surface(
                color = palette.raised,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    stats.bins.forEach { bin ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "الفئة (${bin.label})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${bin.count} تلميذ",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.primaryText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalTeacherPalette.current
    Surface(
        color = palette.raised,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
                textAlign = TextAlign.Center
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = palette.text,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * D13: CSV Export Dialog with delimiter `;` (default) or `,`, decimal mark `.` (default) or `,`,
 * and the exact hint from D13.
 */
@Composable
private fun CsvExportDialog(
    schoolYearLabel: String,
    schoolClass: SchoolClass,
    termId: String,
    config: GradeTermConfig,
    students: List<Student>,
    recordsByStudentId: Map<String, GradeRecord>,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    var delimiter by remember { mutableStateOf(';') }
    var decimalMark by remember { mutableStateOf('.') }
    var exportedNotice by remember { mutableStateOf<String?>(null) }

    val csvContent = remember(schoolYearLabel, schoolClass, termId, config, students, recordsByStudentId, delimiter, decimalMark) {
        GradingEngine.exportGradebookCsv(
            schoolYearLabel = schoolYearLabel,
            schoolClass = schoolClass,
            termId = termId,
            config = config,
            students = students,
            recordsByStudentId = recordsByStudentId,
            delimiter = delimiter,
            decimalMark = decimalMark
        )
    }

    val fileName = "grades-${schoolClass.name.replace(" ", "_")}-$termId.csv"

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val ok = PrintAndExportHelper.writeTextToUri(context, uri, csvContent)
            exportedNotice = if (ok) "تم حفظ ملف CSV بنجاح." else "تعذّر حفظ ملف CSV."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصدير كشف النقاط بصيغة CSV", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = ArStrings.CSV_HINT,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )

                Text("فاصل الأعمدة:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = delimiter == ';',
                        onClick = { delimiter = ';' },
                        label = { Text("فاصلة منقوطة (;) — افتراضي") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = delimiter == ',',
                        onClick = { delimiter = ',' },
                        label = { Text("فاصلة (,)") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }

                Text("الفاصلة العشرية:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = decimalMark == '.',
                        onClick = { decimalMark = '.' },
                        label = { Text("نقطة (.) — افتراضي") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    FilterChip(
                        selected = decimalMark == ',',
                        onClick = { decimalMark = ',' },
                        label = { Text("فاصلة (,)") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }

                if (exportedNotice != null) {
                    Text(
                        text = exportedNotice!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.success,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        PrintAndExportHelper.saveExportCopyToInternalExportsDir(context, fileName, csvContent)
                        try {
                            saveLauncher.launch(fileName)
                        } catch (_: Exception) {
                            exportedNotice = "تم تجهيز ملف CSV ($fileName)."
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("تنزيل ملف CSV")
                }
                OutlinedButton(
                    onClick = {
                        PrintAndExportHelper.shareTextContent(context, "تصدير CSV", fileName, csvContent)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("مشاركة")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("إغلاق")
            }
        }
    )
}
