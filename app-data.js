// ============================================================================
// تطبيق «الأستاذ» — طبقة البيانات والحسابات البيداغوجية والحفظ المحلي
// ============================================================================

const STORAGE_KEY = 'al_ustadh_db_v1';

const ArData = {
  SCHOOL_LEVELS: ['الأولى متوسط', 'الثانية متوسط', 'الثالثة متوسط', 'الرابعة متوسط'],
  SCHOOL_DAYS: ['الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس'],
  TERMS: [
    { id: 'T1', label: 'الفصل الأول' },
    { id: 'T2', label: 'الفصل الثاني' },
    { id: 'T3', label: 'الفصل الثالث' }
  ],
  CLASS_COLORS: [
    '#0F6B63', '#2B6CB0', '#B7791F', '#6B46C1',
    '#2F855A', '#C53030', '#0987A0', '#975A16'
  ],
  DEFAULT_ACTIVITIES: [
    'فهم المنطوق', 'فهم المكتوب (قراءة مشروحة)', 'الظاهرة اللغوية (قواعد)',
    'فهم المكتوب (نص أدبي)', 'إنتاج المكتوب (تعبير كتابي)', 'إدماج الكلمات / الوضعية',
    'حصة استدراك ومعالجة', 'تصحيح فرض / اختبار', 'مشروع بيداغوجي'
  ],
  DEFAULT_TRAINING_CATS: [
    'التشريع المدرسي', 'تعليمية المادة', 'البيداغوجيا والتقويم',
    'علم النفس التربوي', 'هندسة التكوين', 'الوساطة المدرسية'
  ],
  OFFICIAL_HOLIDAYS: [
    { title: 'الدخول المدرسي', date: 'سبتمبر', category: 'وطنية / مدرسية' },
    { title: 'المولد النبوي الشريف', date: '12 ربيع الأول', category: 'دينية' },
    { title: 'اندلاع الثورة التحريرية', date: '01 نوفمبر', category: 'وطنية' },
    { title: 'عطلة الشتاء', date: 'النصف الثاني من ديسمبر', category: 'عطلة مدرسية' },
    { title: 'رأس السنة الميلادية', date: '01 جانفي', category: 'رسمية' },
    { title: 'رأس السنة الأمازيغية (يناير)', date: '12 جانفي', category: 'وطنية' },
    { title: 'عطلة الربيع', date: 'النصف الثاني من مارس', category: 'عطلة مدرسية' },
    { title: 'عيد الفطر المبارك', date: '01 شوال (3 أيام)', category: 'دينية' },
    { title: 'عيد العمال', date: '01 ماي', category: 'رسمية' },
    { title: 'عيد الأضحى المبارك', date: '10 ذو الحجة (3 أيام)', category: 'دينية' },
    { title: 'عيد الاستقلال والشباب', date: '05 جويلية', category: 'وطنية' }
  ]
};

const Utils = {
  uid(prefix = 'id') {
    return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`;
  },
  toWesternDigits(str) {
    if (str == null) return '';
    return String(str)
      .replace(/[٠-٩]/g, d => '٠١٢٣٤٥٦٧٨٩'.indexOf(d))
      .replace(/[۰-۹]/g, d => '۰۱۲۳۴۵۶۷۸۹'.indexOf(d));
  },
  normalizeArabic(str) {
    if (!str) return '';
    return Utils.toWesternDigits(str)
      .trim()
      .toLowerCase()
      .replace(/[\u064B-\u065F\u0670]/g, '')
      .replace(/[أإآ]/g, 'ا')
      .replace(/ة/g, 'ه')
      .replace(/ى/g, 'ي');
  },
  todayStr() {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  },
  addDays(dateStr, delta) {
    const parts = (dateStr || Utils.todayStr()).split('-').map(Number);
    const dt = new Date(parts[0], (parts[1] || 1) - 1, parts[2] || 1);
    dt.setDate(dt.getDate() + delta);
    const y = dt.getFullYear();
    const m = String(dt.getMonth() + 1).padStart(2, '0');
    const d = String(dt.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  },
  formatAlgerianDate(dateStr, includeWeekday = false) {
    if (!dateStr) return '—';
    const months = ['جانفي', 'فيفري', 'مارس', 'أفريل', 'ماي', 'جوان', 'جويلية', 'أوت', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'];
    const days = ['الأحد', 'الاثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
    const parts = dateStr.split('-').map(Number);
    if (parts.length !== 3 || isNaN(parts[0])) return dateStr;
    const dt = new Date(parts[0], parts[1] - 1, parts[2]);
    const dayName = days[dt.getDay()];
    const monthName = months[(parts[1] - 1 + 12) % 12] || '';
    return includeWeekday
      ? `${dayName} ${parts[2]} ${monthName} ${parts[0]}`
      : `${parts[2]} ${monthName} ${parts[0]}`;
  },
  formatNum(n) {
    if (n == null || isNaN(n)) return '—';
    const rounded = Math.round(Number(n) * 100) / 100;
    return Number.isInteger(rounded) ? String(rounded) : rounded.toFixed(2);
  },
  parseGrade(val) {
    if (val === '' || val == null) return null;
    const clean = Utils.toWesternDigits(String(val)).replace(',', '.').trim();
    if (!clean) return null;
    const num = Number(clean);
    if (isNaN(num) || num < 0 || num > 20) return null;
    return Math.round(num * 100) / 100;
  },
  escapeHtml(str) {
    if (str == null) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
};

// ============================================================================
// محرك حساب المعدلات الفصلية (متطابق مع GradeCalculator في تطبيق الأندرويد)
// ============================================================================
const GradeCalc = {
  getTermConfig(snapshot, yearId, termId) {
    const found = (snapshot.gradeTermConfigs || []).find(
      c => c.schoolYearId === yearId && c.termId === termId
    );
    return found || {
      id: `cfg_${yearId}_${termId}`,
      schoolYearId: yearId,
      termId,
      ca2Active: false,
      ca2Required: false,
      as2Active: false,
      as2Required: false,
      weights: { ca: 1, asWeight: 1, exam: 2 },
      visibleColumns: { absences: true, behaviour: true, materials: false, notebook: false }
    };
  },
  computeRow(row, config) {
    const ca1 = row?.ca1 ?? null;
    const ca2 = config.ca2Active ? (row?.ca2 ?? null) : null;
    const as1 = row?.as1 ?? null;
    const as2 = config.as2Active ? (row?.as2 ?? null) : null;
    const exam = row?.exam ?? null;

    let caAvg = null;
    if (ca1 != null) {
      if (!config.ca2Active) {
        caAvg = ca1;
      } else if (ca2 != null) {
        caAvg = (ca1 + ca2) / 2;
      } else if (!config.ca2Required) {
        caAvg = ca1;
      }
    }

    let asAvg = null;
    if (as1 != null) {
      if (!config.as2Active) {
        asAvg = as1;
      } else if (as2 != null) {
        asAvg = (as1 + as2) / 2;
      } else if (!config.as2Required) {
        asAvg = as1;
      }
    }

    const wCa = Number(config.weights?.ca ?? 1);
    const wAs = Number(config.weights?.asWeight ?? 1);
    const wEx = Number(config.weights?.exam ?? 2);
    const totalW = wCa + wAs + wEx;

    let termAvg = null;
    if (caAvg != null && asAvg != null && exam != null && totalW > 0) {
      termAvg = Math.round(((caAvg * wCa + asAvg * wAs + exam * wEx) / totalW) * 100) / 100;
    }

    return {
      caAvg: caAvg != null ? Math.round(caAvg * 100) / 100 : null,
      asAvg: asAvg != null ? Math.round(asAvg * 100) / 100 : null,
      termAvg,
      isComplete: termAvg != null
    };
  }
};

// ============================================================================
// المستودع المركزي للبيانات (TeacherRepository)
// ============================================================================
const Repo = {
  state: null,
  saveStatus: 'SAVED', // 'SAVED' | 'SAVING' | 'FAILED'
  lastSavedAt: 'الآن',
  listeners: [],

  createEmptySnapshot() {
    const defaultYearId = 'sy_2025_2026';
    return {
      meta: {
        schemaVersion: 1,
        activeSchoolYearId: defaultYearId,
        theme: 'light',
        fontScale: 'medium',
        onboardingDone: false
      },
      schoolYears: [
        { id: defaultYearId, label: '2025/2026', status: 'active' }
      ],
      profile: {
        professionalName: '',
        institution: '',
        employmentStatus: 'مرسم(ة)',
        specialization: 'اللغة العربية وآدابها',
        qualifications: '',
        appointmentDate: '',
        rank: 'أستاذ التعليم المتوسط',
        inspectionHistory: '',
        promotionDetails: '',
        optionalIdentifier: ''
      },
      classes: [],
      students: [],
      timetable: [],
      lessonPlans: [],
      observations: [],
      gradeTermConfigs: [],
      gradeRows: [],
      calendarEvents: [],
      activityTemplates: ArData.DEFAULT_ACTIVITIES.map((name, idx) => ({
        id: `tpl_${idx + 1}`,
        name,
        orderIndex: idx
      })),
      trainingCategories: ArData.DEFAULT_TRAINING_CATS.map((name, idx) => ({
        id: `tcat_${idx + 1}`,
        name,
        orderIndex: idx
      })),
      trainingNotes: [],
      seminars: [],
      eduCalendarRecords: []
    };
  },

  init() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        this.state = JSON.parse(raw);
      } else {
        this.state = this.createEmptySnapshot();
        this.seedSampleData(true);
      }
    } catch (e) {
      console.error('Failed to load state:', e);
      this.state = this.createEmptySnapshot();
      this.seedSampleData(true);
    }
    return this.state;
  },

  persist() {
    this.saveStatus = 'SAVING';
    this.notify();
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.state));
      const now = new Date();
      this.lastSavedAt = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
      this.saveStatus = 'SAVED';
    } catch (e) {
      console.error('Save failed:', e);
      this.saveStatus = 'FAILED';
    }
    this.notify();
  },

  subscribe(fn) {
    this.listeners.push(fn);
  },

  notify() {
    this.listeners.forEach(fn => fn(this.state));
  },

  hasSampleData() {
    const s = this.state;
    return (
      (s.classes || []).some(x => x.isSample) ||
      (s.students || []).some(x => x.isSample) ||
      (s.lessonPlans || []).some(x => x.isSample) ||
      (s.trainingNotes || []).some(x => x.isSample)
    );
  },

  clearSampleData() {
    const s = this.state;
    s.classes = (s.classes || []).filter(x => !x.isSample);
    s.students = (s.students || []).filter(x => !x.isSample);
    s.timetable = (s.timetable || []).filter(x => !x.isSample);
    s.lessonPlans = (s.lessonPlans || []).filter(x => !x.isSample);
    s.observations = (s.observations || []).filter(x => !x.isSample);
    s.gradeRows = (s.gradeRows || []).filter(x => !x.isSample);
    s.calendarEvents = (s.calendarEvents || []).filter(x => !x.isSample);
    s.trainingNotes = (s.trainingNotes || []).filter(x => !x.isSample);
    s.seminars = (s.seminars || []).filter(x => !x.isSample);
    s.eduCalendarRecords = (s.eduCalendarRecords || []).filter(x => !x.isSample);
    this.persist();
  },

  seedSampleData(fromOnboarding = false) {
    this.clearSampleData();
    const s = this.state;
    const yearId = s.meta.activeSchoolYearId || 'sy_2025_2026';
    const today = Utils.todayStr();
    const tomorrow = Utils.addDays(today, 1);

    if (fromOnboarding && !s.profile.professionalName) {
      s.profile.professionalName = 'أستاذة اللغة العربية';
      s.profile.institution = 'متوسطة الشهيد العربي بن مهيدي';
      s.profile.employmentStatus = 'مرسم(ة)';
      s.profile.specialization = 'اللغة العربية وآدابها';
      s.profile.qualifications = 'شهادة الليسانس في اللغة والأدب العربي';
      s.profile.appointmentDate = '2019-09-01';
      s.profile.rank = 'أستاذ التعليم المتوسط — الدرجة 3';
      s.profile.inspectionHistory = 'آخر تفتيش: ماي 2025 — العلامة 17.5/20';
    }
    s.meta.onboardingDone = true;

    const c1Id = 'sample_cls_1m1';
    const c2Id = 'sample_cls_2m2';

    s.classes.push(
      {
        id: c1Id,
        schoolYearId: yearId,
        level: 'الأولى متوسط',
        groupLabel: 'م1 ف1',
        fullTitle: 'الأولى متوسط — م1 ف1',
        room: 'قاعة 04',
        colorHex: '#0F6B63',
        notes: 'فوج متميز في القراءة المشروحة والمشاركة الشفوية',
        isArchived: false,
        isSample: true
      },
      {
        id: c2Id,
        schoolYearId: yearId,
        level: 'الثانية متوسط',
        groupLabel: 'م2 ف2',
        fullTitle: 'الثانية متوسط — م2 ف2',
        room: 'قاعة 09',
        colorHex: '#2B6CB0',
        notes: 'يحتاج إلى دعم إضافي في الظاهرة اللغوية وتنظيم الكراس',
        isArchived: false,
        isSample: true
      }
    );

    const names1 = [
      'أنس بن عيسى', 'آية بوزيد', 'بلال منصوري', 'تسنيم رحماني',
      'ريان قاسمي', 'سارة بلحاج', 'عبد الرحمن شريف', 'مريم زروقي'
    ];
    const names2 = [
      'إلياس بن تومي', 'حنين مرابط', 'خالد بوعلام', 'سلسبيل عيساوي',
      'عماد الدين واضح', 'لينة بن يحيى', 'نور اليقين سعيود', 'ياسين حمزاوي'
    ];

    names1.forEach((fullName, idx) => {
      const stId = `sample_stu_1_${idx + 1}`;
      s.students.push({
        id: stId,
        schoolYearId: yearId,
        classId: c1Id,
        fullName,
        number: idx + 1,
        generalNote: idx === 0 ? 'تلميذ نجيب ومواظب' : '',
        status: 'active',
        isSample: true
      });
      s.gradeRows.push({
        id: `sample_gr_1_${idx + 1}`,
        schoolYearId: yearId,
        classId: c1Id,
        studentId: stId,
        termId: 'T1',
        ca1: 13 + (idx % 5),
        ca2: 14 + (idx % 4),
        as1: 12.5 + (idx % 6),
        as2: 14,
        exam: 13.5 + (idx % 5),
        absences: idx === 4 ? 2 : 0,
        behaviour: 'حسن',
        materials: 'مكتمل',
        notebook: 'منظم',
        note: idx === 1 ? 'تحسن ملحوظ في التعبير' : '',
        isSample: true
      });
    });

    names2.forEach((fullName, idx) => {
      const stId = `sample_stu_2_${idx + 1}`;
      s.students.push({
        id: stId,
        schoolYearId: yearId,
        classId: c2Id,
        fullName,
        number: idx + 1,
        generalNote: '',
        status: 'active',
        isSample: true
      });
      s.gradeRows.push({
        id: `sample_gr_2_${idx + 1}`,
        schoolYearId: yearId,
        classId: c2Id,
        studentId: stId,
        termId: 'T1',
        ca1: 11.5 + (idx % 6),
        ca2: 13,
        as1: 11 + (idx % 7),
        as2: 12.5,
        exam: 12 + (idx % 6),
        absences: idx === 2 ? 1 : 0,
        behaviour: 'جيد',
        materials: 'مكتمل',
        notebook: 'مقبول',
        note: '',
        isSample: true
      });
    });

    const jsDay = new Date().getDay();
    const todayIdx = jsDay <= 4 ? jsDay : 0;

    s.timetable.push(
      {
        id: 'sample_tt_1',
        schoolYearId: yearId,
        day: todayIdx,
        startTime: '08:00',
        endTime: '09:00',
        classId: c1Id,
        room: 'قاعة 04',
        note: 'الحصة الصباحية الأولى',
        isSample: true
      },
      {
        id: 'sample_tt_2',
        schoolYearId: yearId,
        day: todayIdx,
        startTime: '10:00',
        endTime: '11:00',
        classId: c2Id,
        room: 'قاعة 09',
        note: 'تطبيقات نحوية',
        isSample: true
      },
      {
        id: 'sample_tt_3',
        schoolYearId: yearId,
        day: (todayIdx + 1) % 5,
        startTime: '09:00',
        endTime: '10:00',
        classId: c1Id,
        room: 'قاعة 04',
        note: 'فهم المكتوب',
        isSample: true
      },
      {
        id: 'sample_tt_4',
        schoolYearId: yearId,
        day: (todayIdx + 2) % 5,
        startTime: '14:00',
        endTime: '15:00',
        classId: c2Id,
        room: 'قاعة 09',
        note: 'إنتاج المكتوب',
        isSample: true
      }
    );

    const lp1Id = 'sample_lp_1';
    const lp2Id = 'sample_lp_2';
    s.lessonPlans.push(
      {
        id: lp1Id,
        schoolYearId: yearId,
        date: today,
        classId: c1Id,
        startTime: '08:00',
        endTime: '09:00',
        activity: 'فهم المكتوب (قراءة مشروحة)',
        title: 'الأخلاق الفاضلة في المجتمع',
        objectives: 'أن يقرأ المتعلم النص قراءة معبرة ويستنبط الفكرة العامة والأفكار الأساسية ويشرح المفردات الصعبة.',
        contentElements: '1. وضعية الانطلاق ومراقبة التحضير القبلي.\n2. القراءة النموذجية والفردية.\n3. مناقشة مضمون النص وتحديد القيم التربوية.',
        tools: 'الكتاب المدرسي ص 24، السبورة، القاموس المدرسي',
        homework: 'تحضير النص القادم واستخراج ثلاث قيم خلقية.',
        reflectionNotes: 'تفاعل ممتاز من الفوج الأول مع النقاش الشفوي.',
        status: 'completed',
        timetableId: 'sample_tt_1',
        isSample: true
      },
      {
        id: lp2Id,
        schoolYearId: yearId,
        date: today,
        classId: c2Id,
        startTime: '10:00',
        endTime: '11:00',
        activity: 'الظاهرة اللغوية (قواعد)',
        title: 'الاسم المقصور والاسم المنقوص',
        objectives: 'أن يميز المتعلم بين الاسم المقصور والمنقوص ويعربهما إعراباً صحيحاً في وضعيات دالة.',
        contentElements: '1. عرض الأمثلة الانطلاقية على السبورة.\n2. الملاحظة والمناقشة والاستنتاج.\n3. توظيف التعلمات وحل التمرين التطبيقي.',
        tools: 'السبورة، كراس القسم، بطاقات الأمثلة',
        homework: 'حل التمرين 3 ص 31 على كراس المحاولات.',
        reflectionNotes: 'تخصيص 10 دقائق إضافية للتدريب على الإعراب التقديري.',
        status: 'planned',
        timetableId: 'sample_tt_2',
        isSample: true
      }
    );

    s.observations.push(
      {
        id: 'sample_obs_1',
        schoolYearId: yearId,
        lessonPlanId: lp1Id,
        classId: c1Id,
        studentId: 'sample_stu_1_1',
        date: today,
        type: 'participation',
        typeArabic: 'مشاركة مميزة',
        note: 'قراءة مسترسلة واستنباط دقيق للفكرة العامة',
        isSample: true
      },
      {
        id: 'sample_obs_2',
        schoolYearId: yearId,
        lessonPlanId: lp1Id,
        classId: c1Id,
        studentId: 'sample_stu_1_5',
        date: today,
        type: 'absence',
        typeArabic: 'غائب',
        note: 'غياب مبرر من طرف الولي',
        isSample: true
      }
    );

    s.calendarEvents.push(
      {
        id: 'sample_ev_1',
        schoolYearId: yearId,
        title: 'الفرض المحروس الأول للثلاثي الأول — م1 ف1',
        date: tomorrow,
        type: 'assessment',
        classId: c1Id,
        note: 'مراجعة دروس المقطع الأول (الحياة العائلية)',
        isSample: true
      },
      {
        id: 'sample_ev_2',
        schoolYearId: yearId,
        title: 'مجلس التعليم لبداية السنة الدراسية',
        date: Utils.addDays(today, 3),
        type: 'meeting',
        classId: null,
        note: 'تنسيق التوزيع السنوي والوسائل التعليمية',
        isSample: true
      }
    );

    s.trainingNotes.push({
      id: 'sample_tn_1',
      schoolYearId: yearId,
      title: 'بناء الوضعيات الإدماجية وفق المقاربة بالكفاءات',
      date: today,
      categoryName: 'تعليمية المادة',
      text: 'ترتكز الوضعية الإدماجية على السياق الدال، السند الوظيفي، والتعليمة الواضحة مع شبكة تقويم معيارية (الوجاهة، الانسجام، سلامة اللغة، الإتقان).',
      isSample: true
    });

    s.seminars.push({
      id: 'sample_sem_1',
      schoolYearId: yearId,
      kind: 'external',
      title: 'تقويم المكتسبات والمعالجة البيداغوجية الفورية',
      date: today,
      location: 'متوسطة العقيد لطفي المركزية',
      facilitator: 'السيد مفتش التربية الوطنية للمادة',
      mainIdeas: 'تحليل نتائج التقويم التشخيصي وتصنيف صعوبات التعلم حسب الميادين.',
      recommendations: 'تفعيل حصص المعالجة البيداغوجية بأفواج مصغرة مرنة.',
      isSample: true
    });

    s.eduCalendarRecords.push({
      id: 'sample_edu_1',
      schoolYearId: yearId,
      type: 'pedagogicalSeminar',
      date: today,
      topic: 'تسيير حصة فهم المنطوق واستثمار السندات السمعية',
      appliedLesson: 'درس تطبيقي في فهم المنطوق',
      teacherName: 'الأستاذة زروقي',
      level: 'الأولى متوسط',
      location: 'متوسطة الشهيد العربي بن مهيدي',
      internshipType: '',
      isSample: true
    });

    this.persist();
  }
};
