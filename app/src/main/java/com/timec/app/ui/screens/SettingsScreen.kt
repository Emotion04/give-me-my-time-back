package com.timec.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.timec.app.data.RecoverMode
import com.timec.app.ui.AppViewModel

@Composable
fun SettingsScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()
    var permissionTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val usageGranted = remember(permissionTick) { viewModel.hasUsageAccess() }
    val overlayGranted = remember(permissionTick) { viewModel.hasOverlayPermission() }
    val batteryIgnored = remember(permissionTick) { viewModel.isIgnoringBatteryOptimizations() }
    val accessibilityGranted = remember(permissionTick) { viewModel.isAccessibilityEnabled() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingToggle(
                title = "启用守护",
                subtitle = "开启后开始低功耗记录目标应用的连续使用时间",
                checked = settings.enabled,
                onCheckedChange = viewModel::setEnabled
            )
        }

        item {
            NumberSetting(
                title = "连续使用额度",
                value = settings.limitMinutes,
                unit = "分钟",
                onChange = viewModel::setLimitMinutes
            )
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("恢复模式", style = MaterialTheme.typography.titleMedium)
                    Text("柔和·回充：离开/锁屏按比例恢复额度；严格·冷却：到点后须冷却再进入。")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        FilterChip(
                            selected = settings.recoverMode == RecoverMode.RECHARGE,
                            onClick = { viewModel.setRecoverMode(RecoverMode.RECHARGE) },
                            label = { Text("柔和·回充") }
                        )
                        FilterChip(
                            selected = settings.recoverMode == RecoverMode.COOLDOWN,
                            onClick = { viewModel.setRecoverMode(RecoverMode.COOLDOWN) },
                            label = { Text("严格·冷却") }
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("阶梯翻倍消耗", style = MaterialTheme.typography.titleMedium)
                    SettingToggle(
                        title = "开启阶梯翻倍",
                        subtitle = "连续使用越过阈值后，消耗速率翻倍，用越久烧越快",
                        checked = settings.tieredEnabled,
                        onCheckedChange = viewModel::setTieredEnabled
                    )
                    if (settings.tieredEnabled) {
                        DecimalSetting(
                            title = "到 100% 后的消耗倍率",
                            value = settings.rate100x,
                            unit = "倍",
                            onChange = viewModel::setRate100x
                        )
                        DecimalSetting(
                            title = "到 150% 后的消耗倍率",
                            value = settings.rate150x,
                            unit = "倍",
                            onChange = viewModel::setRate150x
                        )
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("提前提醒档位", style = MaterialTheme.typography.titleMedium)
                    Text("相对额度的百分比，0 表示关闭该档。")
                    NumberSetting(
                        title = "第一档提醒（默认 80%）",
                        value = settings.warnPct1,
                        unit = "%",
                        onChange = { viewModel.setWarnPct(it, settings.warnPct2) }
                    )
                    NumberSetting(
                        title = "第二档提醒（默认 90%）",
                        value = settings.warnPct2,
                        unit = "%",
                        onChange = { viewModel.setWarnPct(settings.warnPct1, it) }
                    )
                }
            }
        }

        if (settings.recoverMode == RecoverMode.RECHARGE) {
            item {
                NumberSetting(
                    title = "离开多久算结束本次连续使用",
                    value = settings.breakResetSeconds,
                    unit = "秒",
                    onChange = viewModel::setBreakResetSeconds
                )
            }
            item {
                EarnRuleSetting(
                    workSeconds = settings.earnWorkSeconds,
                    rewardSeconds = settings.earnRewardSeconds,
                    onWorkChange = { work -> viewModel.setEarnRule(work, settings.earnRewardSeconds) },
                    onRewardChange = { reward -> viewModel.setEarnRule(settings.earnWorkSeconds, reward) }
                )
            }
        } else {
            item {
                NumberSetting(
                    title = "到点后的冷却时长",
                    value = settings.cooldownSeconds,
                    unit = "秒",
                    onChange = viewModel::setCooldownSeconds
                )
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("正式限制", style = MaterialTheme.typography.titleMedium)
                    NumberSetting(
                        title = "延长时长",
                        value = settings.extensionSeconds,
                        unit = "秒",
                        onChange = viewModel::setExtensionSeconds
                    )
                    NumberSetting(
                        title = "延长次数上限",
                        value = settings.maxExtensions,
                        unit = "次",
                        onChange = viewModel::setMaxExtensions
                    )
                    SettingToggle(
                        title = "硬拦截增强",
                        subtitle = "开启后需启用无障碍服务，目标是真正拦住忍不住的瞬间",
                        checked = settings.hardBlock,
                        onCheckedChange = viewModel::setHardBlock
                    )
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("打开摩擦页", style = MaterialTheme.typography.titleMedium)
                    SettingToggle(
                        title = "打开目标应用时先停一下",
                        subtitle = "One Sec 式：先呼吸几秒再进入，给自己一个反悔的机会",
                        checked = settings.frictionEnabled,
                        onCheckedChange = viewModel::setFrictionEnabled
                    )
                    if (settings.frictionEnabled) {
                        NumberSetting(
                            title = "停留时长",
                            value = settings.frictionSeconds,
                            unit = "秒",
                            onChange = viewModel::setFrictionSeconds
                        )
                    }
                }
            }
        }

        item {
            PermissionCard(
                usageGranted = usageGranted,
                overlayGranted = overlayGranted,
                batteryIgnored = batteryIgnored,
                accessibilityGranted = accessibilityGranted,
                hardBlockEnabled = settings.hardBlock,
                onOpenUsage = viewModel::openUsageAccessSettings,
                onOpenOverlay = viewModel::openOverlaySettings,
                onOpenBattery = viewModel::openBatterySettings,
                onOpenAccessibility = viewModel::openAccessibilitySettings
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
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
private fun NumberSetting(
    title: String,
    value: Int,
    unit: String,
    onChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                text = newValue.filter { it.isDigit() }
                text.toIntOrNull()?.let(onChange)
            },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.width(8.dp))
        Text(unit, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DecimalSetting(
    title: String,
    value: Float,
    unit: String,
    onChange: (Float) -> Unit
) {
    var text by remember(value) { mutableStateOf(formatRate(value)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                var filtered = newValue.filter { it.isDigit() || it == '.' }
                val dotIndex = filtered.indexOf('.')
                if (dotIndex >= 0) {
                    filtered = filtered.substring(0, dotIndex + 1) +
                        filtered.substring(dotIndex + 1).filter { it != '.' }
                }
                text = filtered
                if (!filtered.endsWith(".")) {
                    filtered.toFloatOrNull()?.let(onChange)
                }
            },
            modifier = Modifier.width(96.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.width(8.dp))
        Text(unit, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatRate(value: Float): String {
    val tenths = Math.round(value * 10f)
    val whole = tenths / 10
    val rem = tenths % 10
    return if (rem == 0) whole.toString() else whole.toString() + "." + rem
}

@Composable
private fun EarnRuleSetting(
    workSeconds: Int,
    rewardSeconds: Int,
    onWorkChange: (Int) -> Unit,
    onRewardChange: (Int) -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("离开挣时长", style = MaterialTheme.typography.titleMedium)
            Text("离开目标应用达到设定秒数，就恢复对应的可用秒数。")
            NumberSetting(
                title = "离开时长",
                value = workSeconds,
                unit = "秒",
                onChange = onWorkChange
            )
            NumberSetting(
                title = "恢复时长",
                value = rewardSeconds,
                unit = "秒",
                onChange = onRewardChange
            )
        }
    }
}

@Composable
private fun PermissionCard(
    usageGranted: Boolean,
    overlayGranted: Boolean,
    batteryIgnored: Boolean,
    accessibilityGranted: Boolean,
    hardBlockEnabled: Boolean,
    onOpenUsage: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("权限与后台", style = MaterialTheme.typography.titleMedium)
            PermissionRow("使用情况访问", usageGranted, "用于统计和识别前台应用", onOpenUsage)
            PermissionRow("悬浮窗", overlayGranted, "正式限制、冷却和摩擦页需要", onOpenOverlay)
            PermissionRow("忽略电池优化", batteryIgnored, "让前台服务更稳定", onOpenBattery)
            if (hardBlockEnabled) {
                PermissionRow("无障碍服务", accessibilityGranted, "硬拦截增强需要，请到系统设置开启", onOpenAccessibility)
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    description: String,
    onOpen: () -> Unit
) {
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
