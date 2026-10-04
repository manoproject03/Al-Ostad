package com.example.features

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.components.ArchivedBadge
import com.example.components.SampleBadge
import com.example.components.TeacherEmptyState
import com.example.domain.SearchResultItem
import com.example.i18n.ArStrings
import com.example.storage.TeacherRepository
import com.example.ui.theme.LocalTeacherPalette

/**
 * D1: «المزيد» opens a page listing: التكوين والندوات، بطاقتي المهنية، الإعدادات.
 */
@Composable
fun MoreHubScreen(
    onNavigate: (String) -> Unit
) {
    val palette = LocalTeacherPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("more_hub_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = ArStrings.NAV_MORE,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.text
        )

        MoreDestinationCard(
            icon = Icons.Outlined.School,
            title = ArStrings.NAV_TRAINING,
            subtitle = "ملاحظات التكوين، الندوات الداخلية والخارجية، والرزنامة التربوية",
            testTag = "more_nav_training",
            onClick = { onNavigate("training") }
        )

        MoreDestinationCard(
            icon = Icons.Outlined.Badge,
            title = ArStrings.NAV_PROFILE,
            subtitle = "البيانات المهنية الاختيارية، الأقسام المسندة، وطباعة البطاقة المهنية",
            testTag = "more_nav_profile",
            onClick = { onNavigate("profile") }
        )

        MoreDestinationCard(
            icon = Icons.Outlined.Settings,
            title = ArStrings.NAV_SETTINGS,
            subtitle = "المظهر، قوالب الأنشطة، إعدادات التنقيط، السنوات الدراسية، والنسخ الاحتياطي",
            testTag = "more_nav_settings",
            onClick = { onNavigate("settings") }
        )
    }
}

@Composable
private fun MoreDestinationCard(
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
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.primaryText,
                    modifier = Modifier.size(30.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
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

/**
 * Section 8.9 & D14: Global Search Overlay
 */
@Composable
fun GlobalSearchDialog(
    repository: TeacherRepository,
    onSelectResult: (SearchResultItem) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalTeacherPalette.current
    var query by remember { mutableStateOf("") }
    var includeArchived by remember { mutableStateOf(false) }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    val groupedResults = remember(query, includeArchived, repository.snapshot.value) {
        repository.searchGlobal(query, includeArchived)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("global_search_dialog"),
            color = palette.bg,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, palette.border)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Header
                Surface(
                    color = palette.surface,
                    border = BorderStroke(1.dp, palette.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("ابحثي في التحضيرات، التلاميذ، الأقسام، التكوين، الندوات، الرزنامة…") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("global_search_input"),
                                singleLine = true
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("close_global_search_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق البحث")
                            }
                        }

                        // Real toggle: «تضمين السنوات والسجلات المؤرشفة» (D14)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { includeArchived = !includeArchived }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = includeArchived,
                                onCheckedChange = { includeArchived = it },
                                modifier = Modifier.testTag("search_include_archived_toggle")
                            )
                            Text(
                                text = "تضمين السنوات والسجلات المؤرشفة",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.text
                            )
                        }
                    }
                }

                // Results Body
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (query.trim().isEmpty()) {
                        TeacherEmptyState(
                            icon = Icons.Outlined.Search,
                            title = "البحث الشامل في التطبيق",
                            description = "يدعم البحث التطبيع الذكي للغة العربية (الهمزات، التاء المربوطة، التشكيل، والأرقام). الملاحظات الخاصة بالتلاميذ مستثناة دائمًا حفاظًا على الخصوصية."
                        )
                    } else if (groupedResults.isEmpty()) {
                        TeacherEmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "لا توجد نتائج مطابقة لعبارة البحث",
                            description = "جرّبي كلمات مفتاحية أخرى أو فعّلي خيار «تضمين السنوات والسجلات المؤرشفة»."
                        )
                    } else {
                        groupedResults.forEach { (typeLabel, items) ->
                            val showAll = expandedGroups[typeLabel] == true
                            val visibleItems = if (showAll) items else items.take(20)

                            Surface(
                                color = palette.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, palette.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "$typeLabel (${items.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryText
                                    )

                                    visibleItems.forEach { item ->
                                        Surface(
                                            color = palette.raised,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, palette.border),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSelectResult(item) }
                                                .testTag("search_result_${item.id}")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Surface(
                                                            color = palette.primary.copy(alpha = 0.14f),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Text(
                                                                text = item.typeLabel,
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = palette.primaryText,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = item.title,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = palette.text
                                                        )
                                                        if (item.isSample) SampleBadge()
                                                        if (item.isArchived) ArchivedBadge()
                                                    }
                                                    Text(
                                                        text = "${item.subtitle} • السنة: ${item.schoolYearLabel}",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = palette.textSecondary
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                    contentDescription = "فتح السجل",
                                                    tint = palette.textSecondary
                                                )
                                            }
                                        }
                                    }

                                    if (items.size > 20 && !showAll) {
                                        TextButton(
                                            onClick = { expandedGroups[typeLabel] = true },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                        ) {
                                            Text("عرض المزيد (${items.size - 20} نتيجة إضافية)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
