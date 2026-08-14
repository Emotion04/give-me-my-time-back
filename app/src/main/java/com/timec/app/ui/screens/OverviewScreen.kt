package com.timec.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.timec.app.data.AppInfo
import com.timec.app.monitor.SessionPhase
import com.timec.app.ui.AppViewModel
import com.timec.app.ui.components.AppIcon
import com.timec.app.ui.components.BarChart
import com.timec.app.ui.components.PieChart
import kotlinx.coroutines.delay

private val rangeOptions = listOf(
    1 to "今日",
    7 to "近7天",
    30 to "近30天"
)

@Composable
fun OverviewScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()
    val monitorState by viewModel.monitorState.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val rangeDays by viewModel.rangeDays.collectAsState()
    val rangeUsage by viewModel.rangeUsage.collectAsState()
    val rangeTotals by viewModel.rangeTotals.collectAsState()
    val detailPackage by viewModel.detailPackage.collectAsState()
    val hourlyDetail by viewModel.hourlyDetail.collectAsState()
    val usageGranted by viewModel.usageGranted.collectAsState()

    // 授权返回或回到前台时刷新
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 前台时每 10 秒自动刷新
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            viewModel.refreshRange()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!usageGranted) {
            item {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )) {
                    Column(Modifier.padding(16.dp)) {
                        Text("尚未授权“使用情况访问”", fontWeight = FontWeight.Bold)
                        Text("授权后才能统计各 App 的使用时长，否则会一直显示 0。")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.openUsageAccessSettings() }) {
                            Text("去授权")
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rangeOptions.forEach { (days, label) ->
                    FilterChip(
                        selected = rangeDays == days,
                        onClick = { viewModel.setRangeDays(days) },
                        label = { Text(label) }
                    )
                }
            }
        }

        item {
            ActiveSessionCard(
                monitorState.activePackage,
                monitorState.activeSnapshot,
                apps,
                monitorState.foregroundPackage
            )
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (rangeDays == 1) "今日总使用时长" else "近 " + rangeDays + " 天总使用时长",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        formatDuration(rangeUsage.values.sum()),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (rangeDays > 1) {
            item {
                ChartCard(
                    title = "屏幕总时长趋势",
                    description = "每天的总亮屏使用时长"
                ) {
                    BarChart(rangeTotals.map { dayLabel(it.dayStartMillis) to it.totalMillis })
                }
            }
        }

        item {
            ChartCard(
                title = "应用占比",
                description = "前 6 个应用"
            ) {
                val data = topUsage(rangeUsage, apps, 6)
                PieChart(data)
                Legend(data)
            }
        }

        item {
            ChartCard(
                title = "应用时长条形图",
                description = "使用时长最高的应用"
            ) {
                val data = topUsage(rangeUsage, apps, 6)
                BarChart(data)
                Legend(data)
            }
        }

        item {
            Text("排行榜", style = MaterialTheme.typography.titleLarge)
            Text("点按应用查看今日分时", style = MaterialTheme.typography.bodyMedium)
        }

        items(
            rangeUsage.entries.sortedByDescending { it.value }.take(10),
            key = { it.key }
        ) { entry ->
            val pkg = entry.key
            val millis = entry.value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.openDetail(pkg) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(appInfo(apps, pkg)?.icon, Modifier.size(32.dp))
                Text(appLabel(apps, pkg), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(formatDuration(millis), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    detailPackage?.let { pkg ->
        AlertDialog(
            onDismissRequest = { viewModel.closeDetail() },
            confirmButton = {
                TextButton(onClick = { viewModel.closeDetail() }) { Text("关闭") }
            },
            title = { Text(appLabel(apps, pkg) + " · 今日分时") },
            text = {
                val hours = hourlyDetail.toSortedMap()
                if (hours.isEmpty()) {
                    Text("今天暂无使用记录")
                } else {
                    Box(Modifier.height(300.dp)) {
                        LazyColumn {
                            items(hours.toList()) { (hour, millis) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(hour.toString() + ":00 时")
                                    Text(formatDuration(millis))
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ActiveSessionCard(
    activePackage: String?,
    snapshot: com.timec.app.monitor.SessionSnapshot?,
    apps: List<AppInfo>,
    foregroundPackage: String?
) {
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    )) {
        Column(Modifier.padding(16.dp)) {
            Text("当前守护", style = MaterialTheme.typography.titleMedium)
            val label = activePackage?.let { appLabel(apps, it) } ?: "当前没有正在守护的目标应用"
            Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (activePackage != null && snapshot != null) {
                val remaining = (snapshot.allowedMillis - snapshot.consumedMillis).coerceAtLeast(0L)
                Spacer(Modifier.height(8.dp))
                Text("已使用 " + formatDuration(snapshot.activeMillis) + " / 额度 " + formatDuration(snapshot.allowedMillis))
                Text("已消耗 " + formatDuration(snapshot.consumedMillis) + "，剩余 " + formatDuration(remaining))
                if (snapshot.currentRateX > 1.0) {
                    Text("消耗倍率 x" + snapshot.currentRateX.toString())
                }
                if (snapshot.bankedMillis > 0L) {
                    Text("通过离开挣回 " + formatDuration(snapshot.bankedMillis))
                }
                Text(
                    when (snapshot.phase) {
                        SessionPhase.FINAL -> "已进入本次正式限制"
                        SessionPhase.COOLDOWN -> "冷却中"
                        else -> "正在连续使用"
                    }
                )
            } else if (foregroundPackage != null) {
                Spacer(Modifier.height(8.dp))
                Text("前台应用：" + appLabel(apps, foregroundPackage))
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Legend(data: List<Pair<String, Long>>) {
    Column(Modifier.padding(top = 12.dp)) {
        data.forEachIndexed { index, (name, millis) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp)
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(color = legendColor(index), shape = CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                Text(name, modifier = Modifier.weight(1f))
                Text(formatDuration(millis))
            }
        }
    }
}

private fun topUsage(
    usage: Map<String, Long>,
    apps: List<AppInfo>,
    count: Int
): List<Pair<String, Long>> {
    val sorted = usage.entries.sortedByDescending { it.value }.take(count)
    return sorted.map { appLabel(apps, it.key) to it.value }
}

private fun appInfo(apps: List<AppInfo>, packageName: String): AppInfo? {
    return apps.firstOrNull { it.packageName == packageName }
}

private fun appLabel(apps: List<AppInfo>, packageName: String): String {
    return appInfo(apps, packageName)?.label ?: packageName
}

private fun dayLabel(dayStartMillis: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = dayStartMillis
    return (cal.get(java.util.Calendar.MONTH) + 1).toString() + "/" +
        cal.get(java.util.Calendar.DAY_OF_MONTH).toString()
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d小时%02d分".format(hours, minutes)
    } else {
        "%d分%02d秒".format(minutes, seconds)
    }
}

private fun legendColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF4D6A7A),
        Color(0xFF7FD1AE),
        Color(0xFFE6B76A),
        Color(0xFFDB6868),
        Color(0xFF8FA8C8),
        Color(0xFFA98FC8),
        Color(0xFFC89F9F)
    )
    return colors[index % colors.size]
}
