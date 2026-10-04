package com.example.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.components.*
import com.example.domain.*
import com.example.i18n.ArStrings
import com.example.storage.DatabaseSnapshot
import com.example.storage.TeacherRepository
import com.example.ui.theme.LocalTeacherPalette

private data class MutableClassSetup(
    val tempId: String = newStableId(),
    var level: String = ArStrings.SCHOOL_LEVELS.first(),
    var name: String = "",
    val studentRows: MutableList<String> = mutableStateListOf()
)

@Composable
fun FirstTimeSetupScreen(
    repository: TeacherRepository,
    onSetupFinished: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    var step by remember { mutableIntStateOf(0) }

    var professionalName by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var schoolYearLabel by remember { mutableStateOf("2026-2027") }
    var yearError by remember { mutableStateOf<String?>(null) }

    val draftClasses = remember {
        mutableStateListOf(
            MutableClassSetup(level = "الأولى متوسط", name = "م 1")
        )
    }
    var selectedClassIdx by remember { mutableIntStateOf(0) }
    var singleStudentInput by remember { mutableStateOf("") }
    var batchPasteInput by remember { mutableStateOf("") }
    var submitError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("setup_wizard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header card with decorative ornament
        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, palette.border),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(palette.surface, palette.muted.copy(alpha = 0.6f))
                        )
                    )
            ) {
                PlannerCornerOrnament(modifier = Modifier.align(Alignment.TopStart))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlannerLogo(size = 68.dp)
                    Text(
                        text = ArStrings.APP_NAME,
                        style = MaterialTheme.typography.displayLarge,
                        color = palette.primaryText,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = ArStrings.APP_SUBTITLE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Visual 4-Step Progress Bar when in wizard steps 1..4
        if (step in 1..4) {
            val stepLabels = listOf("1. البطاقة", "2. الأقسام", "3. التلاميذ", "4. المراجعة")
            Surface(
                color = palette.raised,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { step / 4f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = palette.primary,
                        trackColor = palette.muted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        stepLabels.forEachIndexed { idx, lbl ->
                            val isCurrent = (idx + 1) == step
                            val isDone = (idx + 1) < step
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCurrent || isDone) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isCurrent -> palette.primaryText
                                    isDone -> palette.success
                                    else -> palette.textSecondary
                                }
                            )
                        }
                    }
                }
            }
        }

        when (step) {
            0 -> {
                // Step 1: Welcome with two real buttons
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "مرحبًا بكِ في دفتركِ البيداغوجي الرقمي",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.text
                        )
                        Text(
                            text = "صُمّم هذا التطبيق خصيصًا لأستاذة اللغة العربية في التعليم المتوسط وفق مبدأ «أدخلي المعلومة مرة واحدة، ثم أعيدي استعمالها حيثما احتجتِ إليها».\nتُحفظ جميع بياناتكِ محليًا على جهازكِ وتعمل 100% دون إنترنت.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                        Spacer(Modifier.height(2.dp))
                        Button(
                            onClick = { step = 1 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("start_my_setup_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("ابدئي إعداد بياناتي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                val res = repository.seedSampleData(fromOnboarding = true)
                                if (res.isSuccess) {
                                    onSetupFinished()
                                } else {
                                    submitError = res.exceptionOrNull()?.message
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("try_sample_data_button"),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, palette.primary)
                        ) {
                            Icon(Icons.Outlined.Science, contentDescription = null, tint = palette.primaryText)
                            Spacer(Modifier.width(8.dp))
                            Text("جرّبي ببيانات تجريبية", color = palette.primaryText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            1 -> {
                // Step 2: Professional name, institution, school year
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(
                            title = "المعلومات الأساسية والسنة الدراسية",
                            subtitle = "الخطوة 1 من 4 — الاسم والمؤسسة اختياريان"
                        )
                        OutlinedTextField(
                            value = professionalName,
                            onValueChange = { professionalName = it },
                            label = { Text("الاسم المهني للأستاذ(ة) (اختياري)") },
                            placeholder = { Text("مثال: الأستاذة أمينة بن علي") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_name_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = institution,
                            onValueChange = { institution = it },
                            label = { Text("المؤسسة التعليمية / المتوسطة (اختياري)") },
                            placeholder = { Text("مثال: متوسطة الأمير عبد القادر") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_institution_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = schoolYearLabel,
                            onValueChange = {
                                schoolYearLabel = ArabicUtils.toWesternDigits(it)
                                yearError = null
                            },
                            label = { Text("السنة الدراسية (إلزامي، مثال: 2026-2027)") },
                            isError = yearError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("setup_year_input"),
                            singleLine = true
                        )
                        if (yearError != null) {
                            Text(
                                text = yearError!!,
                                color = palette.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { step = 0 },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("رجوع")
                            }
                            Button(
                                onClick = {
                                    val err = DateTimeUtils.validateSchoolYearLabel(schoolYearLabel)
                                    if (err != null) {
                                        yearError = err
                                    } else {
                                        step = 2
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("setup_next_to_classes")
                            ) {
                                Text("التالي: إعداد الأقسام")
                            }
                        }
                    }
                }
            }

            2 -> {
                // Step 3: Classes (level + group name, add several)
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(
                            title = "الأقسام المسندة",
                            subtitle = "الخطوة 2 من 4 — أضيفي الأقسام التي تدرّسينها هذه السنة"
                        )

                        draftClasses.forEachIndexed { idx, item ->
                            var clsLevel by remember(item.tempId) { mutableStateOf(item.level) }
                            var clsName by remember(item.tempId) { mutableStateOf(item.name) }

                            Surface(
                                color = palette.raised,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "القسم ${idx + 1}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.primaryText
                                        )
                                        if (draftClasses.size > 1) {
                                            IconButton(
                                                onClick = { draftClasses.removeAt(idx) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.DeleteOutline,
                                                    contentDescription = "حذف القسم",
                                                    tint = palette.error
                                                )
                                            }
                                        }
                                    }
                                    DropdownRowSelector(
                                        title = "المستوى الدراسي",
                                        selectedLabel = clsLevel,
                                        options = ArStrings.SCHOOL_LEVELS.map { it to it },
                                        onSelect = {
                                            clsLevel = it
                                            item.level = it
                                        }
                                    )
                                    OutlinedTextField(
                                        value = clsName,
                                        onValueChange = {
                                            clsName = it
                                            item.name = it
                                        },
                                        label = { Text("اسم الفوج (مثال: م 1 أو فوج 2)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                draftClasses.add(
                                    MutableClassSetup(
                                        level = ArStrings.SCHOOL_LEVELS.first(),
                                        name = "م ${draftClasses.size + 1}"
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("إضافة قسم آخر")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { step = 1 },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("رجوع")
                            }
                            Button(
                                onClick = {
                                    selectedClassIdx = 0
                                    step = 3
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("setup_next_to_students")
                            ) {
                                Text("التالي: قائمة التلاميذ")
                            }
                        }
                    }
                }
            }

            3 -> {
                // Step 4: Students per class (manual or paste one name per line)
                val validClasses = draftClasses.filter { it.name.isNotBlank() }
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(
                            title = "إدخال التلاميذ (اختياري الآن)",
                            subtitle = "الخطوة 3 من 4 — فرديًا أو بلصق قائمة الأسماء"
                        )
                        if (validClasses.isEmpty()) {
                            Text(
                                text = "لم تتم إضافة أقسام بعد. يمكنكِ المتابعة للمراجعة أو الرجوع لإضافة قسم.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                        } else {
                            val safeIdx = selectedClassIdx.coerceIn(0, validClasses.lastIndex)
                            val activeDraft = validClasses[safeIdx]

                            DropdownRowSelector(
                                title = "اختاري القسم لإدخال تلاميذه",
                                selectedLabel = "${activeDraft.level} — ${activeDraft.name} (${activeDraft.studentRows.size} تلميذ)",
                                options = validClasses.mapIndexed { i, c ->
                                    i to "${c.level} — ${c.name} (${c.studentRows.size})"
                                },
                                onSelect = { selectedClassIdx = it }
                            )

                            // Manual single addition
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = singleStudentInput,
                                    onValueChange = { singleStudentInput = it },
                                    label = { Text("إضافة تلميذ واحد (الاسم واللقب)") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setup_single_student_input"),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (singleStudentInput.isNotBlank()) {
                                            activeDraft.studentRows.add(singleStudentInput.trim())
                                            singleStudentInput = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("setup_add_single_student_btn")
                                ) {
                                    Text("إضافة")
                                }
                            }

                            // Paste multi-line list
                            OutlinedTextField(
                                value = batchPasteInput,
                                onValueChange = { batchPasteInput = it },
                                label = { Text("أو الصقي قائمة الأسماء (اسم واحد في كل سطر)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 100.dp)
                                    .testTag("setup_batch_paste_input"),
                                minLines = 3
                            )
                            OutlinedButton(
                                onClick = {
                                    if (batchPasteInput.isNotEmpty()) {
                                        val lines = batchPasteInput.split("\n", "\r\n")
                                        activeDraft.studentRows.addAll(lines)
                                        batchPasteInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("setup_parse_batch_btn")
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("إدراج الأسطر في قائمة المراجعة")
                            }

                            if (activeDraft.studentRows.isNotEmpty()) {
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
                                            text = "الأسطر المدرجة حاليًا في هذا القسم (${activeDraft.studentRows.size}):",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = palette.primaryText,
                                            fontWeight = FontWeight.Bold
                                        )
                                        activeDraft.studentRows.takeLast(5).forEachIndexed { i, name ->
                                            Text(
                                                text = "• ${name.ifBlank { "(سطر فارغ)" }}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = palette.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { step = 2 },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("رجوع")
                            }
                            Button(
                                onClick = { step = 4 },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("setup_next_to_review")
                            ) {
                                Text("التالي: مراجعة وحفظ")
                            }
                        }
                    }
                }
            }

            4 -> {
                // Step 5: Review step before saving (8.1 & T07)
                val validClasses = draftClasses.filter { it.name.isNotBlank() }
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SectionHeader(
                            title = "مراجعة البيانات قبل الحفظ",
                            subtitle = "الخطوة 4 من 4 — السنة الدراسية: $schoolYearLabel • الأقسام: ${validClasses.size}"
                        )

                        validClasses.forEach { clsDraft ->
                            val rows = clsDraft.studentRows
                            val emptyIndices = rows.mapIndexedNotNull { idx, r -> if (r.trim().isEmpty()) idx else null }
                            val normCounts = rows
                                .map { ArabicUtils.normalizeArabic(it) }
                                .filter { it.isNotEmpty() }
                                .groupingBy { it }
                                .eachCount()
                            val duplicateCount = rows.count {
                                val n = ArabicUtils.normalizeArabic(it)
                                n.isNotEmpty() && (normCounts[n] ?: 0) > 1
                            }

                            Surface(
                                color = palette.raised,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${clsDraft.level} — ${clsDraft.name} (إجمالي الأسطر: ${rows.size}، الأسماء الصالحة: ${rows.count { it.isNotBlank() }})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (emptyIndices.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.WarningAmber,
                                                contentDescription = null,
                                                tint = palette.warning,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "تنبيه: يوجد ${emptyIndices.size} سطر فارغ (يمكنكِ تصحيحه أو حذفه).",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = palette.warning
                                            )
                                        }
                                    }
                                    if (duplicateCount > 0) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.WarningAmber,
                                                contentDescription = null,
                                                tint = palette.warning,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "تنبيه تشابه الأسماء: يوجد $duplicateCount اسم مكرر داخل هذا القسم (لم يُحذف تلقائيًا؛ يمكنكِ تصحيحه أو الإبقاء عليه أو حذفه).",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = palette.warning
                                            )
                                        }
                                    }

                                    rows.forEachIndexed { rIdx, rowText ->
                                        val norm = ArabicUtils.normalizeArabic(rowText)
                                        val isDup = norm.isNotEmpty() && (normCounts[norm] ?: 0) > 1
                                        val isEmpty = rowText.trim().isEmpty()

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = rowText,
                                                onValueChange = { clsDraft.studentRows[rIdx] = it },
                                                label = {
                                                    Text(
                                                        when {
                                                            isEmpty -> "سطر ${rIdx + 1} (فارغ)"
                                                            isDup -> "سطر ${rIdx + 1} (مكرر)"
                                                            else -> "تلميذ ${rIdx + 1}"
                                                        }
                                                    )
                                                },
                                                isError = isEmpty || isDup,
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                            IconButton(
                                                onClick = { clsDraft.studentRows.removeAt(rIdx) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.DeleteOutline,
                                                    contentDescription = "حذف السطر",
                                                    tint = palette.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (submitError != null) {
                            Text(
                                text = submitError!!,
                                color = palette.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { step = 3 },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("رجوع")
                            }
                            Button(
                                onClick = {
                                    val drafts = draftClasses
                                        .filter { it.name.isNotBlank() }
                                        .map {
                                            TeacherRepository.SetupClassDraft(
                                                level = it.level,
                                                name = it.name.trim(),
                                                studentNames = it.studentRows.map { s -> s.trim() }.filter { s -> s.isNotEmpty() }
                                            )
                                        }
                                    val res = repository.completeFirstTimeSetup(
                                        professionalName = professionalName,
                                        institution = institution,
                                        schoolYearLabel = schoolYearLabel,
                                        classesDraft = drafts
                                    )
                                    if (res.isSuccess) {
                                        onSetupFinished()
                                    } else {
                                        submitError = res.exceptionOrNull()?.message
                                    }
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("setup_finish_save_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("حفظ وبدء استخدام التطبيق")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section 8.2 Home «نهاري»
 */
@Composable
fun HomeScreen(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    onOpenLessonPlan: (LessonPlan) -> Unit,
    onNavigateToRoute: (String, String?) -> Unit
) {
    val palette = LocalTeacherPalette.current
    val today = DateTimeUtils.todayDateString()
    val nowTime = DateTimeUtils.currentTimeString()
    val todayDayIndex = DateTimeUtils.getDayIndexFromSunday(today)
    val activeYearId = snapshot.meta.activeSchoolYearId
    val activeYear = snapshot.schoolYears.find { it.id == activeYearId }
    val classById = remember(snapshot.classes) { snapshot.classes.associateBy { it.id } }

    // Saved timetable entries for today in the active year (non-archived)
    val todayTimetable = remember(snapshot.timetableEntries, activeYearId, todayDayIndex, classById) {
        if (todayDayIndex !in 0..4) {
            emptyList()
        } else {
            snapshot.timetableEntries
                .filter {
                    it.schoolYearId == activeYearId &&
                        !it.isArchived &&
                        it.day == todayDayIndex &&
                        classById[it.classId]?.isArchived == false
                }
                .sortedBy { it.startTime }
        }
    }

    // Existing lesson plans for today
    val todayPlansBySlotId = remember(snapshot.lessonPlans, today) {
        snapshot.lessonPlans
            .filter { it.date == today && it.timetableEntryId != null }
            .associateBy { it.timetableEntryId!! }
    }

    // Next lesson card ONLY if a future entry exists today
    val nextEntry = remember(todayTimetable, nowTime) {
        val nowMins = DateTimeUtils.timeToMinutes(nowTime) ?: 0
        todayTimetable.firstOrNull { entry ->
            val endMins = DateTimeUtils.timeToMinutes(entry.endTime) ?: 0
            endMins > nowMins
        }
    }

    // Saved tests/exams from today to +14 days (T16)
    val maxAssessmentDate = remember(today) { DateTimeUtils.addDays(today, 14) }
    val upcomingAssessments = remember(snapshot.assessments, activeYearId, today, maxAssessmentDate) {
        snapshot.assessments
            .filter {
                it.schoolYearId == activeYearId &&
                    it.date >= today &&
                    it.date <= maxAssessmentDate
            }
            .sortedBy { it.date }
    }

    val showBackupReminder = remember(
        snapshot.meta,
        snapshot.classes,
        snapshot.lessonPlans,
        snapshot.gradeRecords,
        snapshot.trainingNotes,
        snapshot.seminars,
        today
    ) {
        repository.shouldShowBackupReminder(today)
    }

    val activeClassesCount = remember(snapshot.classes, activeYearId) {
        snapshot.classes.count { it.schoolYearId == activeYearId && !it.isArchived }
    }
    val activeStudentsCount = remember(snapshot.students, activeYearId) {
        snapshot.students.count { it.schoolYearId == activeYearId && !it.isArchived }
    }
    val yearLessonsCount = remember(snapshot.lessonPlans, activeYearId) {
        snapshot.lessonPlans.count { it.schoolYearId == activeYearId }
    }
    val hasSampleData = remember(snapshot.classes, snapshot.students, snapshot.lessonPlans) {
        snapshot.classes.any { it.isSample } ||
            snapshot.students.any { it.isSample } ||
            snapshot.lessonPlans.any { it.isSample }
    }

    var showQuickAddClassDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Focal Welcome Card (Section 6 & 8.2: uses Aref Ruqaa display font for the title)
        Surface(
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, palette.border),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                palette.surface,
                                palette.muted.copy(alpha = 0.72f)
                            )
                        )
                    )
            ) {
                PlannerCornerOrnament(modifier = Modifier.align(Alignment.TopEnd))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = DateTimeUtils.greetingForCurrentHour(),
                                style = MaterialTheme.typography.displayLarge,
                                color = palette.primaryText
                            )
                            if (snapshot.profile.professionalName.isNotBlank()) {
                                Text(
                                    text = snapshot.profile.professionalName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.text
                                )
                            }
                            if (snapshot.profile.institution.isNotBlank()) {
                                Text(
                                    text = snapshot.profile.institution,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                            }
                        }
                        PlannerLogo(size = 58.dp)
                    }

                    HorizontalDivider(color = palette.border.copy(alpha = 0.65f))

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
                            Icon(
                                Icons.Outlined.Today,
                                contentDescription = null,
                                tint = palette.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = DateTimeUtils.formatAlgerianDate(today, includeWeekday = true),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.text
                            )
                        }
                        if (activeYear != null) {
                            Surface(
                                color = palette.primary.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "السنة: ${activeYear.label}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primaryText,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Executive summary metrics strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeStatMiniBadge(
                            label = "الأقسام",
                            value = activeClassesCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HomeStatMiniBadge(
                            label = "التلاميذ",
                            value = activeStudentsCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HomeStatMiniBadge(
                            label = "حصص اليوم",
                            value = todayTimetable.size.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        HomeStatMiniBadge(
                            label = "التحضيرات",
                            value = yearLessonsCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Sample Data Notice Banner when exploring with sample data
        if (hasSampleData) {
            Surface(
                color = palette.goldAccent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, palette.goldAccent.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Outlined.Science,
                            contentDescription = null,
                            tint = palette.goldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "توجد بيانات تجريبية للمعاينة — يمكنكِ حذفها في أي وقت من الإعدادات.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text
                        )
                    }
                    TextButton(
                        onClick = { onNavigateToRoute("settings", "danger") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("إدارة", fontWeight = FontWeight.Bold, color = palette.primaryText)
                    }
                }
            }
        }

        // 2. Dismissible Backup Reminder (D15)
        if (showBackupReminder) {
            Surface(
                color = palette.warning.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, palette.warning),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup_reminder_banner")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Backup,
                            contentDescription = null,
                            tint = palette.warning
                        )
                        Text(
                            text = ArStrings.BACKUP_REMINDER,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.text
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigateToRoute("settings", "backup") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("النسخ الاحتياطي الآن")
                        }
                        OutlinedButton(
                            onClick = { repository.snoozeBackupReminder(today) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("snooze_backup_reminder_btn")
                        ) {
                            Text("تذكيري بعد 3 أيام")
                        }
                    }
                }
            }
        }

        // 3. Next Lesson Card ONLY if a future entry exists today (8.2)
        if (nextEntry != null) {
            val nextClass = classById[nextEntry.classId]
            val existingPlan = todayPlansBySlotId[nextEntry.id]
            Surface(
                color = palette.primary,
                contentColor = palette.onPrimary,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("next_lesson_card")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = palette.onPrimary)
                            Text(
                                text = "الحصة القادمة اليوم",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.onPrimary
                            )
                        }
                        Surface(
                            color = palette.onPrimary.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = DateTimeUtils.formatTimeRange(nextEntry.startTime, nextEntry.endTime),
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.onPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = nextClass?.fullTitle ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.onPrimary
                    )
                    if (existingPlan != null && existingPlan.title.isNotBlank()) {
                        Text(
                            text = "الدرس: ${existingPlan.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onPrimary.copy(alpha = 0.92f)
                        )
                    }
                    Button(
                        onClick = {
                            repository.openOrCreateLessonFromSlot(nextEntry.id, today)
                                .onSuccess { onOpenLessonPlan(it) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.surface,
                            contentColor = palette.text
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("افتح الحصة", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Today's Timetable Entries (saved records only, 8.2 & T17)
        SectionHeader(
            title = "حصص اليوم المحفوظة في التوقيت",
            subtitle = "افتحي أي حصة لتحرير مذكرتها أو تسجيل الملاحظات السريعة"
        )
        if (todayTimetable.isEmpty()) {
            TeacherEmptyState(
                icon = Icons.Outlined.Schedule,
                title = "لا توجد حصص مسجلة لهذا اليوم في التوقيت",
                description = "أضيفي حصصكِ الأسبوعية في جدول التوقيت لتظهر هنا تلقائيًا في يومها مع زر «افتح الحصة».",
                actionLabel = "إضافة حصة إلى التوقيت",
                onAction = { onNavigateToRoute("schedule", "add_timetable") }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                todayTimetable.forEach { slot ->
                    val cls = classById[slot.classId]
                    val plan = todayPlansBySlotId[slot.id]
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            // RTL Right Accent Strip
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .fillMaxHeight()
                                    .background(palette.primary)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(14.dp),
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
                                            text = cls?.fullTitle ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.text
                                        )
                                        if (slot.isSample || cls?.isSample == true) {
                                            SampleBadge()
                                        }
                                    }
                                    if (plan != null) {
                                        LessonStatusChip(status = plan.status)
                                    }
                                }
                                Text(
                                    text = "${DateTimeUtils.formatTimeRange(slot.startTime, slot.endTime)} • الفترة ${slot.period}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
                                if (plan != null && plan.title.isNotBlank()) {
                                    Text(
                                        text = "عنوان الدرس: ${plan.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = palette.primaryText,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            repository.openOrCreateLessonFromSlot(slot.id, today)
                                                .onSuccess { onOpenLessonPlan(it) }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("open_slot_btn_${slot.id}")
                                    ) {
                                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("افتح الحصة", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Upcoming Assessments (today .. +14 days, 8.2 & T16)
        SectionHeader(
            title = "الفروض والاختبارات خلال 14 يومًا القادمة",
            subtitle = "تذكير بالمواعيد المبرمجة وتواريخ التصحيح"
        )
        if (upcomingAssessments.isEmpty()) {
            TeacherEmptyState(
                icon = Icons.Outlined.Assignment,
                title = "لا توجد فروض أو اختبارات مبرمجة خلال الأسبوعين القادمين",
                description = "برمجي مواعيد الفروض والاختبارات في الرزنامة لتظهر تنبيهاتها هنا قبل موعدها بأسبوعين.",
                actionLabel = "إضافة اختبار أو فرض",
                onAction = { onNavigateToRoute("schedule", "add_assessment") }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                upcomingAssessments.forEach { ass ->
                    val clsNames = ass.classIds.mapNotNull { classById[it]?.fullTitle }.joinToString("، ")
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${ass.kind}: ${ass.title}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.primaryText,
                                    modifier = Modifier.weight(1f)
                                )
                                if (ass.isSample) {
                                    SampleBadge()
                                }
                            }
                            Text(
                                text = "الأقسام: $clsNames",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.text
                            )
                            Text(
                                text = "تاريخ الإجراء: ${DateTimeUtils.formatAlgerianDate(ass.date)} • التصحيح: ${DateTimeUtils.formatAlgerianDate(ass.correctionDate)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // 6. Real Quick Actions (8.2: «إضافة قسم»، «إضافة حصة إلى التوقيت»، «إضافة اختبار أو فرض»)
        SectionHeader(
            title = "إجراءات سريعة",
            subtitle = "اختصارات مباشرة للإضافة والتنظيم"
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeQuickActionCard(
                icon = Icons.Default.GroupAdd,
                title = "إضافة قسم",
                subtitle = "إنشاء قسم جديد في السنة الدراسية النشطة",
                testTag = "home_quick_add_class",
                onClick = { showQuickAddClassDialog = true }
            )
            HomeQuickActionCard(
                icon = Icons.Default.MoreTime,
                title = "إضافة حصة إلى التوقيت",
                subtitle = "برمجة حصة أسبوعية وربطها بأحد الأقسام",
                testTag = "home_quick_add_timetable",
                onClick = { onNavigateToRoute("schedule", "add_timetable") }
            )
            HomeQuickActionCard(
                icon = Icons.Default.PostAdd,
                title = "إضافة اختبار أو فرض",
                subtitle = "تحديد موعد الإجراء وموعد التصحيح في الرزنامة",
                testTag = "home_quick_add_assessment",
                onClick = { onNavigateToRoute("schedule", "add_assessment") }
            )
        }
    }

    if (showQuickAddClassDialog) {
        ClassEditDialog(
            existingClass = null,
            repository = repository,
            onDismiss = { showQuickAddClassDialog = false }
        )
    }
}

@Composable
private fun HomeStatMiniBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalTeacherPalette.current
    Surface(
        color = palette.raised.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.primaryText,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HomeQuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(palette.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = palette.primaryText,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = palette.textSecondary
            )
        }
    }
}

@Composable
fun ClassEditDialog(
    existingClass: SchoolClass?,
    repository: TeacherRepository,
    onDismiss: () -> Unit
) {
    var level by remember { mutableStateOf(existingClass?.level ?: ArStrings.SCHOOL_LEVELS.first()) }
    var name by remember { mutableStateOf(existingClass?.name ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingClass == null) "إضافة قسم جديد" else "تعديل بيانات القسم",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownRowSelector(
                    title = "المستوى الدراسي",
                    selectedLabel = level,
                    options = ArStrings.SCHOOL_LEVELS.map { it to it },
                    onSelect = { level = it }
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("اسم الفوج / القسم (مثال: م 1)") },
                    isError = errorMsg != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_dialog_name_input"),
                    singleLine = true
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val res = repository.saveClass(existingClass?.id, level, name)
                    if (res.isSuccess) {
                        onDismiss()
                    } else {
                        errorMsg = res.exceptionOrNull()?.message
                    }
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("class_dialog_save_btn")
            ) {
                Text("حفظ")
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
