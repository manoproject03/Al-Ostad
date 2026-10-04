package com.example.domain

import com.example.i18n.ArStrings
import java.util.UUID

fun newStableId(): String = UUID.randomUUID().toString()

enum class SaveStatus {
    IDLE,
    SAVING,
    SAVED,
    FAILED
}

data class SaveIndicatorState(
    val status: SaveStatus = SaveStatus.IDLE,
    val message: String = "",
    val errorReason: String? = null,
    val updatedAtMs: Long = 0L
)

data class AppMeta(
    val schemaVersion: Int = 1,
    val activeSchoolYearId: String = "",
    val setupCompleted: Boolean = false,
    val firstDataAt: String? = null,
    val lastBackupExportAt: String? = null,
    val backupReminderSnoozedUntil: String? = null
)

data class SchoolYear(
    val id: String = newStableId(),
    val label: String, // e.g., "2026-2027"
    val status: String = "active", // "active" | "archived"
    val archivedAt: String? = null,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class TeacherProfile(
    val id: String = "main",
    val professionalName: String = "",
    val institution: String = "",
    val employmentStatus: String = "",
    val specialization: String = "",
    val qualifications: String = "",
    val appointmentDate: String = "",
    val rank: String = "",
    val inspectionHistory: String = "",
    val promotionDetails: String = "",
    val optionalIdentifier: String = "",
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class SchoolClass(
    val id: String = newStableId(),
    val schoolYearId: String,
    val level: String, // الأولى متوسط .. الرابعة متوسط
    val name: String,  // e.g. "م 1" or "فوج 1"
    val isArchived: Boolean = false,
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
) {
    val fullTitle: String get() = "$level — $name"
}

data class Student(
    val id: String = newStableId(),
    val schoolYearId: String,
    val classId: String,
    val fullName: String,
    val orderIndex: Int,
    val isArchived: Boolean = false,
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

/**
 * Stored in a separate store (studentPrivateNotes) per D5.
 * Never loaded in ordinary queries, lists, search, print, or CSV.
 */
data class StudentPrivateNote(
    val studentId: String,
    val text: String,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class TimetableEntry(
    val id: String = newStableId(),
    val schoolYearId: String,
    val classId: String,
    val day: Int, // 0 = الأحد .. 4 = الخميس
    val startTime: String, // HH:mm
    val endTime: String,   // HH:mm
    val period: String = ArStrings.PERIOD_MORNING, // صباحية | مسائية
    val isArchived: Boolean = false,
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class CalendarEvent(
    val id: String = newStableId(),
    val schoolYearId: String,
    val kind: String, // "break" | "religious" | "national"
    val season: String? = null, // "autumn" | "winter" | "spring" for breaks
    val title: String,
    val startDate: String, // YYYY-MM-DD
    val endDate: String,   // YYYY-MM-DD
    val notes: String = "",
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
) {
    val kindArabic: String
        get() = when (kind) {
            "break" -> when (season) {
                "autumn" -> "عطلة الخريف"
                "winter" -> "عطلة الشتاء"
                "spring" -> "عطلة الربيع"
                else -> "عطلة مدرسية"
            }
            "religious" -> "مناسبة دينية"
            "national" -> "مناسبة وطنية"
            else -> "حدث مدرسي"
        }
}

data class AssessmentEvent(
    val id: String = newStableId(),
    val schoolYearId: String,
    val kind: String, // "فرض" | "اختبار"
    val title: String,
    val classIds: List<String>,
    val date: String, // YYYY-MM-DD
    val correctionDate: String, // YYYY-MM-DD
    val notes: String = "",
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class PostponementInfo(
    val reason: String,
    val suggestedDate: String? = null
)

data class LessonPlan(
    val id: String = newStableId(),
    val schoolYearId: String,
    val classId: String,
    val timetableEntryId: String? = null,
    val date: String, // YYYY-MM-DD
    val startTime: String, // HH:mm
    val endTime: String,   // HH:mm
    val status: String = ArStrings.STATUS_DRAFT, // مسودة | محضّر | أُنجز | أُجّل
    val title: String = "",
    val learningSegment: String = "",
    val activityId: String? = null,
    val activityName: String = "", // snapshot
    val targetCompetence: String = "",
    val procedure: String = "",
    val materials: String = "",
    val evaluation: String = "",
    val homework: String = "",
    val notes: String = "",
    val postponement: PostponementInfo? = null,
    val copiedFromId: String? = null,
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class ActivityTemplate(
    val id: String = newStableId(),
    val name: String,
    val orderIndex: Int,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class LessonObservation(
    val id: String = newStableId(),
    val schoolYearId: String,
    val lessonPlanId: String,
    val classId: String,
    val studentId: String,
    val attendance: String? = null, // "present" | "absent" | null
    val participated: Boolean = false,
    val note: String = "", // <= 300 chars
    val noteAt: String? = null,
    val observedAt: String = DateTimeUtils.nowIsoUtc(),
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class Gradebook(
    val id: String = newStableId(),
    val schoolYearId: String,
    val classId: String,
    val termId: String, // "T1" | "T2" | "T3"
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
) {
    companion object {
        fun termLabel(termId: String): String = when (termId) {
            "T1" -> ArStrings.TERM_1
            "T2" -> ArStrings.TERM_2
            "T3" -> ArStrings.TERM_3
            else -> ArStrings.TERM_1
        }
    }
}

data class GradeRecord(
    val id: String = newStableId(),
    val gradebookId: String,
    val schoolYearId: String,
    val classId: String,
    val studentId: String,
    val termId: String, // "T1" | "T2" | "T3"
    val orderIndex: Int,
    val ca1: Double? = null,
    val ca2: Double? = null,
    val as1: Double? = null,
    val as2: Double? = null,
    val exam: Double? = null,
    val absences: Int? = null, // 0..999
    val behaviour: String = "", // <= 30 chars, qualitative
    val materials: String = "", // <= 30 chars, qualitative
    val notebook: String = "",  // <= 30 chars, qualitative
    val remarks: String = "",   // <= 200 chars
    val finalAverage: Double? = null, // derived cache
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
) {
    fun hasAnyNonEmptyValue(): Boolean =
        ca1 != null || ca2 != null || as1 != null || as2 != null || exam != null ||
            absences != null || behaviour.isNotBlank() || materials.isNotBlank() ||
            notebook.isNotBlank() || remarks.isNotBlank()
}

data class GradeWeights(
    val ca: Double = 1.0,
    val asWeight: Double = 1.0,
    val exam: Double = 2.0
)

data class GradeVisibleColumns(
    val absences: Boolean = false,
    val behaviour: Boolean = false,
    val materials: Boolean = false,
    val notebook: Boolean = false
)

data class GradeTermConfig(
    val id: String = newStableId(),
    val schoolYearId: String,
    val termId: String, // "T1" | "T2" | "T3"
    val ca2Active: Boolean = true,
    val ca2Required: Boolean = true,
    val as2Active: Boolean = false,
    val as2Required: Boolean = false,
    val weights: GradeWeights = GradeWeights(1.0, 1.0, 2.0),
    val visibleColumns: GradeVisibleColumns = GradeVisibleColumns(),
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class TrainingCategory(
    val id: String = newStableId(),
    val name: String,
    val orderIndex: Int,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class TrainingNote(
    val id: String = newStableId(),
    val schoolYearId: String,
    val title: String,
    val date: String, // YYYY-MM-DD
    val text: String,
    val categoryId: String? = null,
    val categoryName: String, // snapshot
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
)

data class SeminarRecord(
    val id: String = newStableId(),
    val schoolYearId: String,
    val kind: String, // "internal" | "external"
    val date: String, // YYYY-MM-DD
    val title: String,
    val location: String = "",
    val facilitator: String = "",
    val mainIdeas: String = "",
    val recommendations: String = "",
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
) {
    val kindArabic: String
        get() = if (kind == "external") "ندوة خارجية" else "ندوة داخلية"
}

data class EduCalendarRecord(
    val id: String = newStableId(),
    val schoolYearId: String,
    val type: String, // "pedagogicalSeminar" | "studyDay" | "internship"
    val date: String, // YYYY-MM-DD
    val appliedLesson: String = "",
    val teacherName: String = "",
    val level: String = "",
    val topic: String = "",
    val location: String = "",
    val internshipType: String = "",
    val isSample: Boolean = false,
    val createdAt: String = DateTimeUtils.nowIsoUtc(),
    val updatedAt: String = DateTimeUtils.nowIsoUtc()
) {
    val typeArabic: String
        get() = when (type) {
            "pedagogicalSeminar" -> "ندوة تربوية"
            "studyDay" -> "يوم دراسي"
            "internship" -> "تربص"
            else -> "رزنامة تربوية"
        }
}

data class LocalSettings(
    val theme: String = "rose", // "rose" | "dark"
    val customPrimaryHex: String? = null, // e.g. "#7A3E65"
    val fontSize: String = "normal" // "normal" | "large"
)

/**
 * D6 Delete vs Archive dependents summary
 */
data class ClassDependents(
    val studentCount: Int,
    val timetableCount: Int,
    val lessonPlanCount: Int,
    val observationCount: Int,
    val assessmentCount: Int,
    val nonEmptyGradeCount: Int
) {
    val hasHistoryBlockingPermanentDelete: Boolean
        get() = lessonPlanCount > 0 || observationCount > 0 || assessmentCount > 0 || nonEmptyGradeCount > 0
}

data class StudentDependents(
    val observationCount: Int,
    val nonEmptyGradeCount: Int,
    val hasPrivateNote: Boolean,
    val emptyGradeRowCount: Int
) {
    val canDeletePermanently: Boolean
        get() = observationCount == 0 && nonEmptyGradeCount == 0
}

data class TimetableDependents(
    val lessonPlanCount: Int
) {
    val canDeletePermanently: Boolean
        get() = lessonPlanCount == 0
}

data class LessonDependents(
    val observationCount: Int
)

data class SearchResultItem(
    val id: String,
    val typeLabel: String, // تحضير، تلميذ، قسم، تكوين، ندوة، رزنامة، رزنامة تربوية، توقيت
    val title: String,
    val subtitle: String,
    val schoolYearId: String,
    val schoolYearLabel: String,
    val isArchived: Boolean,
    val isSample: Boolean,
    val targetRoute: String,
    val targetEntityId: String,
    val targetSecondaryId: String? = null
)

data class IntegrityReport(
    val isClean: Boolean,
    val errors: List<String>
)

data class BackupPreview(
    val exportedAt: String,
    val schemaVersion: Int,
    val activeSchoolYearId: String,
    val schoolYearsCount: Int,
    val classesCount: Int,
    val studentsCount: Int,
    val lessonsCount: Int,
    val gradeRecordsCount: Int,
    val trainingNotesCount: Int,
    val seminarsCount: Int,
    val otherRecordsCount: Int
)
