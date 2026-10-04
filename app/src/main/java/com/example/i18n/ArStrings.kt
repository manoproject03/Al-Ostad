package com.example.i18n

object ArStrings {
    const val APP_NAME = "تطبيق الأستاذ"
    const val APP_SHORT_NAME = "الأستاذ"
    const val APP_SUBTITLE = "دفتر التنظيم البيداغوجي لأستاذة اللغة العربية — التعليم المتوسط"

    // Save Indicator (Appendix A)
    const val SAVE_SAVING = "جارٍ الحفظ…"
    const val SAVE_SAVED = "تم الحفظ"
    const val SAVE_FAILED = "تعذّر الحفظ"

    // Lesson Statuses (Appendix A)
    const val STATUS_DRAFT = "مسودة"
    const val STATUS_PREPARED = "محضّر"
    const val STATUS_COMPLETED = "أُنجز"
    const val STATUS_POSTPONED = "أُجّل"

    // Terms (Appendix A)
    const val TERM_1 = "الفصل الأول"
    const val TERM_2 = "الفصل الثاني"
    const val TERM_3 = "الفصل الثالث"

    // Periods (Appendix A)
    const val PERIOD_MORNING = "صباحية"
    const val PERIOD_AFTERNOON = "مسائية"

    // Tags (Appendix A)
    const val TAG_SAMPLE = "تجريبية"
    const val TAG_ARCHIVED = "مؤرشف"
    const val TAG_ACTIVE_YEAR = "نشطة"
    const val TAG_ARCHIVED_YEAR = "مؤرشفة"

    // Fixed Notices (Appendix A & D5, D8, D15, D16, D17)
    const val BACKUP_REMINDER = "تذكير: مضى أكثر من 14 يومًا منذ آخر نسخة احتياطية ناجحة. احفظي نسخة في مكان آمن."
    const val RESTORE_MODE_LABEL = "استبدال البيانات الحالية بعد تأكيد"
    const val DESTRUCTIVE_NOTICE = "لا يمكن التراجع عن الحذف من دون نسخة احتياطية."
    const val MISSING_SCORES_PREFIX = "ينقص: "
    const val PRIVATE_NOTE_NOTICE = "ملاحظة خاصة: لا تظهر في القوائم ولا في الطباعة ولا في CSV ولا في البحث، وتُحفظ ضمن النسخة الاحتياطية الكاملة فقط."
    const val BACKUP_PRIVACY_WARNING = "تتضمن النسخة الاحتياطية الملاحظات الخاصة وأسماء التلاميذ؛ احتفظي بالملف في مكان آمن."
    const val BACKUP_STORAGE_WARNING = "قد تضيع البيانات المحلية عند حذف بيانات المتصفح أو فقدان الجهاز. احفظي النسخة الاحتياطية خارج المتصفح وفي مكان آمن."
    const val PRIVACY_LOCAL_NOTICE = "تُحفظ البيانات محليًا على هذا الجهاز والمتصفح. قد تضيع عند حذف بيانات المتصفح أو فقدان الجهاز."
    const val PROFILE_LOCAL_NOTICE = "تُحفظ هذه المعلومات محليًا على هذا الجهاز والمتصفح."
    const val FORMULA_SUGGESTION_NOTE = "هذه صيغة مقترحة قابلة للتعديل وليست قاعدة رسمية."
    const val CSV_HINT = "إذا ظهرت الأعمدة في عمود واحد أو الأرقام كنص، جرّبي الخيار الآخر"
    const val CONTRAST_WARNING = "هذا اللون قد يجعل نص الأزرار صعب القراءة. جرّبي لونًا أغمق أو أفتح."
    const val OFFLINE_READY_BANNER = "اكتمل تخزين ملفات التطبيق؛ يمكن فتحه دون اتصال على هذا الجهاز والمتصفح."
    const val OFFLINE_SETTINGS_NOTE = "يعمل التطبيق دون اتصال بعد أن يُفتح مرة واحدة بنجاح مع الإنترنت وتكتمل عملية التخزين، وعلى نفس الجهاز والمتصفح فقط."
    const val STORAGE_PERSIST_NOTE = "قد يمنع المتصفح حذف البيانات تلقائيًا عند امتلاء المساحة، لكنه لا يمنع حذفها يدويًا أو عند مسح بيانات الموقع. النسخة الاحتياطية الخارجية تبقى ضرورية."

    // Navigation (D1)
    const val NAV_HOME = "الرئيسية"
    const val NAV_DAILY = "اليومي"
    const val NAV_CLASSES_GRADES = "الأقسام والتنقيط"
    const val NAV_SCHEDULE = "التوقيت والرزنامة"
    const val NAV_MORE = "المزيد"
    const val NAV_TRAINING = "التكوين والندوات"
    const val NAV_PROFILE = "بطاقتي المهنية"
    const val NAV_SETTINGS = "الإعدادات"
    const val NAV_SEARCH = "بحث"

    // Algerian Month Names (Section 7)
    val ALGERIAN_MONTHS = listOf(
        "جانفي",
        "فيفري",
        "مارس",
        "أفريل",
        "ماي",
        "جوان",
        "جويلية",
        "أوت",
        "سبتمبر",
        "أكتوبر",
        "نوفمبر",
        "ديسمبر"
    )

    // School Days (Section 7)
    val SCHOOL_DAYS = listOf(
        "الأحد",
        "الاثنين",
        "الثلاثاء",
        "الأربعاء",
        "الخميس"
    )

    // Middle School Levels (Section 7)
    val SCHOOL_LEVELS = listOf(
        "الأولى متوسط",
        "الثانية متوسط",
        "الثالثة متوسط",
        "الرابعة متوسط"
    )

    // Default Activity Templates (Section 8.5)
    val DEFAULT_ACTIVITY_TEMPLATES = listOf(
        "فهم المنطوق",
        "التعبير الشفوي",
        "فهم المكتوب",
        "دراسة النص",
        "الظاهرة النحوية",
        "الظاهرة الصرفية",
        "الظاهرة الإملائية",
        "العروض",
        "التعبير الكتابي",
        "المحفوظات",
        "مشروع",
        "إدماج",
        "معالجة"
    )

    // Default Training Categories (Section 8.7)
    val DEFAULT_TRAINING_CATEGORIES = listOf(
        "تعليمية المادة",
        "التشريع المدرسي",
        "هندسة التكوين",
        "أخلاقيات المهنة",
        "النظام التربوي والمناهج",
        "التقييم والمعالجة البيداغوجية",
        "تقنيات تسيير القسم",
        "الإعلام الآلي",
        "الوساطة التربوية",
        "علوم التربية وعلم النفس",
        "الشفافية والوقاية من الفساد"
    )

    // Search Type Labels (D14)
    const val SEARCH_TYPE_LESSON = "تحضير"
    const val SEARCH_TYPE_STUDENT = "تلميذ"
    const val SEARCH_TYPE_CLASS = "قسم"
    const val SEARCH_TYPE_TRAINING = "تكوين"
    const val SEARCH_TYPE_SEMINAR = "ندوة"
    const val SEARCH_TYPE_CALENDAR = "رزنامة"
    const val SEARCH_TYPE_EDU_CALENDAR = "رزنامة تربوية"
    const val SEARCH_TYPE_TIMETABLE = "توقيت"

    fun viewedYearBanner(label: String): String =
        "أنتِ تتصفحين سنة مؤرشفة: $label — العودة إلى السنة النشطة"

    fun statsIncludedSummary(completeCount: Int, totalCount: Int, archivedCount: Int): String {
        val base = "اعتُمد في الإحصاءات $completeCount من $totalCount سجلًا مكتملًا؛ السجلات الناقصة مستبعدة."
        return if (archivedCount > 0) {
            "$base (منها $archivedCount لتلاميذ مؤرشفين)"
        } else {
            base
        }
    }
}
