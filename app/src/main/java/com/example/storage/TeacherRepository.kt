package com.example.storage

import android.content.Context
import com.example.domain.*
import com.example.i18n.ArStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class ClassCopySelection(
    val sourceClassId: String,
    val newLevel: String,
    val newName: String,
    val copyStudents: Boolean = true
)

data class SampleDeletionSummary(
    val sampleClassesCount: Int,
    val sampleStudentsCount: Int,
    val sampleTimetableCount: Int,
    val sampleLessonsCount: Int,
    val sampleObservationsCount: Int,
    val sampleGradesCount: Int,
    val realRecordsLinkedToSampleClasses: Int
)

class TeacherRepository(private val context: Context?) {

    private val dbFile: File? = context?.filesDir?.let { File(it, "teacher_app_db_v1.json") }
    private val tempFile: File? = context?.filesDir?.let { File(it, "teacher_app_db_v1.tmp") }
    private val prefs = context?.getSharedPreferences("teacher_local_settings", Context.MODE_PRIVATE)

    var simulateWriteFailure: Boolean = false
    var simulatedFailureReason: String = "تعذّر الكتابة في مساحة التخزين المحلية (الذاكرة ممتلئة أو محمية)."

    private val _snapshot = MutableStateFlow(createInitialSnapshot())
    val snapshot: StateFlow<DatabaseSnapshot> = _snapshot.asStateFlow()

    private val _localSettings = MutableStateFlow(loadLocalSettings())
    val localSettings: StateFlow<LocalSettings> = _localSettings.asStateFlow()

    private val _saveIndicator = MutableStateFlow(SaveIndicatorState())
    val saveIndicator: StateFlow<SaveIndicatorState> = _saveIndicator.asStateFlow()

    private val _viewedSchoolYearId = MutableStateFlow("")
    val viewedSchoolYearId: StateFlow<String> = _viewedSchoolYearId.asStateFlow()

    private val _storageInitError = MutableStateFlow<String?>(null)
    val storageInitError: StateFlow<String?> = _storageInitError.asStateFlow()

    init {
        loadFromDisk()
    }

    private fun createDefaultActivityTemplates(): List<ActivityTemplate> {
        val now = DateTimeUtils.nowIsoUtc()
        return ArStrings.DEFAULT_ACTIVITY_TEMPLATES.mapIndexed { idx, name ->
            ActivityTemplate(
                id = "act_tpl_${idx + 1}",
                name = name,
                orderIndex = idx + 1,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    private fun createDefaultTrainingCategories(): List<TrainingCategory> {
        val now = DateTimeUtils.nowIsoUtc()
        return ArStrings.DEFAULT_TRAINING_CATEGORIES.mapIndexed { idx, name ->
            TrainingCategory(
                id = "trn_cat_${idx + 1}",
                name = name,
                orderIndex = idx + 1,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    private fun createInitialSnapshot(): DatabaseSnapshot {
        return DatabaseSnapshot(
            meta = AppMeta(schemaVersion = StoreRegistry.SCHEMA_VERSION),
            activityTemplates = createDefaultActivityTemplates(),
            trainingCategories = createDefaultTrainingCategories()
        )
    }

    private fun loadLocalSettings(): LocalSettings {
        val p = prefs ?: return LocalSettings()
        return LocalSettings(
            theme = p.getString("theme", "rose") ?: "rose",
            customPrimaryHex = p.getString("customPrimaryHex", null)?.takeIf { it.isNotBlank() },
            fontSize = p.getString("fontSize", "normal") ?: "normal"
        )
    }

    fun updateLocalSettings(transform: (LocalSettings) -> LocalSettings) {
        val next = transform(_localSettings.value)
        _localSettings.value = next
        prefs?.edit()?.apply {
            putString("theme", next.theme)
            if (next.customPrimaryHex != null) {
                putString("customPrimaryHex", next.customPrimaryHex)
            } else {
                remove("customPrimaryHex")
            }
            putString("fontSize", next.fontSize)
            apply()
        }
    }

    private fun loadFromDisk() {
        val f = dbFile ?: return
        if (!f.exists()) return
        try {
            val raw = f.readText(Charsets.UTF_8)
            if (raw.isNotBlank()) {
                val dataObj = JSONObject(raw)
                val (loadedSnapshot, _) = BackupSerializer.parseDataObject(dataObj)
                val ensuredTemplates = if (loadedSnapshot.activityTemplates.isEmpty()) {
                    createDefaultActivityTemplates()
                } else loadedSnapshot.activityTemplates
                val ensuredCategories = if (loadedSnapshot.trainingCategories.isEmpty()) {
                    createDefaultTrainingCategories()
                } else loadedSnapshot.trainingCategories
                val finalSnap = loadedSnapshot.copy(
                    activityTemplates = ensuredTemplates,
                    trainingCategories = ensuredCategories
                )
                _snapshot.value = finalSnap
                _viewedSchoolYearId.value = finalSnap.meta.activeSchoolYearId
            }
        } catch (e: Exception) {
            _storageInitError.value = "تعذّر فتح قاعدة البيانات المحلية: ${e.message ?: "خطأ غير معروف"}"
        }
    }

    private fun persistToDiskAtomically(nextSnapshot: DatabaseSnapshot) {
        if (simulateWriteFailure) {
            throw IOException(simulatedFailureReason)
        }
        val f = dbFile ?: return
        val tmp = tempFile ?: return
        val dataObj = BackupSerializer.serializeDataObject(nextSnapshot, _localSettings.value)
        tmp.writeText(dataObj.toString(), Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            f.writeText(dataObj.toString(), Charsets.UTF_8)
            tmp.delete()
        }
    }

    /**
     * D7: Single runSave() wrapper that reports:
     * «جارٍ الحفظ…» -> «تم الحفظ» (after transaction completes) or «تعذّر الحفظ» (with reason, data untouched).
     */
    @Synchronized
    fun runSave(block: (DatabaseSnapshot) -> DatabaseSnapshot): Result<DatabaseSnapshot> {
        _saveIndicator.value = SaveIndicatorState(
            status = SaveStatus.SAVING,
            message = ArStrings.SAVE_SAVING,
            errorReason = null,
            updatedAtMs = System.currentTimeMillis()
        )
        val current = _snapshot.value
        return try {
            var next = block(current)
            // Update firstDataAt if data worth backing up now exists and firstDataAt was null
            if (next.meta.firstDataAt == null && next.hasDataWorthBackingUp()) {
                next = next.copy(
                    meta = next.meta.copy(firstDataAt = DateTimeUtils.todayDateString())
                )
            }
            persistToDiskAtomically(next)
            _snapshot.value = next
            if (_viewedSchoolYearId.value.isBlank() && next.meta.activeSchoolYearId.isNotBlank()) {
                _viewedSchoolYearId.value = next.meta.activeSchoolYearId
            }
            _saveIndicator.value = SaveIndicatorState(
                status = SaveStatus.SAVED,
                message = ArStrings.SAVE_SAVED,
                errorReason = null,
                updatedAtMs = System.currentTimeMillis()
            )
            Result.success(next)
        } catch (e: Exception) {
            val reason = e.message ?: "خطأ في حفظ البيانات المحلية."
            _saveIndicator.value = SaveIndicatorState(
                status = SaveStatus.FAILED,
                message = "${ArStrings.SAVE_FAILED}: $reason",
                errorReason = reason,
                updatedAtMs = System.currentTimeMillis()
            )
            Result.failure(e)
        }
    }

    fun currentViewedYearId(): String {
        val v = _viewedSchoolYearId.value
        val snap = _snapshot.value
        return if (v.isNotBlank() && snap.schoolYears.any { it.id == v }) {
            v
        } else {
            snap.meta.activeSchoolYearId
        }
    }

    fun setViewedSchoolYear(yearId: String) {
        if (_snapshot.value.schoolYears.any { it.id == yearId }) {
            _viewedSchoolYearId.value = yearId
        }
    }

    fun returnToActiveSchoolYear() {
        _viewedSchoolYearId.value = _snapshot.value.meta.activeSchoolYearId
    }

    fun isViewingArchivedYear(): Boolean {
        val snap = _snapshot.value
        val viewed = currentViewedYearId()
        return viewed.isNotBlank() && viewed != snap.meta.activeSchoolYearId
    }

    // =========================================================================
    // 8.1 FIRST-TIME SETUP & D11 SAMPLE DATA
    // =========================================================================

    data class SetupClassDraft(
        val level: String,
        val name: String,
        val studentNames: List<String>
    )

    fun completeFirstTimeSetup(
        professionalName: String,
        institution: String,
        schoolYearLabel: String,
        classesDraft: List<SetupClassDraft>
    ): Result<DatabaseSnapshot> {
        val labelNormalized = ArabicUtils.toWesternDigits(schoolYearLabel.trim())
        DateTimeUtils.validateSchoolYearLabel(labelNormalized)?.let {
            return failValidation(it)
        }
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            val yearId = newStableId()
            val newYear = SchoolYear(
                id = yearId,
                label = labelNormalized,
                status = "active",
                createdAt = now,
                updatedAt = now
            )

            val createdClasses = mutableListOf<SchoolClass>()
            val createdStudents = mutableListOf<Student>()
            val seenClassPairs = mutableSetOf<String>()

            for (draft in classesDraft) {
                val cName = draft.name.trim()
                if (cName.isEmpty()) continue
                val key = "${ArabicUtils.normalizeArabic(draft.level)}|${ArabicUtils.normalizeArabic(cName)}"
                if (!seenClassPairs.add(key)) {
                    throw IllegalArgumentException("يوجد قسمان بنفس المستوى والاسم: ${draft.level} — $cName")
                }
                val classId = newStableId()
                createdClasses.add(
                    SchoolClass(
                        id = classId,
                        schoolYearId = yearId,
                        level = draft.level,
                        name = cName,
                        isArchived = false,
                        isSample = false,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                draft.studentNames.map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { idx, sName ->
                    createdStudents.add(
                        Student(
                            id = newStableId(),
                            schoolYearId = yearId,
                            classId = classId,
                            fullName = sName,
                            orderIndex = idx + 1,
                            isArchived = false,
                            isSample = false,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            val defaultConfigs = listOf("T1", "T2", "T3").map { term ->
                GradeTermConfig(
                    id = newStableId(),
                    schoolYearId = yearId,
                    termId = term,
                    createdAt = now,
                    updatedAt = now
                )
            }

            val next = current.copy(
                meta = current.meta.copy(
                    activeSchoolYearId = yearId,
                    setupCompleted = true
                ),
                schoolYears = listOf(newYear),
                profile = current.profile.copy(
                    professionalName = professionalName.trim(),
                    institution = institution.trim(),
                    updatedAt = now
                ),
                classes = createdClasses,
                students = createdStudents,
                gradeTermConfigs = defaultConfigs
            )
            _viewedSchoolYearId.value = yearId
            next
        }
    }

    /**
     * D11: Modest, clearly fictional sample data («تلميذ تجريبي 1»):
     * 2 classes, 8 students each, timetable entries, 1 assessment, 2 lesson plans,
     * grade rows (some complete, some incomplete). Every sample record has isSample = true.
     */
    fun seedSampleData(fromOnboarding: Boolean = false): Result<DatabaseSnapshot> {
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            val today = DateTimeUtils.todayDateString()
            val todayDayIndex = DateTimeUtils.getDayIndexFromSunday(today).coerceIn(0, 4)

            var activeYearId = current.meta.activeSchoolYearId
            val updatedYears = current.schoolYears.toMutableList()
            if (activeYearId.isBlank() || updatedYears.none { it.id == activeYearId }) {
                val yr = SchoolYear(
                    id = newStableId(),
                    label = "2026-2027",
                    status = "active",
                    createdAt = now,
                    updatedAt = now
                )
                updatedYears.forEachIndexed { idx, y ->
                    updatedYears[idx] = y.copy(status = "archived", archivedAt = now)
                }
                updatedYears.add(yr)
                activeYearId = yr.id
            }

            // Remove existing sample data in active year first to avoid duplicates if tapped twice
            val baseSnap = removeSampleFromSnapshot(
                current.copy(
                    meta = current.meta.copy(
                        activeSchoolYearId = activeYearId,
                        setupCompleted = true
                    ),
                    schoolYears = updatedYears
                )
            )

            val class1 = SchoolClass(
                id = newStableId(),
                schoolYearId = activeYearId,
                level = "الأولى متوسط",
                name = "م 1 (تجريبي)",
                isArchived = false,
                isSample = true,
                createdAt = now,
                updatedAt = now
            )
            val class2 = SchoolClass(
                id = newStableId(),
                schoolYearId = activeYearId,
                level = "الثالثة متوسط",
                name = "م 2 (تجريبي)",
                isArchived = false,
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            val sampleStudents1 = (1..8).map { i ->
                Student(
                    id = newStableId(),
                    schoolYearId = activeYearId,
                    classId = class1.id,
                    fullName = "تلميذ تجريبي $i (م1)",
                    orderIndex = i,
                    isArchived = false,
                    isSample = true,
                    createdAt = now,
                    updatedAt = now
                )
            }
            val sampleStudents2 = (1..8).map { i ->
                Student(
                    id = newStableId(),
                    schoolYearId = activeYearId,
                    classId = class2.id,
                    fullName = "تلميذ تجريبي $i (م2)",
                    orderIndex = i,
                    isArchived = false,
                    isSample = true,
                    createdAt = now,
                    updatedAt = now
                )
            }

            val tt1 = TimetableEntry(
                id = newStableId(),
                schoolYearId = activeYearId,
                classId = class1.id,
                day = todayDayIndex,
                startTime = "08:00",
                endTime = "09:00",
                period = ArStrings.PERIOD_MORNING,
                isSample = true,
                createdAt = now,
                updatedAt = now
            )
            val tt2 = TimetableEntry(
                id = newStableId(),
                schoolYearId = activeYearId,
                classId = class2.id,
                day = todayDayIndex,
                startTime = "10:00",
                endTime = "11:00",
                period = ArStrings.PERIOD_MORNING,
                isSample = true,
                createdAt = now,
                updatedAt = now
            )
            val tt3 = TimetableEntry(
                id = newStableId(),
                schoolYearId = activeYearId,
                classId = class1.id,
                day = (todayDayIndex + 1) % 5,
                startTime = "13:30",
                endTime = "14:30",
                period = ArStrings.PERIOD_AFTERNOON,
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            val sampleAssessment = AssessmentEvent(
                id = newStableId(),
                schoolYearId = activeYearId,
                kind = "فرض",
                title = "الفرض المحروس الأول (تجريبي)",
                classIds = listOf(class1.id, class2.id),
                date = DateTimeUtils.addDays(today, 5),
                correctionDate = DateTimeUtils.addDays(today, 9),
                notes = "بيانات تجريبية للمعاينة",
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            val firstTpl = baseSnap.activityTemplates.firstOrNull()
            val lp1 = LessonPlan(
                id = newStableId(),
                schoolYearId = activeYearId,
                classId = class1.id,
                timetableEntryId = tt1.id,
                date = today,
                startTime = tt1.startTime,
                endTime = tt1.endTime,
                status = ArStrings.STATUS_PREPARED,
                title = "درس تجريبي: القيم الإنسانية في الشعر العربي",
                learningSegment = "المقطع الأول: الحياة العائلية",
                activityId = firstTpl?.id,
                activityName = firstTpl?.name ?: "فهم المنطوق",
                targetCompetence = "يفهم الخطاب المنطوق ويتفاعل معه بلغة سليمة.",
                procedure = "تمهيد، إسماع النص، مناقشة الأفكار الأساسية، استخلاص القيم.",
                materials = "الكتاب المدرسي، السبورة، مسجل صوتي.",
                evaluation = "أسئلة الفهم السريع وتلخيص مضمون الخطاب شفويًا.",
                homework = "تحضير عناصر النص القرائي القادم.",
                notes = "حصة تجريبية للمعاينة.",
                isSample = true,
                createdAt = now,
                updatedAt = now
            )
            val secondTpl = baseSnap.activityTemplates.getOrNull(4) ?: firstTpl
            val lp2 = LessonPlan(
                id = newStableId(),
                schoolYearId = activeYearId,
                classId = class2.id,
                timetableEntryId = tt2.id,
                date = today,
                startTime = tt2.startTime,
                endTime = tt2.endTime,
                status = ArStrings.STATUS_DRAFT,
                title = "درس تجريبي: بناء الفعل الماضي",
                learningSegment = "المقطع الأول: الآفات الاجتماعية",
                activityId = secondTpl?.id,
                activityName = secondTpl?.name ?: "الظاهرة النحوية",
                targetCompetence = "يوظف علامات بناء الفعل الماضي في إنتاجه الشفوي والكتابي.",
                procedure = "عرض الأمثلة، الملاحظة والمناقشة، الاستنتاج، التطبيق الفوري.",
                materials = "السبورة، كراس المحاولات.",
                evaluation = "إعراب جملتين تطبيقيتين.",
                homework = "تمرين 3 ص 24.",
                notes = "",
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            // Ensure term 1 config exists
            val existingConfigs = baseSnap.gradeTermConfigs.toMutableList()
            var t1Config = existingConfigs.find { it.schoolYearId == activeYearId && it.termId == "T1" }
            if (t1Config == null) {
                t1Config = GradeTermConfig(
                    id = newStableId(),
                    schoolYearId = activeYearId,
                    termId = "T1",
                    createdAt = now,
                    updatedAt = now
                )
                existingConfigs.add(t1Config)
            }

            val gb1 = Gradebook(
                id = newStableId(),
                schoolYearId = activeYearId,
                classId = class1.id,
                termId = "T1",
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            // Create grade records for class1: 5 complete, 3 incomplete
            val sampleGrades = sampleStudents1.mapIndexed { idx, st ->
                val raw = when (idx) {
                    0 -> GradeRecord(
                        gradebookId = gb1.id, schoolYearId = activeYearId, classId = class1.id,
                        studentId = st.id, termId = "T1", orderIndex = st.orderIndex,
                        ca1 = 15.0, ca2 = 16.0, as1 = 14.5, exam = 15.5,
                        remarks = "عمل ممتاز", isSample = true, createdAt = now, updatedAt = now
                    )
                    1 -> GradeRecord(
                        gradebookId = gb1.id, schoolYearId = activeYearId, classId = class1.id,
                        studentId = st.id, termId = "T1", orderIndex = st.orderIndex,
                        ca1 = 12.0, ca2 = 13.0, as1 = 11.5, exam = 12.5,
                        remarks = "نتائج حسنة", isSample = true, createdAt = now, updatedAt = now
                    )
                    2 -> GradeRecord(
                        gradebookId = gb1.id, schoolYearId = activeYearId, classId = class1.id,
                        studentId = st.id, termId = "T1", orderIndex = st.orderIndex,
                        ca1 = 9.0, ca2 = 10.0, as1 = 8.5, exam = 9.0,
                        remarks = "يحتاج إلى مزيد من التركيز", isSample = true, createdAt = now, updatedAt = now
                    )
                    3 -> GradeRecord(
                        gradebookId = gb1.id, schoolYearId = activeYearId, classId = class1.id,
                        studentId = st.id, termId = "T1", orderIndex = st.orderIndex,
                        ca1 = 17.0, ca2 = 18.0, as1 = 16.5, exam = 17.5,
                        remarks = "تلميذ متميز", isSample = true, createdAt = now, updatedAt = now
                    )
                    4 -> GradeRecord(
                        gradebookId = gb1.id, schoolYearId = activeYearId, classId = class1.id,
                        studentId = st.id, termId = "T1", orderIndex = st.orderIndex,
                        ca1 = 10.0, ca2 = 11.0, as1 = 10.0, exam = 10.5,
                        remarks = "مقبول", isSample = true, createdAt = now, updatedAt = now
                    )
                    5 -> GradeRecord(
                        gradebookId = gb1.id, schoolYearId = activeYearId, classId = class1.id,
                        studentId = st.id, termId = "T1", orderIndex = st.orderIndex,
                        ca1 = 13.0, ca2 = 14.0, as1 = 12.0, exam = null, // incomplete (missing exam)
                        remarks = "", isSample = true, createdAt = now, updatedAt = now
                    )
                    else -> GradeRecord(
                        gradebookId = gb1.id, schoolYearId = activeYearId, classId = class1.id,
                        studentId = st.id, termId = "T1", orderIndex = st.orderIndex,
                        isSample = true, createdAt = now, updatedAt = now
                    )
                }
                val eval = GradingEngine.evaluate(raw, t1Config)
                raw.copy(finalAverage = eval.finalAverage)
            }

            val updatedProfile = if (fromOnboarding && baseSnap.profile.professionalName.isBlank()) {
                baseSnap.profile.copy(
                    professionalName = "أستاذة اللغة العربية",
                    institution = "متوسطة الشهيد بن بولعيد (نموذج تجريبي)",
                    specialization = "اللغة العربية وآدابها",
                    rank = "أستاذ التعليم المتوسط",
                    updatedAt = now
                )
            } else baseSnap.profile

            val sampleObs1 = LessonObservation(
                id = newStableId(),
                schoolYearId = activeYearId,
                lessonPlanId = lp1.id,
                classId = class1.id,
                studentId = sampleStudents1[0].id,
                attendance = "present",
                participated = true,
                note = "مشاركة متميزة وتفاعل ممتاز مع أبيات القصيدة (بيانات تجريبية)",
                noteAt = today,
                observedAt = now,
                isSample = true,
                createdAt = now,
                updatedAt = now
            )
            val sampleObs2 = LessonObservation(
                id = newStableId(),
                schoolYearId = activeYearId,
                lessonPlanId = lp1.id,
                classId = class1.id,
                studentId = sampleStudents1[2].id,
                attendance = "present",
                participated = false,
                note = "يحتاج إلى مراجعة المفردات اللغوية الصعبة (بيانات تجريبية)",
                noteAt = today,
                observedAt = now,
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            val sampleHoliday = CalendarEvent(
                id = newStableId(),
                schoolYearId = activeYearId,
                kind = "break",
                season = "خريف",
                title = "عطلة الخريف (بيانات تجريبية)",
                startDate = DateTimeUtils.addDays(today, 25),
                endDate = DateTimeUtils.addDays(today, 30),
                notes = "عطلة مدرسية فصلية للمعاينة",
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            val firstCat = baseSnap.trainingCategories.firstOrNull()
            val sampleTrainingNote = TrainingNote(
                id = newStableId(),
                schoolYearId = activeYearId,
                title = "منهجية تسيير حصة فهم المنطوق (نموذج تجريبي)",
                date = today,
                text = "التركيز على الاستماع النشط، أجرأة الكفاءة الختامية للميدان، وتنويع سندات الخطاب المسموع.",
                categoryId = firstCat?.id,
                categoryName = firstCat?.name ?: "تعليمية المادة",
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            val sampleSeminar = SeminarRecord(
                id = newStableId(),
                schoolYearId = activeYearId,
                kind = "internal",
                date = today,
                title = "ندوة داخلية حول بناء وضعيات التقويم (نموذج تجريبي)",
                location = "مكتبة المتوسطة",
                facilitator = "منسق مادة اللغة العربية",
                mainIdeas = "ضبط شبكة التقويم المعيابية ومؤشرات الكفاءة في الوضعيات الإدماجية.",
                recommendations = "توحيد سلم التنقيط وتخصيص حصص للمعالجة البيداغوجية المستهدفة.",
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            val sampleEduRecord = EduCalendarRecord(
                id = newStableId(),
                schoolYearId = activeYearId,
                type = "pedagogicalSeminar",
                date = DateTimeUtils.addDays(today, 12),
                appliedLesson = "درس تطبيقي في القراءة المشروحة (نموذج تجريبي)",
                teacherName = "أستاذة اللغة العربية",
                level = "الأولى متوسط",
                topic = "تفعيل العمل الفوجي وبناء شبكة الفهم",
                location = "المتوسطة المركزية",
                internshipType = "",
                isSample = true,
                createdAt = now,
                updatedAt = now
            )

            _viewedSchoolYearId.value = activeYearId
            baseSnap.copy(
                profile = updatedProfile,
                classes = baseSnap.classes + listOf(class1, class2),
                students = baseSnap.students + sampleStudents1 + sampleStudents2,
                timetableEntries = baseSnap.timetableEntries + listOf(tt1, tt2, tt3),
                calendarEvents = baseSnap.calendarEvents + sampleHoliday,
                assessments = baseSnap.assessments + sampleAssessment,
                lessonPlans = baseSnap.lessonPlans + listOf(lp1, lp2),
                lessonObservations = baseSnap.lessonObservations + listOf(sampleObs1, sampleObs2),
                gradeTermConfigs = existingConfigs,
                gradebooks = baseSnap.gradebooks + gb1,
                gradeRecords = baseSnap.gradeRecords + sampleGrades,
                trainingNotes = baseSnap.trainingNotes + sampleTrainingNote,
                seminars = baseSnap.seminars + sampleSeminar,
                eduCalendarRecords = baseSnap.eduCalendarRecords + sampleEduRecord
            )
        }
    }

    fun getSampleDeletionSummary(): SampleDeletionSummary {
        val snap = _snapshot.value
        val sampleClassIds = snap.classes.filter { it.isSample }.map { it.id }.toSet()
        val studentsToRemove = snap.students.filter { it.isSample || it.classId in sampleClassIds }
        val ttToRemove = snap.timetableEntries.filter { it.isSample || it.classId in sampleClassIds }
        val lpToRemove = snap.lessonPlans.filter { it.isSample || it.classId in sampleClassIds }
        val lpIdsToRemove = lpToRemove.map { it.id }.toSet()
        val obsToRemove = snap.lessonObservations.filter {
            it.isSample || it.classId in sampleClassIds || it.lessonPlanId in lpIdsToRemove
        }
        val gradesToRemove = snap.gradeRecords.filter { it.isSample || it.classId in sampleClassIds }

        val realLinkedCount =
            studentsToRemove.count { !it.isSample } +
                ttToRemove.count { !it.isSample } +
                lpToRemove.count { !it.isSample } +
                obsToRemove.count { !it.isSample } +
                gradesToRemove.count { !it.isSample }

        return SampleDeletionSummary(
            sampleClassesCount = sampleClassIds.size,
            sampleStudentsCount = studentsToRemove.size,
            sampleTimetableCount = ttToRemove.size,
            sampleLessonsCount = lpToRemove.size,
            sampleObservationsCount = obsToRemove.size,
            sampleGradesCount = gradesToRemove.size,
            realRecordsLinkedToSampleClasses = realLinkedCount
        )
    }

    private fun removeSampleFromSnapshot(snap: DatabaseSnapshot): DatabaseSnapshot {
        val sampleClassIds = snap.classes.filter { it.isSample }.map { it.id }.toSet()
        val removedStudentIds = snap.students
            .filter { it.isSample || it.classId in sampleClassIds }
            .map { it.id }
            .toSet()
        val removedLessonIds = snap.lessonPlans
            .filter { it.isSample || it.classId in sampleClassIds }
            .map { it.id }
            .toSet()
        val removedGradebookIds = snap.gradebooks
            .filter { it.isSample || it.classId in sampleClassIds }
            .map { it.id }
            .toSet()

        // Multi-class assessments: remove sample classes from classIds; delete assessment only if it becomes empty or isSample
        val updatedAssessments = snap.assessments.mapNotNull { a ->
            if (a.isSample) return@mapNotNull null
            val remainingClasses = a.classIds.filter { it !in sampleClassIds }
            if (remainingClasses.isEmpty()) null else a.copy(classIds = remainingClasses)
        }

        return snap.copy(
            classes = snap.classes.filterNot { it.isSample || it.id in sampleClassIds },
            students = snap.students.filterNot { it.id in removedStudentIds },
            studentPrivateNotes = snap.studentPrivateNotes.filterNot { it.studentId in removedStudentIds },
            timetableEntries = snap.timetableEntries.filterNot { it.isSample || it.classId in sampleClassIds },
            calendarEvents = snap.calendarEvents.filterNot { it.isSample },
            assessments = updatedAssessments,
            lessonPlans = snap.lessonPlans.filterNot { it.id in removedLessonIds },
            lessonObservations = snap.lessonObservations.filterNot {
                it.isSample || it.classId in sampleClassIds || it.lessonPlanId in removedLessonIds || it.studentId in removedStudentIds
            },
            gradebooks = snap.gradebooks.filterNot { it.id in removedGradebookIds },
            gradeRecords = snap.gradeRecords.filterNot {
                it.isSample || it.classId in sampleClassIds || it.gradebookId in removedGradebookIds || it.studentId in removedStudentIds
            },
            trainingNotes = snap.trainingNotes.filterNot { it.isSample },
            seminars = snap.seminars.filterNot { it.isSample },
            eduCalendarRecords = snap.eduCalendarRecords.filterNot { it.isSample }
        )
    }

    fun removeSampleDataOnly(): Result<DatabaseSnapshot> = runSave { current ->
        removeSampleFromSnapshot(current)
    }

    // =========================================================================
    // D6: DEPENDENTS & DELETE VS ARCHIVE TIERS
    // =========================================================================

    fun getClassDependents(classId: String): ClassDependents {
        val snap = _snapshot.value
        return ClassDependents(
            studentCount = snap.students.count { it.classId == classId },
            timetableCount = snap.timetableEntries.count { it.classId == classId },
            lessonPlanCount = snap.lessonPlans.count { it.classId == classId },
            observationCount = snap.lessonObservations.count { it.classId == classId },
            assessmentCount = snap.assessments.count { classId in it.classIds },
            nonEmptyGradeCount = snap.gradeRecords.count { it.classId == classId && it.hasAnyNonEmptyValue() }
        )
    }

    fun getStudentDependents(studentId: String): StudentDependents {
        val snap = _snapshot.value
        val obsCount = snap.lessonObservations.count { it.studentId == studentId }
        val studentGrades = snap.gradeRecords.filter { it.studentId == studentId }
        val nonEmpty = studentGrades.count { it.hasAnyNonEmptyValue() }
        val emptyRows = studentGrades.size - nonEmpty
        val hasNote = snap.studentPrivateNotes.any { it.studentId == studentId && it.text.isNotBlank() }
        return StudentDependents(
            observationCount = obsCount,
            nonEmptyGradeCount = nonEmpty,
            hasPrivateNote = hasNote,
            emptyGradeRowCount = emptyRows
        )
    }

    fun getTimetableDependents(timetableEntryId: String): TimetableDependents {
        val snap = _snapshot.value
        return TimetableDependents(
            lessonPlanCount = snap.lessonPlans.count { it.timetableEntryId == timetableEntryId }
        )
    }

    fun getLessonDependents(lessonPlanId: String): LessonDependents {
        val snap = _snapshot.value
        return LessonDependents(
            observationCount = snap.lessonObservations.count { it.lessonPlanId == lessonPlanId }
        )
    }

    // =========================================================================
    // 8.3 CLASSES, STUDENTS & PRIVATE NOTES
    // =========================================================================

    fun saveClass(
        existingId: String?,
        level: String,
        name: String,
        schoolYearId: String = currentViewedYearId()
    ): Result<DatabaseSnapshot> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return failValidation("يرجى إدخال اسم الفوج أو القسم (مثال: م 1).")
        }
        val snap = _snapshot.value
        val duplicate = snap.classes.any {
            it.schoolYearId == schoolYearId &&
                it.id != existingId &&
                ArabicUtils.normalizeArabic(it.level) == ArabicUtils.normalizeArabic(level) &&
                ArabicUtils.normalizeArabic(it.name) == ArabicUtils.normalizeArabic(trimmedName)
        }
        if (duplicate) {
            return failValidation("يوجد قسم آخر بنفس المستوى والاسم في هذه السنة الدراسية.")
        }

        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val newClass = SchoolClass(
                    id = newStableId(),
                    schoolYearId = schoolYearId,
                    level = level,
                    name = trimmedName,
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(classes = current.classes + newClass)
            } else {
                current.copy(
                    classes = current.classes.map {
                        if (it.id == existingId) it.copy(level = level, name = trimmedName, updatedAt = now) else it
                    }
                )
            }
        }
    }

    fun setClassArchived(classId: String, archived: Boolean): Result<DatabaseSnapshot> = runSave { current ->
        val now = DateTimeUtils.nowIsoUtc()
        current.copy(
            classes = current.classes.map {
                if (it.id == classId) it.copy(isArchived = archived, updatedAt = now) else it
            }
        )
    }

    fun deleteClassPermanently(classId: String): Result<DatabaseSnapshot> {
        val deps = getClassDependents(classId)
        if (deps.hasHistoryBlockingPermanentDelete) {
            return failValidation("لا يمكن حذف هذا القسم نهائيًا لوجود تحضيرات أو ملاحظات أو نقاط مرتبطة به. يمكنكِ أرشفته بدلًا من ذلك.")
        }
        return runSave { current ->
            val studentIds = current.students.filter { it.classId == classId }.map { it.id }.toSet()
            current.copy(
                classes = current.classes.filterNot { it.id == classId },
                students = current.students.filterNot { it.classId == classId },
                studentPrivateNotes = current.studentPrivateNotes.filterNot { it.studentId in studentIds },
                timetableEntries = current.timetableEntries.filterNot { it.classId == classId },
                gradebooks = current.gradebooks.filterNot { it.classId == classId },
                gradeRecords = current.gradeRecords.filterNot { it.classId == classId }
            )
        }
    }

    fun addOrUpdateStudent(
        existingId: String?,
        classId: String,
        fullName: String
    ): Result<DatabaseSnapshot> {
        val trimmed = fullName.trim()
        if (trimmed.isEmpty()) {
            return failValidation("اسم التلميذ لا يمكن أن يكون فارغًا.")
        }
        return runSave { current ->
            val cls = current.classes.find { it.id == classId }
                ?: throw IllegalArgumentException("القسم المحدد غير موجود.")
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val maxOrder = current.students
                    .filter { it.classId == classId }
                    .maxOfOrNull { it.orderIndex } ?: 0
                val newStudent = Student(
                    id = newStableId(),
                    schoolYearId = cls.schoolYearId,
                    classId = classId,
                    fullName = trimmed,
                    orderIndex = maxOrder + 1,
                    isArchived = false,
                    isSample = false,
                    createdAt = now,
                    updatedAt = now
                )
                // If gradebooks already exist for this class, create empty grade rows for the new student
                val classGradebooks = current.gradebooks.filter { it.classId == classId }
                val newGradeRows = classGradebooks.map { gb ->
                    GradeRecord(
                        id = newStableId(),
                        gradebookId = gb.id,
                        schoolYearId = cls.schoolYearId,
                        classId = classId,
                        studentId = newStudent.id,
                        termId = gb.termId,
                        orderIndex = newStudent.orderIndex,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                current.copy(
                    students = current.students + newStudent,
                    gradeRecords = current.gradeRecords + newGradeRows
                )
            } else {
                current.copy(
                    students = current.students.map {
                        if (it.id == existingId) it.copy(fullName = trimmed, updatedAt = now) else it
                    }
                )
            }
        }
    }

    fun addBatchStudents(classId: String, names: List<String>): Result<DatabaseSnapshot> {
        val validNames = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (validNames.isEmpty()) {
            return failValidation("لا توجد أسماء صالحة للإضافة.")
        }
        return runSave { current ->
            val cls = current.classes.find { it.id == classId }
                ?: throw IllegalArgumentException("القسم المحدد غير موجود.")
            val now = DateTimeUtils.nowIsoUtc()
            var nextOrder = (current.students.filter { it.classId == classId }.maxOfOrNull { it.orderIndex } ?: 0) + 1
            val newStudents = validNames.map { n ->
                Student(
                    id = newStableId(),
                    schoolYearId = cls.schoolYearId,
                    classId = classId,
                    fullName = n,
                    orderIndex = nextOrder++,
                    isArchived = false,
                    isSample = false,
                    createdAt = now,
                    updatedAt = now
                )
            }
            val classGradebooks = current.gradebooks.filter { it.classId == classId }
            val newGradeRows = classGradebooks.flatMap { gb ->
                newStudents.map { st ->
                    GradeRecord(
                        id = newStableId(),
                        gradebookId = gb.id,
                        schoolYearId = cls.schoolYearId,
                        classId = classId,
                        studentId = st.id,
                        termId = gb.termId,
                        orderIndex = st.orderIndex,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            }
            current.copy(
                students = current.students + newStudents,
                gradeRecords = current.gradeRecords + newGradeRows
            )
        }
    }

    fun setStudentArchived(studentId: String, archived: Boolean): Result<DatabaseSnapshot> = runSave { current ->
        val now = DateTimeUtils.nowIsoUtc()
        current.copy(
            students = current.students.map {
                if (it.id == studentId) it.copy(isArchived = archived, updatedAt = now) else it
            }
        )
    }

    fun deleteStudentPermanently(studentId: String): Result<DatabaseSnapshot> {
        val deps = getStudentDependents(studentId)
        if (!deps.canDeletePermanently) {
            return failValidation("لا يمكن حذف التلميذ نهائيًا لوجود ملاحظات حصة أو نقاط مسجلة له. استخدمي الأرشفة للحفاظ على السجل.")
        }
        return runSave { current ->
            current.copy(
                students = current.students.filterNot { it.id == studentId },
                studentPrivateNotes = current.studentPrivateNotes.filterNot { it.studentId == studentId },
                gradeRecords = current.gradeRecords.filterNot { it.studentId == studentId }
            )
        }
    }

    /**
     * D5: Explicit read from studentPrivateNotes store only when student details dialog is opened
     */
    fun getStudentPrivateNote(studentId: String): String {
        return _snapshot.value.studentPrivateNotes.find { it.studentId == studentId }?.text ?: ""
    }

    fun saveStudentPrivateNote(studentId: String, text: String): Result<DatabaseSnapshot> = runSave { current ->
        if (current.students.none { it.id == studentId }) {
            throw IllegalArgumentException("التلميذ غير موجود.")
        }
        val now = DateTimeUtils.nowIsoUtc()
        val trimmed = text.trim()
        val remaining = current.studentPrivateNotes.filterNot { it.studentId == studentId }
        val updated = if (trimmed.isEmpty()) {
            remaining
        } else {
            remaining + StudentPrivateNote(
                studentId = studentId,
                text = trimmed,
                createdAt = now,
                updatedAt = now
            )
        }
        current.copy(studentPrivateNotes = updated)
    }

    // =========================================================================
    // 8.4 TIMETABLE AND CALENDAR
    // =========================================================================

    fun findTimetableOverlaps(
        schoolYearId: String,
        day: Int,
        startTime: String,
        endTime: String,
        ignoreEntryId: String? = null
    ): List<TimetableEntry> {
        val sA = DateTimeUtils.timeToMinutes(startTime) ?: return emptyList()
        val eA = DateTimeUtils.timeToMinutes(endTime) ?: return emptyList()
        return _snapshot.value.timetableEntries.filter { entry ->
            entry.schoolYearId == schoolYearId &&
                !entry.isArchived &&
                entry.day == day &&
                entry.id != ignoreEntryId &&
                run {
                    val sB = DateTimeUtils.timeToMinutes(entry.startTime) ?: return@run false
                    val eB = DateTimeUtils.timeToMinutes(entry.endTime) ?: return@run false
                    sA < eB && sB < eA
                }
        }
    }

    fun saveTimetableEntry(
        existingId: String?,
        classId: String,
        day: Int,
        startTime: String,
        endTime: String,
        period: String,
        allowOverlap: Boolean = false
    ): Result<DatabaseSnapshot> {
        if (day !in 0..4) {
            return failValidation("يرجى اختيار يوم دراسي صالح (من الأحد إلى الخميس).")
        }
        if (!DateTimeUtils.isValidTime(startTime) || !DateTimeUtils.isValidTime(endTime)) {
            return failValidation("يرجى إدخال وقت البداية والنهاية بصيغة صحيحة (HH:mm).")
        }
        if (!DateTimeUtils.isEndAfterStart(startTime, endTime)) {
            return failValidation("وقت نهاية الحصة يجب أن يكون بعد وقت البداية.")
        }
        val snap = _snapshot.value
        val cls = snap.classes.find { it.id == classId && !it.isArchived }
            ?: return failValidation("يرجى اختيار قسم نشط وموجود في السنة الدراسية الحالية.")

        val overlaps = findTimetableOverlaps(cls.schoolYearId, day, startTime, endTime, existingId)
        if (overlaps.isNotEmpty() && !allowOverlap) {
            val conflict = overlaps.first()
            val conflictClass = snap.classes.find { it.id == conflict.classId }?.fullTitle ?: "قسم آخر"
            return failValidation("يوجد تداخل زمني مع حصة $conflictClass (${conflict.startTime} - ${conflict.endTime}). اضغطي «حفظ رغم التداخل» للتأكيد.")
        }

        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val entry = TimetableEntry(
                    id = newStableId(),
                    schoolYearId = cls.schoolYearId,
                    classId = classId,
                    day = day,
                    startTime = startTime,
                    endTime = endTime,
                    period = period,
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(timetableEntries = current.timetableEntries + entry)
            } else {
                current.copy(
                    timetableEntries = current.timetableEntries.map {
                        if (it.id == existingId) {
                            it.copy(
                                classId = classId,
                                day = day,
                                startTime = startTime,
                                endTime = endTime,
                                period = period,
                                updatedAt = now
                            )
                        } else it
                    }
                )
            }
        }
    }

    fun archiveOrDeleteTimetableEntry(entryId: String): Result<DatabaseSnapshot> {
        val deps = getTimetableDependents(entryId)
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (deps.canDeletePermanently) {
                current.copy(timetableEntries = current.timetableEntries.filterNot { it.id == entryId })
            } else {
                current.copy(
                    timetableEntries = current.timetableEntries.map {
                        if (it.id == entryId) it.copy(isArchived = true, updatedAt = now) else it
                    }
                )
            }
        }
    }

    fun saveCalendarEvent(
        existingId: String?,
        kind: String,
        season: String?,
        title: String,
        startDate: String,
        endDate: String,
        notes: String,
        schoolYearId: String = currentViewedYearId()
    ): Result<DatabaseSnapshot> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            return failValidation("يرجى إدخال عنوان العطلة أو المناسبة.")
        }
        if (!DateTimeUtils.isValidDate(startDate) || !DateTimeUtils.isValidDate(endDate)) {
            return failValidation("يرجى إدخال تاريخ بداية ونهاية صالحين.")
        }
        if (endDate < startDate) {
            return failValidation("تاريخ النهاية يجب أن يكون مساويًا لتاريخ البداية أو بعده.")
        }
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val ev = CalendarEvent(
                    id = newStableId(),
                    schoolYearId = schoolYearId,
                    kind = kind,
                    season = if (kind == "break") season else null,
                    title = trimmedTitle,
                    startDate = startDate,
                    endDate = endDate,
                    notes = notes.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(calendarEvents = current.calendarEvents + ev)
            } else {
                current.copy(
                    calendarEvents = current.calendarEvents.map {
                        if (it.id == existingId) {
                            it.copy(
                                kind = kind,
                                season = if (kind == "break") season else null,
                                title = trimmedTitle,
                                startDate = startDate,
                                endDate = endDate,
                                notes = notes.trim(),
                                updatedAt = now
                            )
                        } else it
                    }
                )
            }
        }
    }

    fun deleteCalendarEvent(id: String): Result<DatabaseSnapshot> = runSave { current ->
        current.copy(calendarEvents = current.calendarEvents.filterNot { it.id == id })
    }

    fun saveAssessment(
        existingId: String?,
        kind: String,
        title: String,
        classIds: List<String>,
        date: String,
        correctionDate: String,
        notes: String,
        schoolYearId: String = currentViewedYearId()
    ): Result<DatabaseSnapshot> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            return failValidation("يرجى إدخال عنوان الفرض أو الاختبار.")
        }
        if (classIds.isEmpty()) {
            return failValidation("يرجى اختيار قسم واحد على الأقل.")
        }
        if (!DateTimeUtils.isValidDate(date) || !DateTimeUtils.isValidDate(correctionDate)) {
            return failValidation("يرجى التحقق من صحة تاريخ الإجراء وتاريخ التصحيح.")
        }
        if (correctionDate < date) {
            return failValidation("تاريخ التصحيح يجب أن يكون في نفس يوم الإجراء أو بعده.")
        }
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val item = AssessmentEvent(
                    id = newStableId(),
                    schoolYearId = schoolYearId,
                    kind = kind,
                    title = trimmedTitle,
                    classIds = classIds.distinct(),
                    date = date,
                    correctionDate = correctionDate,
                    notes = notes.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(assessments = current.assessments + item)
            } else {
                current.copy(
                    assessments = current.assessments.map {
                        if (it.id == existingId) {
                            it.copy(
                                kind = kind,
                                title = trimmedTitle,
                                classIds = classIds.distinct(),
                                date = date,
                                correctionDate = correctionDate,
                                notes = notes.trim(),
                                updatedAt = now
                            )
                        } else it
                    }
                )
            }
        }
    }

    fun deleteAssessment(id: String): Result<DatabaseSnapshot> = runSave { current ->
        current.copy(assessments = current.assessments.filterNot { it.id == id })
    }

    // =========================================================================
    // 8.5 DAILY PLANNER & D20 LESSON PLANS + 8.5 QUICK OBSERVATION (D21)
    // =========================================================================

    /**
     * D20: Opens existing plan for [timetableEntryId, date] or creates a draft if none exists.
     */
    fun openOrCreateLessonFromSlot(timetableEntryId: String, date: String): Result<LessonPlan> {
        val snap = _snapshot.value
        val existing = snap.lessonPlans.find { it.timetableEntryId == timetableEntryId && it.date == date }
        if (existing != null) {
            return Result.success(existing)
        }
        val slot = snap.timetableEntries.find { it.id == timetableEntryId }
            ?: return Result.failure(IllegalArgumentException("حصة التوقيت غير موجودة."))
        var createdPlan: LessonPlan? = null
        val saveRes = runSave { current ->
            val already = current.lessonPlans.find { it.timetableEntryId == timetableEntryId && it.date == date }
            if (already != null) {
                createdPlan = already
                current
            } else {
                val now = DateTimeUtils.nowIsoUtc()
                val draft = LessonPlan(
                    id = newStableId(),
                    schoolYearId = slot.schoolYearId,
                    classId = slot.classId,
                    timetableEntryId = slot.id,
                    date = date,
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    status = ArStrings.STATUS_DRAFT,
                    isSample = false,
                    createdAt = now,
                    updatedAt = now
                )
                createdPlan = draft
                current.copy(lessonPlans = current.lessonPlans + draft)
            }
        }
        return saveRes.map { createdPlan!! }
    }

    fun saveLessonPlan(plan: LessonPlan): Result<LessonPlan> {
        if (!DateTimeUtils.isValidDate(plan.date)) {
            return Result.failure(IllegalArgumentException("تاريخ الحصة غير صالح."))
        }
        if (!DateTimeUtils.isEndAfterStart(plan.startTime, plan.endTime)) {
            return Result.failure(IllegalArgumentException("وقت نهاية الحصة يجب أن يكون بعد وقت البداية."))
        }
        if ((plan.status == ArStrings.STATUS_PREPARED || plan.status == ArStrings.STATUS_COMPLETED) &&
            plan.title.trim().isEmpty()
        ) {
            return Result.failure(IllegalArgumentException("يرجى إدخال «عنوان الدرس» قبل تغيير الحالة إلى «${plan.status}»."))
        }
        if (plan.status == ArStrings.STATUS_POSTPONED) {
            val reason = plan.postponement?.reason?.trim().orEmpty()
            if (reason.isEmpty()) {
                return Result.failure(IllegalArgumentException("يرجى كتابة «سبب التأجيل» عند اختيار حالة «أُجّل»."))
            }
        }

        var savedPlan: LessonPlan = plan
        val res = runSave { current ->
            val cls = current.classes.find { it.id == plan.classId }
                ?: throw IllegalArgumentException("القسم المحدد غير موجود.")
            if (plan.timetableEntryId != null) {
                val duplicate = current.lessonPlans.any {
                    it.id != plan.id &&
                        it.timetableEntryId == plan.timetableEntryId &&
                        it.date == plan.date
                }
                if (duplicate) {
                    throw IllegalArgumentException("يوجد تحضير محفوظ بالفعل لنفس حصة التوقيت والتاريخ.")
                }
            }
            val now = DateTimeUtils.nowIsoUtc()
            val cleaned = plan.copy(
                schoolYearId = cls.schoolYearId,
                title = plan.title.trim(),
                learningSegment = plan.learningSegment.trim(),
                activityName = plan.activityName.trim(),
                targetCompetence = plan.targetCompetence.trim(),
                procedure = plan.procedure.trim(),
                materials = plan.materials.trim(),
                evaluation = plan.evaluation.trim(),
                homework = plan.homework.trim(),
                notes = plan.notes.trim(),
                postponement = if (plan.status == ArStrings.STATUS_POSTPONED) plan.postponement else null,
                updatedAt = now
            )
            savedPlan = cleaned
            val exists = current.lessonPlans.any { it.id == cleaned.id }
            val nextList = if (exists) {
                current.lessonPlans.map { if (it.id == cleaned.id) cleaned else it }
            } else {
                current.lessonPlans + cleaned
            }
            current.copy(lessonPlans = nextList)
        }
        return res.map { savedPlan }
    }

    /**
     * D20: «نسخ التحضير» creates a new record with a new ID, timetableEntryId = null,
     * copiedFromId set, and editable date/class/time.
     */
    fun copyLessonPlan(
        sourcePlanId: String,
        targetClassId: String,
        targetDate: String,
        targetStartTime: String,
        targetEndTime: String
    ): Result<LessonPlan> {
        if (!DateTimeUtils.isValidDate(targetDate)) {
            return Result.failure(IllegalArgumentException("التاريخ المختار للنسخة غير صالح."))
        }
        if (!DateTimeUtils.isEndAfterStart(targetStartTime, targetEndTime)) {
            return Result.failure(IllegalArgumentException("وقت النهاية يجب أن يكون بعد وقت البداية."))
        }
        var createdCopy: LessonPlan? = null
        val res = runSave { current ->
            val src = current.lessonPlans.find { it.id == sourcePlanId }
                ?: throw IllegalArgumentException("التحضير الأصلي غير موجود.")
            val targetClass = current.classes.find { it.id == targetClassId }
                ?: throw IllegalArgumentException("القسم المختار للنسخ غير موجود.")
            val now = DateTimeUtils.nowIsoUtc()
            val copy = src.copy(
                id = newStableId(),
                schoolYearId = targetClass.schoolYearId,
                classId = targetClass.id,
                timetableEntryId = null,
                date = targetDate,
                startTime = targetStartTime,
                endTime = targetEndTime,
                status = if (src.title.isNotBlank()) ArStrings.STATUS_PREPARED else ArStrings.STATUS_DRAFT,
                postponement = null,
                copiedFromId = src.id,
                isSample = false,
                createdAt = now,
                updatedAt = now
            )
            createdCopy = copy
            current.copy(lessonPlans = current.lessonPlans + copy)
        }
        return res.map { createdCopy!! }
    }

    fun deleteLessonPlan(lessonPlanId: String): Result<DatabaseSnapshot> = runSave { current ->
        current.copy(
            lessonPlans = current.lessonPlans.filterNot { it.id == lessonPlanId },
            lessonObservations = current.lessonObservations.filterNot { it.lessonPlanId == lessonPlanId }
        )
    }

    // Activity Templates (Section 8.5)
    fun saveActivityTemplate(existingId: String?, name: String): Result<DatabaseSnapshot> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return failValidation("اسم النشاط لا يمكن أن يكون فارغًا.")
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val maxOrder = current.activityTemplates.maxOfOrNull { it.orderIndex } ?: 0
                val tpl = ActivityTemplate(
                    id = newStableId(),
                    name = trimmed,
                    orderIndex = maxOrder + 1,
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(activityTemplates = current.activityTemplates + tpl)
            } else {
                // Old lesson plans keep their activityName snapshot!
                current.copy(
                    activityTemplates = current.activityTemplates.map {
                        if (it.id == existingId) it.copy(name = trimmed, updatedAt = now) else it
                    }
                )
            }
        }
    }

    fun moveActivityTemplate(templateId: String, moveUp: Boolean): Result<DatabaseSnapshot> = runSave { current ->
        val sorted = current.activityTemplates.sortedBy { it.orderIndex }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == templateId }
        if (idx == -1) return@runSave current
        val swapIdx = if (moveUp) idx - 1 else idx + 1
        if (swapIdx !in sorted.indices) return@runSave current
        val tmp = sorted[idx]
        sorted[idx] = sorted[swapIdx]
        sorted[swapIdx] = tmp
        val now = DateTimeUtils.nowIsoUtc()
        val reindexed = sorted.mapIndexed { i, item -> item.copy(orderIndex = i + 1, updatedAt = now) }
        current.copy(activityTemplates = reindexed)
    }

    fun deleteActivityTemplate(templateId: String): Result<DatabaseSnapshot> = runSave { current ->
        current.copy(
            activityTemplates = current.activityTemplates.filterNot { it.id == templateId },
            lessonPlans = current.lessonPlans.map {
                if (it.activityId == templateId) it.copy(activityId = null) else it
            }
        )
    }

    /**
     * D21 Quick observation:
     * Attendance (present/absent/null), participation boolean (disabled while absent),
     * dated short note (<= 300 chars). Unique [lessonPlanId, studentId].
     * R6: NEVER touches gradebooks or gradeRecords!
     */
    fun saveLessonObservation(
        lessonPlanId: String,
        studentId: String,
        attendance: String?, // "present" | "absent" | null
        participated: Boolean,
        note: String
    ): Result<DatabaseSnapshot> {
        if (note.length > 300) {
            return failValidation("الملاحظة القصيرة لا تتجاوز 300 حرف.")
        }
        return runSave { current ->
            val plan = current.lessonPlans.find { it.id == lessonPlanId }
                ?: throw IllegalArgumentException("الحصة غير موجودة.")
            val student = current.students.find { it.id == studentId && it.classId == plan.classId }
                ?: throw IllegalArgumentException("التلميذ لا ينتمي إلى قسم هذه الحصة.")
            val now = DateTimeUtils.nowIsoUtc()
            val effectiveParticipated = if (attendance == "absent") false else participated
            val trimmedNote = note.trim().take(300)
            val existing = current.lessonObservations.find {
                it.lessonPlanId == lessonPlanId && it.studentId == studentId
            }
            val updatedObs = if (existing == null) {
                val created = LessonObservation(
                    id = newStableId(),
                    schoolYearId = plan.schoolYearId,
                    lessonPlanId = lessonPlanId,
                    classId = plan.classId,
                    studentId = studentId,
                    attendance = attendance,
                    participated = effectiveParticipated,
                    note = trimmedNote,
                    noteAt = if (trimmedNote.isNotEmpty()) DateTimeUtils.todayDateString() else null,
                    observedAt = now,
                    isSample = false,
                    createdAt = now,
                    updatedAt = now
                )
                current.lessonObservations + created
            } else {
                current.lessonObservations.map {
                    if (it.id == existing.id) {
                        it.copy(
                            attendance = attendance,
                            participated = effectiveParticipated,
                            note = trimmedNote,
                            noteAt = if (trimmedNote.isNotEmpty()) (existing.noteAt ?: DateTimeUtils.todayDateString()) else null,
                            observedAt = now,
                            updatedAt = now
                        )
                    } else it
                }
            }
            current.copy(lessonObservations = updatedObs)
        }
    }

    // =========================================================================
    // 8.6 GRADEBOOK & GRADE CONFIGS (D8, D9, D10)
    // =========================================================================

    fun getOrCreateTermConfig(schoolYearId: String, termId: String): GradeTermConfig {
        val existing = _snapshot.value.gradeTermConfigs.find {
            it.schoolYearId == schoolYearId && it.termId == termId
        }
        if (existing != null) return existing
        val created = GradeTermConfig(
            id = newStableId(),
            schoolYearId = schoolYearId,
            termId = termId
        )
        runSave { current ->
            val already = current.gradeTermConfigs.find { it.schoolYearId == schoolYearId && it.termId == termId }
            if (already != null) current else current.copy(gradeTermConfigs = current.gradeTermConfigs + created)
        }
        return _snapshot.value.gradeTermConfigs.find {
            it.schoolYearId == schoolYearId && it.termId == termId
        } ?: created
    }

    /**
     * Lazily and safely ensures a gradebook and one GradeRecord per student in the class (active or archived)
     * exist for (classId, termId).
     */
    fun ensureGradebookForClassTerm(classId: String, termId: String): Result<Gradebook> {
        var resultGb: Gradebook? = null
        val res = runSave { current ->
            val cls = current.classes.find { it.id == classId }
                ?: throw IllegalArgumentException("القسم غير موجود.")
            val now = DateTimeUtils.nowIsoUtc()

            var configs = current.gradeTermConfigs
            var config = configs.find { it.schoolYearId == cls.schoolYearId && it.termId == termId }
            if (config == null) {
                config = GradeTermConfig(
                    id = newStableId(),
                    schoolYearId = cls.schoolYearId,
                    termId = termId,
                    createdAt = now,
                    updatedAt = now
                )
                configs = configs + config
            }

            var gradebooks = current.gradebooks
            var gb = gradebooks.find {
                it.schoolYearId == cls.schoolYearId && it.classId == classId && it.termId == termId
            }
            if (gb == null) {
                gb = Gradebook(
                    id = newStableId(),
                    schoolYearId = cls.schoolYearId,
                    classId = classId,
                    termId = termId,
                    isSample = cls.isSample,
                    createdAt = now,
                    updatedAt = now
                )
                gradebooks = gradebooks + gb
            }
            resultGb = gb

            val classStudents = current.students.filter { it.classId == classId }
            val existingStudentIds = current.gradeRecords
                .filter { it.gradebookId == gb.id }
                .map { it.studentId }
                .toSet()

            val missingRows = classStudents.filter { it.id !in existingStudentIds }.map { st ->
                GradeRecord(
                    id = newStableId(),
                    gradebookId = gb.id,
                    schoolYearId = cls.schoolYearId,
                    classId = classId,
                    studentId = st.id,
                    termId = termId,
                    orderIndex = st.orderIndex,
                    isSample = st.isSample,
                    createdAt = now,
                    updatedAt = now
                )
            }

            current.copy(
                gradeTermConfigs = configs,
                gradebooks = gradebooks,
                gradeRecords = current.gradeRecords + missingRows
            )
        }
        return res.map { resultGb!! }
    }

    fun saveGradeRecord(updatedRecord: GradeRecord): Result<DatabaseSnapshot> {
        val scores = listOfNotNull(
            updatedRecord.ca1,
            updatedRecord.ca2,
            updatedRecord.as1,
            updatedRecord.as2,
            updatedRecord.exam
        )
        if (scores.any { !it.isFinite() || it < 0.0 || it > 20.0 }) {
            return failValidation("جميع النقاط يجب أن تكون بين 0 و 20.")
        }
        val abs = updatedRecord.absences
        if (abs != null && abs !in 0..999) {
            return failValidation("عدد الغيابات يجب أن يكون عددًا صحيحًا بين 0 و 999.")
        }

        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            val config = current.gradeTermConfigs.find {
                it.schoolYearId == updatedRecord.schoolYearId && it.termId == updatedRecord.termId
            } ?: GradeTermConfig(schoolYearId = updatedRecord.schoolYearId, termId = updatedRecord.termId)

            val trimmed = updatedRecord.copy(
                behaviour = updatedRecord.behaviour.trim().take(30),
                materials = updatedRecord.materials.trim().take(30),
                notebook = updatedRecord.notebook.trim().take(30),
                remarks = updatedRecord.remarks.trim().take(200),
                updatedAt = now
            )
            val eval = GradingEngine.evaluate(trimmed, config)
            val withCache = trimmed.copy(finalAverage = eval.finalAverage)

            val exists = current.gradeRecords.any { it.id == withCache.id }
            val nextRecords = if (exists) {
                current.gradeRecords.map { if (it.id == withCache.id) withCache else it }
            } else {
                current.gradeRecords + withCache
            }
            current.copy(gradeRecords = nextRecords)
        }
    }

    /**
     * D8: Valid config changes recalculate all affected finalAverage caches in one transaction
     * and never alter stored raw scores.
     */
    fun saveGradeTermConfig(config: GradeTermConfig): Result<DatabaseSnapshot> {
        GradingEngine.validateWeights(config.weights)?.let { err ->
            return failValidation(err)
        }
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            val updatedCfg = config.copy(updatedAt = now)
            val exists = current.gradeTermConfigs.any {
                it.schoolYearId == updatedCfg.schoolYearId && it.termId == updatedCfg.termId
            }
            val nextConfigs = if (exists) {
                current.gradeTermConfigs.map {
                    if (it.schoolYearId == updatedCfg.schoolYearId && it.termId == updatedCfg.termId) {
                        updatedCfg.copy(id = it.id)
                    } else it
                }
            } else {
                current.gradeTermConfigs + updatedCfg
            }

            // Recalculate all gradeRecords in that schoolYear + term without altering raw scores
            val nextRecords = current.gradeRecords.map { rec ->
                if (rec.schoolYearId == updatedCfg.schoolYearId && rec.termId == updatedCfg.termId) {
                    val eval = GradingEngine.evaluate(rec, updatedCfg)
                    rec.copy(finalAverage = eval.finalAverage, updatedAt = now)
                } else {
                    rec
                }
            }

            current.copy(
                gradeTermConfigs = nextConfigs,
                gradeRecords = nextRecords
            )
        }
    }

    // =========================================================================
    // 8.7 TRAINING, SEMINARS, EDU CALENDAR & 8.8 PROFILE
    // =========================================================================

    fun saveTrainingCategory(existingId: String?, name: String): Result<DatabaseSnapshot> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return failValidation("اسم فئة التكوين لا يمكن أن يكون فارغًا.")
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val maxOrder = current.trainingCategories.maxOfOrNull { it.orderIndex } ?: 0
                val cat = TrainingCategory(
                    id = newStableId(),
                    name = trimmed,
                    orderIndex = maxOrder + 1,
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(trainingCategories = current.trainingCategories + cat)
            } else {
                current.copy(
                    trainingCategories = current.trainingCategories.map {
                        if (it.id == existingId) it.copy(name = trimmed, updatedAt = now) else it
                    }
                )
            }
        }
    }

    fun moveTrainingCategory(categoryId: String, moveUp: Boolean): Result<DatabaseSnapshot> = runSave { current ->
        val sorted = current.trainingCategories.sortedBy { it.orderIndex }.toMutableList()
        val idx = sorted.indexOfFirst { it.id == categoryId }
        if (idx == -1) return@runSave current
        val swapIdx = if (moveUp) idx - 1 else idx + 1
        if (swapIdx !in sorted.indices) return@runSave current
        val tmp = sorted[idx]
        sorted[idx] = sorted[swapIdx]
        sorted[swapIdx] = tmp
        val now = DateTimeUtils.nowIsoUtc()
        val reindexed = sorted.mapIndexed { i, item -> item.copy(orderIndex = i + 1, updatedAt = now) }
        current.copy(trainingCategories = reindexed)
    }

    fun deleteTrainingCategory(categoryId: String): Result<DatabaseSnapshot> = runSave { current ->
        current.copy(
            trainingCategories = current.trainingCategories.filterNot { it.id == categoryId },
            trainingNotes = current.trainingNotes.map {
                if (it.categoryId == categoryId) it.copy(categoryId = null) else it
            }
        )
    }

    fun saveTrainingNote(
        existingId: String?,
        title: String,
        date: String,
        text: String,
        categoryId: String?,
        categoryName: String,
        schoolYearId: String = currentViewedYearId()
    ): Result<DatabaseSnapshot> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return failValidation("يرجى إدخال عنوان ملاحظة التكوين.")
        if (!DateTimeUtils.isValidDate(date)) return failValidation("تاريخ ملاحظة التكوين غير صالح.")
        if (categoryName.trim().isEmpty()) return failValidation("يرجى اختيار فئة التكوين.")
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val note = TrainingNote(
                    id = newStableId(),
                    schoolYearId = schoolYearId,
                    title = trimmedTitle,
                    date = date,
                    text = text.trim(),
                    categoryId = categoryId,
                    categoryName = categoryName.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(trainingNotes = current.trainingNotes + note)
            } else {
                current.copy(
                    trainingNotes = current.trainingNotes.map {
                        if (it.id == existingId) {
                            it.copy(
                                title = trimmedTitle,
                                date = date,
                                text = text.trim(),
                                categoryId = categoryId,
                                categoryName = categoryName.trim(),
                                updatedAt = now
                            )
                        } else it
                    }
                )
            }
        }
    }

    fun deleteTrainingNote(id: String): Result<DatabaseSnapshot> = runSave { current ->
        current.copy(trainingNotes = current.trainingNotes.filterNot { it.id == id })
    }

    fun saveSeminar(
        existingId: String?,
        kind: String,
        date: String,
        title: String,
        location: String,
        facilitator: String,
        mainIdeas: String,
        recommendations: String,
        schoolYearId: String = currentViewedYearId()
    ): Result<DatabaseSnapshot> {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) return failValidation("يرجى إدخال عنوان الندوة.")
        if (!DateTimeUtils.isValidDate(date)) return failValidation("تاريخ الندوة غير صالح.")
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val s = SeminarRecord(
                    id = newStableId(),
                    schoolYearId = schoolYearId,
                    kind = kind,
                    date = date,
                    title = trimmedTitle,
                    location = location.trim(),
                    facilitator = facilitator.trim(),
                    mainIdeas = mainIdeas.trim(),
                    recommendations = recommendations.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(seminars = current.seminars + s)
            } else {
                current.copy(
                    seminars = current.seminars.map {
                        if (it.id == existingId) {
                            it.copy(
                                kind = kind,
                                date = date,
                                title = trimmedTitle,
                                location = location.trim(),
                                facilitator = facilitator.trim(),
                                mainIdeas = mainIdeas.trim(),
                                recommendations = recommendations.trim(),
                                updatedAt = now
                            )
                        } else it
                    }
                )
            }
        }
    }

    fun deleteSeminar(id: String): Result<DatabaseSnapshot> = runSave { current ->
        current.copy(seminars = current.seminars.filterNot { it.id == id })
    }

    fun saveEduCalendarRecord(
        existingId: String?,
        type: String,
        date: String,
        appliedLesson: String,
        teacherName: String,
        level: String,
        topic: String,
        location: String,
        internshipType: String,
        schoolYearId: String = currentViewedYearId()
    ): Result<DatabaseSnapshot> {
        if (!DateTimeUtils.isValidDate(date)) return failValidation("التاريخ غير صالح.")
        when (type) {
            "pedagogicalSeminar" -> if (topic.trim().isEmpty() && appliedLesson.trim().isEmpty()) {
                return failValidation("يرجى إدخال موضوع الندوة التربوية أو الدرس التطبيقي.")
            }
            "studyDay" -> if (topic.trim().isEmpty()) {
                return failValidation("يرجى إدخال موضوع اليوم الدراسي.")
            }
            "internship" -> if (internshipType.trim().isEmpty()) {
                return failValidation("يرجى إدخال نوع التربص.")
            }
        }
        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            if (existingId == null) {
                val rec = EduCalendarRecord(
                    id = newStableId(),
                    schoolYearId = schoolYearId,
                    type = type,
                    date = date,
                    appliedLesson = appliedLesson.trim(),
                    teacherName = teacherName.trim(),
                    level = level.trim(),
                    topic = topic.trim(),
                    location = location.trim(),
                    internshipType = internshipType.trim(),
                    createdAt = now,
                    updatedAt = now
                )
                current.copy(eduCalendarRecords = current.eduCalendarRecords + rec)
            } else {
                current.copy(
                    eduCalendarRecords = current.eduCalendarRecords.map {
                        if (it.id == existingId) {
                            it.copy(
                                type = type,
                                date = date,
                                appliedLesson = appliedLesson.trim(),
                                teacherName = teacherName.trim(),
                                level = level.trim(),
                                topic = topic.trim(),
                                location = location.trim(),
                                internshipType = internshipType.trim(),
                                updatedAt = now
                            )
                        } else it
                    }
                )
            }
        }
    }

    fun deleteEduCalendarRecord(id: String): Result<DatabaseSnapshot> = runSave { current ->
        current.copy(eduCalendarRecords = current.eduCalendarRecords.filterNot { it.id == id })
    }

    fun saveProfile(profile: TeacherProfile): Result<DatabaseSnapshot> = runSave { current ->
        val now = DateTimeUtils.nowIsoUtc()
        current.copy(
            profile = profile.copy(
                id = "main",
                professionalName = profile.professionalName.trim(),
                institution = profile.institution.trim(),
                employmentStatus = profile.employmentStatus.trim(),
                specialization = profile.specialization.trim(),
                qualifications = profile.qualifications.trim(),
                appointmentDate = profile.appointmentDate.trim(),
                rank = profile.rank.trim(),
                inspectionHistory = profile.inspectionHistory.trim(),
                promotionDetails = profile.promotionDetails.trim(),
                optionalIdentifier = profile.optionalIdentifier.trim(),
                updatedAt = now
            )
        )
    }

    // =========================================================================
    // 8.11 SCHOOL YEARS (D17)
    // =========================================================================

    fun archiveAndStartNewYear(
        newYearLabel: String,
        copySelections: List<ClassCopySelection>
    ): Result<DatabaseSnapshot> {
        val labelNormalized = ArabicUtils.toWesternDigits(newYearLabel.trim())
        DateTimeUtils.validateSchoolYearLabel(labelNormalized)?.let {
            return failValidation(it)
        }
        val snap = _snapshot.value
        if (snap.schoolYears.any { it.label == labelNormalized }) {
            return failValidation("توجد سنة دراسية مسجلة بنفس التسمية ($labelNormalized).")
        }

        return runSave { current ->
            val now = DateTimeUtils.nowIsoUtc()
            val newYearId = newStableId()
            val newYear = SchoolYear(
                id = newYearId,
                label = labelNormalized,
                status = "active",
                createdAt = now,
                updatedAt = now
            )
            val updatedYears = current.schoolYears.map { y ->
                if (y.status == "active") y.copy(status = "archived", archivedAt = now, updatedAt = now) else y
            } + newYear

            val newClasses = mutableListOf<SchoolClass>()
            val newStudents = mutableListOf<Student>()

            // D17: Never copies grades, observations, lessons, timetable, or sample records
            for (sel in copySelections) {
                val srcClass = current.classes.find { it.id == sel.sourceClassId && !it.isSample } ?: continue
                val newClassId = newStableId()
                newClasses.add(
                    SchoolClass(
                        id = newClassId,
                        schoolYearId = newYearId,
                        level = sel.newLevel.ifBlank { srcClass.level },
                        name = sel.newName.trim().ifBlank { srcClass.name },
                        isArchived = false,
                        isSample = false,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                if (sel.copyStudents) {
                    val activeRealStudents = current.students
                        .filter { it.classId == srcClass.id && !it.isArchived && !it.isSample }
                        .sortedBy { it.orderIndex }
                    activeRealStudents.forEachIndexed { idx, st ->
                        newStudents.add(
                            Student(
                                id = newStableId(),
                                schoolYearId = newYearId,
                                classId = newClassId,
                                fullName = st.fullName,
                                orderIndex = idx + 1,
                                isArchived = false,
                                isSample = false,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                }
            }

            val defaultConfigs = listOf("T1", "T2", "T3").map { term ->
                GradeTermConfig(
                    id = newStableId(),
                    schoolYearId = newYearId,
                    termId = term,
                    createdAt = now,
                    updatedAt = now
                )
            }

            _viewedSchoolYearId.value = newYearId
            current.copy(
                meta = current.meta.copy(activeSchoolYearId = newYearId),
                schoolYears = updatedYears,
                classes = current.classes + newClasses,
                students = current.students + newStudents,
                gradeTermConfigs = current.gradeTermConfigs + defaultConfigs
            )
        }
    }

    fun activateSchoolYear(targetYearId: String): Result<DatabaseSnapshot> = runSave { current ->
        if (current.schoolYears.none { it.id == targetYearId }) {
            throw IllegalArgumentException("السنة الدراسية المحددة غير موجودة.")
        }
        val now = DateTimeUtils.nowIsoUtc()
        val updatedYears = current.schoolYears.map { y ->
            if (y.id == targetYearId) {
                y.copy(status = "active", archivedAt = null, updatedAt = now)
            } else if (y.status == "active") {
                y.copy(status = "archived", archivedAt = now, updatedAt = now)
            } else {
                y
            }
        }
        _viewedSchoolYearId.value = targetYearId
        current.copy(
            meta = current.meta.copy(activeSchoolYearId = targetYearId),
            schoolYears = updatedYears
        )
    }

    // =========================================================================
    // 8.10 BACKUP, RESTORE, REMINDER (D15, D16) & RESET
    // =========================================================================

    fun markBackupExportSucceeded(exportedIso: String = DateTimeUtils.nowIsoUtc()): Result<DatabaseSnapshot> =
        runSave { current ->
            current.copy(
                meta = current.meta.copy(
                    lastBackupExportAt = exportedIso.take(10),
                    backupReminderSnoozedUntil = null
                )
            )
        }

    /**
     * D15 Backup Reminder:
     * - "Data worth backing up" = at least one class, lesson plan, grade record, or training/seminar record.
     * - Reference date = lastBackupExportAt, or firstDataAt if never exported.
     * - If more than 14 days passed, show dismissible reminder on «نهاري»; dismissal snoozes 3 days.
     */
    fun shouldShowBackupReminder(todayDate: String = DateTimeUtils.todayDateString()): Boolean {
        val snap = _snapshot.value
        if (!snap.hasDataWorthBackingUp()) return false
        val snoozedUntil = snap.meta.backupReminderSnoozedUntil
        if (snoozedUntil != null && todayDate <= snoozedUntil) return false
        val refDate = snap.meta.lastBackupExportAt?.take(10) ?: snap.meta.firstDataAt?.take(10) ?: return false
        val daysPassed = DateTimeUtils.daysBetween(refDate, todayDate)
        return daysPassed > 14
    }

    fun snoozeBackupReminder(todayDate: String = DateTimeUtils.todayDateString()): Result<DatabaseSnapshot> =
        runSave { current ->
            val until = DateTimeUtils.addDays(todayDate, 3)
            current.copy(meta = current.meta.copy(backupReminderSnoozedUntil = until))
        }

    /**
     * D16 Restore (replace only):
     * Validates in memory, then commits in one atomic transaction.
     * Any error aborts and leaves existing data intact.
     */
    fun restoreFromValidatedBackup(rawJson: String): Result<DatabaseSnapshot> {
        val validation = BackupSerializer.validateAndParseBackup(rawJson)
        if (validation is BackupSerializer.RestoreValidationResult.Invalid) {
            return failValidation(validation.errorAr)
        }
        val valid = validation as BackupSerializer.RestoreValidationResult.Valid
        return runSave { _ ->
            val exportedDatePart = valid.exportedAt.take(10)
            val restoredSnapshot = valid.snapshot.copy(
                meta = valid.snapshot.meta.copy(
                    lastBackupExportAt = exportedDatePart,
                    backupReminderSnoozedUntil = null
                )
            )
            // Verify integrity before commit
            val integrity = StoreRegistry.verifyIntegrity(restoredSnapshot)
            if (!integrity.isClean) {
                throw IllegalArgumentException(integrity.errors.first())
            }
            updateLocalSettings { valid.localSettings }
            _viewedSchoolYearId.value = restoredSnapshot.meta.activeSchoolYearId
            restoredSnapshot
        }
    }

    fun resetAllData(): Result<DatabaseSnapshot> = runSave { _ ->
        val fresh = createInitialSnapshot()
        _viewedSchoolYearId.value = ""
        fresh
    }

    // =========================================================================
    // 8.9 GLOBAL SEARCH (D14)
    // =========================================================================

    /**
     * D14 Search:
     * - Arabic-aware normalization on query and indexed text
     * - Type labels: تحضير، تلميذ، قسم، تكوين، ندوة، رزنامة، رزنامة تربوية، توقيت
     * - Scope: active (or currently viewed) year, non-archived;
     *   when includeArchived == true, includes archived years and archived records, marked «مؤرشف»
     * - NEVER searches studentPrivateNotes!
     */
    fun searchGlobal(
        rawQuery: String,
        includeArchived: Boolean
    ): Map<String, List<SearchResultItem>> {
        val normQuery = ArabicUtils.normalizeArabic(rawQuery)
        if (normQuery.isEmpty()) return emptyMap()

        val snap = _snapshot.value
        val viewedYearId = currentViewedYearId()
        val yearById = snap.schoolYears.associateBy { it.id }
        val classById = snap.classes.associateBy { it.id }

        fun matchesYearAndArchive(schoolYearId: String, recordArchived: Boolean): Pair<Boolean, Boolean> {
            val year = yearById[schoolYearId]
            val isYearArchived = year?.status == "archived" && schoolYearId != viewedYearId
            val isOverallArchived = recordArchived || (year?.status == "archived")
            val allowed = if (includeArchived) {
                true
            } else {
                schoolYearId == viewedYearId && !recordArchived
            }
            return allowed to (isOverallArchived || isYearArchived)
        }

        fun matchesText(vararg fields: String): Boolean {
            return fields.any { field ->
                ArabicUtils.normalizeArabic(field).contains(normQuery)
            }
        }

        val results = mutableListOf<SearchResultItem>()

        // 1. تحضير (Lesson Plans)
        for (lp in snap.lessonPlans) {
            val cls = classById[lp.classId]
            val (allowed, isArch) = matchesYearAndArchive(lp.schoolYearId, cls?.isArchived == true)
            if (!allowed) continue
            if (matchesText(
                    lp.title,
                    lp.activityName,
                    lp.learningSegment,
                    lp.targetCompetence,
                    lp.procedure,
                    lp.homework,
                    lp.notes,
                    lp.date
                )
            ) {
                val yrLabel = yearById[lp.schoolYearId]?.label ?: ""
                results.add(
                    SearchResultItem(
                        id = "lp_${lp.id}",
                        typeLabel = ArStrings.SEARCH_TYPE_LESSON,
                        title = lp.title.ifBlank { "حصة بدون عنوان (${lp.activityName.ifBlank { "تحضير" }})" },
                        subtitle = "${cls?.fullTitle ?: ""} • ${DateTimeUtils.formatAlgerianDate(lp.date)}",
                        schoolYearId = lp.schoolYearId,
                        schoolYearLabel = yrLabel,
                        isArchived = isArch,
                        isSample = lp.isSample || (cls?.isSample == true),
                        targetRoute = "daily",
                        targetEntityId = lp.id,
                        targetSecondaryId = lp.date
                    )
                )
            }
        }

        // 2. تلميذ (Students — NEVER private notes!)
        for (st in snap.students) {
            val cls = classById[st.classId]
            val (allowed, isArch) = matchesYearAndArchive(st.schoolYearId, st.isArchived || cls?.isArchived == true)
            if (!allowed) continue
            if (matchesText(st.fullName)) {
                val yrLabel = yearById[st.schoolYearId]?.label ?: ""
                results.add(
                    SearchResultItem(
                        id = "st_${st.id}",
                        typeLabel = ArStrings.SEARCH_TYPE_STUDENT,
                        title = st.fullName,
                        subtitle = cls?.fullTitle ?: "",
                        schoolYearId = st.schoolYearId,
                        schoolYearLabel = yrLabel,
                        isArchived = isArch,
                        isSample = st.isSample || (cls?.isSample == true),
                        targetRoute = "classes",
                        targetEntityId = st.classId,
                        targetSecondaryId = st.id
                    )
                )
            }
        }

        // 3. قسم (Classes)
        for (cls in snap.classes) {
            val (allowed, isArch) = matchesYearAndArchive(cls.schoolYearId, cls.isArchived)
            if (!allowed) continue
            if (matchesText(cls.level, cls.name, cls.fullTitle)) {
                val yrLabel = yearById[cls.schoolYearId]?.label ?: ""
                results.add(
                    SearchResultItem(
                        id = "cls_${cls.id}",
                        typeLabel = ArStrings.SEARCH_TYPE_CLASS,
                        title = cls.fullTitle,
                        subtitle = "السنة الدراسية $yrLabel",
                        schoolYearId = cls.schoolYearId,
                        schoolYearLabel = yrLabel,
                        isArchived = isArch,
                        isSample = cls.isSample,
                        targetRoute = "classes",
                        targetEntityId = cls.id
                    )
                )
            }
        }

        // 4. تكوين (Training Notes)
        for (tn in snap.trainingNotes) {
            val (allowed, isArch) = matchesYearAndArchive(tn.schoolYearId, false)
            if (!allowed) continue
            if (matchesText(tn.title, tn.text, tn.categoryName, tn.date)) {
                val yrLabel = yearById[tn.schoolYearId]?.label ?: ""
                results.add(
                    SearchResultItem(
                        id = "tn_${tn.id}",
                        typeLabel = ArStrings.SEARCH_TYPE_TRAINING,
                        title = tn.title,
                        subtitle = "${tn.categoryName} • ${DateTimeUtils.formatAlgerianDate(tn.date)}",
                        schoolYearId = tn.schoolYearId,
                        schoolYearLabel = yrLabel,
                        isArchived = isArch,
                        isSample = tn.isSample,
                        targetRoute = "training",
                        targetEntityId = tn.id,
                        targetSecondaryId = "notes"
                    )
                )
            }
        }

        // 5. ندوة (Seminars)
        for (sm in snap.seminars) {
            val (allowed, isArch) = matchesYearAndArchive(sm.schoolYearId, false)
            if (!allowed) continue
            if (matchesText(sm.title, sm.location, sm.facilitator, sm.mainIdeas, sm.recommendations, sm.date)) {
                val yrLabel = yearById[sm.schoolYearId]?.label ?: ""
                results.add(
                    SearchResultItem(
                        id = "sm_${sm.id}",
                        typeLabel = ArStrings.SEARCH_TYPE_SEMINAR,
                        title = sm.title,
                        subtitle = "${sm.kindArabic} • ${DateTimeUtils.formatAlgerianDate(sm.date)}",
                        schoolYearId = sm.schoolYearId,
                        schoolYearLabel = yrLabel,
                        isArchived = isArch,
                        isSample = sm.isSample,
                        targetRoute = "training",
                        targetEntityId = sm.id,
                        targetSecondaryId = "seminars"
                    )
                )
            }
        }

        // 6. رزنامة (Calendar Events + Assessments)
        for (ce in snap.calendarEvents) {
            val (allowed, isArch) = matchesYearAndArchive(ce.schoolYearId, false)
            if (!allowed) continue
            if (matchesText(ce.title, ce.kindArabic, ce.notes, ce.startDate, ce.endDate)) {
                val yrLabel = yearById[ce.schoolYearId]?.label ?: ""
                results.add(
                    SearchResultItem(
                        id = "ce_${ce.id}",
                        typeLabel = ArStrings.SEARCH_TYPE_CALENDAR,
                        title = ce.title,
                        subtitle = "${ce.kindArabic} • ${DateTimeUtils.formatAlgerianDate(ce.startDate)}",
                        schoolYearId = ce.schoolYearId,
                        schoolYearLabel = yrLabel,
                        isArchived = isArch,
                        isSample = ce.isSample,
                        targetRoute = "schedule",
                        targetEntityId = ce.id,
                        targetSecondaryId = "calendar"
                    )
                )
            }
        }
        for (ass in snap.assessments) {
            val (allowed, isArch) = matchesYearAndArchive(ass.schoolYearId, false)
            if (!allowed) continue
            val classTitles = ass.classIds.mapNotNull { classById[it]?.fullTitle }.joinToString("، ")
            if (matchesText(ass.title, ass.kind, ass.notes, classTitles, ass.date)) {
                val yrLabel = yearById[ass.schoolYearId]?.label ?: ""
                results.add(
                    SearchResultItem(
                        id = "ass_${ass.id}",
                        typeLabel = ArStrings.SEARCH_TYPE_CALENDAR,
                        title = "${ass.kind}: ${ass.title}",
                        subtitle = "$classTitles • ${DateTimeUtils.formatAlgerianDate(ass.date)}",
                        schoolYearId = ass.schoolYearId,
                        schoolYearLabel = yrLabel,
                        isArchived = isArch,
                        isSample = ass.isSample,
                        targetRoute = "schedule",
                        targetEntityId = ass.id,
                        targetSecondaryId = "assessments"
                    )
                )
            }
        }

        // 7. رزنامة تربوية (Educational Calendar Records)
        for (ec in snap.eduCalendarRecords) {
            val (allowed, isArch) = matchesYearAndArchive(ec.schoolYearId, false)
            if (!allowed) continue
            if (matchesText(ec.typeArabic, ec.appliedLesson, ec.teacherName, ec.level, ec.topic, ec.location, ec.internshipType, ec.date)) {
                val yrLabel = yearById[ec.schoolYearId]?.label ?: ""
                val mainTitle = ec.topic.ifBlank { ec.appliedLesson.ifBlank { ec.internshipType.ifBlank { ec.typeArabic } } }
                results.add(
                    SearchResultItem(
                        id = "ec_${ec.id}",
                        typeLabel = ArStrings.SEARCH_TYPE_EDU_CALENDAR,
                        title = "${ec.typeArabic}: $mainTitle",
                        subtitle = DateTimeUtils.formatAlgerianDate(ec.date),
                        schoolYearId = ec.schoolYearId,
                        schoolYearLabel = yrLabel,
                        isArchived = isArch,
                        isSample = ec.isSample,
                        targetRoute = "training",
                        targetEntityId = ec.id,
                        targetSecondaryId = "eduCalendar"
                    )
                )
            }
        }

        // 8. توقيت (Timetable Entries)
        for (tt in snap.timetableEntries) {
            val cls = classById[tt.classId]
            val (allowed, isArch) = matchesYearAndArchive(tt.schoolYearId, tt.isArchived || cls?.isArchived == true)
            if (!allowed) continue
            val dayName = ArStrings.SCHOOL_DAYS.getOrElse(tt.day) { "" }
            val clsTitle = cls?.fullTitle ?: ""
            if (matchesText(dayName, clsTitle, tt.startTime, tt.endTime, tt.period)) {
                val yrLabel = yearById[tt.schoolYearId]?.label ?: ""
                results.add(
                    SearchResultItem(
                        id = "tt_${tt.id}",
                        typeLabel = ArStrings.SEARCH_TYPE_TIMETABLE,
                        title = "$dayName — $clsTitle",
                        subtitle = "${DateTimeUtils.formatTimeRange(tt.startTime, tt.endTime)} (${tt.period})",
                        schoolYearId = tt.schoolYearId,
                        schoolYearLabel = yrLabel,
                        isArchived = isArch,
                        isSample = tt.isSample || (cls?.isSample == true),
                        targetRoute = "schedule",
                        targetEntityId = tt.id,
                        targetSecondaryId = "timetable"
                    )
                )
            }
        }

        return results.groupBy { it.typeLabel }
    }

    private fun failValidation(messageAr: String): Result<DatabaseSnapshot> {
        _saveIndicator.value = SaveIndicatorState(
            status = SaveStatus.FAILED,
            message = "${ArStrings.SAVE_FAILED}: $messageAr",
            errorReason = messageAr,
            updatedAtMs = System.currentTimeMillis()
        )
        return Result.failure(IllegalArgumentException(messageAr))
    }
}
