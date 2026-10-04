package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.*
import com.example.i18n.ArStrings
import com.example.print.PrintAndExportHelper
import com.example.storage.BackupSerializer
import com.example.storage.ClassCopySelection
import com.example.storage.StoreRegistry
import com.example.storage.TeacherRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "teacher_app_db_v1.json").delete()
        File(context.filesDir, "teacher_app_db_v1.tmp").delete()
        DateTimeUtils.testFixedTodayDate = "2026-10-04"
    }

    @Test
    fun `read string from context matches تطبيق الأستاذ`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("تطبيق الأستاذ", appName)
    }

    @Test
    fun `T06 T08 T09 T10 Setup persistence sample data fault injection and integrity`() {
        val repo = TeacherRepository(context)
        val setupRes = repo.completeFirstTimeSetup(
            professionalName = "الأستاذة سارة",
            institution = "متوسطة ابن خلدون",
            schoolYearLabel = "2026-2027",
            classesDraft = listOf(
                TeacherRepository.SetupClassDraft(
                    level = "الأولى متوسط",
                    name = "فوج 1",
                    studentNames = listOf("أحمد بن محمد", "فاطمة الزهراء")
                )
            )
        )
        assertTrue(setupRes.isSuccess)
        assertEquals(SaveStatus.SAVED, repo.saveIndicator.value.status)

        // Reload from disk in a new repository instance
        val reloaded = TeacherRepository(context)
        assertEquals("الأستاذة سارة", reloaded.snapshot.value.profile.professionalName)
        assertEquals(1, reloaded.snapshot.value.classes.size)
        assertEquals(2, reloaded.snapshot.value.students.size)
        assertTrue(StoreRegistry.verifyIntegrity(reloaded.snapshot.value).isClean)

        // T08: Seed sample data and remove sample data only (real class untouched)
        reloaded.seedSampleData(fromOnboarding = false)
        assertTrue(reloaded.snapshot.value.classes.count { it.isSample } == 2)
        assertTrue(reloaded.snapshot.value.classes.count { !it.isSample } == 1)

        reloaded.removeSampleDataOnly()
        assertEquals(0, reloaded.snapshot.value.classes.count { it.isSample })
        assertEquals(1, reloaded.snapshot.value.classes.count { !it.isSample })
        assertEquals(2, reloaded.snapshot.value.students.count { !it.isSample })

        // T09: Fault injection shows تعذّر الحفظ and leaves data untouched
        reloaded.simulateWriteFailure = true
        val failedRes = reloaded.saveClass(null, "الثانية متوسط", "فوج 9")
        assertTrue(failedRes.isFailure)
        assertEquals(SaveStatus.FAILED, reloaded.saveIndicator.value.status)
        assertTrue(reloaded.saveIndicator.value.message.contains(ArStrings.SAVE_FAILED))
        assertEquals(1, reloaded.snapshot.value.classes.size)
        reloaded.simulateWriteFailure = false
    }

    @Test
    fun `T11 T12 T13 T14 T15 T19 T20 T21 T23 T24 T27 T28 Full planner observations and grade isolation`() {
        val repo = TeacherRepository(context)
        repo.completeFirstTimeSetup(
            professionalName = "أستاذة",
            institution = "متوسطة",
            schoolYearLabel = "2026-2027",
            classesDraft = listOf(
                TeacherRepository.SetupClassDraft("الأولى متوسط", "م 1", listOf("تلميذ أول", "تلميذ ثان"))
            )
        )
        val cls = repo.snapshot.value.classes.first()
        val st1 = repo.snapshot.value.students.first()

        // T11: Private note stored separately and persists
        repo.saveStudentPrivateNote(st1.id, "ملاحظة سرية خاصة بالتلميذ")
        assertEquals("ملاحظة سرية خاصة بالتلميذ", repo.getStudentPrivateNote(st1.id))

        // T14: End <= start blocked
        val badTimeRes = repo.saveTimetableEntry(null, cls.id, 0, "10:00", "09:00", ArStrings.PERIOD_MORNING)
        assertTrue(badTimeRes.isFailure)

        // T13 & T15: Valid timetable entry + overlap blocked unless allowOverlap = true
        assertTrue(repo.saveTimetableEntry(null, cls.id, 0, "08:00", "09:00", ArStrings.PERIOD_MORNING).isSuccess)
        val overlapBlocked = repo.saveTimetableEntry(null, cls.id, 0, "08:30", "09:30", ArStrings.PERIOD_MORNING, allowOverlap = false)
        assertTrue(overlapBlocked.isFailure)
        val overlapAllowed = repo.saveTimetableEntry(null, cls.id, 0, "08:30", "09:30", ArStrings.PERIOD_MORNING, allowOverlap = true)
        assertTrue(overlapAllowed.isSuccess)

        val slot = repo.snapshot.value.timetableEntries.first()

        // T19 & T20: Open slot creates draft once; reopening returns existing without duplicate
        val plan1 = repo.openOrCreateLessonFromSlot(slot.id, "2026-10-04").getOrThrow()
        val plan2 = repo.openOrCreateLessonFromSlot(slot.id, "2026-10-04").getOrThrow()
        assertEquals(plan1.id, plan2.id)
        assertEquals(1, repo.snapshot.value.lessonPlans.size)

        // T26: Prepared status requires title
        val noTitleRes = repo.saveLessonPlan(plan1.copy(status = ArStrings.STATUS_PREPARED, title = ""))
        assertTrue(noTitleRes.isFailure)
        val withTitleRes = repo.saveLessonPlan(plan1.copy(status = ArStrings.STATUS_PREPARED, title = "الفاعل وأحكامه"))
        assertTrue(withTitleRes.isSuccess)

        // T21: Editing plan leaves timetable entry unchanged
        assertEquals("08:00", repo.snapshot.value.timetableEntries.first { it.id == slot.id }.startTime)

        // T23: Copy lesson plan creates new ID and keeps original untouched
        val copied = repo.copyLessonPlan(plan1.id, cls.id, "2026-10-05", "10:00", "11:00").getOrThrow()
        assertNotEquals(plan1.id, copied.id)
        assertEquals(plan1.id, copied.copiedFromId)
        assertNull(copied.timetableEntryId)

        // T24: Postponement requires reason
        assertTrue(repo.saveLessonPlan(copied.copy(status = ArStrings.STATUS_POSTPONED, postponement = null)).isFailure)
        assertTrue(
            repo.saveLessonPlan(
                copied.copy(
                    status = ArStrings.STATUS_POSTPONED,
                    postponement = PostponementInfo("اجتماع بيداغوجي", "2026-10-12")
                )
            ).isSuccess
        )

        // T27 & T28: Quick observations persist and NEVER create or alter grades
        repo.saveLessonObservation(plan1.id, st1.id, "present", true, "تفاعل ممتاز في الحصة")
        assertEquals(0, repo.snapshot.value.gradeRecords.size)

        // T12: Student with observations cannot be permanently deleted; class with lessons cannot be permanently deleted
        assertFalse(repo.getStudentDependents(st1.id).canDeletePermanently)
        assertTrue(repo.deleteStudentPermanently(st1.id).isFailure)
        assertTrue(repo.getClassDependents(cls.id).hasHistoryBlockingPermanentDelete)
        assertTrue(repo.deleteClassPermanently(cls.id).isFailure)

        assertTrue(StoreRegistry.verifyIntegrity(repo.snapshot.value).isClean)
    }

    @Test
    fun `T44 T45 T46 T47 T48 T49 T50 T51 T54 T55 Search Backup Restore and School Years`() {
        val repo = TeacherRepository(context)
        repo.completeFirstTimeSetup(
            professionalName = "الأستاذة نورة",
            institution = "متوسطة العقيد لطفي",
            schoolYearLabel = "2026-2027",
            classesDraft = listOf(
                TeacherRepository.SetupClassDraft("الثالثة متوسط", "م 2", listOf("إبراهيم خليل"))
            )
        )
        val cls = repo.snapshot.value.classes.first()
        val st = repo.snapshot.value.students.first()
        repo.saveStudentPrivateNote(st.id, "ملاحظة خاصة سرية لا تبحث")

        // T44, T45, T46: Training note, Seminar, EduCalendar
        repo.saveTrainingNote(null, "قانون التوجيه المدرسي", "2026-10-04", "تفاصيل التشريع", "trn_cat_2", "التشريع المدرسي")
        repo.saveSeminar(null, "internal", "2026-10-04", "ندوة المقاربة بالكفاءات", "المكتبة", "السيد المفتش", "أفكار", "توصيات")
        repo.saveEduCalendarRecord(null, "studyDay", "2026-10-04", "", "", "", "يوم دراسي حول التقويم", "متوسطة مركزية", "")

        // T48: Arabic normalized search finds student & training note, NEVER finds private note
        val foundStudent = repo.searchGlobal("ابراهيم", includeArchived = false)
        assertTrue(foundStudent.containsKey(ArStrings.SEARCH_TYPE_STUDENT))
        val privateSearch = repo.searchGlobal("سرية", includeArchived = true)
        assertTrue(privateSearch.isEmpty())

        // T43: Profile print shows only filled fields
        val profPrint = PrintAndExportHelper.buildProfilePrintSpec(repo.snapshot.value.profile, listOf(cls), "2026-2027")
        val printedKeys = profPrint.previewSections.first().rows.map { it.first }
        assertTrue(printedKeys.contains("الاسم المهني"))
        assertFalse(printedKeys.contains("الرتبة والدرجة"))

        // T54: Backup reminder hidden at day 0, shown after > 14 days, snoozed for 3 days
        assertFalse(repo.shouldShowBackupReminder("2026-10-10"))
        assertTrue(repo.shouldShowBackupReminder("2026-10-20"))
        repo.snoozeBackupReminder("2026-10-20")
        assertFalse(repo.shouldShowBackupReminder("2026-10-22"))
        assertTrue(repo.shouldShowBackupReminder("2026-10-24"))

        // T49, T50, T51: Backup export, invalid restore rejection, and full restore after reset
        val exportedJson = BackupSerializer.exportBackupJson(repo.snapshot.value, repo.localSettings.value, "2026-10-25T10:00:00.000Z")
        repo.markBackupExportSucceeded("2026-10-25T10:00:00.000Z")

        val invalidRestore = repo.restoreFromValidatedBackup("{not valid json")
        assertTrue(invalidRestore.isFailure)
        assertEquals(1, repo.snapshot.value.classes.size) // Data untouched

        repo.resetAllData()
        assertEquals(0, repo.snapshot.value.classes.size)

        val validRestore = repo.restoreFromValidatedBackup(exportedJson)
        assertTrue(validRestore.isSuccess)
        assertEquals(1, repo.snapshot.value.classes.size)
        assertEquals("ملاحظة خاصة سرية لا تبحث", repo.getStudentPrivateNote(st.id))

        // T55: Archive year + start new year with class/student copy (new IDs, no lessons/grades copied)
        val archiveRes = repo.archiveAndStartNewYear(
            newYearLabel = "2027-2028",
            copySelections = listOf(
                ClassCopySelection(sourceClassId = cls.id, newLevel = "الرابعة متوسط", newName = "م 2", copyStudents = true)
            )
        )
        assertTrue(archiveRes.isSuccess)
        val newActiveYearId = repo.snapshot.value.meta.activeSchoolYearId
        val newYearClasses = repo.snapshot.value.classes.filter { it.schoolYearId == newActiveYearId }
        assertEquals(1, newYearClasses.size)
        assertNotEquals(cls.id, newYearClasses.first().id)
        val newYearStudents = repo.snapshot.value.students.filter { it.schoolYearId == newActiveYearId }
        assertEquals(1, newYearStudents.size)
        assertNotEquals(st.id, newYearStudents.first().id)
        assertEquals(0, repo.snapshot.value.trainingNotes.count { it.schoolYearId == newActiveYearId })
        assertTrue(StoreRegistry.verifyIntegrity(repo.snapshot.value).isClean)
    }
}
