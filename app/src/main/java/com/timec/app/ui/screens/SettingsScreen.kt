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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.timec.app.ui.AppViewModel
import com.timec.app.ui.theme.themeNames

@Composable
fun SettingsScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()
    var permissionTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val usageGranted = remember(permissionTick) { viewModel.hasUsageAccess() }
    val overlayGranted = remember(permissionTick) { viewModel.hasOverlayPermission() }
    val batteryIgnored = remember(permissionTick) { viewModel.isIgnoringBatteryOptimizations() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionHeader("基本") }
        item {
            SettingToggle(
                title = "启用守护",
                subtitle = "开启后开始低功耗记录目标应用的连续使用时间",
                checked = settings.enabled,
                onCheckedChange = viewModel::setEnabled
            )
        }

        item { SectionHeader("透支等待") }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("透支等待（秒）", style = MaterialTheme.typography.titleMedium)
                    Text("点“继续/透支”前需等待的秒数，0 = 立即可点", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.overdraftDelaySeconds.toFloat(),
                        onValueChange = { v -> viewModel.setOverdraftDelaySeconds(Math.round(v)) },
                        valueRange = 0f..60f
                    )
                    Text(settings.overdraftDelaySeconds.toString() + " 秒", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item { SectionHeader("外观") }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("莫奈配色主题", style = MaterialTheme.typography.titleMedium)
                    ThemeSelector(settings.themeIndex, viewModel::setThemeIndex)
                }
            }
        }

        item { SectionHeader("权限与后台") }
        item {
            PermissionCard(
                usageGranted = usageGranted,
                overlayGranted = overlayGranted,
                batteryIgnored = batteryIgnored,
                onOpenUsage = viewModel::openUsageAccessSettings,
                onOpenOverlay = viewModel::openOverlaySettings,
                onOpenBattery = viewModel::openBatterySettings
            )
        }
        item {
            Button(onClick = { viewModel.showTestOverlay() }, modifier = Modifier.fillMaxWidth()) {
                Text("测试悬浮窗（验证权限）")
            }
        }
        item {
            Button(onClick = { viewModel.showFinalOverlayTest() }, modifier = Modifier.fillMaxWidth()) {
                Text("测试正式限制弹窗（三选一）")
            }
        }
        item {
            Button(onClick = { viewModel.restartService() }, modifier = Modifier.fillMaxWidth()) {
                Text("重启守护服务")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ThemeSelector(current: Int, onSelect: (Int) -> Unit) {
    val colors = listOf(
        Color(0xFF1B2A41), Color(0xFF00696D), Color(0xFF6750A4), Color(0xFF386A20), Color(0xFF9A4523)
    )
    Column {
        themeNames.forEachIndexed { index, name ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(index) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(22.dp).background(colors[index % colors.size], CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (index == current) {
                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionCard(
    usageGranted: Boolean,
    overlayGranted: Boolean,
    batteryIgnored: Boolean,
    onOpenUsage: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenBattery: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PermissionRow("使用情况访问", usageGranted, "统计和识别前台应用", onOpenUsage)
            PermissionRow("悬浮窗", overlayGranted, "正式限制、冷却和摩擦页需要", onOpenOverlay)
            PermissionRow("忽略电池优化", batteryIgnored, "让前台服务更稳定（可选）", onOpenBattery)
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, description: String, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onOpen) {
            Text(if (granted) "已授权" else "去授权")
        }
    }
}
