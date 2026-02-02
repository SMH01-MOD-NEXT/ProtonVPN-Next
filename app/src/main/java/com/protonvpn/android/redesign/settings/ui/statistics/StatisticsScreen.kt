package com.protonvpn.android.redesign.settings.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.statistics.StatisticsDao
import com.protonvpn.android.statistics.VpnSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// Constants for formatting and calculations
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val BYTES_PER_KB = 1024
private const val BYTES_PER_MB = 1024 * 1024
private const val BYTES_PER_GB = 1024 * 1024 * 1024
private const val SUBSCRIBE_TIMEOUT_MS = 5000L
private const val SUCCESS_RATE_THRESHOLD_HIGH = 90
private const val SUCCESS_RATE_THRESHOLD_MEDIUM = 70
private const val PERCENT_SCALE = 100

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val dao: StatisticsDao
) : ViewModel() {

    enum class TimeRange(val labelResourceId: Int) {
        DAY(R.string.statistics_day),
        MONTH(R.string.statistics_month),
        YEAR(R.string.statistics_year)
    }

    private val _selectedRange = MutableStateFlow(TimeRange.DAY)
    val selectedRange = _selectedRange.asStateFlow()

    val statsState = combine(dao.getAllSessions(), _selectedRange) { sessions, range ->
        filterSessions(sessions, range)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), StatisticsState())

    fun setRange(range: TimeRange) {
        _selectedRange.value = range
    }

    private fun filterSessions(sessions: List<VpnSessionEntity>, range: TimeRange): StatisticsState {
        val startOfRange = Calendar.getInstance()

        when (range) {
            TimeRange.DAY -> startOfRange.set(Calendar.HOUR_OF_DAY, 0)
            TimeRange.MONTH -> startOfRange.set(Calendar.DAY_OF_MONTH, 1)
            TimeRange.YEAR -> startOfRange.set(Calendar.DAY_OF_YEAR, 1)
        }

        if (range != TimeRange.DAY) {
            startOfRange.set(Calendar.HOUR_OF_DAY, 0)
        }
        startOfRange.set(Calendar.MINUTE, 0)
        startOfRange.set(Calendar.SECOND, 0)

        val filtered = sessions.filter { it.startTime >= startOfRange.timeInMillis }
        val successfulSessions = filtered.filterNot { it.isConnectionError }
        val failedConnections = filtered.filter { it.isConnectionError }

        val totalDuration = successfulSessions.sumOf { it.durationSeconds }
        val count = successfulSessions.size
        val totalDownload = successfulSessions.sumOf { it.downloadBytes }
        val totalUpload = successfulSessions.sumOf { it.uploadBytes }

        val serverStats = successfulSessions.groupingBy { it.serverCountry }.eachCount()
        val protocolStats = successfulSessions.groupingBy { it.protocol }.eachCount()

        return StatisticsState(
            sessions = filtered,
            successfulSessions = successfulSessions,
            failedConnections = failedConnections,
            totalConnections = count,
            totalDurationSeconds = totalDuration,
            totalDownloadBytes = totalDownload,
            totalUploadBytes = totalUpload,
            serverStats = serverStats,
            protocolStats = protocolStats,
            connectionFailureCount = failedConnections.size
        )
    }
}

data class StatisticsState(
    val sessions: List<VpnSessionEntity> = emptyList(),
    val successfulSessions: List<VpnSessionEntity> = emptyList(),
    val failedConnections: List<VpnSessionEntity> = emptyList(),
    val totalConnections: Int = 0,
    val totalDurationSeconds: Long = 0,
    val totalDownloadBytes: Long = 0,
    val totalUploadBytes: Long = 0,
    val serverStats: Map<String, Int> = emptyMap(),
    val protocolStats: Map<String, Int> = emptyMap(),
    val connectionFailureCount: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onClose: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val range by viewModel.selectedRange.collectAsState()
    val state by viewModel.statsState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Time Range Selection
            item {
                TimeRangeSelector(range, viewModel::setRange)
            }

            // Summary Cards
            item {
                StatisticsSummaryCards(state)
            }

            // Connection Success Rate
            item {
                SuccessRateCard(state)
            }

            // Traffic Chart
            item {
                TrafficCard(state)
            }

            // Server Distribution
            if (state.serverStats.isNotEmpty()) {
                item {
                    ServerDistributionCard(state.serverStats)
                }
            }

            // Protocol Distribution
            if (state.protocolStats.isNotEmpty()) {
                item {
                    ProtocolDistributionCard(state.protocolStats)
                }
            }

            // Session History
            item {
                Text(
                    stringResource(R.string.statistics_connection_history),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(state.sessions) { session ->
                SessionItemCard(session)
            }

            if (state.sessions.isEmpty()) {
                item {
                    EmptyStateMessage()
                }
            }
        }
    }
}

@Composable
private fun TimeRangeSelector(range: StatisticsViewModel.TimeRange, onRangeSelect: (StatisticsViewModel.TimeRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatisticsViewModel.TimeRange.entries.forEach { timeRange ->
            Button(
                onClick = { onRangeSelect(timeRange) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (range == timeRange) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (range == timeRange) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(stringResource(timeRange.labelResourceId), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StatisticsSummaryCards(state: StatisticsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatisticsCard(
                title = stringResource(R.string.statistics_connections),
                value = state.totalConnections.toString(),
                icon = "📡",
                modifier = Modifier.weight(1f)
            )
            StatisticsCard(
                title = stringResource(R.string.statistics_errors),
                value = state.connectionFailureCount.toString(),
                icon = "⚠️",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatisticsCard(
                title = stringResource(R.string.statistics_total_time),
                value = formatDuration(state.totalDurationSeconds),
                icon = "⏱️",
                modifier = Modifier.weight(1f)
            )
            StatisticsCard(
                title = stringResource(R.string.statistics_success_rate),
                value = if (state.totalConnections > 0) {
                    "${(state.totalConnections - state.connectionFailureCount) * 100 / state.totalConnections}%"
                } else "0%",
                icon = "✓",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatisticsCard(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SuccessRateCard(state: StatisticsState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.statistics_success_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            val successRate = if (state.totalConnections > 0) {
                (state.totalConnections - state.connectionFailureCount) * PERCENT_SCALE / state.totalConnections
            } else 0

            Box(modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { successRate / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (successRate >= SUCCESS_RATE_THRESHOLD_HIGH) MaterialTheme.colorScheme.primary else if (successRate >= SUCCESS_RATE_THRESHOLD_MEDIUM) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "$successRate% (${state.totalConnections - state.connectionFailureCount}/${state.totalConnections})",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TrafficCard(state: StatisticsState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.statistics_traffic),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TrafficItem(
                    label = stringResource(R.string.statistics_download),
                    value = formatBytes(state.totalDownloadBytes),
                    icon = "⬇️"
                )
                TrafficItem(
                    label = stringResource(R.string.statistics_upload),
                    value = formatBytes(state.totalUploadBytes),
                    icon = "⬆️"
                )
                TrafficItem(
                    label = stringResource(R.string.statistics_total),
                    value = formatBytes(state.totalDownloadBytes + state.totalUploadBytes),
                    icon = "↔️"
                )
            }
        }
    }
}

@Composable
private fun TrafficItem(label: String, value: String, icon: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ServerDistributionCard(serverStats: Map<String, Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.statistics_server_distribution),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            val sortedServers = serverStats.toList().sortedByDescending { it.second }
            val totalConnections = sortedServers.sumOf { it.second }

            sortedServers.forEach { (country, count) ->
                val percentage = (count * PERCENT_SCALE) / totalConnections
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(country, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("$count", style = MaterialTheme.typography.labelSmall)
                    }
                    LinearProgressIndicator(
                        progress = { percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ProtocolDistributionCard(protocolStats: Map<String, Int>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.statistics_protocol_distribution),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                protocolStats.forEach { (protocol, count) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(protocol, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItemCard(session: VpnSessionEntity) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (session.isConnectionError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dateFormat.format(Date(session.startTime)),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (session.serverName.isNotEmpty()) {
                        Text(
                            "${session.serverCountry} • ${session.serverName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    if (session.isConnectionError) stringResource(R.string.statistics_error) else stringResource(R.string.statistics_success),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (session.isConnectionError) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.statistics_error_label, session.errorMessage ?: "Unknown error"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.statistics_duration_label, formatDuration(session.durationSeconds)),
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (session.protocol.isNotEmpty()) {
                        Text(
                            session.protocol,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "📊",
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.statistics_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.statistics_empty_description),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun formatDuration(seconds: Long): String {
    return when {
        seconds < SECONDS_PER_MINUTE -> "${seconds}с"
        seconds < SECONDS_PER_HOUR -> "${seconds / SECONDS_PER_MINUTE}м ${seconds % SECONDS_PER_MINUTE}с"
        else -> {
            val hours = seconds / SECONDS_PER_HOUR
            val minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
            "$hours:${String.format(Locale.US, "%02d", minutes)}"
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < BYTES_PER_KB -> "$bytes B"
        bytes < BYTES_PER_MB -> "${bytes / BYTES_PER_KB} KB"
        bytes < BYTES_PER_GB -> "${String.format(Locale.US, "%.2f", bytes.toDouble() / BYTES_PER_MB)} MB"
        else -> "${String.format(Locale.US, "%.2f", bytes.toDouble() / BYTES_PER_GB)} GB"
    }
}