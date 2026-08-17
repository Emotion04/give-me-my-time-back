package com.timec.app.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.timec.app.data.AppInfo
import com.timec.app.monitor.MonitorService
import com.timec.app.monitor.SessionPhase
import com.timec.app.ui.AppViewModel
import com.timec.app.ui.components.AppIcon
import com.timec.app.ui.components.BarChart
import com.timec.app.ui.components.PieChart
import com.timec.app.ui.components.TimelineBar
import com.timec.app.ui.components.chartColor
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
    val appActivity by viewModel.appActivity.collectAsState()

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
            val serviceRunning = MonitorService.isRunning
            val guardedCount = settings.appRules.size
            val manual = settings.serviceManual
            when {
                !settings.enabled -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.startGuard() }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("守护已关闭", fontWeight = FontWeight.Bold)
                        Text("点击开启守护（含悬浮计时窗）")
                    }
                }

                serviceRunning && guardedCount > 0 -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("守护运行中 · 守护 " + guardedCount + " 个应用", fontWeight = FontWeight.Bold)
                        Text("悬浮计时窗已就绪：拖动换位置，点击切换指标")
                    }
                }

                serviceRunning -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.stopGuard() }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("悬浮计时窗运行中（手动）", fontWeight = FontWeight.Bold)
                        Text("未守护应用，仅提供计时窗；点击停止")
                    }
                }

                guardedCount > 0 -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.startGuard() }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("守护未运行", fontWeight = FontWeight.Bold)
                        Text("点击启动守护服务")
                    }
                }

                else -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.startGuard() }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("守护未运行 · 悬浮计时窗可用", fontWeight = FontWeight.Bold)
                        Text("点击手动启动（用于只看悬浮计时窗）；若要在所有应用显示，请打开“所有应用都显示”")
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
                monitorState.foregroundPackage,
                settings.appRules.size
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
                PieChart(
                    data.map { it.second to it.third },
                    onClick = { idx -> data.getOrNull(idx)?.let { viewModel.openDetail(it.first) } }
                )
                Legend(data.map { it.second to it.third })
            }
        }

        item {
            ChartCard(
                title = "应用时长条形图",
                description = "使用时长最高的应用"
            ) {
                val data = topUsage(rangeUsage, apps, 6)
                BarChart(
                    data.map { it.second to it.third },
                    onClick = { idx -> data.getOrNull(idx)?.let { viewModel.openDetail(it.first) } }
                )
                Legend(data.map { it.second to it.third })
            }
        }

        item {
            ChartCard(
                title = "最近24小时活动",
                description = "长条=长时间使用，碎片=频繁切换"
            ) {
                val act = appActivity.entries.sortedByDescending { it.value.totalMillis }.take(5)
                if (act.isEmpty()) {
                    Text("暂无数据", style = MaterialTheme.typography.bodyMedium)
                } else {
                    act.forEachIndexed { index, (pkg, activity) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        ) {
                            Text(
                                appLabel(apps, pkg),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(72.dp)
                            )
                            TimelineBar(activity.hourBuckets, chartColor(index), Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item {
            Text("排行榜", style = MaterialTheme.typography.titleLarge)
            Text("点按应用查看 24 小时详情", style = MaterialTheme.typography.bodyMedium)
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
                    .animateItem()
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
            title = { Text(appLabel(apps, pkg) + " · 最近24小时") },
            text = {
                val act = appActivity[pkg]
                if (act == null || act.totalMillis <= 0L) {
                    Text("最近24小时暂无使用记录")
                } else {
                    val colorIndex = rangeUsage.entries.sortedByDescending { it.value }.indexOfFirst { it.key == pkg }
                    Column {
                        Text("打开 " + act.openCount + " 次（单次超过3秒） · 共 " + formatDuration(act.totalMillis))
                        if (act.openCount > 0) {
                            Text("平均每次 " + formatDuration(act.totalMillis / act.openCount))
                        }
                        Spacer(Modifier.height(12.dp))
                        TimelineBar(act.hourBuckets, chartColor(if (colorIndex >= 0) colorIndex else 0), Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("每个色块代表一小时，越宽表示使用越久", style = MaterialTheme.typography.bodySmall)
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
    foregroundPackage: String?,
    guardedCount: Int
) {
    Card(colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    )) {
        Column(Modifier.padding(16.dp).animateContentSize()) {
            Text("当前守护", style = MaterialTheme.typography.titleMedium)
            Text("已守护 " + guardedCount + " 个应用", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(if (MonitorService.isRunning) "守护服务：运行中" else "守护服务：已停止")
            Spacer(Modifier.height(4.dp))
            if (foregroundPackage != null) {
                Text("当前前台：" + appLabel(apps, foregroundPackage))
            } else {
                Text("当前前台：未检测到")
            }
            if (activePackage != null && snapshot != null) {
                Spacer(Modifier.height(8.dp))
                Text("本次连续使用 " + formatDuration(snapshot.sessionActiveMillis) + " / " + formatDuration(snapshot.sessionLimitMillis))
                if (snapshot.dailyLimitMillis > 0L) {
                    Text("今日已用 " + formatDuration(snapshot.dailyUsedMillis) + " / " + formatDuration(snapshot.dailyLimitMillis))
                } else {
                    Text("今日已用 " + formatDuration(snapshot.dailyUsedMillis))
                }
                Text(
                    when (snapshot.phase) {
                        SessionPhase.RUNNING -> "正常使用中"
                        SessionPhase.OVERDRAFT -> "透支中：" + formatDuration(snapshot.overdraftConsumedMillis) + " / " + formatDuration(snapshot.overdraftAllowanceMillis)
                        SessionPhase.COOLDOWN_OVERDRAFT -> "冷却期透支中，下次冷却已 +" + formatDuration(snapshot.cooldownPenaltyMillis)
                        SessionPhase.COOLDOWN -> "冷却中，剩余 " + formatDuration(snapshot.cooldownRemainingMillis)
                        SessionPhase.DAILY_EXHAUSTED -> "今日总限额已用完"
                        SessionPhase.IDLE -> "空闲"
                    }
                )
            } else if (guardedCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text("打开目标应用后，这里会显示实时使用状态")
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
): List<Triple<String, String, Long>> {
    val sorted = usage.entries.sortedByDescending { it.value }.take(count)
    return sorted.map { Triple(it.key, appLabel(apps, it.key), it.value) }
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
