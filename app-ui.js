// ============================================================================
// تطبيق «الأستاذ» — واجهة المستخدم التفاعلية وجميع الشاشات والنوافذ والطباعة A4
// ============================================================================

const AppUI = {
  currentRoute: 'home',
  viewedYearId: 'sy_2025_2026',
  selectedDate: Utils.todayStr(),
  plannerViewMode: 'daily', // 'daily' | 'weekly'
  plannerClassFilter: 'ALL',
  selectedClassId: null,
  classSubTab: 'students', // 'students' | 'gradebook'
  studentSearch: '',
  selectedTermId: 'T1',
  scheduleSubTab: 'timetable', // 'timetable' | 'assessments' | 'calendar'
  scheduleDayIdx: Math.min(new Date().getDay(), 4),
  trainingSubTab: 'notes', // 'notes' | 'seminars' | 'eduCalendar'
  trainingCategoryFilter: 'ALL',
  trainingSearch: '',
  settingsSubTab: 'appearance', // 'appearance' | 'grading' | 'templates' | 'years' | 'backup'

  init() {
    const snap = Repo.init();
    this.viewedYearId = snap.meta.activeSchoolYearId || 'sy_2025_2026';
    this.applyThemeAndFont();
    Repo.subscribe(() => {
      this.applyThemeAndFont();
      this.render();
    });
    this.render();
  },

  applyThemeAndFont() {
    const meta = Repo.state.meta || {};
    document.documentElement.setAttribute('data-theme', meta.theme || 'light');
    document.documentElement.setAttribute('data-font', meta.fontScale || 'medium');
  },

  navigate(route, opts = {}) {
    this.currentRoute = route;
    if (opts.classId !== undefined) this.selectedClassId = opts.classId;
    if (opts.classSubTab) this.classSubTab = opts.classSubTab;
    if (opts.date) this.selectedDate = opts.date;
    window.scrollTo({ top: 0, behavior: 'smooth' });
    this.render();
  },

  isReadOnly() {
    return this.viewedYearId !== Repo.state.meta.activeSchoolYearId;
  },

  getYearClasses() {
    return (Repo.state.classes || []).filter(
      c => c.schoolYearId === this.viewedYearId && !c.isArchived
    );
  },

  render() {
    const s = Repo.state;
    const activeYear = (s.schoolYears || []).find(y => y.id === s.meta.activeSchoolYearId) || { label: '2025/2026' };
    const viewedYear = (s.schoolYears || []).find(y => y.id === this.viewedYearId) || activeYear;
    const isArchived = this.isReadOnly();

    // Update Top Bar
    const yearPill = document.getElementById('top-year-pill');
    if (yearPill) {
      yearPill.className = `year-pill ${isArchived ? 'archived' : ''}`;
      yearPill.textContent = isArchived ? `أرشيف ${viewedYear.label}` : viewedYear.label;
    }

    const saveBadge = document.getElementById('save-status-badge');
    if (saveBadge) {
      if (Repo.saveStatus === 'SAVING') {
        saveBadge.className = 'save-badge saving';
        saveBadge.innerHTML = '⏳ جاري الحفظ...';
      } else if (Repo.saveStatus === 'FAILED') {
        saveBadge.className = 'save-badge failed';
        saveBadge.innerHTML = '⚠️ تعذر الحفظ';
      } else {
        saveBadge.className = 'save-badge';
        saveBadge.innerHTML = `✓ تم الحفظ تلقائيًا (${Repo.lastSavedAt})`;
      }
    }

    const archivedBanner = document.getElementById('archived-banner-slot');
    if (archivedBanner) {
      if (isArchived) {
        archivedBanner.innerHTML = `
          <div class="archived-banner">
            <span>📁 وضع القراءة فقط — تتصفحين حاليًا السنة المؤرشفة (${Utils.escapeHtml(viewedYear.label)})</span>
            <button class="btn btn-sm btn-outline" onclick="AppUI.switchToActiveYear()">العودة للسنة النشطة (${Utils.escapeHtml(activeYear.label)})</button>
          </div>
        `;
      } else {
        archivedBanner.innerHTML = '';
      }
    }

    // Highlight active nav items
    document.querySelectorAll('[data-route]').forEach(btn => {
      const r = btn.getAttribute('data-route');
      btn.classList.toggle('active', r === this.currentRoute);
    });

    const main = document.getElementById('main-view');
    if (!main) return;

    switch (this.currentRoute) {
      case 'home': main.innerHTML = this.renderHome(); break;
      case 'planner': main.innerHTML = this.renderPlanner(); break;
      case 'classes': main.innerHTML = this.renderClasses(); break;
      case 'gradebook': main.innerHTML = this.renderGradebookStandalone(); break;
      case 'schedule': main.innerHTML = this.renderSchedule(); break;
      case 'training': main.innerHTML = this.renderTraining(); break;
      case 'profile': main.innerHTML = this.renderProfile(); break;
      case 'settings': main.innerHTML = this.renderSettings(); break;
      default: main.innerHTML = this.renderHome();
    }
  },

  switchToActiveYear() {
    this.viewedYearId = Repo.state.meta.activeSchoolYearId;
    this.render();
  },

  toggleTheme() {
    Repo.state.meta.theme = Repo.state.meta.theme === 'dark' ? 'light' : 'dark';
    Repo.persist();
  },

  // ==========================================================================
  // 1. الشاشة الرئيسية (Home Screen)
  // ==========================================================================
  renderHome() {
    const s = Repo.state;
    const classes = this.getYearClasses();
    const studentsCount = (s.students || []).filter(st => st.schoolYearId === this.viewedYearId).length;
    const ttCount = (s.timetable || []).filter(t => t.schoolYearId === this.viewedYearId).length;
    const lpCount = (s.lessonPlans || []).filter(l => l.schoolYearId === this.viewedYearId).length;
    const viewedYear = (s.schoolYears || []).find(y => y.id === this.viewedYearId) || { label: '2025/2026' };

    const hour = new Date().getHours();
    const greeting = hour < 12 ? 'صباح الخير' : 'مساء الخير';
    const profName = s.profile?.professionalName?.trim() || 'أستاذتي الكريمة';
    const todayStr = Utils.todayStr();
    const jsDay = new Date().getDay();
    const todayIdx = jsDay <= 4 ? jsDay : 0;

    const todaySlots = (s.timetable || [])
      .filter(t => t.schoolYearId === this.viewedYearId && t.day === todayIdx)
      .sort((a, b) => a.startTime.localeCompare(b.startTime));

    const todayLessons = (s.lessonPlans || []).filter(
      l => l.schoolYearId === this.viewedYearId && l.date === todayStr
    );

    const upcomingEvents = (s.calendarEvents || [])
      .filter(e => e.schoolYearId === this.viewedYearId && e.date >= todayStr)
      .sort((a, b) => a.date.localeCompare(b.date))
      .slice(0, 4);

    const sampleBanner = Repo.hasSampleData() ? `
      <div class="card" style="background: var(--accent-surface); border-color: var(--accent); display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:0.75rem;">
        <div>
          <strong style="color: var(--warning);">✨ وضع التجربة ببيانات نموذجية مفعّل</strong>
          <div style="font-size:0.86rem; color: var(--text-secondary);">يمكنكِ استكشاف جميع ميزات التطبيق أو مسح البيانات التجريبية في أي وقت للبدء ببياناتكِ الفعلية.</div>
        </div>
        <button class="btn btn-sm btn-outline" onclick="AppUI.confirmClearSample()">حذف البيانات التجريبية</button>
      </div>
    ` : '';

    return `
      ${sampleBanner}
      <div class="hero-card">
        <div class="row-between">
          <div>
            <div style="font-size:1.35rem; font-weight:800; color:var(--text);">${greeting}، ${Utils.escapeHtml(profName)} 👋</div>
            <div style="font-size:0.92rem; color:var(--text-secondary); margin-top:0.2rem;">
              📅 ${Utils.formatAlgerianDate(todayStr, true)} • ${Utils.escapeHtml(s.profile?.institution || 'الكراس اليومي وتسيير الأقسام')}
            </div>
          </div>
          <span class="badge badge-primary">السنة الدراسية ${Utils.escapeHtml(viewedYear.label)}</span>
        </div>

        <div class="metrics-grid">
          <div class="metric-box">
            <div class="metric-val">${classes.length}</div>
            <div class="metric-lbl">الأقسام النشطة</div>
          </div>
          <div class="metric-box">
            <div class="metric-val">${studentsCount}</div>
            <div class="metric-lbl">مجموع التلاميذ</div>
          </div>
          <div class="metric-box">
            <div class="metric-val">${ttCount}</div>
            <div class="metric-lbl">حصص الأسبوع</div>
          </div>
          <div class="metric-box">
            <div class="metric-val">${lpCount}</div>
            <div class="metric-lbl">تحضيرات الحصص</div>
          </div>
        </div>
      </div>

      <div class="grid-2">
        <div>
          <div class="row-between" style="margin-bottom:0.65rem;">
            <h3 style="font-size:1.1rem; font-weight:800;">📖 حصص اليوم والتحضير (${ArData.SCHOOL_DAYS[todayIdx]})</h3>
            <button class="btn btn-sm btn-tonal" onclick="AppUI.navigate('planner')">فتح الكراس اليومي</button>
          </div>
          ${todaySlots.length === 0 ? `
            <div class="empty-state">
              <div class="empty-icon">🗓️</div>
              <div style="font-weight:700;">لا توجد حصص مبرمجة لهذا اليوم</div>
              <div style="font-size:0.85rem; color:var(--text-secondary); margin:0.35rem 0 0.85rem;">أضيفي حصصكِ في التوقيت الأسبوعي لتظهر هنا تلقائيًا كل يوم.</div>
              <button class="btn btn-sm btn-primary" onclick="AppUI.navigate('schedule')">إعداد التوقيت الأسبوعي</button>
            </div>
          ` : todaySlots.map(slot => {
            const cls = classes.find(c => c.id === slot.classId);
            const linkedLesson = todayLessons.find(l => l.timetableId === slot.id || (l.classId === slot.classId && l.startTime === slot.startTime));
            const color = cls?.colorHex || '#0F6B63';
            return `
              <div class="strip-card">
                <div class="color-strip" style="background:${color};"></div>
                <div class="strip-card-body">
                  <div class="row-between">
                    <div>
                      <span class="badge" style="background:${color}22; color:${color};">${Utils.escapeHtml(cls?.fullTitle || 'قسم')}</span>
                      <span style="font-weight:700; font-size:0.88rem; margin-right:0.4rem;">⏰ ${slot.startTime} – ${slot.endTime}</span>
                      ${slot.room ? `<span style="font-size:0.82rem; color:var(--text-secondary);"> • ${Utils.escapeHtml(slot.room)}</span>` : ''}
                    </div>
                    <span class="badge ${linkedLesson ? 'badge-success' : 'badge-warning'}">
                      ${linkedLesson ? '✓ محضّرة' : 'غير محضّرة'}
                    </span>
                  </div>
                  <div style="margin-top:0.5rem; font-weight:700; color:var(--text);">
                    ${linkedLesson ? `${Utils.escapeHtml(linkedLesson.activity)}: ${Utils.escapeHtml(linkedLesson.title)}` : (Utils.escapeHtml(slot.note) || 'لم يتم تحضير هذه الحصة بعد')}
                  </div>
                  <div class="row-wrap" style="margin-top:0.65rem;">
                    ${linkedLesson ? `
                      <button class="btn btn-sm btn-primary" onclick="AppUI.openLessonModal('${linkedLesson.id}')">تعديل التحضير</button>
                      <button class="btn btn-sm btn-tonal" onclick="AppUI.openQuickObsModal('${linkedLesson.id}')">ملاحظات الحصة</button>
                    ` : `
                      <button class="btn btn-sm btn-primary" onclick="AppUI.createLessonFromSlot('${slot.id}', '${todayStr}')">+ تحضير الحصة الآن</button>
                    `}
                  </div>
                </div>
              </div>
            `;
          }).join('')}
        </div>

        <div>
          <div class="row-between" style="margin-bottom:0.65rem;">
            <h3 style="font-size:1.1rem; font-weight:800;">⚡ إجراءات سريعة</h3>
          </div>
          <div class="grid-2" style="margin-bottom:1rem;">
            <button class="card" style="text-align:right; cursor:pointer; margin:0;" onclick="AppUI.openLessonModal()">
              <div style="font-size:1.35rem;">✍️</div>
              <div style="font-weight:800; margin-top:0.25rem;">تحضير حصة جديدة</div>
              <div style="font-size:0.78rem; color:var(--text-secondary);">إضافة مذكرة يومية للكراس</div>
            </button>
            <button class="card" style="text-align:right; cursor:pointer; margin:0;" onclick="AppUI.navigate('gradebook')">
              <div style="font-size:1.35rem;">📊</div>
              <div style="font-weight:800; margin-top:0.25rem;">حجز النقاط والمعدلات</div>
              <div style="font-size:0.78rem; color:var(--text-secondary);">حساب آلي فوري للمعدل الفصلي</div>
            </button>
            <button class="card" style="text-align:right; cursor:pointer; margin:0;" onclick="AppUI.navigate('classes')">
              <div style="font-size:1.35rem;">👥</div>
              <div style="font-weight:800; margin-top:0.25rem;">الأقسام والتلاميذ</div>
              <div style="font-size:0.78rem; color:var(--text-secondary);">إدارة القوائم وملفات التلاميذ</div>
            </button>
            <button class="card" style="text-align:right; cursor:pointer; margin:0;" onclick="AppUI.exportBackupJson()">
              <div style="font-size:1.35rem;">💾</div>
              <div style="font-weight:800; margin-top:0.25rem;">نسخة احتياطية JSON</div>
              <div style="font-size:0.78rem; color:var(--text-secondary);">حفظ جميع بياناتكِ بأمان</div>
            </button>
          </div>

          <div class="row-between" style="margin-bottom:0.65rem;">
            <h3 style="font-size:1.1rem; font-weight:800;">📌 المواعيد والفروض القادمة</h3>
            <button class="btn btn-sm btn-outline" onclick="AppUI.openEventModal()">+ موعد جديد</button>
          </div>
          ${upcomingEvents.length === 0 ? `
            <div class="card" style="color:var(--text-secondary); text-align:center;">لا توجد مواعيد أو فروض مبرمجة قريبًا.</div>
          ` : upcomingEvents.map(ev => {
            const cls = classes.find(c => c.id === ev.classId);
            return `
              <div class="card" style="padding:0.85rem 1rem; margin-bottom:0.6rem;">
                <div class="row-between">
                  <span style="font-weight:800;">${Utils.escapeHtml(ev.title)}</span>
                  <span class="badge ${ev.type === 'assessment' ? 'badge-warning' : 'badge-primary'}">
                    ${ev.type === 'assessment' ? 'فرض / اختبار' : 'موعد تربوي'}
                  </span>
                </div>
                <div style="font-size:0.84rem; color:var(--text-secondary); margin-top:0.25rem;">
                  📅 ${Utils.formatAlgerianDate(ev.date, true)} ${cls ? `• ${Utils.escapeHtml(cls.fullTitle)}` : ''}
                </div>
              </div>
            `;
          }).join('')}
        </div>
      </div>
    `;
  },

  // ==========================================================================
  // 2. الكراس اليومي (Daily Planner Screen)
  // ==========================================================================
  renderPlanner() {
    const s = Repo.state;
    const classes = this.getYearClasses();
    const allPlans = (s.lessonPlans || []).filter(l => l.schoolYearId === this.viewedYearId);

    const filteredPlans = allPlans.filter(l => {
      if (this.plannerClassFilter !== 'ALL' && l.classId !== this.plannerClassFilter) return false;
      if (this.plannerViewMode === 'daily') {
        return l.date === this.selectedDate;
      } else {
        const endWeek = Utils.addDays(this.selectedDate, 6);
        return l.date >= this.selectedDate && l.date <= endWeek;
      }
    }).sort((a, b) => (a.date + a.startTime).localeCompare(b.date + b.startTime));

    return `
      <div class="section-header">
        <div class="section-title">كراسي اليومي</div>
        <div class="section-subtitle">تحضير الحصص اليومية، تتبع الإنجاز، ورصد ملاحظات التلاميذ في الحصة</div>
      </div>

      <div class="row-wrap" style="margin-bottom:0.85rem;">
        <button class="btn btn-primary" style="flex:1;" onclick="AppUI.openLessonModal()" ${this.isReadOnly() ? 'disabled' : ''}>
          + تحضير حصة جديدة
        </button>
        <button class="btn btn-outline" style="flex:1;" onclick="AppUI.printDailyPlanner()">
          🖨️ طباعة الكراس A4
        </button>
      </div>

      <div class="pill-row">
        <button class="pill-chip ${this.plannerViewMode === 'daily' ? 'active' : ''}" onclick="AppUI.plannerViewMode='daily'; AppUI.render();">
          📅 عرض يومي
        </button>
        <button class="pill-chip ${this.plannerViewMode === 'weekly' ? 'active' : ''}" onclick="AppUI.plannerViewMode='weekly'; AppUI.render();">
          🗓️ عرض أسبوعي (7 أيام)
        </button>
      </div>

      <div class="card raised" style="padding:0.85rem;">
        <div class="row-between">
          <button class="btn btn-sm btn-outline" onclick="AppUI.selectedDate=Utils.addDays(AppUI.selectedDate, -1); AppUI.render();">▶ اليوم السابق</button>
          <div style="text-align:center;">
            <div style="font-weight:800;">${Utils.formatAlgerianDate(this.selectedDate, true)}</div>
            <input type="date" value="${this.selectedDate}" onchange="AppUI.selectedDate=this.value; AppUI.render();" style="border:none; background:transparent; color:var(--primary-text); font-weight:700; font-size:0.82rem; cursor:pointer;" />
          </div>
          <div class="row-wrap">
            <button class="btn btn-sm btn-tonal" onclick="AppUI.selectedDate=Utils.todayStr(); AppUI.render();">اليوم الحالي</button>
            <button class="btn btn-sm btn-outline" onclick="AppUI.selectedDate=Utils.addDays(AppUI.selectedDate, 1); AppUI.render();">اليوم الموالي ◀</button>
          </div>
        </div>
        <div style="margin-top:0.65rem;">
          <select class="form-control" onchange="AppUI.plannerClassFilter=this.value; AppUI.render();">
            <option value="ALL" ${this.plannerClassFilter === 'ALL' ? 'selected' : ''}>جميع الأقسام</option>
            ${classes.map(c => `<option value="${c.id}" ${this.plannerClassFilter === c.id ? 'selected' : ''}>${Utils.escapeHtml(c.fullTitle)}</option>`).join('')}
          </select>
        </div>
      </div>

      ${filteredPlans.length === 0 ? `
        <div class="empty-state">
          <div class="empty-icon">📓</div>
          <div style="font-weight:800;">لا توجد حصص محضّرة في هذا التاريخ</div>
          <div style="font-size:0.88rem; color:var(--text-secondary); margin:0.35rem 0 0.9rem;">ابدئي بتحضير حصة جديدة أو استوردي حصة من جدول التوقيت الأسبوعي.</div>
          <button class="btn btn-primary" onclick="AppUI.openLessonModal()">+ تحضير حصة جديدة</button>
        </div>
      ` : filteredPlans.map(lp => {
        const cls = classes.find(c => c.id === lp.classId);
        const color = cls?.colorHex || '#0F6B63';
        const obsCount = (s.observations || []).filter(o => o.lessonPlanId === lp.id).length;
        const statusBadge = lp.status === 'completed'
          ? '<span class="badge badge-success">✓ منجزة</span>'
          : lp.status === 'postponed'
          ? '<span class="badge badge-warning">⏳ مؤجلة</span>'
          : '<span class="badge badge-primary">مخططة</span>';

        return `
          <div class="strip-card">
            <div class="color-strip" style="background:${color};"></div>
            <div class="strip-card-body">
              <div class="row-between">
                <div class="row-wrap">
                  <span class="badge" style="background:${color}22; color:${color};">${Utils.escapeHtml(cls?.fullTitle || 'قسم')}</span>
                  <span class="badge badge-neutral">⏰ ${lp.startTime} – ${lp.endTime}</span>
                  <span class="badge badge-primary">${Utils.escapeHtml(lp.activity)}</span>
                </div>
                ${statusBadge}
              </div>

              <h3 style="font-size:1.15rem; font-weight:800; margin-top:0.55rem;">${Utils.escapeHtml(lp.title)}</h3>
              <div style="font-size:0.82rem; color:var(--text-secondary); margin-bottom:0.5rem;">📅 ${Utils.formatAlgerianDate(lp.date, true)}</div>

              ${lp.objectives ? `<div style="margin-top:0.4rem;"><strong>🎯 الأهداف التعلمية:</strong> <span style="white-space:pre-line;">${Utils.escapeHtml(lp.objectives)}</span></div>` : ''}
              ${lp.contentElements ? `<div style="margin-top:0.4rem;"><strong>📋 سير الحصة وعناصر الدرس:</strong><div style="white-space:pre-line; background:var(--raised); padding:0.6rem; border-radius:10px; margin-top:0.25rem; font-size:0.9rem;">${Utils.escapeHtml(lp.contentElements)}</div></div>` : ''}
              ${lp.tools ? `<div style="margin-top:0.4rem; font-size:0.9rem;"><strong>🧰 الوسائل والسندات:</strong> ${Utils.escapeHtml(lp.tools)}</div>` : ''}
              ${lp.homework ? `<div style="margin-top:0.4rem; font-size:0.9rem;"><strong>📝 الواجب المنزلي:</strong> ${Utils.escapeHtml(lp.homework)}</div>` : ''}
              ${lp.reflectionNotes ? `<div style="margin-top:0.4rem; font-size:0.9rem; color:var(--primary-text);"><strong>💡 ملاحظات تقييمية بعد الحصة:</strong> ${Utils.escapeHtml(lp.reflectionNotes)}</div>` : ''}

              <div class="row-between" style="margin-top:0.85rem; padding-top:0.65rem; border-top:1px solid var(--border);">
                <div class="row-wrap">
                  <button class="btn btn-sm btn-tonal" onclick="AppUI.openQuickObsModal('${lp.id}')">
                    👥 ملاحظات الحصة ${obsCount > 0 ? `(${obsCount})` : ''}
                  </button>
                  <button class="btn btn-sm btn-outline" onclick="AppUI.duplicateLesson('${lp.id}')">📋 نسخ الحصة</button>
                  <button class="btn btn-sm btn-outline" onclick="AppUI.printSingleLesson('${lp.id}')">🖨️ طباعة A4</button>
                </div>
                <div class="row-wrap">
                  <button class="btn btn-sm btn-outline" onclick="AppUI.openLessonModal('${lp.id}')">✏️ تعديل</button>
                  <button class="btn btn-sm btn-danger" onclick="AppUI.deleteLesson('${lp.id}')">🗑️ حذف</button>
                </div>
              </div>
            </div>
          </div>
        `;
      }).join('')}
    `;
  },

  // ==========================================================================
  // 3. أقسامي وتلاميذي (Classes & Students Screen)
  // ==========================================================================
  renderClasses() {
    const classes = this.getYearClasses();
    if (this.selectedClassId) {
      const cls = classes.find(c => c.id === this.selectedClassId);
      if (cls) return this.renderClassDetail(cls);
      this.selectedClassId = null;
    }

    return `
      <div class="row-between section-header">
        <div>
          <div class="section-title">أقسامي وتلاميذي</div>
          <div class="section-subtitle">إدارة الأفواج التربوية، قوائم التلاميذ، المتابعة الفردية، وكشوف النقاط</div>
        </div>
        <button class="btn btn-primary" onclick="AppUI.openClassModal()" ${this.isReadOnly() ? 'disabled' : ''}>+ إضافة قسم جديد</button>
      </div>

      ${classes.length === 0 ? `
        <div class="empty-state">
          <div class="empty-icon">🏫</div>
          <div style="font-weight:800;">لا توجد أقسام مسجلة في هذه السنة</div>
          <div style="font-size:0.88rem; color:var(--text-secondary); margin:0.35rem 0 0.9rem;">أضيفي أقسامكِ الدراسية للبدء في تسجيل التلاميذ، الحصص، والنقاط.</div>
          <button class="btn btn-primary" onclick="AppUI.openClassModal()">+ إضافة قسم الآن</button>
        </div>
      ` : `
        <div class="grid-2">
          ${classes.map(cls => {
            const stuCount = (Repo.state.students || []).filter(s => s.classId === cls.id && s.status === 'active').length;
            const ttCount = (Repo.state.timetable || []).filter(t => t.classId === cls.id).length;
            return `
              <div class="strip-card" style="margin-bottom:0;">
                <div class="color-strip" style="background:${cls.colorHex || '#0F6B63'};"></div>
                <div class="strip-card-body">
                  <div class="row-between">
                    <span class="badge" style="background:${cls.colorHex}22; color:${cls.colorHex};">${Utils.escapeHtml(cls.level)}</span>
                    ${cls.room ? `<span class="badge badge-neutral">📍 ${Utils.escapeHtml(cls.room)}</span>` : ''}
                  </div>
                  <h3 style="font-size:1.2rem; font-weight:800; margin-top:0.45rem;">${Utils.escapeHtml(cls.fullTitle)}</h3>
                  <div style="font-size:0.88rem; color:var(--text-secondary); margin-top:0.25rem;">
                    👥 التلاميذ: <strong>${stuCount}</strong> • 🗓️ حصص الأسبوع: <strong>${ttCount}</strong>
                  </div>
                  ${cls.notes ? `<div style="font-size:0.84rem; color:var(--text-secondary); margin-top:0.35rem;">${Utils.escapeHtml(cls.notes)}</div>` : ''}
                  <div class="row-between" style="margin-top:0.9rem; padding-top:0.65rem; border-top:1px solid var(--border);">
                    <div class="row-wrap">
                      <button class="btn btn-sm btn-primary" onclick="AppUI.navigate('classes', {classId:'${cls.id}', classSubTab:'students'})">👥 التلاميذ (${stuCount})</button>
                      <button class="btn btn-sm btn-tonal" onclick="AppUI.navigate('classes', {classId:'${cls.id}', classSubTab:'gradebook'})">📊 كشف النقاط</button>
                    </div>
                    <div class="row-wrap">
                      <button class="btn btn-sm btn-outline" onclick="AppUI.openClassModal('${cls.id}')">✏️</button>
                      <button class="btn btn-sm btn-danger" onclick="AppUI.deleteClass('${cls.id}')">🗑️</button>
                    </div>
                  </div>
                </div>
              </div>
            `;
          }).join('')}
        </div>
      `}
    `;
  },

  renderClassDetail(cls) {
    const allStudents = (Repo.state.students || [])
      .filter(s => s.classId === cls.id && s.status === 'active')
      .sort((a, b) => a.number - b.number);

    const normQ = Utils.normalizeArabic(this.studentSearch);
    const filteredStudents = allStudents.filter(st =>
      !normQ || Utils.normalizeArabic(st.fullName).includes(normQ)
    );

    return `
      <div class="row-between" style="margin-bottom:0.85rem;">
        <div class="row-wrap">
          <button class="btn btn-sm btn-outline" onclick="AppUI.selectedClassId=null; AppUI.render();">▶ كل الأقسام</button>
          <span class="badge" style="background:${cls.colorHex}22; color:${cls.colorHex}; font-size:0.95rem; padding:0.35rem 0.85rem;">
            ${Utils.escapeHtml(cls.fullTitle)}
          </span>
          ${cls.room ? `<span class="badge badge-neutral">📍 ${Utils.escapeHtml(cls.room)}</span>` : ''}
        </div>
      </div>

      <div class="pill-row">
        <button class="pill-chip ${this.classSubTab === 'students' ? 'active' : ''}" onclick="AppUI.classSubTab='students'; AppUI.render();">
          👥 قائمة التلاميذ (${allStudents.length})
        </button>
        <button class="pill-chip ${this.classSubTab === 'gradebook' ? 'active' : ''}" onclick="AppUI.classSubTab='gradebook'; AppUI.render();">
          📊 كشف النقاط الفصلي
        </button>
      </div>

      ${this.classSubTab === 'gradebook' ? this.renderGradebookForClass(cls, allStudents) : `
        <div class="card" style="padding:0.9rem;">
          <input type="text" class="form-control" placeholder="🔍 بحث في تلاميذ القسم بالاسم..." value="${Utils.escapeHtml(this.studentSearch)}" oninput="AppUI.studentSearch=this.value; AppUI.render();" style="margin-bottom:0.65rem;" />
          <div class="row-wrap">
            <button class="btn btn-primary" style="flex:1;" onclick="AppUI.openStudentModal('${cls.id}')">+ إضافة تلميذ</button>
            <button class="btn btn-tonal" style="flex:1;" onclick="AppUI.openBatchStudentsModal('${cls.id}')">📋 لصق قائمة أسماء</button>
          </div>
        </div>

        ${filteredStudents.length === 0 ? `
          <div class="empty-state">
            <div class="empty-icon">🎓</div>
            <div style="font-weight:800;">لا يوجد تلاميذ مطابقون</div>
            <div style="font-size:0.86rem; color:var(--text-secondary); margin-top:0.25rem;">أضيفي التلاميذ فرديًا أو الصقي القائمة الكاملة دفعة واحدة.</div>
          </div>
        ` : filteredStudents.map(st => {
          const obs = (Repo.state.observations || []).filter(o => o.studentId === st.id);
          const absCount = obs.filter(o => o.type === 'absence').length;
          const lateCount = obs.filter(o => o.type === 'lateness').length;
          const posCount = obs.filter(o => o.type === 'participation').length;
          return `
            <div class="card" style="padding:0.9rem 1rem; margin-bottom:0.65rem;">
              <div class="row-between">
                <div class="row-wrap">
                  <span class="badge badge-primary" style="min-width:32px; justify-content:center;">${st.number}</span>
                  <strong style="font-size:1.03rem;">${Utils.escapeHtml(st.fullName)}</strong>
                </div>
                <div class="row-wrap">
                  ${absCount > 0 ? `<span class="badge badge-error">غياب: ${absCount}</span>` : ''}
                  ${lateCount > 0 ? `<span class="badge badge-warning">تأخر: ${lateCount}</span>` : ''}
                  ${posCount > 0 ? `<span class="badge badge-success">تميز: ${posCount}</span>` : ''}
                  ${obs.length === 0 ? `<span class="badge badge-neutral">بدون ملاحظات</span>` : ''}
                </div>
              </div>
              ${st.generalNote ? `<div style="font-size:0.85rem; color:var(--text-secondary); margin-top:0.35rem;">📌 ${Utils.escapeHtml(st.generalNote)}</div>` : ''}
              <div class="row-between" style="margin-top:0.65rem; padding-top:0.5rem; border-top:1px solid var(--border);">
                <button class="btn btn-sm btn-tonal" onclick="AppUI.openStudentProfileModal('${st.id}')">🗂️ ملف التلميذ والنقاط</button>
                <div class="row-wrap">
                  <button class="btn btn-sm btn-outline" onclick="AppUI.openStudentModal('${cls.id}', '${st.id}')">✏️ تعديل</button>
                  <button class="btn btn-sm btn-danger" onclick="AppUI.deleteStudent('${st.id}')">🗑️ حذف</button>
                </div>
              </div>
            </div>
          `;
        }).join('')}
      `}
    `;
  },

  // ==========================================================================
  // 4. كشف النقاط الفصلي (Gradebook View)
  // ==========================================================================
  renderGradebookStandalone() {
    const classes = this.getYearClasses();
    if (classes.length === 0) {
      return `
        <div class="section-header">
          <div class="section-title">كشف النقاط الفصلي</div>
          <div class="section-subtitle">حساب آلي فوري للمعدلات الفصلية مع التصدير بصيغة CSV والطباعة الرسمية A4</div>
        </div>
        <div class="empty-state">
          <div class="empty-icon">📊</div>
          <div style="font-weight:800;">أضيفي قسمًا دراسيًا أولًا</div>
          <button class="btn btn-primary" style="margin-top:0.75rem;" onclick="AppUI.navigate('classes')">الذهاب إلى الأقسام</button>
        </div>
      `;
    }
    const activeCls = classes.find(c => c.id === this.selectedClassId) || classes[0];
    this.selectedClassId = activeCls.id;
    const students = (Repo.state.students || [])
      .filter(s => s.classId === activeCls.id && s.status === 'active')
      .sort((a, b) => a.number - b.number);

    return `
      <div class="section-header">
        <div class="section-title">كشف النقاط الفصلي</div>
        <div class="section-subtitle">رصد نقاط التقويم المستمر، الفروض، والاختبارات مع حساب المعدل الفصلي آليًا</div>
      </div>
      <div class="card" style="padding:0.85rem;">
        <label class="form-label">اختيار القسم الدراسي:</label>
        <select class="form-control" onchange="AppUI.selectedClassId=this.value; AppUI.render();">
          ${classes.map(c => `<option value="${c.id}" ${c.id === activeCls.id ? 'selected' : ''}>${Utils.escapeHtml(c.fullTitle)}</option>`).join('')}
        </select>
      </div>
      ${this.renderGradebookForClass(activeCls, students)}
    `;
  },

  renderGradebookForClass(cls, students) {
    const s = Repo.state;
    const cfg = GradeCalc.getTermConfig(s, this.viewedYearId, this.selectedTermId);
    const rows = (s.gradeRows || []).filter(
      r => r.schoolYearId === this.viewedYearId && r.classId === cls.id && r.termId === this.selectedTermId
    );

    const computedList = students.map(st => {
      const r = rows.find(x => x.studentId === st.id) || { studentId: st.id };
      const calc = GradeCalc.computeRow(r, cfg);
      return { st, r, calc };
    });

    const validAvgs = computedList.map(x => x.calc.termAvg).filter(v => v != null);
    const classAvg = validAvgs.length > 0 ? validAvgs.reduce((a, b) => a + b, 0) / validAvgs.length : null;
    const maxAvg = validAvgs.length > 0 ? Math.max(...validAvgs) : null;
    const passCount = validAvgs.filter(v => v >= 10).length;
    const passRate = validAvgs.length > 0 ? Math.round((passCount / validAvgs.length) * 100) : null;

    return `
      <div class="pill-row">
        ${ArData.TERMS.map(t => `
          <button class="pill-chip ${this.selectedTermId === t.id ? 'active' : ''}" onclick="AppUI.selectedTermId='${t.id}'; AppUI.render();">
            ${t.label}
          </button>
        `).join('')}
      </div>

      <div class="row-wrap" style="margin-bottom:0.85rem;">
        <button class="btn btn-outline" style="flex:1;" onclick="AppUI.exportGradebookCsv('${cls.id}')">📥 تصدير CSV</button>
        <button class="btn btn-primary" style="flex:1;" onclick="AppUI.printGradebook('${cls.id}')">🖨️ طباعة الكشف A4</button>
      </div>

      <div class="metrics-grid" style="margin-bottom:1rem;">
        <div class="metric-box">
          <div class="metric-val">${Utils.formatNum(classAvg)}</div>
          <div class="metric-lbl">معدل القسم</div>
        </div>
        <div class="metric-box">
          <div class="metric-val">${Utils.formatNum(maxAvg)}</div>
          <div class="metric-lbl">أعلى معدل</div>
        </div>
        <div class="metric-box">
          <div class="metric-val">${passRate != null ? passRate + '%' : '—'}</div>
          <div class="metric-lbl">نسبة النجاح (≥10)</div>
        </div>
        <div class="metric-box">
          <div class="metric-val">${validAvgs.length}/${students.length}</div>
          <div class="metric-lbl">المعدلات المكتملة</div>
        </div>
      </div>

      ${students.length === 0 ? `
        <div class="empty-state">
          <div style="font-weight:800;">لا يوجد تلاميذ في هذا القسم</div>
        </div>
      ` : `
        <div class="table-responsive">
          <table class="grade-table">
            <thead>
              <tr>
                <th>#</th>
                <th class="student-col">اللقب والاسم</th>
                <th>تقويم 1</th>
                ${cfg.ca2Active ? '<th>تقويم 2</th>' : ''}
                <th>فرض 1</th>
                ${cfg.as2Active ? '<th>فرض 2</th>' : ''}
                <th>الاختبار</th>
                <th>المعدل الفصلي</th>
                ${cfg.visibleColumns?.absences ? '<th>الغياب</th>' : ''}
                ${cfg.visibleColumns?.behaviour ? '<th>السلوك</th>' : ''}
              </tr>
            </thead>
            <tbody>
              ${computedList.map(({ st, r, calc }) => {
                const avgBadgeClass = calc.termAvg == null
                  ? 'badge-neutral'
                  : calc.termAvg >= 10 ? 'badge-success' : 'badge-error';
                return `
                  <tr>
                    <td>${st.number}</td>
                    <td class="student-col">${Utils.escapeHtml(st.fullName)}</td>
                    <td><input type="number" step="0.25" min="0" max="20" class="grade-input" value="${r.ca1 ?? ''}" onchange="AppUI.updateGradeCell('${cls.id}','${st.id}','ca1',this.value)" /></td>
                    ${cfg.ca2Active ? `<td><input type="number" step="0.25" min="0" max="20" class="grade-input" value="${r.ca2 ?? ''}" onchange="AppUI.updateGradeCell('${cls.id}','${st.id}','ca2',this.value)" /></td>` : ''}
                    <td><input type="number" step="0.25" min="0" max="20" class="grade-input" value="${r.as1 ?? ''}" onchange="AppUI.updateGradeCell('${cls.id}','${st.id}','as1',this.value)" /></td>
                    ${cfg.as2Active ? `<td><input type="number" step="0.25" min="0" max="20" class="grade-input" value="${r.as2 ?? ''}" onchange="AppUI.updateGradeCell('${cls.id}','${st.id}','as2',this.value)" /></td>` : ''}
                    <td><input type="number" step="0.25" min="0" max="20" class="grade-input" value="${r.exam ?? ''}" onchange="AppUI.updateGradeCell('${cls.id}','${st.id}','exam',this.value)" /></td>
                    <td><span class="badge ${avgBadgeClass}" style="font-size:0.9rem;">${Utils.formatNum(calc.termAvg)}</span></td>
                    ${cfg.visibleColumns?.absences ? `<td><input type="number" min="0" class="grade-input" style="width:54px;" value="${r.absences ?? 0}" onchange="AppUI.updateGradeExtra('${cls.id}','${st.id}','absences',this.value)" /></td>` : ''}
                    ${cfg.visibleColumns?.behaviour ? `<td><input type="text" class="grade-input" style="width:82px; direction:rtl;" value="${Utils.escapeHtml(r.behaviour || '')}" onchange="AppUI.updateGradeExtra('${cls.id}','${st.id}','behaviour',this.value)" /></td>` : ''}
                  </tr>
                `;
              }).join('')}
            </tbody>
          </table>
        </div>
      `}
    `;
  },

  updateGradeCell(classId, studentId, field, rawVal) {
    const parsed = Utils.parseGrade(rawVal);
    const s = Repo.state;
    let row = (s.gradeRows || []).find(
      r => r.schoolYearId === this.viewedYearId && r.classId === classId && r.studentId === studentId && r.termId === this.selectedTermId
    );
    if (!row) {
      row = {
        id: Utils.uid('gr'),
        schoolYearId: this.viewedYearId,
        classId,
        studentId,
        termId: this.selectedTermId,
        absences: 0,
        behaviour: 'حسن',
        materials: 'مكتمل',
        notebook: 'منظم',
        note: ''
      };
      s.gradeRows.push(row);
    }
    row[field] = parsed;
    Repo.persist();
  },

  updateGradeExtra(classId, studentId, field, rawVal) {
    const s = Repo.state;
    let row = (s.gradeRows || []).find(
      r => r.schoolYearId === this.viewedYearId && r.classId === classId && r.studentId === studentId && r.termId === this.selectedTermId
    );
    if (!row) {
      row = {
        id: Utils.uid('gr'),
        schoolYearId: this.viewedYearId,
        classId,
        studentId,
        termId: this.selectedTermId,
        absences: 0,
        behaviour: 'حسن'
      };
      s.gradeRows.push(row);
    }
    row[field] = field === 'absences' ? Math.max(0, parseInt(rawVal, 10) || 0) : rawVal;
    Repo.persist();
  },

  // ==========================================================================
  // 5. جدول التوقيت والرزنامة (Schedule & Calendar Screen)
  // ==========================================================================
  renderSchedule() {
    const s = Repo.state;
    const classes = this.getYearClasses();
    const tt = (s.timetable || []).filter(t => t.schoolYearId === this.viewedYearId);
    const events = (s.calendarEvents || []).filter(e => e.schoolYearId === this.viewedYearId).sort((a, b) => a.date.localeCompare(b.date));

    return `
      <div class="section-header">
        <div class="section-title">التوقيت الأسبوعي والرزنامة</div>
        <div class="section-subtitle">تنظيم الحصص الأسبوعية، الفروض والاختبارات، والعطل والمناسبات الرسمية</div>
      </div>

      <div class="pill-row">
        <button class="pill-chip ${this.scheduleSubTab === 'timetable' ? 'active' : ''}" onclick="AppUI.scheduleSubTab='timetable'; AppUI.render();">🗓️ التوقيت الأسبوعي</button>
        <button class="pill-chip ${this.scheduleSubTab === 'assessments' ? 'active' : ''}" onclick="AppUI.scheduleSubTab='assessments'; AppUI.render();">📝 الفروض والرزنامة (${events.length})</button>
        <button class="pill-chip ${this.scheduleSubTab === 'calendar' ? 'active' : ''}" onclick="AppUI.scheduleSubTab='calendar'; AppUI.render();">🇩🇿 العطل والمناسبات الرسمية</button>
      </div>

      ${this.scheduleSubTab === 'timetable' ? `
        <div class="row-wrap" style="margin-bottom:0.85rem;">
          <button class="btn btn-primary" style="flex:1;" onclick="AppUI.openTimetableModal()">+ إضافة حصة أسبوعية</button>
          <button class="btn btn-outline" style="flex:1;" onclick="AppUI.printTimetable()">🖨️ طباعة الجدول A4</button>
        </div>

        <div class="pill-row">
          ${ArData.SCHOOL_DAYS.map((dName, idx) => {
            const count = tt.filter(x => x.day === idx).length;
            return `<button class="pill-chip ${this.scheduleDayIdx === idx ? 'active' : ''}" onclick="AppUI.scheduleDayIdx=${idx}; AppUI.render();">${dName} (${count})</button>`;
          }).join('')}
        </div>

        ${tt.filter(x => x.day === this.scheduleDayIdx).sort((a, b) => a.startTime.localeCompare(b.startTime)).map(slot => {
          const cls = classes.find(c => c.id === slot.classId);
          const color = cls?.colorHex || '#0F6B63';
          return `
            <div class="strip-card">
              <div class="color-strip" style="background:${color};"></div>
              <div class="strip-card-body">
                <div class="row-between">
                  <div>
                    <span class="badge" style="background:${color}22; color:${color};">${Utils.escapeHtml(cls?.fullTitle || 'قسم')}</span>
                    <strong style="margin-right:0.5rem;">⏰ ${slot.startTime} – ${slot.endTime}</strong>
                    ${slot.room ? `<span style="color:var(--text-secondary); font-size:0.86rem;"> • 📍 ${Utils.escapeHtml(slot.room)}</span>` : ''}
                  </div>
                  <div class="row-wrap">
                    <button class="btn btn-sm btn-tonal" onclick="AppUI.createLessonFromSlot('${slot.id}', Utils.todayStr())">تحضير الحصة</button>
                    <button class="btn btn-sm btn-danger" onclick="AppUI.deleteTimetableSlot('${slot.id}')">🗑️</button>
                  </div>
                </div>
                ${slot.note ? `<div style="font-size:0.86rem; color:var(--text-secondary); margin-top:0.35rem;">${Utils.escapeHtml(slot.note)}</div>` : ''}
              </div>
            </div>
          `;
        }).join('') || `<div class="empty-state">لا توجد حصص مبرمجة ليوم ${ArData.SCHOOL_DAYS[this.scheduleDayIdx]}.</div>`}
      ` : this.scheduleSubTab === 'assessments' ? `
        <div style="margin-bottom:0.85rem;">
          <button class="btn btn-primary btn-block" onclick="AppUI.openEventModal()">+ إضافة فرض أو موعد تربوي</button>
        </div>
        ${events.map(ev => {
          const cls = classes.find(c => c.id === ev.classId);
          return `
            <div class="card">
              <div class="row-between">
                <strong>${Utils.escapeHtml(ev.title)}</strong>
                <div class="row-wrap">
                  <span class="badge ${ev.type === 'assessment' ? 'badge-warning' : 'badge-primary'}">${ev.type === 'assessment' ? 'فرض / اختبار' : 'موعد تربوي'}</span>
                  <button class="btn btn-sm btn-danger" onclick="AppUI.deleteEvent('${ev.id}')">🗑️</button>
                </div>
              </div>
              <div style="font-size:0.86rem; color:var(--text-secondary); margin-top:0.3rem;">
                📅 ${Utils.formatAlgerianDate(ev.date, true)} ${cls ? `• ${Utils.escapeHtml(cls.fullTitle)}` : ''}
              </div>
              ${ev.note ? `<div style="font-size:0.86rem; margin-top:0.3rem;">${Utils.escapeHtml(ev.note)}</div>` : ''}
            </div>
          `;
        }).join('') || `<div class="empty-state">لا توجد فروض أو مواعيد مسجلة بعد.</div>`}
      ` : `
        <div class="card">
          <h3 style="font-size:1.1rem; font-weight:800; margin-bottom:0.75rem;">🇩🇿 رزنامة العطل والمناسبات الرسمية</h3>
          ${ArData.OFFICIAL_HOLIDAYS.map(h => `
            <div class="row-between" style="padding:0.65rem 0; border-bottom:1px solid var(--border);">
              <div>
                <strong>${Utils.escapeHtml(h.title)}</strong>
                <div style="font-size:0.82rem; color:var(--text-secondary);">${Utils.escapeHtml(h.date)}</div>
              </div>
              <span class="badge badge-primary">${Utils.escapeHtml(h.category)}</span>
            </div>
          `).join('')}
        </div>
      `}
    `;
  },

  // ==========================================================================
  // 6. التكوين والندوات والرزنامة التربوية (Training & Seminars)
  // ==========================================================================
  renderTraining() {
    const s = Repo.state;
    const notes = (s.trainingNotes || []).filter(n => n.schoolYearId === this.viewedYearId);
    const seminars = (s.seminars || []).filter(x => x.schoolYearId === this.viewedYearId);
    const eduRecords = (s.eduCalendarRecords || []).filter(x => x.schoolYearId === this.viewedYearId);

    return `
      <div class="section-header">
        <div class="section-title">التكوين والندوات والرزنامة التربوية</div>
        <div class="section-subtitle">توثيق الملاحظات التكوينية، الندوات الداخلية والخارجية، وسجل الرزنامة التربوية</div>
      </div>

      <div class="pill-row">
        <button class="pill-chip ${this.trainingSubTab === 'notes' ? 'active' : ''}" onclick="AppUI.trainingSubTab='notes'; AppUI.render();">📚 ملاحظات التكوين (${notes.length})</button>
        <button class="pill-chip ${this.trainingSubTab === 'seminars' ? 'active' : ''}" onclick="AppUI.trainingSubTab='seminars'; AppUI.render();">🎙️ الندوات (${seminars.length})</button>
        <button class="pill-chip ${this.trainingSubTab === 'eduCalendar' ? 'active' : ''}" onclick="AppUI.trainingSubTab='eduCalendar'; AppUI.render();">🗂️ الرزنامة التربوية (${eduRecords.length})</button>
      </div>

      ${this.trainingSubTab === 'notes' ? `
        <button class="btn btn-primary btn-block" style="margin-bottom:0.85rem;" onclick="AppUI.openTrainingNoteModal()">+ إضافة ملاحظة تكوين</button>
        ${notes.map(n => `
          <div class="card">
            <div class="row-between">
              <span class="badge badge-primary">${Utils.escapeHtml(n.categoryName)}</span>
              <button class="btn btn-sm btn-danger" onclick="AppUI.deleteTrainingNote('${n.id}')">🗑️</button>
            </div>
            <h3 style="font-size:1.08rem; font-weight:800; margin-top:0.4rem;">${Utils.escapeHtml(n.title)}</h3>
            <div style="font-size:0.82rem; color:var(--text-secondary);">📅 ${Utils.formatAlgerianDate(n.date, true)}</div>
            <div style="margin-top:0.45rem; white-space:pre-line;">${Utils.escapeHtml(n.text)}</div>
          </div>
        `).join('') || `<div class="empty-state">لا توجد ملاحظات تكوين مسجلة بعد.</div>`}
      ` : this.trainingSubTab === 'seminars' ? `
        <button class="btn btn-primary btn-block" style="margin-bottom:0.85rem;" onclick="AppUI.openSeminarModal()">+ إضافة ندوة تربوية</button>
        ${seminars.map(sem => `
          <div class="card">
            <div class="row-between">
              <span class="badge badge-primary">${sem.kind === 'internal' ? 'ندوة داخلية' : 'ندوة خارجية'}</span>
              <button class="btn btn-sm btn-danger" onclick="AppUI.deleteSeminar('${sem.id}')">🗑️</button>
            </div>
            <h3 style="font-size:1.08rem; font-weight:800; margin-top:0.4rem;">${Utils.escapeHtml(sem.title)}</h3>
            <div style="font-size:0.84rem; color:var(--text-secondary);">📅 ${Utils.formatAlgerianDate(sem.date)} • 📍 ${Utils.escapeHtml(sem.location || '—')} • المؤطر: ${Utils.escapeHtml(sem.facilitator || '—')}</div>
            ${sem.mainIdeas ? `<div style="margin-top:0.4rem;"><strong>الأفكار الأساسية:</strong> ${Utils.escapeHtml(sem.mainIdeas)}</div>` : ''}
            ${sem.recommendations ? `<div style="margin-top:0.3rem;"><strong>التوصيات:</strong> ${Utils.escapeHtml(sem.recommendations)}</div>` : ''}
          </div>
        `).join('') || `<div class="empty-state">لا توجد ندوات مسجلة بعد.</div>`}
      ` : `
        <button class="btn btn-primary btn-block" style="margin-bottom:0.85rem;" onclick="AppUI.openEduCalModal()">+ إضافة سجل في الرزنامة التربوية</button>
        ${eduRecords.map(rec => `
          <div class="card">
            <div class="row-between">
              <span class="badge badge-primary">${rec.type === 'pedagogicalSeminar' ? 'ندوة تربوية' : rec.type === 'studyDay' ? 'يوم دراسي' : 'تربص'}</span>
              <button class="btn btn-sm btn-danger" onclick="AppUI.deleteEduRecord('${rec.id}')">🗑️</button>
            </div>
            <h3 style="font-size:1.08rem; font-weight:800; margin-top:0.4rem;">${Utils.escapeHtml(rec.topic || rec.internshipType || 'نشاط تربوي')}</h3>
            <div style="font-size:0.84rem; color:var(--text-secondary);">📅 ${Utils.formatAlgerianDate(rec.date)} • 📍 ${Utils.escapeHtml(rec.location || '—')}</div>
            ${rec.appliedLesson ? `<div style="margin-top:0.35rem;"><strong>الدرس التطبيقي:</strong> ${Utils.escapeHtml(rec.appliedLesson)} (${Utils.escapeHtml(rec.level || '')})</div>` : ''}
          </div>
        `).join('') || `<div class="empty-state">لا توجد سجلات في الرزنامة التربوية بعد.</div>`}
      `}
    `;
  },

  // ==========================================================================
  // 7. بطاقتي المهنية (Professional Profile Screen)
  // ==========================================================================
  renderProfile() {
    const p = Repo.state.profile || {};
    const classes = this.getYearClasses();
    return `
      <div class="section-header">
        <div class="section-title">بطاقتي المهنية</div>
        <div class="section-subtitle">البيانات الإدارية والمهنية الخاصة بالأستاذ(ة) للطباعة الرسمية بصيغة A4</div>
      </div>

      <div class="row-wrap" style="margin-bottom:0.9rem;">
        <button class="btn btn-outline" style="flex:1;" onclick="AppUI.printProfileCard()">🖨️ طباعة البطاقة A4</button>
        <button class="btn btn-primary" style="flex:1;" onclick="AppUI.saveProfileForm()">✓ حفظ البطاقة</button>
      </div>

      <div class="card raised" style="padding:0.85rem 1rem;">
        🔒 تُحفظ هذه البيانات محليًا على جهازكِ فقط، وجميع الحقول اختيارية وتُطبع فقط الحقول المملوءة.
      </div>

      ${classes.length > 0 ? `
        <div class="card">
          <div style="font-weight:800; color:var(--primary-text);">الأقسام المسندة في السنة النشطة:</div>
          <div style="margin-top:0.25rem; font-weight:700;">${classes.map(c => Utils.escapeHtml(c.fullTitle)).join(' ، ')}</div>
        </div>
      ` : ''}

      <div class="card">
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">الاسم واللقب المهني</label>
            <input id="prof-name" class="form-control" value="${Utils.escapeHtml(p.professionalName || '')}" />
          </div>
          <div class="form-group">
            <label class="form-label">المؤسسة التعليمية</label>
            <input id="prof-inst" class="form-control" value="${Utils.escapeHtml(p.institution || '')}" />
          </div>
          <div class="form-group">
            <label class="form-label">الوضعية المهنية (مرسم / متربص / متعاقد)</label>
            <input id="prof-status" class="form-control" value="${Utils.escapeHtml(p.employmentStatus || '')}" />
          </div>
          <div class="form-group">
            <label class="form-label">مادة التخصص</label>
            <input id="prof-spec" class="form-control" value="${Utils.escapeHtml(p.specialization || '')}" />
          </div>
          <div class="form-group">
            <label class="form-label">الشهادات والمؤهلات العلمية</label>
            <input id="prof-qual" class="form-control" value="${Utils.escapeHtml(p.qualifications || '')}" />
          </div>
          <div class="form-group">
            <label class="form-label">تاريخ أول تعيين</label>
            <input id="prof-date" type="date" class="form-control" value="${Utils.escapeHtml(p.appointmentDate || '')}" />
          </div>
          <div class="form-group">
            <label class="form-label">الرتبة والدرجة الحالية</label>
            <input id="prof-rank" class="form-control" value="${Utils.escapeHtml(p.rank || '')}" />
          </div>
          <div class="form-group">
            <label class="form-label">آخر تفتيش ونقطته</label>
            <input id="prof-insp" class="form-control" value="${Utils.escapeHtml(p.inspectionHistory || '')}" />
          </div>
        </div>
      </div>
    `;
  },

  saveProfileForm() {
    const p = Repo.state.profile;
    p.professionalName = document.getElementById('prof-name')?.value || '';
    p.institution = document.getElementById('prof-inst')?.value || '';
    p.employmentStatus = document.getElementById('prof-status')?.value || '';
    p.specialization = document.getElementById('prof-spec')?.value || '';
    p.qualifications = document.getElementById('prof-qual')?.value || '';
    p.appointmentDate = document.getElementById('prof-date')?.value || '';
    p.rank = document.getElementById('prof-rank')?.value || '';
    p.inspectionHistory = document.getElementById('prof-insp')?.value || '';
    Repo.persist();
  },

  // ==========================================================================
  // 8. الإعدادات والنسخ الاحتياطي (Settings Screen)
  // ==========================================================================
  renderSettings() {
    const s = Repo.state;
    const cfg = GradeCalc.getTermConfig(s, this.viewedYearId, this.selectedTermId);
    const totalW = Number(cfg.weights.ca) + Number(cfg.weights.asWeight) + Number(cfg.weights.exam);

    return `
      <div class="section-header">
        <div class="section-title">الإعدادات والنسخ الاحتياطي</div>
        <div class="section-subtitle">تخصيص المظهر، معاملات التنقيط، السنوات الدراسية، والنسخ الاحتياطي المحلي JSON</div>
      </div>

      <div class="pill-row">
        <button class="pill-chip ${this.settingsSubTab === 'appearance' ? 'active' : ''}" onclick="AppUI.settingsSubTab='appearance'; AppUI.render();">🎨 المظهر والخط</button>
        <button class="pill-chip ${this.settingsSubTab === 'grading' ? 'active' : ''}" onclick="AppUI.settingsSubTab='grading'; AppUI.render();">⚖️ إعدادات التنقيط</button>
        <button class="pill-chip ${this.settingsSubTab === 'years' ? 'active' : ''}" onclick="AppUI.settingsSubTab='years'; AppUI.render();">📅 السنوات الدراسية</button>
        <button class="pill-chip ${this.settingsSubTab === 'backup' ? 'active' : ''}" onclick="AppUI.settingsSubTab='backup'; AppUI.render();">💾 النسخ الاحتياطي والبيانات</button>
      </div>

      ${this.settingsSubTab === 'appearance' ? `
        <div class="card">
          <h3 style="font-weight:800; margin-bottom:0.75rem;">نمط الألوان وحجم الخط</h3>
          <div class="row-wrap" style="margin-bottom:1rem;">
            <button class="btn ${s.meta.theme === 'light' ? 'btn-primary' : 'btn-outline'}" onclick="Repo.state.meta.theme='light'; Repo.persist();">☀️ الوضع النهاري الهادئ</button>
            <button class="btn ${s.meta.theme === 'dark' ? 'btn-primary' : 'btn-outline'}" onclick="Repo.state.meta.theme='dark'; Repo.persist();">🌙 الوضع الليلي المريح</button>
          </div>
          <label class="form-label">حجم الخط في التطبيق:</label>
          <div class="row-wrap">
            <button class="btn ${s.meta.fontScale === 'small' ? 'btn-primary' : 'btn-outline'}" onclick="Repo.state.meta.fontScale='small'; Repo.persist();">صغير</button>
            <button class="btn ${s.meta.fontScale === 'medium' ? 'btn-primary' : 'btn-outline'}" onclick="Repo.state.meta.fontScale='medium'; Repo.persist();">متوسط (الافتراضي)</button>
            <button class="btn ${s.meta.fontScale === 'large' ? 'btn-primary' : 'btn-outline'}" onclick="Repo.state.meta.fontScale='large'; Repo.persist();">كبير (واضح جدًا)</button>
          </div>
        </div>
      ` : this.settingsSubTab === 'grading' ? `
        <div class="pill-row">
          ${ArData.TERMS.map(t => `<button class="pill-chip ${this.selectedTermId === t.id ? 'active' : ''}" onclick="AppUI.selectedTermId='${t.id}'; AppUI.render();">${t.label}</button>`).join('')}
        </div>
        <div class="card">
          <div class="row-between" style="margin-bottom:0.75rem;">
            <strong>تفعيل «التقويم المستمر 2»</strong>
            <input type="checkbox" ${cfg.ca2Active ? 'checked' : ''} onchange="AppUI.toggleGradeCfg('ca2Active', this.checked)" style="width:22px; height:22px;" />
          </div>
          <div class="row-between" style="margin-bottom:0.75rem;">
            <strong>تفعيل «الفرض المحروس 2»</strong>
            <input type="checkbox" ${cfg.as2Active ? 'checked' : ''} onchange="AppUI.toggleGradeCfg('as2Active', this.checked)" style="width:22px; height:22px;" />
          </div>
          <hr style="border:none; border-top:1px solid var(--border); margin:0.85rem 0;" />
          <div class="row-between" style="margin-bottom:0.5rem;">
            <strong>معاملات الفئات (المقترح: 1 / 1 / 2)</strong>
            <button class="btn btn-sm btn-tonal" onclick="AppUI.resetDefaultWeights()">إرجاع للافتراضي</button>
          </div>
          <div class="grid-3">
            <div class="form-group">
              <label class="form-label">التقويم المستمر</label>
              <input id="w-ca" type="number" step="0.5" min="0.5" class="form-control" value="${cfg.weights.ca}" />
            </div>
            <div class="form-group">
              <label class="form-label">الفروض</label>
              <input id="w-as" type="number" step="0.5" min="0.5" class="form-control" value="${cfg.weights.asWeight}" />
            </div>
            <div class="form-group">
              <label class="form-label">الاختبار</label>
              <input id="w-ex" type="number" step="0.5" min="0.5" class="form-control" value="${cfg.weights.exam}" />
            </div>
          </div>
          <div class="card raised" style="padding:0.75rem; font-size:0.88rem; color:var(--primary-text); font-weight:700;">
            المعادلة المطبقة: (معدل التقويم × ${cfg.weights.ca} + معدل الفروض × ${cfg.weights.asWeight} + الاختبار × ${cfg.weights.exam}) ÷ ${totalW}
          </div>
          <button class="btn btn-primary btn-block" onclick="AppUI.saveWeights()">حفظ إعدادات التنقيط وإعادة حساب المعدلات</button>
        </div>
      ` : this.settingsSubTab === 'years' ? `
        <div class="card">
          <div class="row-between" style="margin-bottom:0.85rem;">
            <h3 style="font-weight:800;">السنوات الدراسية والأرشفة</h3>
            <button class="btn btn-sm btn-primary" onclick="AppUI.startNewSchoolYearPrompt()">+ بدء سنة دراسية جديدة</button>
          </div>
          ${(s.schoolYears || []).map(yr => `
            <div class="card raised" style="margin-bottom:0.6rem;">
              <div class="row-between">
                <div>
                  <strong style="font-size:1.05rem;">السنة الدراسية ${Utils.escapeHtml(yr.label)}</strong>
                  <span class="badge ${yr.id === s.meta.activeSchoolYearId ? 'badge-success' : 'badge-warning'}" style="margin-right:0.4rem;">
                    ${yr.id === s.meta.activeSchoolYearId ? 'نشطة' : 'مؤرشفة'}
                  </span>
                </div>
                <div class="row-wrap">
                  ${yr.id !== this.viewedYearId ? `<button class="btn btn-sm btn-outline" onclick="AppUI.viewedYearId='${yr.id}'; AppUI.render();">تصفح</button>` : ''}
                  ${yr.id !== s.meta.activeSchoolYearId ? `<button class="btn btn-sm btn-tonal" onclick="AppUI.activateYear('${yr.id}')">تعيين كسنة نشطة</button>` : ''}
                </div>
              </div>
            </div>
          `).join('')}
        </div>
      ` : `
        <div class="card">
          <h3 style="font-weight:800; margin-bottom:0.5rem;">💾 النسخ الاحتياطي والاسترجاع (JSON)</h3>
          <p style="font-size:0.88rem; color:var(--text-secondary); margin-bottom:0.9rem;">
            تُحفظ جميع بياناتكِ محليًا على المتصفح. يُنصح بتحميل نسخة احتياطية بصيغة JSON دوريًا للاحتفاظ بها أو نقلها إلى جهاز آخر.
          </p>
          <div class="row-wrap" style="margin-bottom:1rem;">
            <button class="btn btn-primary" style="flex:1;" onclick="AppUI.exportBackupJson()">⬇️ تحميل نسخة احتياطية JSON</button>
            <label class="btn btn-tonal" style="flex:1; cursor:pointer;">
              ⬆️ استرجاع من ملف JSON
              <input type="file" accept=".json,application/json" style="display:none;" onchange="AppUI.importBackupJson(event)" />
            </label>
          </div>
          <hr style="border:none; border-top:1px solid var(--border); margin:1rem 0;" />
          <div class="row-wrap">
            <button class="btn btn-outline" onclick="Repo.seedSampleData(false)">✨ إعادة تحميل البيانات التجريبية</button>
            <button class="btn btn-danger" onclick="AppUI.confirmClearSample()">🧹 حذف البيانات التجريبية فقط</button>
          </div>
        </div>
      `}
    `;
  },

  toggleGradeCfg(key, val) {
    const cfg = GradeCalc.getTermConfig(Repo.state, this.viewedYearId, this.selectedTermId);
    cfg[key] = val;
    if (!(Repo.state.gradeTermConfigs || []).includes(cfg)) {
      Repo.state.gradeTermConfigs.push(cfg);
    }
    Repo.persist();
  },

  resetDefaultWeights() {
    const cfg = GradeCalc.getTermConfig(Repo.state, this.viewedYearId, this.selectedTermId);
    cfg.weights = { ca: 1, asWeight: 1, exam: 2 };
    if (!(Repo.state.gradeTermConfigs || []).includes(cfg)) {
      Repo.state.gradeTermConfigs.push(cfg);
    }
    Repo.persist();
  },

  saveWeights() {
    const cfg = GradeCalc.getTermConfig(Repo.state, this.viewedYearId, this.selectedTermId);
    const ca = parseFloat(document.getElementById('w-ca')?.value) || 1;
    const asW = parseFloat(document.getElementById('w-as')?.value) || 1;
    const ex = parseFloat(document.getElementById('w-ex')?.value) || 2;
    cfg.weights = { ca, asWeight: asW, exam: ex };
    if (!(Repo.state.gradeTermConfigs || []).includes(cfg)) {
      Repo.state.gradeTermConfigs.push(cfg);
    }
    Repo.persist();
  },

  startNewSchoolYearPrompt() {
    const label = prompt('أدخلي تسمية السنة الدراسية الجديدة (مثال: 2026/2027):', '2026/2027');
    if (!label || !label.trim()) return;
    const newId = Utils.uid('sy');
    (Repo.state.schoolYears || []).forEach(y => { y.status = 'archived'; });
    Repo.state.schoolYears.push({ id: newId, label: label.trim(), status: 'active' });
    Repo.state.meta.activeSchoolYearId = newId;
    this.viewedYearId = newId;
    Repo.persist();
  },

  activateYear(id) {
    (Repo.state.schoolYears || []).forEach(y => { y.status = y.id === id ? 'active' : 'archived'; });
    Repo.state.meta.activeSchoolYearId = id;
    this.viewedYearId = id;
    Repo.persist();
  },

  confirmClearSample() {
    if (confirm('هل تريدين حذف جميع البيانات التجريبية والاحتفاظ ببياناتكِ الفعلية فقط؟')) {
      Repo.clearSampleData();
    }
  },

  // ==========================================================================
  // النوافذ المنبثقة (Modals) والإجراءات التفاعلية
  // ==========================================================================
  showModal(title, bodyHtml, footerHtml = '') {
    const root = document.getElementById('modal-root');
    if (!root) return;
    root.innerHTML = `
      <div class="modal-backdrop" onclick="if(event.target===this) AppUI.closeModal()">
        <div class="modal-box">
          <div class="modal-header">
            <div class="modal-title">${title}</div>
            <button class="btn btn-sm btn-outline" onclick="AppUI.closeModal()">✕</button>
          </div>
          <div class="modal-body">${bodyHtml}</div>
          ${footerHtml ? `<div class="modal-footer">${footerHtml}</div>` : ''}
        </div>
      </div>
    `;
  },

  closeModal() {
    const root = document.getElementById('modal-root');
    if (root) root.innerHTML = '';
  },

  openClassModal(editId = null) {
    const existing = editId ? this.getYearClasses().find(c => c.id === editId) : null;
    const level = existing?.level || ArData.SCHOOL_LEVELS[0];
    const groupLabel = existing?.groupLabel || 'م1 ف1';
    const room = existing?.room || '';
    const colorHex = existing?.colorHex || ArData.CLASS_COLORS[0];
    const notes = existing?.notes || '';

    this.showModal(
      existing ? 'تعديل بيانات القسم' : 'إضافة قسم دراسي جديد',
      `
        <div class="form-group">
          <label class="form-label">المستوى الدراسي</label>
          <select id="mdl-cls-level" class="form-control">
            ${ArData.SCHOOL_LEVELS.map(l => `<option value="${l}" ${l === level ? 'selected' : ''}>${l}</option>`).join('')}
          </select>
        </div>
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">رمز الفوج (مثال: م1 ف1)</label>
            <input id="mdl-cls-group" class="form-control" value="${Utils.escapeHtml(groupLabel)}" />
          </div>
          <div class="form-group">
            <label class="form-label">القاعة</label>
            <input id="mdl-cls-room" class="form-control" value="${Utils.escapeHtml(room)}" />
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">اللون المميز للقسم</label>
          <input id="mdl-cls-color" type="color" class="form-control" value="${colorHex}" style="height:48px; padding:4px;" />
        </div>
        <div class="form-group">
          <label class="form-label">ملاحظات تربوية حول القسم</label>
          <textarea id="mdl-cls-notes" class="form-control">${Utils.escapeHtml(notes)}</textarea>
        </div>
      `,
      `
        <button class="btn btn-outline" onclick="AppUI.closeModal()">إلغاء</button>
        <button class="btn btn-primary" onclick="AppUI.saveClassModal('${editId || ''}')">حفظ القسم</button>
      `
    );
  },

  saveClassModal(editId) {
    const level = document.getElementById('mdl-cls-level').value;
    const groupLabel = document.getElementById('mdl-cls-group').value.trim();
    const room = document.getElementById('mdl-cls-room').value.trim();
    const colorHex = document.getElementById('mdl-cls-color').value;
    const notes = document.getElementById('mdl-cls-notes').value.trim();
    if (!groupLabel) return alert('يرجى إدخال رمز الفوج');

    const fullTitle = `${level} — ${groupLabel}`;
    if (editId) {
      const cls = Repo.state.classes.find(c => c.id === editId);
      if (cls) Object.assign(cls, { level, groupLabel, fullTitle, room, colorHex, notes });
    } else {
      Repo.state.classes.push({
        id: Utils.uid('cls'),
        schoolYearId: this.viewedYearId,
        level, groupLabel, fullTitle, room, colorHex, notes,
        isArchived: false
      });
    }
    this.closeModal();
    Repo.persist();
  },

  deleteClass(id) {
    if (!confirm('هل أنتِ متأكدة من حذف هذا القسم؟')) return;
    Repo.state.classes = Repo.state.classes.filter(c => c.id !== id);
    if (this.selectedClassId === id) this.selectedClassId = null;
    Repo.persist();
  },

  openStudentModal(classId, editId = null) {
    const st = editId ? Repo.state.students.find(x => x.id === editId) : null;
    this.showModal(
      st ? 'تعديل بيانات التلميذ(ة)' : 'إضافة تلميذ(ة) للقسم',
      `
        <div class="form-group">
          <label class="form-label">اللقب والاسم الكامل</label>
          <input id="mdl-stu-name" class="form-control" value="${Utils.escapeHtml(st?.fullName || '')}" />
        </div>
        <div class="form-group">
          <label class="form-label">ملاحظة عامة (اختياري)</label>
          <input id="mdl-stu-note" class="form-control" value="${Utils.escapeHtml(st?.generalNote || '')}" />
        </div>
      `,
      `
        <button class="btn btn-outline" onclick="AppUI.closeModal()">إلغاء</button>
        <button class="btn btn-primary" onclick="AppUI.saveStudentModal('${classId}', '${editId || ''}')">حفظ</button>
      `
    );
  },

  saveStudentModal(classId, editId) {
    const fullName = document.getElementById('mdl-stu-name').value.trim();
    const generalNote = document.getElementById('mdl-stu-note').value.trim();
    if (!fullName) return alert('يرجى إدخال اسم التلميذ');
    if (editId) {
      const st = Repo.state.students.find(x => x.id === editId);
      if (st) Object.assign(st, { fullName, generalNote });
    } else {
      const count = Repo.state.students.filter(x => x.classId === classId && x.status === 'active').length;
      Repo.state.students.push({
        id: Utils.uid('stu'),
        schoolYearId: this.viewedYearId,
        classId,
        fullName,
        number: count + 1,
        generalNote,
        status: 'active'
      });
    }
    this.closeModal();
    Repo.persist();
  },

  openBatchStudentsModal(classId) {
    this.showModal(
      'لصق قائمة أسماء التلاميذ دفعة واحدة',
      `
        <p style="font-size:0.86rem; color:var(--text-secondary); margin-bottom:0.65rem;">ضعي كل اسم تلميذ في سطر مستقل، وسيتم ترقيمهم وإضافتهم للقسم تلقائيًا:</p>
        <textarea id="mdl-batch-names" class="form-control" style="min-height:180px;" placeholder="أنس بن عيسى&#10;آية بوزيد&#10;بلال منصوري"></textarea>
      `,
      `
        <button class="btn btn-outline" onclick="AppUI.closeModal()">إلغاء</button>
        <button class="btn btn-primary" onclick="AppUI.saveBatchStudents('${classId}')">إضافة القائمة</button>
      `
    );
  },

  saveBatchStudents(classId) {
    const raw = document.getElementById('mdl-batch-names').value || '';
    const lines = raw.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
    if (lines.length === 0) return alert('يرجى لصق اسم واحد على الأقل');
    let count = Repo.state.students.filter(x => x.classId === classId && x.status === 'active').length;
    lines.forEach(fullName => {
      count += 1;
      Repo.state.students.push({
        id: Utils.uid('stu'),
        schoolYearId: this.viewedYearId,
        classId,
        fullName,
        number: count,
        generalNote: '',
        status: 'active'
      });
    });
    this.closeModal();
    Repo.persist();
  },

  deleteStudent(id) {
    if (!confirm('حذف هذا التلميذ من القائمة؟')) return;
    Repo.state.students = Repo.state.students.filter(s => s.id !== id);
    Repo.persist();
  },

  openStudentProfileModal(studentId) {
    const st = Repo.state.students.find(x => x.id === studentId);
    if (!st) return;
    const obs = (Repo.state.observations || []).filter(o => o.studentId === studentId).sort((a, b) => b.date.localeCompare(a.date));
    this.showModal(
      `ملف التلميذ: ${Utils.escapeHtml(st.fullName)}`,
      `
        <div class="row-wrap" style="margin-bottom:0.85rem;">
          <span class="badge badge-error">الغيابات: ${obs.filter(o => o.type === 'absence').length}</span>
          <span class="badge badge-warning">التأخرات: ${obs.filter(o => o.type === 'lateness').length}</span>
          <span class="badge badge-success">المشاركات المميزة: ${obs.filter(o => o.type === 'participation').length}</span>
        </div>
        <h4 style="margin-bottom:0.5rem;">سجل الملاحظات في الحصص (${obs.length}):</h4>
        ${obs.map(o => `
          <div class="card raised" style="padding:0.65rem; margin-bottom:0.5rem;">
            <div class="row-between">
              <span class="badge badge-primary">${Utils.escapeHtml(o.typeArabic)}</span>
              <span style="font-size:0.8rem; color:var(--text-secondary);">${Utils.formatAlgerianDate(o.date)}</span>
            </div>
            ${o.note ? `<div style="font-size:0.88rem; margin-top:0.25rem;">${Utils.escapeHtml(o.note)}</div>` : ''}
          </div>
        `).join('') || `<div style="color:var(--text-secondary);">لا توجد ملاحظات مسجلة لهذا التلميذ بعد.</div>`}
      `,
      `<button class="btn btn-primary" onclick="AppUI.closeModal()">إغلاق</button>`
    );
  },

  openLessonModal(editId = null, prefill = {}) {
    const classes = this.getYearClasses();
    if (classes.length === 0) return alert('يرجى إضافة قسم دراسي أولًا');
    const lp = editId ? Repo.state.lessonPlans.find(x => x.id === editId) : null;
    const classId = lp?.classId || prefill.classId || classes[0].id;
    const date = lp?.date || prefill.date || this.selectedDate;
    const startTime = lp?.startTime || prefill.startTime || '08:00';
    const endTime = lp?.endTime || prefill.endTime || '09:00';
    const activity = lp?.activity || (Repo.state.activityTemplates?.[0]?.name || 'فهم المكتوب');
    const status = lp?.status || 'planned';

    this.showModal(
      lp ? 'تعديل تحضير الحصة' : 'تحضير حصة جديدة في الكراس اليومي',
      `
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">القسم</label>
            <select id="mdl-lp-cls" class="form-control">
              ${classes.map(c => `<option value="${c.id}" ${c.id === classId ? 'selected' : ''}>${Utils.escapeHtml(c.fullTitle)}</option>`).join('')}
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">التاريخ</label>
            <input id="mdl-lp-date" type="date" class="form-control" value="${date}" />
          </div>
          <div class="form-group">
            <label class="form-label">من الساعة</label>
            <input id="mdl-lp-start" type="time" class="form-control" value="${startTime}" />
          </div>
          <div class="form-group">
            <label class="form-label">إلى الساعة</label>
            <input id="mdl-lp-end" type="time" class="form-control" value="${endTime}" />
          </div>
        </div>
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">النشاط / الميدان</label>
            <input id="mdl-lp-act" list="act-list" class="form-control" value="${Utils.escapeHtml(activity)}" />
            <datalist id="act-list">
              ${(Repo.state.activityTemplates || []).map(a => `<option value="${Utils.escapeHtml(a.name)}"></option>`).join('')}
            </datalist>
          </div>
          <div class="form-group">
            <label class="form-label">حالة الحصة</label>
            <select id="mdl-lp-status" class="form-control">
              <option value="planned" ${status === 'planned' ? 'selected' : ''}>مخططة</option>
              <option value="completed" ${status === 'completed' ? 'selected' : ''}>منجزة</option>
              <option value="postponed" ${status === 'postponed' ? 'selected' : ''}>مؤجلة</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">عنوان الدرس / الموضوع</label>
          <input id="mdl-lp-title" class="form-control" value="${Utils.escapeHtml(lp?.title || '')}" placeholder="مثال: الأخلاق الفاضلة في المجتمع" />
        </div>
        <div class="form-group">
          <label class="form-label">الأهداف التعلمية / مؤشرات الكفاءة</label>
          <textarea id="mdl-lp-obj" class="form-control">${Utils.escapeHtml(lp?.objectives || '')}</textarea>
        </div>
        <div class="form-group">
          <label class="form-label">سير الحصة وعناصر الدرس الأساسية</label>
          <textarea id="mdl-lp-content" class="form-control">${Utils.escapeHtml(lp?.contentElements || '')}</textarea>
        </div>
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">الوسائل والسندات</label>
            <input id="mdl-lp-tools" class="form-control" value="${Utils.escapeHtml(lp?.tools || '')}" />
          </div>
          <div class="form-group">
            <label class="form-label">الواجب المنزلي</label>
            <input id="mdl-lp-hw" class="form-control" value="${Utils.escapeHtml(lp?.homework || '')}" />
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">ملاحظات تقييمية بعد الحصة</label>
          <input id="mdl-lp-ref" class="form-control" value="${Utils.escapeHtml(lp?.reflectionNotes || '')}" />
        </div>
      `,
      `
        <button class="btn btn-outline" onclick="AppUI.closeModal()">إلغاء</button>
        <button class="btn btn-primary" onclick="AppUI.saveLessonModal('${editId || ''}', '${prefill.timetableId || lp?.timetableId || ''}')">حفظ التحضير</button>
      `
    );
  },

  saveLessonModal(editId, timetableId) {
    const classId = document.getElementById('mdl-lp-cls').value;
    const date = document.getElementById('mdl-lp-date').value;
    const startTime = document.getElementById('mdl-lp-start').value;
    const endTime = document.getElementById('mdl-lp-end').value;
    const activity = document.getElementById('mdl-lp-act').value.trim();
    const status = document.getElementById('mdl-lp-status').value;
    const title = document.getElementById('mdl-lp-title').value.trim();
    const objectives = document.getElementById('mdl-lp-obj').value.trim();
    const contentElements = document.getElementById('mdl-lp-content').value.trim();
    const tools = document.getElementById('mdl-lp-tools').value.trim();
    const homework = document.getElementById('mdl-lp-hw').value.trim();
    const reflectionNotes = document.getElementById('mdl-lp-ref').value.trim();

    if (!title) return alert('يرجى إدخال عنوان الدرس');

    if (editId) {
      const lp = Repo.state.lessonPlans.find(x => x.id === editId);
      if (lp) Object.assign(lp, { classId, date, startTime, endTime, activity, status, title, objectives, contentElements, tools, homework, reflectionNotes });
    } else {
      Repo.state.lessonPlans.push({
        id: Utils.uid('lp'),
        schoolYearId: this.viewedYearId,
        classId, date, startTime, endTime, activity, status, title,
        objectives, contentElements, tools, homework, reflectionNotes,
        timetableId: timetableId || null
      });
    }
    this.selectedDate = date;
    this.closeModal();
    Repo.persist();
  },

  createLessonFromSlot(slotId, dateStr) {
    const slot = (Repo.state.timetable || []).find(t => t.id === slotId);
    if (!slot) return;
    this.openLessonModal(null, {
      classId: slot.classId,
      date: dateStr,
      startTime: slot.startTime,
      endTime: slot.endTime,
      timetableId: slot.id
    });
  },

  duplicateLesson(id) {
    const lp = Repo.state.lessonPlans.find(x => x.id === id);
    if (!lp) return;
    const copy = Object.assign({}, lp, {
      id: Utils.uid('lp'),
      title: `${lp.title} (نسخة)`,
      status: 'planned',
      isSample: false
    });
    Repo.state.lessonPlans.push(copy);
    Repo.persist();
  },

  deleteLesson(id) {
    if (!confirm('حذف تحضير هذه الحصة؟')) return;
    Repo.state.lessonPlans = Repo.state.lessonPlans.filter(x => x.id !== id);
    Repo.persist();
  },

  openQuickObsModal(lessonId) {
    const lp = Repo.state.lessonPlans.find(x => x.id === lessonId);
    if (!lp) return;
    const students = (Repo.state.students || []).filter(s => s.classId === lp.classId && s.status === 'active').sort((a, b) => a.number - b.number);
    const obs = (Repo.state.observations || []).filter(o => o.lessonPlanId === lessonId);

    this.showModal(
      `ملاحظات الحصة السريعة: ${Utils.escapeHtml(lp.title)}`,
      `
        <div class="row-between" style="margin-bottom:0.85rem;">
          <div class="row-wrap">
            <span class="badge badge-error">الغيابات: ${obs.filter(o => o.type === 'absence').length}</span>
            <span class="badge badge-warning">التأخرات: ${obs.filter(o => o.type === 'lateness').length}</span>
            <span class="badge badge-primary">إجمالي الملاحظات: ${obs.length}</span>
          </div>
          <button class="btn btn-sm btn-tonal" onclick="AppUI.markAllPresent('${lessonId}')">✓ تحديد الكل حاضر</button>
        </div>
        ${students.map(st => {
          const stObs = obs.find(o => o.studentId === st.id);
          const curType = stObs?.type || 'present';
          return `
            <div class="card" style="padding:0.75rem; margin-bottom:0.5rem;">
              <div class="row-between">
                <strong>${st.number}. ${Utils.escapeHtml(st.fullName)}</strong>
                <div class="row-wrap">
                  ${[
                    ['present', 'حاضر', 'badge-success'],
                    ['absence', 'غائب', 'badge-error'],
                    ['lateness', 'متأخر', 'badge-warning'],
                    ['participation', 'مشاركة مميزة', 'badge-primary']
                  ].map(([tKey, tLbl]) => `
                    <button class="btn btn-sm ${curType === tKey ? 'btn-primary' : 'btn-outline'}" onclick="AppUI.setStudentObs('${lessonId}','${st.id}','${tKey}','${tLbl}')">${tLbl}</button>
                  `).join('')}
                </div>
              </div>
            </div>
          `;
        }).join('')}
      `,
      `<button class="btn btn-primary" onclick="AppUI.closeModal()">تم الحفظ والإغلاق</button>`
    );
  },

  setStudentObs(lessonId, studentId, type, typeArabic) {
    const lp = Repo.state.lessonPlans.find(x => x.id === lessonId);
    if (!lp) return;
    Repo.state.observations = (Repo.state.observations || []).filter(
      o => !(o.lessonPlanId === lessonId && o.studentId === studentId)
    );
    if (type !== 'present') {
      Repo.state.observations.push({
        id: Utils.uid('obs'),
        schoolYearId: this.viewedYearId,
        lessonPlanId: lessonId,
        classId: lp.classId,
        studentId,
        date: lp.date,
        type,
        typeArabic,
        note: ''
      });
    }
    Repo.persist();
    this.openQuickObsModal(lessonId);
  },

  markAllPresent(lessonId) {
    Repo.state.observations = (Repo.state.observations || []).filter(
      o => !(o.lessonPlanId === lessonId && (o.type === 'absence' || o.type === 'lateness'))
    );
    Repo.persist();
    this.openQuickObsModal(lessonId);
  },

  openTimetableModal() {
    const classes = this.getYearClasses();
    if (classes.length === 0) return alert('أضيفي قسمًا دراسيًا أولًا');
    this.showModal(
      'إضافة حصة في التوقيت الأسبوعي',
      `
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">اليوم</label>
            <select id="mdl-tt-day" class="form-control">
              ${ArData.SCHOOL_DAYS.map((d, i) => `<option value="${i}" ${i === this.scheduleDayIdx ? 'selected' : ''}>${d}</option>`).join('')}
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">القسم</label>
            <select id="mdl-tt-cls" class="form-control">
              ${classes.map(c => `<option value="${c.id}">${Utils.escapeHtml(c.fullTitle)}</option>`).join('')}
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">من الساعة</label>
            <input id="mdl-tt-start" type="time" class="form-control" value="08:00" />
          </div>
          <div class="form-group">
            <label class="form-label">إلى الساعة</label>
            <input id="mdl-tt-end" type="time" class="form-control" value="09:00" />
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">القاعة / ملاحظة</label>
          <input id="mdl-tt-room" class="form-control" placeholder="قاعة 04" />
        </div>
      `,
      `
        <button class="btn btn-outline" onclick="AppUI.closeModal()">إلغاء</button>
        <button class="btn btn-primary" onclick="AppUI.saveTimetableModal()">حفظ الحصة</button>
      `
    );
  },

  saveTimetableModal() {
    const day = parseInt(document.getElementById('mdl-tt-day').value, 10);
    const classId = document.getElementById('mdl-tt-cls').value;
    const startTime = document.getElementById('mdl-tt-start').value;
    const endTime = document.getElementById('mdl-tt-end').value;
    const room = document.getElementById('mdl-tt-room').value.trim();
    Repo.state.timetable.push({
      id: Utils.uid('tt'),
      schoolYearId: this.viewedYearId,
      day, classId, startTime, endTime, room, note: ''
    });
    this.scheduleDayIdx = day;
    this.closeModal();
    Repo.persist();
  },

  deleteTimetableSlot(id) {
    Repo.state.timetable = Repo.state.timetable.filter(x => x.id !== id);
    Repo.persist();
  },

  openEventModal() {
    const classes = this.getYearClasses();
    this.showModal(
      'إضافة فرض أو موعد في الرزنامة',
      `
        <div class="form-group">
          <label class="form-label">عنوان الفرض أو الموعد</label>
          <input id="mdl-ev-title" class="form-control" placeholder="الفرض المحروس الأول" />
        </div>
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">التاريخ</label>
            <input id="mdl-ev-date" type="date" class="form-control" value="${Utils.todayStr()}" />
          </div>
          <div class="form-group">
            <label class="form-label">النوع</label>
            <select id="mdl-ev-type" class="form-control">
              <option value="assessment">فرض / اختبار</option>
              <option value="meeting">مجلس / اجتماع تربوي</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">القسم المعني (اختياري)</label>
          <select id="mdl-ev-cls" class="form-control">
            <option value="">عام لجميع الأقسام</option>
            ${classes.map(c => `<option value="${c.id}">${Utils.escapeHtml(c.fullTitle)}</option>`).join('')}
          </select>
        </div>
      `,
      `
        <button class="btn btn-outline" onclick="AppUI.closeModal()">إلغاء</button>
        <button class="btn btn-primary" onclick="AppUI.saveEventModal()">حفظ الموعد</button>
      `
    );
  },

  saveEventModal() {
    const title = document.getElementById('mdl-ev-title').value.trim();
    const date = document.getElementById('mdl-ev-date').value;
    const type = document.getElementById('mdl-ev-type').value;
    const classId = document.getElementById('mdl-ev-cls').value || null;
    if (!title) return alert('يرجى إدخال العنوان');
    Repo.state.calendarEvents.push({
      id: Utils.uid('ev'),
      schoolYearId: this.viewedYearId,
      title, date, type, classId, note: ''
    });
    this.closeModal();
    Repo.persist();
  },

  deleteEvent(id) {
    Repo.state.calendarEvents = Repo.state.calendarEvents.filter(e => e.id !== id);
    Repo.persist();
  },

  openTrainingNoteModal() {
    const cats = Repo.state.trainingCategories || [];
    this.showModal(
      'إضافة ملاحظة تكوين',
      `
        <div class="form-group">
          <label class="form-label">فئة التكوين</label>
          <select id="mdl-tn-cat" class="form-control">
            ${cats.map(c => `<option value="${Utils.escapeHtml(c.name)}">${Utils.escapeHtml(c.name)}</option>`).join('')}
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">عنوان الملاحظة</label>
          <input id="mdl-tn-title" class="form-control" />
        </div>
        <div class="form-group">
          <label class="form-label">التاريخ</label>
          <input id="mdl-tn-date" type="date" class="form-control" value="${Utils.todayStr()}" />
        </div>
        <div class="form-group">
          <label class="form-label">محتوى الملاحظة التكوينية</label>
          <textarea id="mdl-tn-text" class="form-control"></textarea>
        </div>
      `,
      `
        <button class="btn btn-outline" onclick="AppUI.closeModal()">إلغاء</button>
        <button class="btn btn-primary" onclick="AppUI.saveTrainingNoteModal()">حفظ</button>
      `
    );
  },

  saveTrainingNoteModal() {
    const categoryName = document.getElementById('mdl-tn-cat').value;
    const title = document.getElementById('mdl-tn-title').value.trim();
    const date = document.getElementById('mdl-tn-date').value;
    const text = document.getElementById('mdl-tn-text').value.trim();
    if (!title) return alert('يرجى إدخال العنوان');
    Repo.state.trainingNotes.push({
      id: Utils.uid('tn'),
      schoolYearId: this.viewedYearId,
      categoryName, title, date, text
    });
    this.closeModal();
    Repo.persist();
  },

  deleteTrainingNote(id) {
    Repo.state.trainingNotes = Repo.state.trainingNotes.filter(x => x.id !== id);
    Repo.persist();
  },

  openSeminarModal() {
    this.showModal(
      'إضافة ندوة تربوية',
      `
        <div class="form-group">
          <label class="form-label">عنوان الندوة</label>
          <input id="mdl-sem-title" class="form-control" />
        </div>
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">نوع الندوة</label>
            <select id="mdl-sem-kind" class="form-control">
              <option value="internal">ندوة داخلية</option>
              <option value="external">ندوة خارجية</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">التاريخ</label>
            <input id="mdl-sem-date" type="date" class="form-control" value="${Utils.todayStr()}" />
          </div>
        </div>
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">المكان</label>
            <input id="mdl-sem-loc" class="form-control" />
          </div>
          <div class="form-group">
            <label class="form-label">المؤطر / المفتش</label>
            <input id="mdl-sem-fac" class="form-control" />
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">الأفكار الأساسية والتوصيات</label>
          <textarea id="mdl-sem-ideas" class="form-control"></textarea>
        </div>
      `,
      `
        <button class="btn btn-outline" onclick="AppUI.closeModal()">إلغاء</button>
        <button class="btn btn-primary" onclick="AppUI.saveSeminarModal()">حفظ الندوة</button>
      `
    );
  },

  saveSeminarModal() {
    const title = document.getElementById('mdl-sem-title').value.trim();
    const kind = document.getElementById('mdl-sem-kind').value;
    const date = document.getElementById('mdl-sem-date').value;
    const location = document.getElementById('mdl-sem-loc').value.trim();
    const facilitator = document.getElementById('mdl-sem-fac').value.trim();
    const mainIdeas = document.getElementById('mdl-sem-ideas').value.trim();
    if (!title) return alert('يرجى إدخال عنوان الندوة');
    Repo.state.seminars.push({
      id: Utils.uid('sem'),
      schoolYearId: this.viewedYearId,
      title, kind, date, location, facilitator, mainIdeas, recommendations: ''
    });
    this.closeModal();
    Repo.persist();
  },

  deleteSeminar(id) {
    Repo.state.seminars = Repo.state.seminars.filter(x => x.id !== id);
    Repo.persist();
  },

  openEduCalModal() {
    this.showModal(
      'إضافة سجل في الرزنامة التربوية',
      `
        <div class="grid-2">
          <div class="form-group">
            <label class="form-label">نوع النشاط</label>
            <select id="mdl-edu-type" class="form-control">
              <option value="pedagogicalSeminar">ندوة تربوية</option>
              <option value="studyDay">يوم دراسي</option>
              <option value="internship">تربص</option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">التاريخ</label>
            <input id="mdl-edu-date" type="date" class="form-control" value="${Utils.todayStr()}" />
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">الموضوع</label>
          <input id="mdl-edu-topic" class="form-control" />
        </div>
        <div class="form-group">
          <label class="form-label">المكان</label>
          <input id="mdl-edu-loc" class="form-control" />
        </div>
      `,
      `
        <button class="btn btn-outline" onclick="AppUI.closeModal()">إلغاء</button>
        <button class="btn btn-primary" onclick="AppUI.saveEduCalModal()">حفظ</button>
      `
    );
  },

  saveEduCalModal() {
    const type = document.getElementById('mdl-edu-type').value;
    const date = document.getElementById('mdl-edu-date').value;
    const topic = document.getElementById('mdl-edu-topic').value.trim();
    const location = document.getElementById('mdl-edu-loc').value.trim();
    if (!topic) return alert('يرجى إدخال الموضوع');
    Repo.state.eduCalendarRecords.push({
      id: Utils.uid('edu'),
      schoolYearId: this.viewedYearId,
      type, date, topic, location, appliedLesson: '', teacherName: '', level: '', internshipType: ''
    });
    this.closeModal();
    Repo.persist();
  },

  deleteEduRecord(id) {
    Repo.state.eduCalendarRecords = Repo.state.eduCalendarRecords.filter(x => x.id !== id);
    Repo.persist();
  },

  openGlobalSearchModal() {
    this.showModal(
      '🔍 البحث الشامل في التطبيق',
      `
        <input id="global-search-input" class="form-control" placeholder="ابحثي عن تلميذ، عنوان درس، ملاحظة تكوين، أو ندوة..." oninput="AppUI.runGlobalSearch(this.value)" />
        <div id="global-search-results" style="margin-top:0.85rem;"></div>
      `,
      `<button class="btn btn-outline" onclick="AppUI.closeModal()">إغلاق</button>`
    );
  },

  runGlobalSearch(q) {
    const box = document.getElementById('global-search-results');
    if (!box) return;
    const norm = Utils.normalizeArabic(q);
    if (!norm) { box.innerHTML = ''; return; }
    const s = Repo.state;
    const stHits = (s.students || []).filter(x => Utils.normalizeArabic(x.fullName).includes(norm)).slice(0, 5);
    const lpHits = (s.lessonPlans || []).filter(x => Utils.normalizeArabic(x.title).includes(norm) || Utils.normalizeArabic(x.activity).includes(norm)).slice(0, 5);

    box.innerHTML = `
      ${stHits.map(st => `<div class="card raised" style="padding:0.6rem; margin-bottom:0.4rem; cursor:pointer;" onclick="AppUI.closeModal(); AppUI.navigate('classes', {classId:'${st.classId}', classSubTab:'students'});">🎓 تلميذ: <strong>${Utils.escapeHtml(st.fullName)}</strong></div>`).join('')}
      ${lpHits.map(lp => `<div class="card raised" style="padding:0.6rem; margin-bottom:0.4rem; cursor:pointer;" onclick="AppUI.closeModal(); AppUI.navigate('planner', {date:'${lp.date}'});">📖 تحضير: <strong>${Utils.escapeHtml(lp.title)}</strong> (${lp.date})</div>`).join('')}
      ${stHits.length === 0 && lpHits.length === 0 ? `<div style="color:var(--text-secondary); text-align:center;">لا توجد نتائج مطابقة</div>` : ''}
    `;
  },

  // ==========================================================================
  // التصدير (JSON / CSV) والطباعة الرسمية A4
  // ==========================================================================
  exportBackupJson() {
    const dataStr = JSON.stringify(Repo.state, null, 2);
    const blob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `al_ustadh_backup_${Utils.todayStr()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  },

  importBackupJson(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const parsed = JSON.parse(e.target.result);
        if (!parsed.meta || !Array.isArray(parsed.classes)) {
          return alert('ملف النسخة الاحتياطية غير صالح.');
        }
        Repo.state = parsed;
        this.viewedYearId = parsed.meta.activeSchoolYearId || 'sy_2025_2026';
        Repo.persist();
        alert('تم استرجاع النسخة الاحتياطية بنجاح!');
      } catch (err) {
        alert('تعذر قراءة ملف JSON.');
      }
    };
    reader.readAsText(file);
  },

  exportGradebookCsv(classId) {
    const cls = this.getYearClasses().find(c => c.id === classId);
    if (!cls) return;
    const students = (Repo.state.students || []).filter(s => s.classId === classId && s.status === 'active').sort((a, b) => a.number - b.number);
    const cfg = GradeCalc.getTermConfig(Repo.state, this.viewedYearId, this.selectedTermId);
    const rows = (Repo.state.gradeRows || []).filter(r => r.classId === classId && r.termId === this.selectedTermId);

    const lines = [['الرقم', 'اللقب والاسم', 'التقويم 1', 'التقويم 2', 'الفرض 1', 'الفرض 2', 'الاختبار', 'المعدل الفصلي'].join(',')];
    students.forEach(st => {
      const r = rows.find(x => x.studentId === st.id) || {};
      const calc = GradeCalc.computeRow(r, cfg);
      lines.push([
        st.number,
        `"${st.fullName.replace(/"/g, '""')}"`,
        r.ca1 ?? '',
        r.ca2 ?? '',
        r.as1 ?? '',
        r.as2 ?? '',
        r.exam ?? '',
        calc.termAvg ?? ''
      ].join(','));
    });

    const blob = new Blob(['\uFEFF' + lines.join('\n')], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `gradebook_${cls.groupLabel}_${this.selectedTermId}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  },

  triggerPrintHtml(html) {
    const area = document.getElementById('print-area');
    if (!area) return;
    area.innerHTML = html;
    window.print();
  },

  printSingleLesson(id) {
    const lp = Repo.state.lessonPlans.find(x => x.id === id);
    if (!lp) return;
    const cls = this.getYearClasses().find(c => c.id === lp.classId);
    const p = Repo.state.profile || {};
    this.triggerPrintHtml(`
      <div class="print-header">
        <h2>الجمهورية الجزائرية الديمقراطية الشعبية — وزارة التربية الوطنية</h2>
        <h3>${Utils.escapeHtml(p.institution || 'المؤسسة التعليمية')} • الأستاذ(ة): ${Utils.escapeHtml(p.professionalName || '—')}</h3>
        <h3>مذكرة تحضير حصة بيداغوجية — ${Utils.formatAlgerianDate(lp.date, true)}</h3>
      </div>
      <table class="print-table">
        <tr><th>القسم</th><td>${Utils.escapeHtml(cls?.fullTitle || '')}</td><th>التوقيت</th><td>${lp.startTime} – ${lp.endTime}</td></tr>
        <tr><th>الميدان / النشاط</th><td>${Utils.escapeHtml(lp.activity)}</td><th>عنوان الدرس</th><td><strong>${Utils.escapeHtml(lp.title)}</strong></td></tr>
        <tr><th>الأهداف التعلمية</th><td colspan="3">${Utils.escapeHtml(lp.objectives)}</td></tr>
        <tr><th>سير الحصة وعناصر الدرس</th><td colspan="3" style="white-space:pre-line;">${Utils.escapeHtml(lp.contentElements)}</td></tr>
        <tr><th>الوسائل التعليمية</th><td>${Utils.escapeHtml(lp.tools)}</td><th>الواجب المنزلي</th><td>${Utils.escapeHtml(lp.homework)}</td></tr>
      </table>
    `);
  },

  printDailyPlanner() {
    const classes = this.getYearClasses();
    const plans = (Repo.state.lessonPlans || []).filter(l => l.schoolYearId === this.viewedYearId && l.date === this.selectedDate);
    const p = Repo.state.profile || {};
    this.triggerPrintHtml(`
      <div class="print-header">
        <h2>الكراس اليومي للأستاذ(ة): ${Utils.escapeHtml(p.professionalName || '—')}</h2>
        <h3>اليوم والتاريخ: ${Utils.formatAlgerianDate(this.selectedDate, true)}</h3>
      </div>
      <table class="print-table">
        <thead>
          <tr><th>التوقيت</th><th>القسم</th><th>النشاط</th><th>عنوان الدرس</th><th>عناصر الدرس والأهداف</th></tr>
        </thead>
        <tbody>
          ${plans.map(lp => {
            const cls = classes.find(c => c.id === lp.classId);
            return `<tr><td>${lp.startTime}–${lp.endTime}</td><td>${Utils.escapeHtml(cls?.fullTitle || '')}</td><td>${Utils.escapeHtml(lp.activity)}</td><td><strong>${Utils.escapeHtml(lp.title)}</strong></td><td>${Utils.escapeHtml(lp.objectives)}</td></tr>`;
          }).join('')}
        </tbody>
      </table>
    `);
  },

  printGradebook(classId) {
    const cls = this.getYearClasses().find(c => c.id === classId);
    if (!cls) return;
    const students = (Repo.state.students || []).filter(s => s.classId === classId && s.status === 'active').sort((a, b) => a.number - b.number);
    const cfg = GradeCalc.getTermConfig(Repo.state, this.viewedYearId, this.selectedTermId);
    const rows = (Repo.state.gradeRows || []).filter(r => r.classId === classId && r.termId === this.selectedTermId);
    const termLabel = ArData.TERMS.find(t => t.id === this.selectedTermId)?.label || '';

    this.triggerPrintHtml(`
      <div class="print-header">
        <h2>كشف النقاط الفصلي — ${Utils.escapeHtml(cls.fullTitle)} (${termLabel})</h2>
        <h3>الأستاذ(ة): ${Utils.escapeHtml(Repo.state.profile?.professionalName || '—')} • المؤسسة: ${Utils.escapeHtml(Repo.state.profile?.institution || '—')}</h3>
      </div>
      <table class="print-table">
        <thead>
          <tr><th>#</th><th>اللقب والاسم</th><th>تقويم 1</th>${cfg.ca2Active ? '<th>تقويم 2</th>' : ''}<th>فرض 1</th>${cfg.as2Active ? '<th>فرض 2</th>' : ''}<th>الاختبار</th><th>المعدل الفصلي</th></tr>
        </thead>
        <tbody>
          ${students.map(st => {
            const r = rows.find(x => x.studentId === st.id) || {};
            const calc = GradeCalc.computeRow(r, cfg);
            return `<tr><td>${st.number}</td><td>${Utils.escapeHtml(st.fullName)}</td><td>${r.ca1 ?? '—'}</td>${cfg.ca2Active ? `<td>${r.ca2 ?? '—'}</td>` : ''}<td>${r.as1 ?? '—'}</td>${cfg.as2Active ? `<td>${r.as2 ?? '—'}</td>` : ''}<td>${r.exam ?? '—'}</td><td><strong>${Utils.formatNum(calc.termAvg)}</strong></td></tr>`;
          }).join('')}
        </tbody>
      </table>
    `);
  },

  printTimetable() {
    const classes = this.getYearClasses();
    const tt = (Repo.state.timetable || []).filter(t => t.schoolYearId === this.viewedYearId);
    this.triggerPrintHtml(`
      <div class="print-header">
        <h2>جدول التوقيت الأسبوعي للأستاذ(ة): ${Utils.escapeHtml(Repo.state.profile?.professionalName || '—')}</h2>
      </div>
      <table class="print-table">
        <thead><tr><th>اليوم</th><th>الحصص المبرمجة</th></tr></thead>
        <tbody>
          ${ArData.SCHOOL_DAYS.map((dName, idx) => {
            const slots = tt.filter(x => x.day === idx).sort((a, b) => a.startTime.localeCompare(b.startTime));
            return `<tr><td><strong>${dName}</strong></td><td>${slots.map(s => {
              const cls = classes.find(c => c.id === s.classId);
              return `${s.startTime}–${s.endTime}: ${Utils.escapeHtml(cls?.fullTitle || '')} (${Utils.escapeHtml(s.room || '')})`;
            }).join(' | ') || '—'}</td></tr>`;
          }).join('')}
        </tbody>
      </table>
    `);
  },

  printProfileCard() {
    this.saveProfileForm();
    const p = Repo.state.profile || {};
    const classes = this.getYearClasses();
    this.triggerPrintHtml(`
      <div class="print-header">
        <h2>البطاقة المهنية للأستاذ(ة)</h2>
        <h3>${Utils.escapeHtml(p.institution || '')}</h3>
      </div>
      <table class="print-table">
        <tr><th>الاسم واللقب</th><td>${Utils.escapeHtml(p.professionalName || '—')}</td></tr>
        <tr><th>المؤسسة التعليمية</th><td>${Utils.escapeHtml(p.institution || '—')}</td></tr>
        <tr><th>الوضعية المهنية</th><td>${Utils.escapeHtml(p.employmentStatus || '—')}</td></tr>
        <tr><th>التخصص</th><td>${Utils.escapeHtml(p.specialization || '—')}</td></tr>
        <tr><th>الشهادات والمؤهلات</th><td>${Utils.escapeHtml(p.qualifications || '—')}</td></tr>
        <tr><th>تاريخ أول تعيين</th><td>${Utils.escapeHtml(p.appointmentDate || '—')}</td></tr>
        <tr><th>الرتبة والدرجة</th><td>${Utils.escapeHtml(p.rank || '—')}</td></tr>
        <tr><th>آخر تفتيش ونقطته</th><td>${Utils.escapeHtml(p.inspectionHistory || '—')}</td></tr>
        <tr><th>الأقسام المسندة</th><td>${classes.map(c => Utils.escapeHtml(c.fullTitle)).join(' ، ') || '—'}</td></tr>
      </table>
    `);
  }
};

window.addEventListener('DOMContentLoaded', () => AppUI.init());
