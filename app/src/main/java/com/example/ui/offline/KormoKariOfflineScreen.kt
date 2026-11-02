package com.example.ui.offline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.CachedApplication
import com.example.data.local.CachedJob
import com.example.network.NetworkState
import com.example.ui.startup.StartupScreenState
import com.example.ui.theme.KormoKariNavy
import com.example.ui.theme.KormoKariPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KormoKariOfflineScreen(
    state: StartupScreenState,
    onReconnect: () -> Unit,
    onBackToStartup: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onSelectJob: (CachedJob?) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_offline_jobs),
        stringResource(R.string.tab_offline_applications),
        stringResource(R.string.tab_offline_settings)
    )

    val filteredJobs = remember(state.cachedJobs, state.offlineSearchQuery, state.selectedOfflineCategory) {
        state.cachedJobs.filter { job ->
            val matchesCategory = state.selectedOfflineCategory == null || job.category.equals(state.selectedOfflineCategory, ignoreCase = true)
            val query = state.offlineSearchQuery.trim()
            val matchesQuery = query.isEmpty() ||
                job.title.contains(query, ignoreCase = true) ||
                job.company.contains(query, ignoreCase = true) ||
                job.location.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFBF9FB))
            .testTag("offline_screen")
    ) {
        // 1. Top Header with Back button and prominent 'Offline Mode' badge
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBackToStartup,
                            modifier = Modifier.testTag("offline_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Return to startup screen",
                                tint = KormoKariNavy
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "KormoKari",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = KormoKariPrimary
                        )
                    }

                    // Prominent 'Offline Mode' Badge
                    OfflineModeBadge()
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Network reconnect indicator banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = when (state.networkState) {
                                is NetworkState.Connected -> Color(0xFFD1FAE5)
                                else -> Color(0xFFFFFBEB)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (state.networkState) {
                                is NetworkState.Connected -> Color(0xFF10B981)
                                else -> Color(0xFFF59E0B)
                            },
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (state.networkState) {
                                is NetworkState.Connected -> "Connection restored! Tap to launch live app."
                                else -> stringResource(R.string.offline_banner_subtitle)
                            },
                            fontSize = 11.5.sp,
                            color = when (state.networkState) {
                                is NetworkState.Connected -> Color(0xFF065F46)
                                else -> Color(0xFF92400E)
                            },
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onReconnect,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("reconnect_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_reconnect),
                            tint = when (state.networkState) {
                                is NetworkState.Connected -> Color(0xFF047857)
                                else -> Color(0xFFB45309)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 2. Navigation Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.White,
            contentColor = KormoKariPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = KormoKariPrimary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.5.sp
                        )
                    },
                    modifier = Modifier.testTag("offline_tab_$index")
                )
            }
        }

        // 3. Tab Contents
        when (selectedTabIndex) {
            0 -> {
                OfflineJobsTab(
                    jobs = filteredJobs,
                    searchQuery = state.offlineSearchQuery,
                    onSearchQueryChanged = onSearchQueryChanged,
                    selectedCategory = state.selectedOfflineCategory,
                    onCategorySelected = onCategorySelected,
                    onSelectJob = onSelectJob
                )
            }
            1 -> {
                OfflineApplicationsTab(applications = state.cachedApplications)
            }
            2 -> {
                OfflineSettingsTab(
                    isIgnoringBatteryOptimizations = state.isIgnoringBatteryOptimizations,
                    onRequestBatteryExemption = onRequestBatteryExemption,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onReconnect = onReconnect
                )
            }
        }
    }

    // Modal Sheet for Job Detail View
    if (state.selectedJobDetail != null) {
        JobDetailBottomSheet(
            job = state.selectedJobDetail,
            onDismiss = { onSelectJob(null) }
        )
    }
}

/**
 * High-visibility 'Offline Mode' badge required by user specifications.
 */
@Composable
fun OfflineModeBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFEF3C7), // Soft amber palette
        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
        modifier = modifier.testTag("offline_mode_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = Color(0xFFD97706),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.offline_mode_badge),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF92400E)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFF59E0B).copy(alpha = 0.25f)
            ) {
                Text(
                    text = "READ-ONLY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF78350F),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun OfflineJobsTab(
    jobs: List<CachedJob>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onSelectJob: (CachedJob) -> Unit
) {
    val categories = listOf("All", "Delivery", "Skilled Trades", "Hospitality", "Customer Service", "Retail")

    Column(modifier = Modifier.fillMaxSize()) {
        // Search and Filter Bar
        Surface(
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_search_field"),
                    placeholder = { Text("Search cached jobs, titles, locations…", fontSize = 13.5.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChanged("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KormoKariPrimary,
                        unfocusedBorderColor = Color(0xFFE5E7EB)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val isSelected = if (category == "All") selectedCategory == null else selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onCategorySelected(if (category == "All") null else category)
                            },
                            label = { Text(category, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KormoKariPrimary,
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }
        }

        // Job list
        if (jobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.WorkOutline,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No cached jobs matching criteria",
                        style = MaterialTheme.typography.titleMedium,
                        color = KormoKariNavy,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Try clearing your search query or choosing another category.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("offline_jobs_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "${jobs.size} Opportunities Cached on Device",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(jobs, key = { it.id }) { job ->
                    CachedJobCard(job = job, onClick = { onSelectJob(job) })
                }
            }
        }
    }
}

@Composable
private fun CachedJobCard(job: CachedJob, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("job_card_${job.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFF0EDF0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KormoKariNavy
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = job.company,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4B5563),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (job.isFeatured) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE91069).copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "Featured",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = KormoKariPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Location & Salary Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF3F4F6)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = job.location,
                            fontSize = 11.5.sp,
                            color = Color(0xFF374151)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFECFDF5)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = job.salary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF047857)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = job.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Cached ${job.postedAgo}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Text(
                    text = "View Details →",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = KormoKariPrimary
                )
            }
        }
    }
}

@Composable
private fun OfflineApplicationsTab(applications: List<CachedApplication>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("offline_applications_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Your applications are synced for offline reference. Interviews and offers will alert you once connection is active.",
                        fontSize = 12.sp,
                        color = Color(0xFF1E40AF),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        items(applications, key = { it.id }) { app ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("application_card_${app.id}"),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFF0EDF0))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = app.jobTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = KormoKariNavy
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = when (app.status) {
                                "Interview Scheduled" -> Color(0xFFDCFCE7)
                                else -> Color(0xFFFEF3C7)
                            }
                        ) {
                            Text(
                                text = app.status,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (app.status) {
                                    "Interview Scheduled" -> Color(0xFF15803D)
                                    else -> Color(0xFFB45309)
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = app.company,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = app.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Applied on ${app.appliedDate}",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineSettingsTab(
    isIgnoringBatteryOptimizations: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onReconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Battery Optimization Exemption Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("battery_optimization_card"),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFF0EDF0))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isIgnoringBatteryOptimizations) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = if (isIgnoringBatteryOptimizations) Color(0xFF10B981) else KormoKariPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.battery_optimization_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = KormoKariNavy
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.battery_optimization_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4B5563),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Current Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isIgnoringBatteryOptimizations) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isIgnoringBatteryOptimizations) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isIgnoringBatteryOptimizations)
                                stringResource(R.string.battery_optimization_status_unrestricted)
                            else
                                stringResource(R.string.battery_optimization_status_restricted),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIgnoringBatteryOptimizations) Color(0xFF047857) else Color(0xFFB91C1C)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isIgnoringBatteryOptimizations) {
                    Button(
                        onClick = onRequestBatteryExemption,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("request_battery_exemption_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = KormoKariPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_request_battery_exemption),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onOpenBatterySettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("open_battery_settings_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_open_battery_settings),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = KormoKariNavy
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Background alerts are fully permitted by OS.",
                            fontSize = 12.sp,
                            color = Color(0xFF065F46),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Live Reconnect CTA
        Button(
            onClick = onReconnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("offline_reconnect_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = KormoKariNavy,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp,
                hoveredElevation = 6.dp
            ),
            shape = RoundedCornerShape(26.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.action_reconnect),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.action_reconnect),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobDetailBottomSheet(
    job: CachedJob,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .testTag("job_detail_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OfflineModeBadge()
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close sheet")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = job.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = KormoKariNavy
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${job.company} • ${job.location}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFECFDF5)
            ) {
                Text(
                    text = "Compensation: ${job.salary}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF047857),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Job Description",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = KormoKariNavy
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = job.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF374151),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Requirements",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = KormoKariNavy
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = job.requirements,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF374151),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF1F2),
                border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = KormoKariPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Direct Contact (Offline)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = KormoKariPrimary
                        )
                        Text(
                            text = job.contactEmail,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KormoKariNavy
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
