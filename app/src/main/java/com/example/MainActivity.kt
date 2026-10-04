package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.components.PlannerLogo
import com.example.components.SaveIndicatorBadge
import com.example.domain.LessonPlan
import com.example.features.*
import com.example.i18n.ArStrings
import com.example.storage.TeacherRepository
import com.example.ui.theme.LocalTeacherPalette
import com.example.ui.theme.TeacherAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current.applicationContext
            val repository = remember { TeacherRepository(context) }
            TeacherAppRoot(repository = repository)
        }
    }
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun TeacherAppRoot(
    repository: TeacherRepository
) {
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()
    val localSettings by repository.localSettings.collectAsStateWithLifecycle()
    val saveIndicator by repository.saveIndicator.collectAsStateWithLifecycle()
    val viewedYearId by repository.viewedSchoolYearId.collectAsStateWithLifecycle()
    val storageInitError by repository.storageInitError.collectAsStateWithLifecycle()

    TeacherAppTheme(settings = localSettings) {
        val palette = LocalTeacherPalette.current

        // Section 4 Startup Guard
        if (storageInitError != null) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = palette.bg
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, palette.error)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = palette.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "تعذّر تشغيل التخزين المحلي على هذا الجهاز",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.error
                            )
                            Text(
                                text = storageInitError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.text
                            )
                        }
                    }
                }
            }
            return@TeacherAppTheme
        }

        // First-time setup wizard when setup is not yet completed
        if (!snapshot.meta.setupCompleted) {
            FirstTimeSetupScreen(
                repository = repository,
                onSetupFinished = {}
            )
            return@TeacherAppTheme
        }

        var currentRoute by remember { mutableStateOf("home") }
        var showGlobalSearch by remember { mutableStateOf(false) }

        // Deep-link / navigation state parameters
        var pendingLessonToOpen by remember { mutableStateOf<LessonPlan?>(null) }
        var pendingDailyDate by remember { mutableStateOf<String?>(null) }
        var pendingClassId by remember { mutableStateOf<String?>(null) }
        var pendingStudentId by remember { mutableStateOf<String?>(null) }
        var pendingScheduleAction by remember { mutableStateOf<String?>(null) }
        var pendingTrainingTab by remember { mutableStateOf<String?>(null) }
        var pendingSettingsSubPage by remember { mutableStateOf<String?>(null) }

        val effectiveViewedYearId = if (viewedYearId.isNotBlank() && snapshot.schoolYears.any { it.id == viewedYearId }) {
            viewedYearId
        } else {
            snapshot.meta.activeSchoolYearId
        }
        val viewedYear = snapshot.schoolYears.find { it.id == effectiveViewedYearId }
        val isViewingArchivedYear = effectiveViewedYearId.isNotBlank() &&
            effectiveViewedYearId != snapshot.meta.activeSchoolYearId

        if (currentRoute != "home") {
            BackHandler {
                currentRoute = "home"
                repository.returnToActiveSchoolYear()
            }
        }

        val bottomBarDestinations = remember {
            listOf(
                NavDestination("home", ArStrings.NAV_HOME, Icons.Filled.Home, Icons.Outlined.Home),
                NavDestination("daily", ArStrings.NAV_DAILY, Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
                NavDestination("classes", ArStrings.NAV_CLASSES_GRADES, Icons.Filled.Groups, Icons.Outlined.Groups),
                NavDestination("schedule", ArStrings.NAV_SCHEDULE, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                NavDestination("more", ArStrings.NAV_MORE, Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
            )
        }

        val desktopDestinations = remember {
            listOf(
                NavDestination("home", ArStrings.NAV_HOME, Icons.Filled.Home, Icons.Outlined.Home),
                NavDestination("daily", ArStrings.NAV_DAILY, Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
                NavDestination("classes", ArStrings.NAV_CLASSES_GRADES, Icons.Filled.Groups, Icons.Outlined.Groups),
                NavDestination("schedule", ArStrings.NAV_SCHEDULE, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                NavDestination("training", ArStrings.NAV_TRAINING, Icons.Filled.School, Icons.Outlined.School),
                NavDestination("profile", ArStrings.NAV_PROFILE, Icons.Filled.Badge, Icons.Outlined.Badge),
                NavDestination("settings", ArStrings.NAV_SETTINGS, Icons.Filled.Settings, Icons.Outlined.Settings)
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isDesktop = maxWidth >= 1024.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = palette.bg,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = palette.surface,
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
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
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    PlannerLogo(size = 38.dp)
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = ArStrings.APP_NAME,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = palette.text
                                            )
                                            if (viewedYear != null) {
                                                Surface(
                                                    color = palette.primary.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(50),
                                                    border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.3f))
                                                ) {
                                                    Text(
                                                        text = viewedYear.label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = palette.primaryText,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SaveIndicatorBadge(state = saveIndicator)

                                    // D1: Search button («بحث») sits in the top app bar on every screen
                                    OutlinedButton(
                                        onClick = { showGlobalSearch = true },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("top_bar_search_btn"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        border = BorderStroke(1.dp, palette.border)
                                    ) {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = ArStrings.NAV_SEARCH,
                                            tint = palette.primaryText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = ArStrings.NAV_SEARCH,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.text
                                        )
                                    }
                                }
                            }
                        }

                        // D17: Persistent Viewed Archived Year Banner on every page except الرئيسية
                        if (isViewingArchivedYear && currentRoute != "home" && viewedYear != null) {
                            Surface(
                                color = palette.warning.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, palette.warning),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        repository.returnToActiveSchoolYear()
                                    }
                                    .testTag("viewed_archived_year_banner")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Inventory2,
                                            contentDescription = null,
                                            tint = palette.warning,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = ArStrings.viewedYearBanner(viewedYear.label),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.text
                                        )
                                    }
                                    Surface(
                                        color = palette.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, palette.warning)
                                    ) {
                                        Text(
                                            text = "العودة للسنة النشطة",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.primaryText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    if (!isDesktop) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            HorizontalDivider(color = palette.border, thickness = 1.dp)
                            NavigationBar(
                                containerColor = palette.surface,
                                contentColor = palette.text,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("bottom_navigation_bar")
                            ) {
                                val activeMainRoute = if (currentRoute in listOf("training", "profile", "settings")) {
                                    "more"
                                } else {
                                    currentRoute
                                }
                                bottomBarDestinations.forEach { dest ->
                                    val selected = activeMainRoute == dest.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            if (dest.route == "home") {
                                                // D17: Tapping الرئيسية returns to active year
                                                repository.returnToActiveSchoolYear()
                                            }
                                            currentRoute = dest.route
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = dest.label,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = palette.primaryText,
                                            selectedTextColor = palette.primaryText,
                                            indicatorColor = palette.primary.copy(alpha = 0.16f),
                                            unselectedIconColor = palette.textSecondary,
                                            unselectedTextColor = palette.textSecondary
                                        ),
                                        modifier = Modifier.testTag("nav_item_${dest.route}")
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // D1: Desktop (>= 1024dp): fixed right-side sidebar (in RTL, first item in Row is on the right!)
                    if (isDesktop) {
                        Surface(
                            color = palette.surface,
                            border = BorderStroke(1.dp, palette.border),
                            modifier = Modifier
                                .width(250.dp)
                                .fillMaxHeight()
                                .testTag("desktop_sidebar")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                desktopDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationDrawerItem(
                                        selected = selected,
                                        onClick = {
                                            if (dest.route == "home") {
                                                repository.returnToActiveSchoolYear()
                                            }
                                            currentRoute = dest.route
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = dest.label,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = palette.primary.copy(alpha = 0.15f),
                                            selectedIconColor = palette.primaryText,
                                            selectedTextColor = palette.primaryText,
                                            unselectedIconColor = palette.textSecondary,
                                            unselectedTextColor = palette.text
                                        ),
                                        modifier = Modifier.testTag("sidebar_item_${dest.route}")
                                    )
                                }
                            }
                        }
                    }

                    // Main Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (currentRoute) {
                            "home" -> HomeScreen(
                                snapshot = snapshot,
                                repository = repository,
                                onOpenLessonPlan = { plan ->
                                    pendingLessonToOpen = plan
                                    currentRoute = "daily"
                                },
                                onNavigateToRoute = { route, action ->
                                    when (route) {
                                        "schedule" -> {
                                            pendingScheduleAction = action
                                            currentRoute = "schedule"
                                        }
                                        "settings" -> {
                                            pendingSettingsSubPage = action
                                            currentRoute = "settings"
                                        }
                                        else -> currentRoute = route
                                    }
                                }
                            )

                            "daily" -> DailyPlannerScreen(
                                snapshot = snapshot,
                                repository = repository,
                                viewedYearId = effectiveViewedYearId,
                                initialPlanToOpen = pendingLessonToOpen,
                                initialDate = pendingDailyDate,
                                onConsumedInitialPlan = {
                                    pendingLessonToOpen = null
                                    pendingDailyDate = null
                                }
                            )

                            "classes" -> ClassesAndGradebookScreen(
                                snapshot = snapshot,
                                repository = repository,
                                viewedYearId = effectiveViewedYearId,
                                initialClassId = pendingClassId,
                                initialStudentId = pendingStudentId,
                                onConsumedInitialTarget = {
                                    pendingClassId = null
                                    pendingStudentId = null
                                },
                                onOpenGradeSettings = {
                                    pendingSettingsSubPage = "grading"
                                    currentRoute = "settings"
                                }
                            )

                            "schedule" -> TimetableAndCalendarScreen(
                                snapshot = snapshot,
                                repository = repository,
                                viewedYearId = effectiveViewedYearId,
                                initialAction = pendingScheduleAction,
                                onConsumedInitialAction = { pendingScheduleAction = null },
                                onOpenLessonPlan = { plan ->
                                    pendingLessonToOpen = plan
                                    currentRoute = "daily"
                                }
                            )

                            "more" -> MoreHubScreen(
                                onNavigate = { dest -> currentRoute = dest }
                            )

                            "training" -> TrainingAndSeminarsScreen(
                                snapshot = snapshot,
                                repository = repository,
                                viewedYearId = effectiveViewedYearId,
                                initialTab = pendingTrainingTab,
                                onConsumedInitialTab = { pendingTrainingTab = null }
                            )

                            "profile" -> ProfessionalProfileScreen(
                                snapshot = snapshot,
                                repository = repository
                            )

                            "settings" -> SettingsScreen(
                                snapshot = snapshot,
                                localSettings = localSettings,
                                repository = repository,
                                viewedYearId = effectiveViewedYearId,
                                initialSubPage = pendingSettingsSubPage,
                                onConsumedInitialSubPage = { pendingSettingsSubPage = null },
                                onNavigateToRoute = { dest -> currentRoute = dest }
                            )
                        }
                    }
                }
            }
        }

        // Section 8.9: Global Search Overlay
        if (showGlobalSearch) {
            GlobalSearchDialog(
                repository = repository,
                onDismiss = { showGlobalSearch = false },
                onSelectResult = { item ->
                    showGlobalSearch = false
                    // Switch viewedYearId if needed (Section 8.9)
                    if (item.schoolYearId.isNotBlank() && item.schoolYearId != effectiveViewedYearId) {
                        repository.setViewedSchoolYear(item.schoolYearId)
                    }
                    when (item.targetRoute) {
                        "daily" -> {
                            val plan = snapshot.lessonPlans.find { it.id == item.targetEntityId }
                            pendingLessonToOpen = plan
                            pendingDailyDate = item.targetSecondaryId
                            currentRoute = "daily"
                        }
                        "classes" -> {
                            pendingClassId = item.targetEntityId
                            pendingStudentId = item.targetSecondaryId
                            currentRoute = "classes"
                        }
                        "training" -> {
                            pendingTrainingTab = item.targetSecondaryId
                            currentRoute = "training"
                        }
                        "schedule" -> {
                            pendingScheduleAction = item.targetSecondaryId
                            currentRoute = "schedule"
                        }
                        else -> {
                            currentRoute = item.targetRoute
                        }
                    }
                }
            )
        }
    }
}
