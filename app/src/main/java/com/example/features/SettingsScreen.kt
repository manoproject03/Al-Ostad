package com.example.features

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.components.*
import com.example.domain.*
import com.example.i18n.ArStrings
import com.example.print.PrintAndExportHelper
import com.example.storage.*
import com.example.ui.theme.*
import java.io.File
import java.util.Locale

@Composable
fun SettingsScreen(
    snapshot: DatabaseSnapshot,
    localSettings: LocalSettings,
    repository: TeacherRepository,
    viewedYearId: String,
    initialSubPage: String? = null,
    onConsumedInitialSubPage: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit
) {
    var activeSubPage by remember(initialSubPage) { mutableStateOf(initialSubPage) }

    LaunchedEffect(initialSubPage) {
        if (initialSubPage != null) {
            activeSubPage = initialSubPage
            onConsumedInitialSubPage()
        }
    }

    if (activeSubPage == null) {
        SettingsHubList(
            onOpenSubPage = { activeSubPage = it }
        )
    } else {
        BackHandler { activeSubPage = null }
        SettingsSubPageContainer(
            subPageKey = activeSubPage!!,
            onBack = { activeSubPage = null }
        ) {
            when (activeSubPage) {
                "appearance" -> AppearanceSettingsSubPage(localSettings, repository)
                "privacy" -> PrivacyAndStorageSubPage()
                "offline" -> OfflineAndInstallSubPage()
                "activities" -> ActivityTemplatesSubPage(snapshot, repository)
                "trainingCategories" -> TrainingCategoriesSubPage(snapshot, repository)
                "grading" -> GradingSettingsSubPage(snapshot, repository, viewedYearId)
                "schoolYears" -> SchoolYearsSubPage(
                    snapshot = snapshot,
                    repository = repository,
                    viewedYearId = viewedYearId,
                    onBrowseYear = { yrId ->
                        repository.setViewedSchoolYear(yrId)
                        onNavigateToRoute("classes")
                    }
                )
                "backup" -> BackupAndRestoreSubPage(snapshot, localSettings, repository)
                "danger" -> SampleAndResetSubPage(snapshot, localSettings, repository)
            }
        }
    }
}

@Composable
private fun SettingsHubList(
    onOpenSubPage: (String) -> Unit
) {
    val palette = LocalTeacherPalette.current
    val items = listOf(
        Triple("appearance", "المظهر والألوان وحجم الخط", Icons.Outlined.Palette),
        Triple("privacy", "الخصوصية وتخزين البيانات", Icons.Outlined.Security),
        Triple("offline", "الاستخدام دون اتصال والتثبيت", Icons.Outlined.OfflinePin),
        Triple("activities", "قوالب الأنشطة", Icons.Outlined.Category),
        Triple("trainingCategories", "فئات التكوين", Icons.Outlined.School),
        Triple("grading", "إعدادات التنقيط والمعاملات", Icons.Outlined.Calculate),
        Triple("schoolYears", "السنوات الدراسية والأرشفة", Icons.Outlined.DateRange),
        Triple("backup", "النسخ الاحتياطي والاستعادة", Icons.Outlined.Backup),
        Triple("danger", "البيانات التجريبية والحذف", Icons.Outlined.DeleteSweep)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_hub_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = ArStrings.NAV_SETTINGS,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.text
        )

        items.forEach { (key, title, icon) ->
            Surface(
                color = palette.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (key == "danger") palette.error.copy(alpha = 0.5f) else palette.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSubPage(key) }
                    .testTag("settings_item_$key")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (key == "danger") palette.error else palette.primaryText
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (key == "danger") palette.error else palette.text
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = palette.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSubPageContainer(
    subPageKey: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = LocalTeacherPalette.current
    val title = when (subPageKey) {
        "appearance" -> "المظهر والألوان وحجم الخط"
        "privacy" -> "الخصوصية وتخزين البيانات"
        "offline" -> "الاستخدام دون اتصال والتثبيت"
        "activities" -> "قوالب الأنشطة"
        "trainingCategories" -> "فئات التكوين"
        "grading" -> "إعدادات التنقيط"
        "schoolYears" -> "السنوات الدراسية"
        "backup" -> "النسخ الاحتياطي والاستعادة"
        "danger" -> "البيانات التجريبية والحذف"
        else -> ArStrings.NAV_SETTINGS
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_subpage_$subPageKey"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_back_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع إلى الإعدادات")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.text
            )
        }
        content()
    }
}

// =========================================================================
// 1. APPEARANCE (المظهر) — Section 6 & T56, T57
// =========================================================================
@Composable
private fun AppearanceSettingsSubPage(
    localSettings: LocalSettings,
    repository: TeacherRepository
) {
    val palette = LocalTeacherPalette.current
    var hexInput by remember(localSettings.customPrimaryHex) {
        mutableStateOf(localSettings.customPrimaryHex ?: "")
    }

    // Real Theme Preview Cards (Section 6)
    Text(
        text = "نمط الألوان الأساسي",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Rose Theme Preview Card
        ThemePreviewCard(
            title = "النهاري الدافئ (وردي وكريمي)",
            bgColor = RoseBg,
            surfaceColor = RoseSurface,
            textColor = RoseText,
            primaryColor = RosePrimary,
            isSelected = localSettings.theme == "rose",
            onClick = { repository.updateLocalSettings { it.copy(theme = "rose") } },
            modifier = Modifier
                .weight(1f)
                .testTag("theme_card_rose")
        )

        // Dark Theme Preview Card
        ThemePreviewCard(
            title = "الليلي الهادئ (داكن مريح)",
            bgColor = DarkBg,
            surfaceColor = DarkSurface,
            textColor = DarkText,
            primaryColor = DarkPrimary,
            isSelected = localSettings.theme == "dark",
            onClick = { repository.updateLocalSettings { it.copy(theme = "dark") } },
            modifier = Modifier
                .weight(1f)
                .testTag("theme_card_dark")
        )
    }

    // «اختاري لونكِ» Custom Primary Color (Section 6)
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "اختاري لونكِ (تخصيص اللون الأساسي للأزرار والعناوين)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Preset swatches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContrastUtils.PRESET_CUSTOM_COLORS.forEach { (hex, label) ->
                    val swatchColor = ContrastUtils.parseHexColor(hex) ?: RosePrimary
                    val isSelected = (localSettings.customPrimaryHex ?: "#B04468").equals(hex, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                hexInput = hex
                                val nextHex = if (hex == "#B04468") null else hex
                                repository.updateLocalSettings { it.copy(customPrimaryHex = nextHex) }
                            }
                            .padding(vertical = 6.dp, horizontal = 8.dp)
                            .testTag("color_swatch_$hex"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(2.dp, if (isSelected) palette.text else palette.border, CircleShape)
                        )
                        Text(
                            text = "$label ($hex)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "محدد", tint = palette.primaryText)
                        }
                    }
                }
            }

            // Custom Hex Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("كود لون مخصص (#RRGGBB)") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("custom_hex_input"),
                    singleLine = true
                )
                Button(
                    onClick = {
                        val parsed = ContrastUtils.parseHexColor(hexInput)
                        if (parsed != null) {
                            repository.updateLocalSettings {
                                it.copy(customPrimaryHex = ContrastUtils.toHexString(parsed))
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("تطبيق")
                }
                TextButton(
                    onClick = {
                        hexInput = ""
                        repository.updateLocalSettings { it.copy(customPrimaryHex = null) }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("الافتراضي")
                }
            }

            // WCAG Contrast Warning if still below 4.5:1 (Section 6)
            if (palette.hasLowOnPrimaryContrast) {
                Surface(
                    color = palette.warning.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.warning),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("low_contrast_warning")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = palette.warning)
                        Text(
                            text = ArStrings.CONTRAST_WARNING,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text
                        )
                    }
                }
            }
        }
    }

    // Font Size («عادي» vs «كبير») — Section 6 & T57
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "حجم الخط في التطبيق",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = localSettings.fontSize == "normal",
                    onClick = { repository.updateLocalSettings { it.copy(fontSize = "normal") } },
                    label = { Text("عادي (16px)") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("font_size_normal_btn")
                )
                FilterChip(
                    selected = localSettings.fontSize == "large",
                    onClick = { repository.updateLocalSettings { it.copy(fontSize = "large") } },
                    label = { Text("كبير (20px)") },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("font_size_large_btn")
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    title: String,
    bgColor: Color,
    surfaceColor: Color,
    textColor: Color,
    primaryColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, if (isSelected) primaryColor else Color.Gray),
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = surfaceColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = title,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .fillMaxWidth(0.6f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(primaryColor)
                    )
                }
            }
            if (isSelected) {
                Text(
                    text = "النمط المفعل حاليًا",
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =========================================================================
// 2. PRIVACY & STORAGE (الخصوصية وتخزين البيانات) — Section 4 & 8.12
// =========================================================================
@Composable
private fun PrivacyAndStorageSubPage() {
    val palette = LocalTeacherPalette.current
    val context = LocalContext.current
    var persistProtected by remember { mutableStateOf(true) }

    val dbFile = remember { File(context.filesDir, "teacher_app_db_v1.json") }
    val usedKb = remember(dbFile.exists(), dbFile.length()) {
        if (dbFile.exists()) String.format(Locale.US, "%.1f كيلوبايت", dbFile.length() / 1024.0)
        else "0.0 كيلوبايت"
    }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "الخصوصية وحماية البيانات",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.primaryText
            )
            Text(
                text = ArStrings.PRIVACY_LOCAL_NOTICE,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text
            )
            Text(
                text = "لا يرسل التطبيق أي بيانات إلى الإنترنت، ولا يحتوي على حسابات سحابية أو تتبع إحصائي. الملاحظات الخاصة بالتلاميذ محفوظة في مخزن مستقل ولا تظهر في القوائم أو البحث أو الطباعة أو ملفات CSV.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
    }

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "تخزين البيانات على الجهاز",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "حجم قاعدة البيانات المحلية الحالية: $usedKb • حالة التخزين الدائم: ${if (persistProtected) "مفعّل" else "عادي"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = ArStrings.STORAGE_PERSIST_NOTE,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            OutlinedButton(
                onClick = { persistProtected = true },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("طلب حماية إضافية للتخزين")
            }
        }
    }
}

// =========================================================================
// 3. OFFLINE & INSTALL (الاستخدام دون اتصال والتثبيت) — Section 4 & 8.12
// =========================================================================
@Composable
private fun OfflineAndInstallSubPage() {
    val palette = LocalTeacherPalette.current
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = palette.success)
                Text(
                    text = "حالة العمل دون اتصال: جاهز",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.success
                )
            }
            Text(
                text = ArStrings.OFFLINE_READY_BANNER,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = ArStrings.OFFLINE_SETTINGS_NOTE,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            HorizontalDivider(color = palette.border)
            Text(
                text = "جميع الخطوط العربية (Noto Sans Arabic و Aref Ruqaa) والأيقونات ومحرّك الحسابات والطباعة مضمّنة محليًا داخل حزمة التطبيق دون أي طلبات خارجية.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
    }
}

// =========================================================================
// 4. ACTIVITY TEMPLATES (قوالب الأنشطة) — Section 8.5 & T25
// =========================================================================
@Composable
private fun ActivityTemplatesSubPage(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository
) {
    val palette = LocalTeacherPalette.current
    val sorted = remember(snapshot.activityTemplates) {
        snapshot.activityTemplates.sortedBy { it.orderIndex }
    }
    var newName by remember { mutableStateOf("") }
    var editingTemplate by remember { mutableStateOf<ActivityTemplate?>(null) }
    var deletingTemplate by remember { mutableStateOf<ActivityTemplate?>(null) }

    Text(
        text = "هذه القائمة مقترحة وقابلة للتعديل. عند تعديل أو حذف قالب نشاط، تحتفظ المذكرات السابقة باسم النشاط الذي سُجّلت به.",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.textSecondary
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("إضافة قالب نشاط جديد") },
            modifier = Modifier
                .weight(1f)
                .testTag("new_activity_template_input"),
            singleLine = true
        )
        Button(
            onClick = {
                if (newName.isNotBlank()) {
                    repository.saveActivityTemplate(null, newName)
                    newName = ""
                }
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("add_activity_template_btn")
        ) {
            Text("إضافة")
        }
    }

    sorted.forEachIndexed { idx, tpl ->
        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${idx + 1}. ${tpl.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(
                        onClick = { repository.moveActivityTemplate(tpl.id, moveUp = true) },
                        enabled = idx > 0,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "تحريك لأعلى")
                    }
                    IconButton(
                        onClick = { repository.moveActivityTemplate(tpl.id, moveUp = false) },
                        enabled = idx < sorted.lastIndex,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "تحريك لأسفل")
                    }
                    IconButton(
                        onClick = { editingTemplate = tpl },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = "تعديل الاسم")
                    }
                    IconButton(
                        onClick = { deletingTemplate = tpl },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف القالب", tint = palette.error)
                    }
                }
            }
        }
    }

    if (editingTemplate != null) {
        var nameEdit by remember(editingTemplate) { mutableStateOf(editingTemplate!!.name) }
        AlertDialog(
            onDismissRequest = { editingTemplate = null },
            title = { Text("تعديل اسم النشاط") },
            text = {
                OutlinedTextField(
                    value = nameEdit,
                    onValueChange = { nameEdit = it },
                    label = { Text("اسم النشاط") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.saveActivityTemplate(editingTemplate!!.id, nameEdit)
                        editingTemplate = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTemplate = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (deletingTemplate != null) {
        ConfirmActionDialog(
            title = "حذف قالب النشاط",
            message = "هل أنتِ متأكدة من حذف القالب «${deletingTemplate!!.name}»؟ ستحتفظ الحصص السابقة بنص النشاط المسجل فيها.",
            confirmLabel = "حذف القالب",
            onConfirm = {
                repository.deleteActivityTemplate(deletingTemplate!!.id)
                deletingTemplate = null
            },
            onDismiss = { deletingTemplate = null }
        )
    }
}

// =========================================================================
// 5. TRAINING CATEGORIES (فئات التكوين) — Section 8.7
// =========================================================================
@Composable
private fun TrainingCategoriesSubPage(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository
) {
    val palette = LocalTeacherPalette.current
    val sorted = remember(snapshot.trainingCategories) {
        snapshot.trainingCategories.sortedBy { it.orderIndex }
    }
    var newName by remember { mutableStateOf("") }
    var editingCat by remember { mutableStateOf<TrainingCategory?>(null) }
    var deletingCat by remember { mutableStateOf<TrainingCategory?>(null) }

    Text(
        text = "فئات التكوين المقترحة قابلة للتعديل وإعادة الترتيب. تحتفظ ملاحظات التكوين السابقة باسم الفئة النصي المسجل فيها.",
        style = MaterialTheme.typography.bodyMedium,
        color = palette.textSecondary
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("إضافة فئة تكوين جديدة") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Button(
            onClick = {
                if (newName.isNotBlank()) {
                    repository.saveTrainingCategory(null, newName)
                    newName = ""
                }
            },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("إضافة")
        }
    }

    sorted.forEachIndexed { idx, cat ->
        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${idx + 1}. ${cat.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(
                        onClick = { repository.moveTrainingCategory(cat.id, moveUp = true) },
                        enabled = idx > 0,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "لأعلى")
                    }
                    IconButton(
                        onClick = { repository.moveTrainingCategory(cat.id, moveUp = false) },
                        enabled = idx < sorted.lastIndex,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "لأسفل")
                    }
                    IconButton(onClick = { editingCat = cat }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "تعديل")
                    }
                    IconButton(onClick = { deletingCat = cat }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = palette.error)
                    }
                }
            }
        }
    }

    if (editingCat != null) {
        var nameEdit by remember(editingCat) { mutableStateOf(editingCat!!.name) }
        AlertDialog(
            onDismissRequest = { editingCat = null },
            title = { Text("تعديل فئة التكوين") },
            text = {
                OutlinedTextField(
                    value = nameEdit,
                    onValueChange = { nameEdit = it },
                    label = { Text("اسم الفئة") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.saveTrainingCategory(editingCat!!.id, nameEdit)
                        editingCat = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCat = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (deletingCat != null) {
        ConfirmActionDialog(
            title = "حذف فئة التكوين",
            message = "هل أنتِ متأكدة من حذف فئة «${deletingCat!!.name}»؟",
            confirmLabel = "تأكيد الحذف",
            onConfirm = {
                repository.deleteTrainingCategory(deletingCat!!.id)
                deletingCat = null
            },
            onDismiss = { deletingCat = null }
        )
    }
}

// =========================================================================
// 6. GRADING SETTINGS (إعدادات التنقيط) — Section 8.6 & D8, T33, T34, T35
// =========================================================================
@Composable
private fun GradingSettingsSubPage(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    viewedYearId: String
) {
    val palette = LocalTeacherPalette.current
    var selectedTermId by remember { mutableStateOf("T1") }

    val existingConfig = remember(snapshot.gradeTermConfigs, viewedYearId, selectedTermId) {
        snapshot.gradeTermConfigs.find {
            it.schoolYearId == viewedYearId && it.termId == selectedTermId
        } ?: GradeTermConfig(schoolYearId = viewedYearId, termId = selectedTermId)
    }

    var ca2Active by remember(existingConfig) { mutableStateOf(existingConfig.ca2Active) }
    var ca2Required by remember(existingConfig) { mutableStateOf(existingConfig.ca2Required) }
    var as2Active by remember(existingConfig) { mutableStateOf(existingConfig.as2Active) }
    var as2Required by remember(existingConfig) { mutableStateOf(existingConfig.as2Required) }
    var weightCaText by remember(existingConfig) {
        mutableStateOf(ArabicUtils.formatWesternNumber(existingConfig.weights.ca))
    }
    var weightAsText by remember(existingConfig) {
        mutableStateOf(ArabicUtils.formatWesternNumber(existingConfig.weights.asWeight))
    }
    var weightExamText by remember(existingConfig) {
        mutableStateOf(ArabicUtils.formatWesternNumber(existingConfig.weights.exam))
    }
    var showAbsences by remember(existingConfig) { mutableStateOf(existingConfig.visibleColumns.absences) }
    var showBehaviour by remember(existingConfig) { mutableStateOf(existingConfig.visibleColumns.behaviour) }
    var showMaterials by remember(existingConfig) { mutableStateOf(existingConfig.visibleColumns.materials) }
    var showNotebook by remember(existingConfig) { mutableStateOf(existingConfig.visibleColumns.notebook) }

    var errorMsg by remember { mutableStateOf<String?>(null) }

    ScrollablePillRow(
        items = listOf("T1" to ArStrings.TERM_1, "T2" to ArStrings.TERM_2, "T3" to ArStrings.TERM_3),
        selectedValue = selectedTermId,
        onSelect = {
            selectedTermId = it
            errorMsg = null
        },
        testTagPrefix = "grade_settings_term"
    )

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = ArStrings.FORMULA_SUGGESTION_NOTE,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.primaryText,
                fontWeight = FontWeight.SemiBold
            )

            // CA2 Active & Required
            Text("حقول التقويم المستمر والفروض:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تفعيل «التقويم المستمر 2»", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = ca2Active,
                    onCheckedChange = { ca2Active = it },
                    modifier = Modifier.testTag("switch_ca2_active")
                )
            }
            if (ca2Active) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("جعل «التقويم المستمر 2» إجباريًا لاكتمال المعدل", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = ca2Required,
                        onCheckedChange = { ca2Required = it },
                        modifier = Modifier.testTag("switch_ca2_required")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تفعيل «الفرض 2»", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = as2Active,
                    onCheckedChange = { as2Active = it },
                    modifier = Modifier.testTag("switch_as2_active")
                )
            }
            if (as2Active) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("جعل «الفرض 2» إجباريًا لاكتمال المعدل", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = as2Required,
                        onCheckedChange = { as2Required = it },
                        modifier = Modifier.testTag("switch_as2_required")
                    )
                }
            }

            HorizontalDivider(color = palette.border)

            // Category Weights (defaults 1 / 1 / 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "معاملات الفئات (المقترح: 1 / 1 / 2)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        weightCaText = "1"
                        weightAsText = "1"
                        weightExamText = "2"
                        errorMsg = null
                    },
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text("إرجاع للافتراضي", style = MaterialTheme.typography.labelLarge)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = weightCaText,
                    onValueChange = { weightCaText = it; errorMsg = null },
                    label = { Text("التقويم المستمر") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("weight_ca_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = weightAsText,
                    onValueChange = { weightAsText = it; errorMsg = null },
                    label = { Text("الفروض") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("weight_as_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = weightExamText,
                    onValueChange = { weightExamText = it; errorMsg = null },
                    label = { Text("الاختبار") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("weight_exam_input"),
                    singleLine = true
                )
            }

            val parsedCa = ArabicUtils.toWesternDigits(weightCaText.trim()).replace(',', '.').toDoubleOrNull() ?: 0.0
            val parsedAs = ArabicUtils.toWesternDigits(weightAsText.trim()).replace(',', '.').toDoubleOrNull() ?: 0.0
            val parsedEx = ArabicUtils.toWesternDigits(weightExamText.trim()).replace(',', '.').toDoubleOrNull() ?: 0.0
            val totalWeight = parsedCa + parsedAs + parsedEx
            Surface(
                color = palette.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (totalWeight > 0.0) {
                        "المعادلة المطبقة: (معدل التقويم × ${ArabicUtils.formatWesternNumber(parsedCa)} + معدل الفروض × ${ArabicUtils.formatWesternNumber(parsedAs)} + الاختبار × ${ArabicUtils.formatWesternNumber(parsedEx)}) ÷ ${ArabicUtils.formatWesternNumber(totalWeight)}"
                    } else {
                        "تنبيه: يجب أن يكون مجموع المعاملات أكبر من 0"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (totalWeight > 0.0) palette.primaryText else palette.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(10.dp)
                )
            }

            HorizontalDivider(color = palette.border)

            // Optional Columns Visibility
            Text(
                text = "الأعمدة التنظيمية الاختيارية (لا تدخل في حساب المعدل أبدًا)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("إظهار عمود الغيابات")
                Switch(checked = showAbsences, onCheckedChange = { showAbsences = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("إظهار عمود السلوك")
                Switch(checked = showBehaviour, onCheckedChange = { showBehaviour = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("إظهار عمود إحضار الأدوات")
                Switch(checked = showMaterials, onCheckedChange = { showMaterials = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("إظهار عمود تنظيم الكراس")
                Switch(checked = showNotebook, onCheckedChange = { showNotebook = it })
            }

            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = palette.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("grade_settings_error")
                )
            }

            Button(
                onClick = {
                    val wCa = ArabicUtils.toWesternDigits(weightCaText.trim()).replace(',', '.').toDoubleOrNull()
                    val wAs = ArabicUtils.toWesternDigits(weightAsText.trim()).replace(',', '.').toDoubleOrNull()
                    val wEx = ArabicUtils.toWesternDigits(weightExamText.trim()).replace(',', '.').toDoubleOrNull()
                    if (wCa == null || wAs == null || wEx == null) {
                        errorMsg = "يرجى إدخال أرقام صحيحة أو عشرية صالحة لمعاملات الفئات."
                        return@Button
                    }
                    val nextConfig = existingConfig.copy(
                        ca2Active = ca2Active,
                        ca2Required = ca2Required,
                        as2Active = as2Active,
                        as2Required = as2Required,
                        weights = GradeWeights(wCa, wAs, wEx),
                        visibleColumns = GradeVisibleColumns(
                            absences = showAbsences,
                            behaviour = showBehaviour,
                            materials = showMaterials,
                            notebook = showNotebook
                        )
                    )
                    val res = repository.saveGradeTermConfig(nextConfig)
                    if (res.isFailure) {
                        errorMsg = res.exceptionOrNull()?.message
                    } else {
                        errorMsg = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_grade_settings_btn")
            ) {
                Text("حفظ إعدادات التنقيط وإعادة حساب المعدلات")
            }
        }
    }
}

// =========================================================================
// 7. SCHOOL YEARS (السنوات الدراسية) — Section 8.11 & D17, T55
// =========================================================================
@Composable
private fun SchoolYearsSubPage(
    snapshot: DatabaseSnapshot,
    repository: TeacherRepository,
    viewedYearId: String,
    onBrowseYear: (String) -> Unit
) {
    val palette = LocalTeacherPalette.current
    val activeYearId = snapshot.meta.activeSchoolYearId
    val activeYear = snapshot.schoolYears.find { it.id == activeYearId }
    var showArchiveAndNewYearWizard by remember { mutableStateOf(false) }
    var yearToActivate by remember { mutableStateOf<SchoolYear?>(null) }

    Button(
        onClick = { showArchiveAndNewYearWizard = true },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag("start_new_school_year_btn")
    ) {
        Icon(Icons.Default.EventRepeat, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("إنهاء السنة وبدء سنة جديدة")
    }

    snapshot.schoolYears.forEach { yr ->
        val isActive = yr.status == "active"
        val classesCount = snapshot.classes.count { it.schoolYearId == yr.id }
        val studentsCount = snapshot.students.count { it.schoolYearId == yr.id }
        val lessonsCount = snapshot.lessonPlans.count { it.schoolYearId == yr.id }

        Surface(
            color = palette.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (isActive) palette.primary else palette.border),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("school_year_card_${yr.label}")
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
                        text = "السنة الدراسية ${yr.label}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isActive) {
                        Surface(
                            color = palette.success.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = palette.success, modifier = Modifier.size(15.dp))
                                Text(ArStrings.TAG_ACTIVE_YEAR, color = palette.success, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        ArchivedBadge(text = ArStrings.TAG_ARCHIVED_YEAR)
                    }
                }

                Text(
                    text = "الأقسام: $classesCount • التلاميذ: $studentsCount • التحضيرات: $lessonsCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )

                if (!isActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onBrowseYear(yr.id) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("browse_archived_year_${yr.label}")
                        ) {
                            Text("عرض")
                        }
                        Button(
                            onClick = { yearToActivate = yr },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("activate_year_btn_${yr.label}")
                        ) {
                            Text("جعلها السنة النشطة")
                        }
                    }
                }
            }
        }
    }

    if (yearToActivate != null) {
        ConfirmActionDialog(
            title = "تغيير السنة الدراسية النشطة",
            message = "هل تريدين جعل السنة «${yearToActivate!!.label}» هي السنة النشطة وأرشفة السنة الحالية؟",
            confirmLabel = "تأكيد التفعيل",
            isDestructive = false,
            onConfirm = {
                repository.activateSchoolYear(yearToActivate!!.id)
                yearToActivate = null
            },
            onDismiss = { yearToActivate = null }
        )
    }

    // D17 Guided Flow: «إنهاء السنة وبدء سنة جديدة»
    if (showArchiveAndNewYearWizard && activeYear != null) {
        val oldClasses = snapshot.classes.filter { it.schoolYearId == activeYear.id && !it.isSample && !it.isArchived }
        val oldStudentsCount = snapshot.students.count { it.schoolYearId == activeYear.id }
        val oldTimetableCount = snapshot.timetableEntries.count { it.schoolYearId == activeYear.id }
        val oldLessonsCount = snapshot.lessonPlans.count { it.schoolYearId == activeYear.id }
        val oldGradebooksCount = snapshot.gradebooks.count { it.schoolYearId == activeYear.id }
        val oldTrainingCount = snapshot.trainingNotes.count { it.schoolYearId == activeYear.id } +
            snapshot.seminars.count { it.schoolYearId == activeYear.id }

        var newLabel by remember { mutableStateOf("2027-2028") }
        var confirmStep by remember { mutableIntStateOf(1) } // 1 = form & first confirm, 2 = double confirmation
        var errorMsg by remember { mutableStateOf<String?>(null) }

        val selectedClassCopyMap = remember(oldClasses) {
            mutableStateMapOf<String, Boolean>().apply {
                oldClasses.forEach { put(it.id, false) }
            }
        }
        val selectedClassLevelMap = remember(oldClasses) {
            mutableStateMapOf<String, String>().apply {
                oldClasses.forEach { put(it.id, it.level) }
            }
        }

        AlertDialog(
            onDismissRequest = { showArchiveAndNewYearWizard = false },
            title = { Text("إنهاء السنة (${activeYear.label}) وبدء سنة جديدة", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (confirmStep == 1) {
                        Text(
                            text = "ملخص ما سيبقى محفوظًا مع السنة المؤرشفة (${activeYear.label}):\n" +
                                "• الأقسام: ${snapshot.classes.count { it.schoolYearId == activeYear.id }}\n" +
                                "• التلاميذ: $oldStudentsCount\n" +
                                "• حصص التوقيت: $oldTimetableCount\n" +
                                "• مذكرات التحضير: $oldLessonsCount\n" +
                                "• دفاتر التنقيط: $oldGradebooksCount\n" +
                                "• ملاحظات التكوين والندوات: $oldTrainingCount",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedTextField(
                            value = newLabel,
                            onValueChange = {
                                newLabel = ArabicUtils.toWesternDigits(it)
                                errorMsg = null
                            },
                            label = { Text("تسمية السنة الدراسية الجديدة (مثال: 2027-2028)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_school_year_label_input"),
                            singleLine = true
                        )

                        if (oldClasses.isNotEmpty()) {
                            Text(
                                text = "نسخ اختياري للأقسام والتلاميذ النشطين (بمعرّفات جديدة، دون نسخ النقاط أو التحضيرات أو التوقيت):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            oldClasses.forEach { cls ->
                                val isChecked = selectedClassCopyMap[cls.id] == true
                                val chosenLevel = selectedClassLevelMap[cls.id] ?: cls.level
                                Surface(
                                    color = palette.raised,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, palette.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { selectedClassCopyMap[cls.id] = it },
                                                modifier = Modifier.testTag("copy_class_checkbox_${cls.id}")
                                            )
                                            Text("نسخ ${cls.fullTitle}")
                                        }
                                        if (isChecked) {
                                            DropdownRowSelector(
                                                title = "المستوى في السنة الجديدة",
                                                selectedLabel = chosenLevel,
                                                options = ArStrings.SCHOOL_LEVELS.map { it to it },
                                                onSelect = { selectedClassLevelMap[cls.id] = it }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "تأكيد نهائي (الخطوة 2 من 2): سيتم أرشفة السنة «${activeYear.label}» وتفعيل السنة الجديدة «$newLabel» في معاملة واحدة.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.primaryText
                        )
                    }

                    if (errorMsg != null) {
                        Text(text = errorMsg!!, color = palette.error)
                    }
                }
            },
            confirmButton = {
                if (confirmStep == 1) {
                    Button(
                        onClick = {
                            val err = DateTimeUtils.validateSchoolYearLabel(newLabel)
                            if (err != null) {
                                errorMsg = err
                            } else if (snapshot.schoolYears.any { it.label == newLabel.trim() }) {
                                errorMsg = "توجد سنة دراسية بنفس التسمية."
                            } else {
                                confirmStep = 2
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("new_year_step1_confirm_btn")
                    ) {
                        Text("متابعة للتأكيد النهائي")
                    }
                } else {
                    Button(
                        onClick = {
                            val copyList = oldClasses.filter { selectedClassCopyMap[it.id] == true }.map { cls ->
                                ClassCopySelection(
                                    sourceClassId = cls.id,
                                    newLevel = selectedClassLevelMap[cls.id] ?: cls.level,
                                    newName = cls.name,
                                    copyStudents = true
                                )
                            }
                            val res = repository.archiveAndStartNewYear(newLabel, copyList)
                            if (res.isSuccess) {
                                showArchiveAndNewYearWizard = false
                            } else {
                                errorMsg = res.exceptionOrNull()?.message
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("new_year_step2_confirm_btn")
                    ) {
                        Text("تأكيد إنهاء السنة وبدء $newLabel")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showArchiveAndNewYearWizard = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// =========================================================================
// 8. BACKUP AND RESTORE (النسخ الاحتياطي والاستعادة) — Section 8.10 & D15, D16, T49..T53
// =========================================================================
@Composable
private fun BackupAndRestoreSubPage(
    snapshot: DatabaseSnapshot,
    localSettings: LocalSettings,
    repository: TeacherRepository
) {
    val palette = LocalTeacherPalette.current
    val context = LocalContext.current

    var backupStatusNotice by remember { mutableStateOf<String?>(null) }
    var restoreErrorNotice by remember { mutableStateOf<String?>(null) }
    var pendingRestoreValid by remember { mutableStateOf<BackupSerializer.RestoreValidationResult.Valid?>(null) }
    var pendingRawJson by remember { mutableStateOf("") }
    var restoreConfirmStep by remember { mutableIntStateOf(1) }
    var manualRestoreJsonInput by remember { mutableStateOf("") }
    var showPasteRestoreBox by remember { mutableStateOf(false) }

    val exportFileName = remember {
        val today = DateTimeUtils.todayDateString()
        val hhmm = DateTimeUtils.currentTimeString().replace(":", "")
        "teacher-app-backup-$today-$hhmm.json"
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val exportedAt = DateTimeUtils.nowIsoUtc()
            val json = BackupSerializer.exportBackupJson(snapshot, localSettings, exportedAt)
            val ok = PrintAndExportHelper.writeTextToUri(context, uri, json)
            if (ok) {
                repository.markBackupExportSucceeded(exportedAt)
                backupStatusNotice = "تم تصدير النسخة الاحتياطية وحفظها بنجاح."
            } else {
                backupStatusNotice = "تعذّر حفظ ملف النسخة الاحتياطية في المسار المختار."
            }
        }
    }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val readRes = PrintAndExportHelper.readTextFromUri(context, uri)
            if (readRes.isSuccess) {
                val raw = readRes.getOrThrow()
                when (val valRes = BackupSerializer.validateAndParseBackup(raw)) {
                    is BackupSerializer.RestoreValidationResult.Invalid -> {
                        restoreErrorNotice = valRes.errorAr
                        pendingRestoreValid = null
                    }
                    is BackupSerializer.RestoreValidationResult.Valid -> {
                        restoreErrorNotice = null
                        pendingRawJson = raw
                        pendingRestoreValid = valRes
                        restoreConfirmStep = 1
                    }
                }
            } else {
                restoreErrorNotice = readRes.exceptionOrNull()?.message
            }
        }
    }

    // Export Section
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "تصدير نسخة احتياطية كاملة (JSON)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.primaryText
            )
            Text(
                text = "آخر نسخة احتياطية ناجحة: " +
                    (snapshot.meta.lastBackupExportAt?.let { DateTimeUtils.formatAlgerianDate(it.take(10)) } ?: "لم يتم التصدير بعد"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("last_backup_date_text")
            )
            Text(
                text = ArStrings.BACKUP_STORAGE_WARNING,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            Surface(
                color = palette.warning.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, palette.warning)
            ) {
                Text(
                    text = ArStrings.BACKUP_PRIVACY_WARNING,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val exportedAt = DateTimeUtils.nowIsoUtc()
                        val json = BackupSerializer.exportBackupJson(snapshot, localSettings, exportedAt)
                        val file = PrintAndExportHelper.saveExportCopyToInternalExportsDir(context, exportFileName, json)
                        if (file != null) {
                            repository.markBackupExportSucceeded(exportedAt)
                            backupStatusNotice = "تم إنشاء النسخة الاحتياطية ($exportFileName) بنجاح."
                        }
                        try {
                            exportLauncher.launch(exportFileName)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("export_backup_btn")
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("تنزيل نسخة احتياطية (JSON)")
                }
            }

            if (backupStatusNotice != null) {
                Text(
                    text = backupStatusNotice!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.success,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Restore Section (D16: «استبدال البيانات الحالية بعد تأكيد» only)
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "استعادة نسخة احتياطية (${ArStrings.RESTORE_MODE_LABEL})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "يتم فحص سلامة الملف والبصمة الرقمية وترابط السجلات في الذاكرة أولًا قبل أي تعديل على بياناتكِ الحالية.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            restoreFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        } catch (_: Exception) {
                            showPasteRestoreBox = true
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("select_restore_file_btn")
                ) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("اختيار ملف نسخة احتياطية")
                }
                OutlinedButton(
                    onClick = { showPasteRestoreBox = !showPasteRestoreBox },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("toggle_paste_restore_btn")
                ) {
                    Text("لصق محتوى JSON")
                }
            }

            if (showPasteRestoreBox) {
                OutlinedTextField(
                    value = manualRestoreJsonInput,
                    onValueChange = { manualRestoreJsonInput = it },
                    label = { Text("محتوى ملف النسخة الاحتياطية JSON") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .testTag("paste_restore_json_input"),
                    minLines = 4
                )
                Button(
                    onClick = {
                        when (val valRes = BackupSerializer.validateAndParseBackup(manualRestoreJsonInput)) {
                            is BackupSerializer.RestoreValidationResult.Invalid -> {
                                restoreErrorNotice = valRes.errorAr
                                pendingRestoreValid = null
                            }
                            is BackupSerializer.RestoreValidationResult.Valid -> {
                                restoreErrorNotice = null
                                pendingRawJson = manualRestoreJsonInput
                                pendingRestoreValid = valRes
                                restoreConfirmStep = 1
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("validate_pasted_restore_btn")
                ) {
                    Text("فحص ومعاينة النسخة الاحتياطية")
                }
            }

            if (restoreErrorNotice != null) {
                Surface(
                    color = palette.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, palette.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restore_error_banner")
                ) {
                    Text(
                        text = restoreErrorNotice!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }

    // Restore Preview & Double Confirmation Dialog (Section 8.10 & D16)
    if (pendingRestoreValid != null) {
        val valid = pendingRestoreValid!!
        val p = valid.preview
        AlertDialog(
            onDismissRequest = { pendingRestoreValid = null },
            title = {
                Text(
                    text = if (restoreConfirmStep == 1) "معاينة النسخة الاحتياطية قبل الاستعادة"
                    else "تأكيد نهائي لاستبدال البيانات الحالية",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "الوضع: ${ArStrings.RESTORE_MODE_LABEL}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryText
                    )
                    Text(
                        text = "• تاريخ التصدير: ${p.exportedAt.take(10)}\n" +
                            "• إصدار المخطط (schemaVersion): ${p.schemaVersion}\n" +
                            "• السنوات الدراسية: ${p.schoolYearsCount}\n" +
                            "• الأقسام: ${p.classesCount}\n" +
                            "• التلاميذ: ${p.studentsCount}\n" +
                            "• مذكرات التحضير: ${p.lessonsCount}\n" +
                            "• سجلات النقاط: ${p.gradeRecordsCount}\n" +
                            "• ملاحظات التكوين: ${p.trainingNotesCount}\n" +
                            "• الندوات: ${p.seminarsCount}\n" +
                            "• السجلات الأخرى: ${p.otherRecordsCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            val nowIso = DateTimeUtils.nowIsoUtc()
                            val safetyJson = BackupSerializer.exportBackupJson(snapshot, localSettings, nowIso)
                            PrintAndExportHelper.saveExportCopyToInternalExportsDir(
                                context,
                                "pre-restore-safety-backup.json",
                                safetyJson
                            )
                            backupStatusNotice = "تم حفظ نسخة أمان من البيانات الحالية."
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("تنزيل نسخة من البيانات الحالية قبل الاستبدال")
                    }
                    if (restoreConfirmStep == 2) {
                        Text(
                            text = "تحذير نهائي: سيتم استبدال جميع البيانات الحالية بالنسخة الاحتياطية في معاملة واحدة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                if (restoreConfirmStep == 1) {
                    Button(
                        onClick = { restoreConfirmStep = 2 },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("restore_confirm_step1_btn")
                    ) {
                        Text("متابعة للتأكيد الثاني")
                    }
                } else {
                    Button(
                        onClick = {
                            val res = repository.restoreFromValidatedBackup(pendingRawJson)
                            if (res.isSuccess) {
                                pendingRestoreValid = null
                                showPasteRestoreBox = false
                                backupStatusNotice = "تمت استعادة النسخة الاحتياطية بنجاح."
                            } else {
                                restoreErrorNotice = res.exceptionOrNull()?.message
                                pendingRestoreValid = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.error,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("restore_confirm_step2_btn")
                    ) {
                        Text("تأكيد استبدال البيانات الآن")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRestoreValid = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

// =========================================================================
// 9. SAMPLE DATA & DANGER ZONE (البيانات التجريبية والحذف) — D11 & 8.12, T08
// =========================================================================
@Composable
private fun SampleAndResetSubPage(
    snapshot: DatabaseSnapshot,
    localSettings: LocalSettings,
    repository: TeacherRepository
) {
    val palette = LocalTeacherPalette.current
    val context = LocalContext.current
    var showRemoveSampleDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    var resetConfirmStep by remember { mutableIntStateOf(1) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    // Load Sample Data card
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "البيانات التجريبية",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "تُضاف البيانات التجريبية بطلبكِ فقط وتحمل وسم «تجريبية» في كل الشاشات، ويمكن حذفها وحدها دون المساس بأقسامكِ الحقيقية.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            OutlinedButton(
                onClick = {
                    repository.seedSampleData(fromOnboarding = false)
                    statusNotice = "تمت إضافة البيانات التجريبية."
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("settings_load_sample_btn")
            ) {
                Icon(Icons.Outlined.Science, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("جرّبي ببيانات تجريبية")
            }
            if (statusNotice != null) {
                Text(
                    text = statusNotice!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.success,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Visually separated Danger Zone (Section 8.12)
    Surface(
        color = palette.error.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, palette.error),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = palette.error)
                Text(
                    text = "منطقة الخطر والحذف",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.error
                )
            }

            OutlinedButton(
                onClick = { showRemoveSampleDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.error),
                border = BorderStroke(1.dp, palette.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("remove_sample_data_only_btn")
            ) {
                Text("حذف البيانات التجريبية فقط")
            }

            HorizontalDivider(color = palette.error.copy(alpha = 0.4f))

            Text(
                text = ArStrings.DESTRUCTIVE_NOTICE,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = palette.error
            )

            Button(
                onClick = {
                    resetConfirmStep = 1
                    showResetAllDialog = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.error,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("reset_all_data_btn")
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("حذف كل البيانات")
            }
        }
    }

    // Remove Sample Data Only Confirmation (D11)
    if (showRemoveSampleDialog) {
        val summary = repository.getSampleDeletionSummary()
        AlertDialog(
            onDismissRequest = { showRemoveSampleDialog = false },
            title = { Text("حذف البيانات التجريبية فقط", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "سيتم حذف الأقسام التجريبية وكل ما يرتبط بها فقط:\n" +
                            "• أقسام تجريبية: ${summary.sampleClassesCount}\n" +
                            "• تلاميذ مرتبطون بها: ${summary.sampleStudentsCount}\n" +
                            "• حصص توقيت: ${summary.sampleTimetableCount}\n" +
                            "• مذكرات تحضير: ${summary.sampleLessonsCount}\n" +
                            "• ملاحظات حصة: ${summary.sampleObservationsCount}\n" +
                            "• سجلات تنقيط: ${summary.sampleGradesCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (summary.realRecordsLinkedToSampleClasses > 0) {
                        Text(
                            text = "تنبيه: يتضمن ذلك ${summary.realRecordsLinkedToSampleClasses} سجل أضفتِه داخل قسم تجريبي.",
                            color = palette.warning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "لن تُمس أي بيانات تابعة لأقسامكِ الحقيقية.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.success,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.removeSampleDataOnly()
                        showRemoveSampleDialog = false
                        statusNotice = "تم حذف البيانات التجريبية بنجاح."
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.error,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_remove_sample_btn")
                ) {
                    Text("تأكيد حذف التجريبية فقط")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveSampleDialog = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("إلغاء")
                }
            }
        )
    }

    // Reset All Data Double Confirmation Dialog (Section 8.12)
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = {
                Text(
                    text = if (resetConfirmStep == 1) "حذف كل البيانات (تأكيد 1 من 2)"
                    else "تأكيد نهائي لحذف كل البيانات (2 من 2)",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = ArStrings.DESTRUCTIVE_NOTICE,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.error
                    )
                    Text(
                        text = "سيؤدي هذا الإجراء إلى مسح جميع السنوات والأقسام والتلاميذ والتحضيرات والنقاط والعودة إلى شاشة الإعداد الأولي (مع الاحتفاظ بتفضيلات المظهر فقط).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            val nowIso = DateTimeUtils.nowIsoUtc()
                            val json = BackupSerializer.exportBackupJson(snapshot, localSettings, nowIso)
                            PrintAndExportHelper.saveExportCopyToInternalExportsDir(
                                context,
                                "pre-reset-backup.json",
                                json
                            )
                            repository.markBackupExportSucceeded(nowIso)
                            statusNotice = "تم حفظ نسخة احتياطية قبل الحذف."
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("تنزيل نسخة احتياطية أولًا")
                    }
                }
            },
            confirmButton = {
                if (resetConfirmStep == 1) {
                    Button(
                        onClick = { resetConfirmStep = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.error, contentColor = Color.White),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("reset_all_step1_btn")
                    ) {
                        Text("متابعة للتأكيد النهائي")
                    }
                } else {
                    Button(
                        onClick = {
                            repository.resetAllData()
                            showResetAllDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.error, contentColor = Color.White),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("reset_all_step2_btn")
                    ) {
                        Text("تأكيد حذف كل البيانات نهائيًا")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("إلغاء")
                }
            }
        )
    }
}
