package com.example.storage

import com.example.domain.*
import com.example.i18n.ArStrings
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

data class StoreDescriptor(
    val name: String,
    val primaryKey: String,
    val isYearScoped: Boolean,
    val supportsSample: Boolean,
    val includedInBackup: Boolean = true
)

data class DatabaseSnapshot(
    val meta: AppMeta = AppMeta(),
    val schoolYears: List<SchoolYear> = emptyList(),
    val profile: TeacherProfile = TeacherProfile(),
    val classes: List<SchoolClass> = emptyList(),
    val students: List<Student> = emptyList(),
    val studentPrivateNotes: List<StudentPrivateNote> = emptyList(),
    val timetableEntries: List<TimetableEntry> = emptyList(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val assessments: List<AssessmentEvent> = emptyList(),
    val lessonPlans: List<LessonPlan> = emptyList(),
    val activityTemplates: List<ActivityTemplate> = emptyList(),
    val lessonObservations: List<LessonObservation> = emptyList(),
    val gradebooks: List<Gradebook> = emptyList(),
    val gradeRecords: List<GradeRecord> = emptyList(),
    val gradeTermConfigs: List<GradeTermConfig> = emptyList(),
    val trainingCategories: List<TrainingCategory> = emptyList(),
    val trainingNotes: List<TrainingNote> = emptyList(),
    val seminars: List<SeminarRecord> = emptyList(),
    val eduCalendarRecords: List<EduCalendarRecord> = emptyList()
) {
    fun hasDataWorthBackingUp(): Boolean {
        return classes.isNotEmpty() ||
            lessonPlans.isNotEmpty() ||
            gradeRecords.any { it.hasAnyNonEmptyValue() } ||
            trainingNotes.isNotEmpty() ||
            seminars.isNotEmpty()
    }
}

object StoreRegistry {
    const val SCHEMA_VERSION = 1
    const val FORMAT_VERSION = 1
    const val BACKUP_FORMAT = "teacher-app-backup"

    val ALL_STORES: List<StoreDescriptor> = listOf(
        StoreDescriptor("meta", "key", isYearScoped = false, supportsSample = false),
        StoreDescriptor("schoolYears", "id", isYearScoped = false, supportsSample = false),
        StoreDescriptor("profile", "id", isYearScoped = false, supportsSample = false),
        StoreDescriptor("classes", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("students", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("studentPrivateNotes", "studentId", isYearScoped = false, supportsSample = false),
        StoreDescriptor("timetableEntries", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("calendarEvents", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("assessments", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("lessonPlans", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("activityTemplates", "id", isYearScoped = false, supportsSample = false),
        StoreDescriptor("lessonObservations", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("gradebooks", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("gradeRecords", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("gradeTermConfigs", "id", isYearScoped = true, supportsSample = false),
        StoreDescriptor("trainingCategories", "id", isYearScoped = false, supportsSample = false),
        StoreDescriptor("trainingNotes", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("seminars", "id", isYearScoped = true, supportsSample = true),
        StoreDescriptor("eduCalendarRecords", "id", isYearScoped = true, supportsSample = true)
    )

    /**
     * Registry-driven integrity verifier (Section 5 & T10):
     * Checks active school year count, duplicate IDs, composite keys, broken references,
     * orphan private notes, out-of-range scores, and class consistency.
     */
    fun verifyIntegrity(snapshot: DatabaseSnapshot): IntegrityReport {
        val errors = mutableListOf<String>()

        if (snapshot.setupCompletedOrHasYears()) {
            val activeYears = snapshot.schoolYears.filter { it.status == "active" }
            if (activeYears.size != 1) {
                errors.add("يجب أن توجد سنة دراسية نشطة واحدة فقط (الحالي: ${activeYears.size}).")
            } else if (snapshot.meta.activeSchoolYearId != activeYears.first().id) {
                errors.add("معرّف السنة النشطة في meta لا يطابق السنة النشطة الفعلية.")
            }
        }

        val yearIds = snapshot.schoolYears.map { it.id }.toSet()
        if (yearIds.size != snapshot.schoolYears.size) {
            errors.add("توجد معرّفات سنوات دراسية مكررة.")
        }
        val yearLabels = snapshot.schoolYears.map { it.label }.toSet()
        if (yearLabels.size != snapshot.schoolYears.size) {
            errors.add("توجد تسميات سنوات دراسية مكررة.")
        }

        val classById = snapshot.classes.associateBy { it.id }
        if (classById.size != snapshot.classes.size) {
            errors.add("توجد معرّفات أقسام مكررة.")
        }
        snapshot.classes.forEach { c ->
            if (c.schoolYearId !in yearIds) {
                errors.add("قسم (${c.fullTitle}) مرتبط بسنة دراسية غير موجودة.")
            }
        }

        val studentById = snapshot.students.associateBy { it.id }
        if (studentById.size != snapshot.students.size) {
            errors.add("توجد معرّفات تلاميذ مكررة.")
        }
        snapshot.students.forEach { s ->
            val cls = classById[s.classId]
            if (cls == null) {
                errors.add("تلميذ (${s.fullName}) مرتبط بقسم غير موجود.")
            } else if (cls.schoolYearId != s.schoolYearId) {
                errors.add("سنة التلميذ (${s.fullName}) لا تطابق سنة قسمه.")
            }
        }

        // Private notes must reference existing students
        val noteStudentIds = mutableSetOf<String>()
        snapshot.studentPrivateNotes.forEach { n ->
            if (!noteStudentIds.add(n.studentId)) {
                errors.add("توجد ملاحظات خاصة مكررة لنفس التلميذ.")
            }
            if (n.studentId !in studentById) {
                errors.add("ملاحظة خاصة يتيمة لتلميذ غير موجود (${n.studentId}).")
            }
        }

        val timetableById = snapshot.timetableEntries.associateBy { it.id }
        if (timetableById.size != snapshot.timetableEntries.size) {
            errors.add("توجد معرّفات حصص توقيت مكررة.")
        }
        snapshot.timetableEntries.forEach { t ->
            val cls = classById[t.classId]
            if (cls == null) {
                errors.add("حصة توقيت مرتبطة بقسم غير موجود.")
            } else if (cls.schoolYearId != t.schoolYearId) {
                errors.add("سنة حصة التوقيت لا تطابق سنة القسم.")
            }
            if (!DateTimeUtils.isEndAfterStart(t.startTime, t.endTime)) {
                errors.add("حصة توقيت بوقت نهاية غير صالح (${t.startTime} - ${t.endTime}).")
            }
        }

        val lessonById = snapshot.lessonPlans.associateBy { it.id }
        if (lessonById.size != snapshot.lessonPlans.size) {
            errors.add("توجد معرّفات تحضيرات مكررة.")
        }
        val slotDatePairs = mutableSetOf<Pair<String, String>>()
        snapshot.lessonPlans.forEach { lp ->
            val cls = classById[lp.classId]
            if (cls == null) {
                errors.add("تحضير درس مرتبط بقسم غير موجود.")
            } else if (cls.schoolYearId != lp.schoolYearId) {
                errors.add("سنة تحضير الدرس لا تطابق سنة القسم.")
            }
            if (lp.timetableEntryId != null) {
                if (lp.timetableEntryId !in timetableById) {
                    errors.add("تحضير درس مرتبط بحصة توقيت غير موجودة.")
                }
                if (!slotDatePairs.add(lp.timetableEntryId to lp.date)) {
                    errors.add("تحضير مكرر لنفس حصة التوقيت والتاريخ (${lp.date}).")
                }
            }
        }

        val obsPairs = mutableSetOf<Pair<String, String>>()
        snapshot.lessonObservations.forEach { ob ->
            val lp = lessonById[ob.lessonPlanId]
            val st = studentById[ob.studentId]
            if (lp == null) {
                errors.add("ملاحظة حصة مرتبطة بتحضير غير موجود.")
            }
            if (st == null) {
                errors.add("ملاحظة حصة مرتبطة بتلميذ غير موجود.")
            }
            if (lp != null && st != null && (lp.classId != st.classId || ob.classId != st.classId)) {
                errors.add("ملاحظة حصة لتلميذ لا ينتمي إلى قسم الحصة.")
            }
            if (!obsPairs.add(ob.lessonPlanId to ob.studentId)) {
                errors.add("ملاحظة مكررة لنفس التلميذ في نفس الحصة.")
            }
        }

        val gradebookById = snapshot.gradebooks.associateBy { it.id }
        val gbKeys = mutableSetOf<Triple<String, String, String>>()
        snapshot.gradebooks.forEach { gb ->
            if (gb.classId !in classById) {
                errors.add("دفتر تنقيط مرتبط بقسم غير موجود.")
            }
            if (!gbKeys.add(Triple(gb.schoolYearId, gb.classId, gb.termId))) {
                errors.add("دفتر تنقيط مكرر لنفس القسم والفصل.")
            }
        }

        val grKeys = mutableSetOf<Pair<String, String>>()
        snapshot.gradeRecords.forEach { gr ->
            val gb = gradebookById[gr.gradebookId]
            val st = studentById[gr.studentId]
            if (gb == null) {
                errors.add("سجل نقاط مرتبط بدفتر تنقيط غير موجود.")
            }
            if (st == null) {
                errors.add("سجل نقاط مرتبط بتلميذ غير موجود.")
            }
            if (gb != null && st != null && (gb.classId != st.classId || gr.classId != st.classId)) {
                errors.add("سجل نقاط لتلميذ لا ينتمي إلى قسم دفتر التنقيط.")
            }
            if (!grKeys.add(gr.gradebookId to gr.studentId)) {
                errors.add("سجل نقاط مكرر لنفس التلميذ في نفس الفصل.")
            }
            val scores = listOfNotNull(gr.ca1, gr.ca2, gr.as1, gr.as2, gr.exam)
            if (scores.any { !it.isFinite() || it < 0.0 || it > 20.0 }) {
                errors.add("توجد نقطة خارج المجال المسموح (0 إلى 20).")
            }
        }

        snapshot.calendarEvents.forEach { ce ->
            if (ce.schoolYearId !in yearIds) {
                errors.add("حدث رزنامة مرتبط بسنة غير موجودة.")
            }
            if (ce.endDate < ce.startDate) {
                errors.add("حدث رزنامة بتاريخ نهاية قبل تاريخ البداية.")
            }
        }

        snapshot.assessments.forEach { a ->
            if (a.schoolYearId !in yearIds) {
                errors.add("فرض/اختبار مرتبط بسنة غير موجودة.")
            }
            if (a.classIds.isEmpty() || a.classIds.any { it !in classById }) {
                errors.add("فرض/اختبار مرتبط بقسم غير موجود.")
            }
            if (a.correctionDate < a.date) {
                errors.add("تاريخ تصحيح الفرض/الاختبار قبل تاريخ الإجراء.")
            }
        }

        return IntegrityReport(isClean = errors.isEmpty(), errors = errors)
    }

    private fun DatabaseSnapshot.setupCompletedOrHasYears(): Boolean =
        meta.setupCompleted || schoolYears.isNotEmpty()
}

object BackupSerializer {

    sealed class RestoreValidationResult {
        data class Valid(
            val preview: BackupPreview,
            val snapshot: DatabaseSnapshot,
            val localSettings: LocalSettings,
            val exportedAt: String
        ) : RestoreValidationResult()

        data class Invalid(val errorAr: String) : RestoreValidationResult()
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    fun serializeDataObject(snapshot: DatabaseSnapshot, localSettings: LocalSettings): JSONObject {
        val data = JSONObject()

        // 1. meta
        data.put("meta", JSONObject().apply {
            put("schemaVersion", snapshot.meta.schemaVersion)
            put("activeSchoolYearId", snapshot.meta.activeSchoolYearId)
            put("setupCompleted", snapshot.meta.setupCompleted)
            put("firstDataAt", snapshot.meta.firstDataAt ?: JSONObject.NULL)
            put("lastBackupExportAt", snapshot.meta.lastBackupExportAt ?: JSONObject.NULL)
            put("backupReminderSnoozedUntil", snapshot.meta.backupReminderSnoozedUntil ?: JSONObject.NULL)
        })

        // 2. schoolYears
        data.put("schoolYears", JSONArray().apply {
            snapshot.schoolYears.forEach { y ->
                put(JSONObject().apply {
                    put("id", y.id)
                    put("label", y.label)
                    put("status", y.status)
                    put("archivedAt", y.archivedAt ?: JSONObject.NULL)
                    put("createdAt", y.createdAt)
                    put("updatedAt", y.updatedAt)
                })
            }
        })

        // 3. profile
        data.put("profile", JSONObject().apply {
            val p = snapshot.profile
            put("id", p.id)
            put("professionalName", p.professionalName)
            put("institution", p.institution)
            put("employmentStatus", p.employmentStatus)
            put("specialization", p.specialization)
            put("qualifications", p.qualifications)
            put("appointmentDate", p.appointmentDate)
            put("rank", p.rank)
            put("inspectionHistory", p.inspectionHistory)
            put("promotionDetails", p.promotionDetails)
            put("optionalIdentifier", p.optionalIdentifier)
            put("createdAt", p.createdAt)
            put("updatedAt", p.updatedAt)
        })

        // 4. classes
        data.put("classes", JSONArray().apply {
            snapshot.classes.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("schoolYearId", c.schoolYearId)
                    put("level", c.level)
                    put("name", c.name)
                    put("isArchived", c.isArchived)
                    put("isSample", c.isSample)
                    put("createdAt", c.createdAt)
                    put("updatedAt", c.updatedAt)
                })
            }
        })

        // 5. students
        data.put("students", JSONArray().apply {
            snapshot.students.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("schoolYearId", s.schoolYearId)
                    put("classId", s.classId)
                    put("fullName", s.fullName)
                    put("orderIndex", s.orderIndex)
                    put("isArchived", s.isArchived)
                    put("isSample", s.isSample)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                })
            }
        })

        // 6. studentPrivateNotes
        data.put("studentPrivateNotes", JSONArray().apply {
            snapshot.studentPrivateNotes.forEach { n ->
                put(JSONObject().apply {
                    put("studentId", n.studentId)
                    put("text", n.text)
                    put("createdAt", n.createdAt)
                    put("updatedAt", n.updatedAt)
                })
            }
        })

        // 7. timetableEntries
        data.put("timetableEntries", JSONArray().apply {
            snapshot.timetableEntries.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("schoolYearId", t.schoolYearId)
                    put("classId", t.classId)
                    put("day", t.day)
                    put("startTime", t.startTime)
                    put("endTime", t.endTime)
                    put("period", t.period)
                    put("isArchived", t.isArchived)
                    put("isSample", t.isSample)
                    put("createdAt", t.createdAt)
                    put("updatedAt", t.updatedAt)
                })
            }
        })

        // 8. calendarEvents
        data.put("calendarEvents", JSONArray().apply {
            snapshot.calendarEvents.forEach { ce ->
                put(JSONObject().apply {
                    put("id", ce.id)
                    put("schoolYearId", ce.schoolYearId)
                    put("kind", ce.kind)
                    put("season", ce.season ?: JSONObject.NULL)
                    put("title", ce.title)
                    put("startDate", ce.startDate)
                    put("endDate", ce.endDate)
                    put("notes", ce.notes)
                    put("isSample", ce.isSample)
                    put("createdAt", ce.createdAt)
                    put("updatedAt", ce.updatedAt)
                })
            }
        })

        // 9. assessments
        data.put("assessments", JSONArray().apply {
            snapshot.assessments.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("schoolYearId", a.schoolYearId)
                    put("kind", a.kind)
                    put("title", a.title)
                    put("classIds", JSONArray(a.classIds))
                    put("date", a.date)
                    put("correctionDate", a.correctionDate)
                    put("notes", a.notes)
                    put("isSample", a.isSample)
                    put("createdAt", a.createdAt)
                    put("updatedAt", a.updatedAt)
                })
            }
        })

        // 10. lessonPlans
        data.put("lessonPlans", JSONArray().apply {
            snapshot.lessonPlans.forEach { lp ->
                put(JSONObject().apply {
                    put("id", lp.id)
                    put("schoolYearId", lp.schoolYearId)
                    put("classId", lp.classId)
                    put("timetableEntryId", lp.timetableEntryId ?: JSONObject.NULL)
                    put("date", lp.date)
                    put("startTime", lp.startTime)
                    put("endTime", lp.endTime)
                    put("status", lp.status)
                    put("title", lp.title)
                    put("learningSegment", lp.learningSegment)
                    put("activityId", lp.activityId ?: JSONObject.NULL)
                    put("activityName", lp.activityName)
                    put("targetCompetence", lp.targetCompetence)
                    put("procedure", lp.procedure)
                    put("materials", lp.materials)
                    put("evaluation", lp.evaluation)
                    put("homework", lp.homework)
                    put("notes", lp.notes)
                    if (lp.postponement != null) {
                        put("postponement", JSONObject().apply {
                            put("reason", lp.postponement.reason)
                            put("suggestedDate", lp.postponement.suggestedDate ?: JSONObject.NULL)
                        })
                    } else {
                        put("postponement", JSONObject.NULL)
                    }
                    put("copiedFromId", lp.copiedFromId ?: JSONObject.NULL)
                    put("isSample", lp.isSample)
                    put("createdAt", lp.createdAt)
                    put("updatedAt", lp.updatedAt)
                })
            }
        })

        // 11. activityTemplates
        data.put("activityTemplates", JSONArray().apply {
            snapshot.activityTemplates.forEach { at ->
                put(JSONObject().apply {
                    put("id", at.id)
                    put("name", at.name)
                    put("orderIndex", at.orderIndex)
                    put("createdAt", at.createdAt)
                    put("updatedAt", at.updatedAt)
                })
            }
        })

        // 12. lessonObservations
        data.put("lessonObservations", JSONArray().apply {
            snapshot.lessonObservations.forEach { ob ->
                put(JSONObject().apply {
                    put("id", ob.id)
                    put("schoolYearId", ob.schoolYearId)
                    put("lessonPlanId", ob.lessonPlanId)
                    put("classId", ob.classId)
                    put("studentId", ob.studentId)
                    put("attendance", ob.attendance ?: JSONObject.NULL)
                    put("participated", ob.participated)
                    put("note", ob.note)
                    put("noteAt", ob.noteAt ?: JSONObject.NULL)
                    put("observedAt", ob.observedAt)
                    put("isSample", ob.isSample)
                    put("createdAt", ob.createdAt)
                    put("updatedAt", ob.updatedAt)
                })
            }
        })

        // 13. gradebooks
        data.put("gradebooks", JSONArray().apply {
            snapshot.gradebooks.forEach { gb ->
                put(JSONObject().apply {
                    put("id", gb.id)
                    put("schoolYearId", gb.schoolYearId)
                    put("classId", gb.classId)
                    put("termId", gb.termId)
                    put("isSample", gb.isSample)
                    put("createdAt", gb.createdAt)
                    put("updatedAt", gb.updatedAt)
                })
            }
        })

        // 14. gradeRecords
        data.put("gradeRecords", JSONArray().apply {
            snapshot.gradeRecords.forEach { gr ->
                put(JSONObject().apply {
                    put("id", gr.id)
                    put("gradebookId", gr.gradebookId)
                    put("schoolYearId", gr.schoolYearId)
                    put("classId", gr.classId)
                    put("studentId", gr.studentId)
                    put("termId", gr.termId)
                    put("orderIndex", gr.orderIndex)
                    put("ca1", gr.ca1 ?: JSONObject.NULL)
                    put("ca2", gr.ca2 ?: JSONObject.NULL)
                    put("as1", gr.as1 ?: JSONObject.NULL)
                    put("as2", gr.as2 ?: JSONObject.NULL)
                    put("exam", gr.exam ?: JSONObject.NULL)
                    put("absences", gr.absences ?: JSONObject.NULL)
                    put("behaviour", gr.behaviour)
                    put("materials", gr.materials)
                    put("notebook", gr.notebook)
                    put("remarks", gr.remarks)
                    put("finalAverage", gr.finalAverage ?: JSONObject.NULL)
                    put("isSample", gr.isSample)
                    put("createdAt", gr.createdAt)
                    put("updatedAt", gr.updatedAt)
                })
            }
        })

        // 15. gradeTermConfigs
        data.put("gradeTermConfigs", JSONArray().apply {
            snapshot.gradeTermConfigs.forEach { gc ->
                put(JSONObject().apply {
                    put("id", gc.id)
                    put("schoolYearId", gc.schoolYearId)
                    put("termId", gc.termId)
                    put("ca2Active", gc.ca2Active)
                    put("ca2Required", gc.ca2Required)
                    put("as2Active", gc.as2Active)
                    put("as2Required", gc.as2Required)
                    put("weights", JSONObject().apply {
                        put("ca", gc.weights.ca)
                        put("as", gc.weights.asWeight)
                        put("exam", gc.weights.exam)
                    })
                    put("visibleColumns", JSONObject().apply {
                        put("absences", gc.visibleColumns.absences)
                        put("behaviour", gc.visibleColumns.behaviour)
                        put("materials", gc.visibleColumns.materials)
                        put("notebook", gc.visibleColumns.notebook)
                    })
                    put("createdAt", gc.createdAt)
                    put("updatedAt", gc.updatedAt)
                })
            }
        })

        // 16. trainingCategories
        data.put("trainingCategories", JSONArray().apply {
            snapshot.trainingCategories.forEach { tc ->
                put(JSONObject().apply {
                    put("id", tc.id)
                    put("name", tc.name)
                    put("orderIndex", tc.orderIndex)
                    put("createdAt", tc.createdAt)
                    put("updatedAt", tc.updatedAt)
                })
            }
        })

        // 17. trainingNotes
        data.put("trainingNotes", JSONArray().apply {
            snapshot.trainingNotes.forEach { tn ->
                put(JSONObject().apply {
                    put("id", tn.id)
                    put("schoolYearId", tn.schoolYearId)
                    put("title", tn.title)
                    put("date", tn.date)
                    put("text", tn.text)
                    put("categoryId", tn.categoryId ?: JSONObject.NULL)
                    put("categoryName", tn.categoryName)
                    put("isSample", tn.isSample)
                    put("createdAt", tn.createdAt)
                    put("updatedAt", tn.updatedAt)
                })
            }
        })

        // 18. seminars
        data.put("seminars", JSONArray().apply {
            snapshot.seminars.forEach { sm ->
                put(JSONObject().apply {
                    put("id", sm.id)
                    put("schoolYearId", sm.schoolYearId)
                    put("kind", sm.kind)
                    put("date", sm.date)
                    put("title", sm.title)
                    put("location", sm.location)
                    put("facilitator", sm.facilitator)
                    put("mainIdeas", sm.mainIdeas)
                    put("recommendations", sm.recommendations)
                    put("isSample", sm.isSample)
                    put("createdAt", sm.createdAt)
                    put("updatedAt", sm.updatedAt)
                })
            }
        })

        // 19. eduCalendarRecords
        data.put("eduCalendarRecords", JSONArray().apply {
            snapshot.eduCalendarRecords.forEach { ec ->
                put(JSONObject().apply {
                    put("id", ec.id)
                    put("schoolYearId", ec.schoolYearId)
                    put("type", ec.type)
                    put("date", ec.date)
                    put("appliedLesson", ec.appliedLesson)
                    put("teacherName", ec.teacherName)
                    put("level", ec.level)
                    put("topic", ec.topic)
                    put("location", ec.location)
                    put("internshipType", ec.internshipType)
                    put("isSample", ec.isSample)
                    put("createdAt", ec.createdAt)
                    put("updatedAt", ec.updatedAt)
                })
            }
        })

        // Local settings
        data.put("localSettings", JSONObject().apply {
            put("theme", localSettings.theme)
            put("customPrimaryHex", localSettings.customPrimaryHex ?: JSONObject.NULL)
            put("fontSize", localSettings.fontSize)
        })

        return data
    }

    /**
     * Builds the full backup JSON string per Section 8.10:
     * { format, formatVersion, schemaVersion, exportedAt, activeSchoolYearId, counts, checksum, data }
     */
    fun exportBackupJson(
        snapshot: DatabaseSnapshot,
        localSettings: LocalSettings,
        exportedAt: String = DateTimeUtils.nowIsoUtc()
    ): String {
        val dataObj = serializeDataObject(snapshot, localSettings)
        val dataCompact = dataObj.toString()
        val checksum = sha256Hex(dataCompact)

        val countsObj = JSONObject().apply {
            put("schoolYears", snapshot.schoolYears.size)
            put("classes", snapshot.classes.size)
            put("students", snapshot.students.size)
            put("studentPrivateNotes", snapshot.studentPrivateNotes.size)
            put("timetableEntries", snapshot.timetableEntries.size)
            put("calendarEvents", snapshot.calendarEvents.size)
            put("assessments", snapshot.assessments.size)
            put("lessonPlans", snapshot.lessonPlans.size)
            put("lessonObservations", snapshot.lessonObservations.size)
            put("gradebooks", snapshot.gradebooks.size)
            put("gradeRecords", snapshot.gradeRecords.size)
            put("trainingNotes", snapshot.trainingNotes.size)
            put("seminars", snapshot.seminars.size)
            put("eduCalendarRecords", snapshot.eduCalendarRecords.size)
        }

        val root = JSONObject().apply {
            put("format", StoreRegistry.BACKUP_FORMAT)
            put("formatVersion", StoreRegistry.FORMAT_VERSION)
            put("schemaVersion", StoreRegistry.SCHEMA_VERSION)
            put("exportedAt", exportedAt)
            put("activeSchoolYearId", snapshot.meta.activeSchoolYearId)
            put("counts", countsObj)
            put("checksum", checksum)
            put("data", dataObj)
        }
        return root.toString(2)
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "")
        return if (v == "null") null else v
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val d = optDouble(key, Double.NaN)
        return if (d.isNaN()) null else d
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return try {
            getInt(key)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates a backup JSON string in memory without touching storage (Section 8.10 & T50, T51).
     */
    fun validateAndParseBackup(rawJson: String): RestoreValidationResult {
        if (rawJson.isBlank()) {
            return RestoreValidationResult.Invalid("الملف المختار فارغ.")
        }
        if (rawJson.contains("\"__proto__\"") || rawJson.contains("\"constructor\"")) {
            return RestoreValidationResult.Invalid("الملف يحتوي على مفاتيح غير مسموحة أمنيًا.")
        }

        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return RestoreValidationResult.Invalid("الملف المختار ليس ملف JSON صالحًا. يرجى اختيار نسخة احتياطية صحيحة.")
        }

        val format = root.optString("format", "")
        if (format != StoreRegistry.BACKUP_FORMAT) {
            return RestoreValidationResult.Invalid("صيغة الملف غير متوافقة (ليس ملف نسخة احتياطية لتطبيق الأستاذ).")
        }

        val schemaVersion = root.optInt("schemaVersion", -1)
        if (schemaVersion <= 0) {
            return RestoreValidationResult.Invalid("رقم إصدار البيانات (schemaVersion) مفقود أو غير صالح.")
        }
        if (schemaVersion > StoreRegistry.SCHEMA_VERSION) {
            return RestoreValidationResult.Invalid("هذه النسخة الاحتياطية أُنشئت بإصدار أحدث من التطبيق ولا يمكن استعادتها هنا.")
        }

        val exportedAt = root.optString("exportedAt", "")
        if (exportedAt.isBlank()) {
            return RestoreValidationResult.Invalid("تاريخ تصدير النسخة الاحتياطية مفقود.")
        }

        val dataObj = root.optJSONObject("data")
            ?: return RestoreValidationResult.Invalid("قسم البيانات (data) مفقود في ملف النسخة الاحتياطية.")

        // Verify required collections exist in dataObj
        for (store in StoreRegistry.ALL_STORES) {
            if (!dataObj.has(store.name)) {
                return RestoreValidationResult.Invalid("المخزن المطلوب «${store.name}» مفقود في ملف النسخة الاحتياطية.")
            }
        }

        // Verify checksum if present
        if (root.has("checksum") && !root.isNull("checksum")) {
            val expectedChecksum = root.optString("checksum", "")
            val actualChecksum = sha256Hex(dataObj.toString())
            if (expectedChecksum.isNotBlank() && expectedChecksum != actualChecksum) {
                return RestoreValidationResult.Invalid("فشل التحقق من سلامة الملف (البصمة الرقمية checksum غير متطابقة؛ قد يكون الملف تالفًا أو معدّلًا).")
            }
        }

        return try {
            val (snapshot, settings) = parseDataObject(dataObj, exportedAt)
            val integrity = StoreRegistry.verifyIntegrity(snapshot)
            if (!integrity.isClean) {
                return RestoreValidationResult.Invalid(
                    "البيانات داخل النسخة الاحتياطية غير مترابطة: ${integrity.errors.first()}"
                )
            }

            val otherCount = snapshot.timetableEntries.size +
                snapshot.calendarEvents.size +
                snapshot.assessments.size +
                snapshot.lessonObservations.size +
                snapshot.eduCalendarRecords.size +
                snapshot.studentPrivateNotes.size

            val preview = BackupPreview(
                exportedAt = exportedAt,
                schemaVersion = schemaVersion,
                activeSchoolYearId = snapshot.meta.activeSchoolYearId,
                schoolYearsCount = snapshot.schoolYears.size,
                classesCount = snapshot.classes.size,
                studentsCount = snapshot.students.size,
                lessonsCount = snapshot.lessonPlans.size,
                gradeRecordsCount = snapshot.gradeRecords.size,
                trainingNotesCount = snapshot.trainingNotes.size,
                seminarsCount = snapshot.seminars.size,
                otherRecordsCount = otherCount
            )
            RestoreValidationResult.Valid(preview, snapshot, settings, exportedAt)
        } catch (e: IllegalArgumentException) {
            RestoreValidationResult.Invalid(e.message ?: "بيانات غير صالحة داخل النسخة الاحتياطية.")
        } catch (_: Exception) {
            RestoreValidationResult.Invalid("تعذّرت قراءة السجلات داخل النسخة الاحتياطية بسبب بنية غير صالحة.")
        }
    }

    fun parseDataObject(
        dataObj: JSONObject,
        overrideLastBackupAt: String? = null
    ): Pair<DatabaseSnapshot, LocalSettings> {
        val metaJson = dataObj.getJSONObject("meta")
        val meta = AppMeta(
            schemaVersion = metaJson.optInt("schemaVersion", 1),
            activeSchoolYearId = metaJson.optString("activeSchoolYearId", ""),
            setupCompleted = metaJson.optBoolean("setupCompleted", false),
            firstDataAt = metaJson.optNullableString("firstDataAt"),
            lastBackupExportAt = overrideLastBackupAt ?: metaJson.optNullableString("lastBackupExportAt"),
            backupReminderSnoozedUntil = metaJson.optNullableString("backupReminderSnoozedUntil")
        )

        val yearsArr = dataObj.getJSONArray("schoolYears")
        val schoolYears = (0 until yearsArr.length()).map { i ->
            val o = yearsArr.getJSONObject(i)
            val label = o.getString("label")
            DateTimeUtils.validateSchoolYearLabel(label)?.let { throw IllegalArgumentException(it) }
            val status = o.getString("status")
            if (status != "active" && status != "archived") {
                throw IllegalArgumentException("حالة السنة الدراسية غير صالحة: $status")
            }
            SchoolYear(
                id = o.getString("id"),
                label = label,
                status = status,
                archivedAt = o.optNullableString("archivedAt"),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val profJson = dataObj.getJSONObject("profile")
        val profile = TeacherProfile(
            id = "main",
            professionalName = profJson.optString("professionalName", ""),
            institution = profJson.optString("institution", ""),
            employmentStatus = profJson.optString("employmentStatus", ""),
            specialization = profJson.optString("specialization", ""),
            qualifications = profJson.optString("qualifications", ""),
            appointmentDate = profJson.optString("appointmentDate", ""),
            rank = profJson.optString("rank", ""),
            inspectionHistory = profJson.optString("inspectionHistory", ""),
            promotionDetails = profJson.optString("promotionDetails", ""),
            optionalIdentifier = profJson.optString("optionalIdentifier", ""),
            createdAt = profJson.optString("createdAt", DateTimeUtils.nowIsoUtc()),
            updatedAt = profJson.optString("updatedAt", DateTimeUtils.nowIsoUtc())
        )

        val classesArr = dataObj.getJSONArray("classes")
        val classes = (0 until classesArr.length()).map { i ->
            val o = classesArr.getJSONObject(i)
            val name = o.getString("name").trim()
            if (name.isEmpty()) throw IllegalArgumentException("اسم القسم لا يمكن أن يكون فارغًا.")
            SchoolClass(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                level = o.getString("level"),
                name = name,
                isArchived = o.optBoolean("isArchived", false),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val studentsArr = dataObj.getJSONArray("students")
        val students = (0 until studentsArr.length()).map { i ->
            val o = studentsArr.getJSONObject(i)
            val fullName = o.getString("fullName").trim()
            if (fullName.isEmpty()) throw IllegalArgumentException("اسم التلميذ لا يمكن أن يكون فارغًا.")
            Student(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                classId = o.getString("classId"),
                fullName = fullName,
                orderIndex = o.optInt("orderIndex", i + 1),
                isArchived = o.optBoolean("isArchived", false),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val notesArr = dataObj.getJSONArray("studentPrivateNotes")
        val privateNotes = (0 until notesArr.length()).map { i ->
            val o = notesArr.getJSONObject(i)
            StudentPrivateNote(
                studentId = o.getString("studentId"),
                text = o.optString("text", ""),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val ttArr = dataObj.getJSONArray("timetableEntries")
        val timetableEntries = (0 until ttArr.length()).map { i ->
            val o = ttArr.getJSONObject(i)
            val day = o.getInt("day")
            if (day !in 0..4) throw IllegalArgumentException("يوم التوقيت غير صالح.")
            val start = o.getString("startTime")
            val end = o.getString("endTime")
            if (!DateTimeUtils.isValidTime(start) || !DateTimeUtils.isValidTime(end)) {
                throw IllegalArgumentException("توقيت الحصة غير صالح.")
            }
            TimetableEntry(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                classId = o.getString("classId"),
                day = day,
                startTime = start,
                endTime = end,
                period = o.optString("period", ArStrings.PERIOD_MORNING),
                isArchived = o.optBoolean("isArchived", false),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val calArr = dataObj.getJSONArray("calendarEvents")
        val calendarEvents = (0 until calArr.length()).map { i ->
            val o = calArr.getJSONObject(i)
            val sd = o.getString("startDate")
            val ed = o.getString("endDate")
            if (!DateTimeUtils.isValidDate(sd) || !DateTimeUtils.isValidDate(ed)) {
                throw IllegalArgumentException("تاريخ حدث الرزنامة غير صالح.")
            }
            CalendarEvent(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                kind = o.getString("kind"),
                season = o.optNullableString("season"),
                title = o.getString("title"),
                startDate = sd,
                endDate = ed,
                notes = o.optString("notes", ""),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val assArr = dataObj.getJSONArray("assessments")
        val assessments = (0 until assArr.length()).map { i ->
            val o = assArr.getJSONObject(i)
            val cIdsJson = o.getJSONArray("classIds")
            val cIds = (0 until cIdsJson.length()).map { idx -> cIdsJson.getString(idx) }
            val dt = o.getString("date")
            val cdt = o.getString("correctionDate")
            if (!DateTimeUtils.isValidDate(dt) || !DateTimeUtils.isValidDate(cdt)) {
                throw IllegalArgumentException("تاريخ الفرض أو الاختبار غير صالح.")
            }
            AssessmentEvent(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                kind = o.getString("kind"),
                title = o.getString("title"),
                classIds = cIds,
                date = dt,
                correctionDate = cdt,
                notes = o.optString("notes", ""),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val lpArr = dataObj.getJSONArray("lessonPlans")
        val lessonPlans = (0 until lpArr.length()).map { i ->
            val o = lpArr.getJSONObject(i)
            val dt = o.getString("date")
            if (!DateTimeUtils.isValidDate(dt)) {
                throw IllegalArgumentException("تاريخ التحضير غير صالح.")
            }
            val postObj = if (o.has("postponement") && !o.isNull("postponement")) {
                o.optJSONObject("postponement")
            } else null
            val post = postObj?.let {
                PostponementInfo(
                    reason = it.optString("reason", ""),
                    suggestedDate = it.optNullableString("suggestedDate")
                )
            }
            LessonPlan(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                classId = o.getString("classId"),
                timetableEntryId = o.optNullableString("timetableEntryId"),
                date = dt,
                startTime = o.getString("startTime"),
                endTime = o.getString("endTime"),
                status = o.getString("status"),
                title = o.optString("title", ""),
                learningSegment = o.optString("learningSegment", ""),
                activityId = o.optNullableString("activityId"),
                activityName = o.optString("activityName", ""),
                targetCompetence = o.optString("targetCompetence", ""),
                procedure = o.optString("procedure", ""),
                materials = o.optString("materials", ""),
                evaluation = o.optString("evaluation", ""),
                homework = o.optString("homework", ""),
                notes = o.optString("notes", ""),
                postponement = post,
                copiedFromId = o.optNullableString("copiedFromId"),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val atArr = dataObj.getJSONArray("activityTemplates")
        val activityTemplates = (0 until atArr.length()).map { i ->
            val o = atArr.getJSONObject(i)
            ActivityTemplate(
                id = o.getString("id"),
                name = o.getString("name"),
                orderIndex = o.optInt("orderIndex", i),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val obArr = dataObj.getJSONArray("lessonObservations")
        val lessonObservations = (0 until obArr.length()).map { i ->
            val o = obArr.getJSONObject(i)
            LessonObservation(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                lessonPlanId = o.getString("lessonPlanId"),
                classId = o.getString("classId"),
                studentId = o.getString("studentId"),
                attendance = o.optNullableString("attendance"),
                participated = o.optBoolean("participated", false),
                note = o.optString("note", "").take(300),
                noteAt = o.optNullableString("noteAt"),
                observedAt = o.optString("observedAt", DateTimeUtils.nowIsoUtc()),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val gbArr = dataObj.getJSONArray("gradebooks")
        val gradebooks = (0 until gbArr.length()).map { i ->
            val o = gbArr.getJSONObject(i)
            Gradebook(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                classId = o.getString("classId"),
                termId = o.getString("termId"),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val gcArr = dataObj.getJSONArray("gradeTermConfigs")
        val gradeTermConfigs = (0 until gcArr.length()).map { i ->
            val o = gcArr.getJSONObject(i)
            val wObj = o.getJSONObject("weights")
            val weights = GradeWeights(
                ca = wObj.getDouble("ca"),
                asWeight = wObj.getDouble("as"),
                exam = wObj.getDouble("exam")
            )
            GradingEngine.validateWeights(weights)?.let { throw IllegalArgumentException(it) }
            val vcObj = o.getJSONObject("visibleColumns")
            GradeTermConfig(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                termId = o.getString("termId"),
                ca2Active = o.optBoolean("ca2Active", true),
                ca2Required = o.optBoolean("ca2Required", true),
                as2Active = o.optBoolean("as2Active", false),
                as2Required = o.optBoolean("as2Required", false),
                weights = weights,
                visibleColumns = GradeVisibleColumns(
                    absences = vcObj.optBoolean("absences", false),
                    behaviour = vcObj.optBoolean("behaviour", false),
                    materials = vcObj.optBoolean("materials", false),
                    notebook = vcObj.optBoolean("notebook", false)
                ),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }
        val configByYearTerm = gradeTermConfigs.associateBy { it.schoolYearId to it.termId }

        val grArr = dataObj.getJSONArray("gradeRecords")
        val gradeRecords = (0 until grArr.length()).map { i ->
            val o = grArr.getJSONObject(i)
            val schoolYearId = o.getString("schoolYearId")
            val termId = o.getString("termId")
            val cfg = configByYearTerm[schoolYearId to termId]
                ?: GradeTermConfig(schoolYearId = schoolYearId, termId = termId)
            val rawRecord = GradeRecord(
                id = o.getString("id"),
                gradebookId = o.getString("gradebookId"),
                schoolYearId = schoolYearId,
                classId = o.getString("classId"),
                studentId = o.getString("studentId"),
                termId = termId,
                orderIndex = o.optInt("orderIndex", i + 1),
                ca1 = o.optNullableDouble("ca1"),
                ca2 = o.optNullableDouble("ca2"),
                as1 = o.optNullableDouble("as1"),
                as2 = o.optNullableDouble("as2"),
                exam = o.optNullableDouble("exam"),
                absences = o.optNullableInt("absences"),
                behaviour = o.optString("behaviour", "").take(30),
                materials = o.optString("materials", "").take(30),
                notebook = o.optString("notebook", "").take(30),
                remarks = o.optString("remarks", "").take(200),
                finalAverage = null,
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
            // Rebuild finalAverage cache on restore per D8
            val eval = GradingEngine.evaluate(rawRecord, cfg)
            rawRecord.copy(finalAverage = eval.finalAverage)
        }

        val tcArr = dataObj.getJSONArray("trainingCategories")
        val trainingCategories = (0 until tcArr.length()).map { i ->
            val o = tcArr.getJSONObject(i)
            TrainingCategory(
                id = o.getString("id"),
                name = o.getString("name"),
                orderIndex = o.optInt("orderIndex", i),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val tnArr = dataObj.getJSONArray("trainingNotes")
        val trainingNotes = (0 until tnArr.length()).map { i ->
            val o = tnArr.getJSONObject(i)
            val dt = o.getString("date")
            if (!DateTimeUtils.isValidDate(dt)) throw IllegalArgumentException("تاريخ ملاحظة التكوين غير صالح.")
            TrainingNote(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                title = o.getString("title"),
                date = dt,
                text = o.optString("text", ""),
                categoryId = o.optNullableString("categoryId"),
                categoryName = o.getString("categoryName"),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val smArr = dataObj.getJSONArray("seminars")
        val seminars = (0 until smArr.length()).map { i ->
            val o = smArr.getJSONObject(i)
            val dt = o.getString("date")
            if (!DateTimeUtils.isValidDate(dt)) throw IllegalArgumentException("تاريخ الندوة غير صالح.")
            SeminarRecord(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                kind = o.getString("kind"),
                date = dt,
                title = o.getString("title"),
                location = o.optString("location", ""),
                facilitator = o.optString("facilitator", ""),
                mainIdeas = o.optString("mainIdeas", ""),
                recommendations = o.optString("recommendations", ""),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val ecArr = dataObj.getJSONArray("eduCalendarRecords")
        val eduCalendarRecords = (0 until ecArr.length()).map { i ->
            val o = ecArr.getJSONObject(i)
            val dt = o.getString("date")
            if (!DateTimeUtils.isValidDate(dt)) throw IllegalArgumentException("تاريخ الرزنامة التربوية غير صالح.")
            EduCalendarRecord(
                id = o.getString("id"),
                schoolYearId = o.getString("schoolYearId"),
                type = o.getString("type"),
                date = dt,
                appliedLesson = o.optString("appliedLesson", ""),
                teacherName = o.optString("teacherName", ""),
                level = o.optString("level", ""),
                topic = o.optString("topic", ""),
                location = o.optString("location", ""),
                internshipType = o.optString("internshipType", ""),
                isSample = o.optBoolean("isSample", false),
                createdAt = o.optString("createdAt", DateTimeUtils.nowIsoUtc()),
                updatedAt = o.optString("updatedAt", DateTimeUtils.nowIsoUtc())
            )
        }

        val lsJson = dataObj.optJSONObject("localSettings")
        val localSettings = if (lsJson != null) {
            LocalSettings(
                theme = lsJson.optString("theme", "rose").let { if (it == "dark") "dark" else "rose" },
                customPrimaryHex = lsJson.optNullableString("customPrimaryHex"),
                fontSize = lsJson.optString("fontSize", "normal").let { if (it == "large") "large" else "normal" }
            )
        } else {
            LocalSettings()
        }

        val snapshot = DatabaseSnapshot(
            meta = meta,
            schoolYears = schoolYears,
            profile = profile,
            classes = classes,
            students = students,
            studentPrivateNotes = privateNotes,
            timetableEntries = timetableEntries,
            calendarEvents = calendarEvents,
            assessments = assessments,
            lessonPlans = lessonPlans,
            activityTemplates = activityTemplates,
            lessonObservations = lessonObservations,
            gradebooks = gradebooks,
            gradeRecords = gradeRecords,
            gradeTermConfigs = gradeTermConfigs,
            trainingCategories = trainingCategories,
            trainingNotes = trainingNotes,
            seminars = seminars,
            eduCalendarRecords = eduCalendarRecords
        )
        return snapshot to localSettings
    }
}
