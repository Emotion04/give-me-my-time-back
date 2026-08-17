package com.timec.app.ui.screens

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.timec.app.monitor.ColorSamplerService
import com.timec.app.monitor.ScreenColorSampler
import com.timec.app.monitor.TimerMetrics
import com.timec.app.ui.AppViewModel
import com.timec.app.ui.theme.themeNames

@Composable
fun SettingsScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()
    var permissionTick by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            viewModel.setWidgetBackground(3)
            ColorSamplerService.start(context, result.resultCode, data)
        }
    }
    val launchProjection: () -> Unit = {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        mgr?.let { projectionLauncher.launch(it.createScreenCaptureIntent()) }
    }
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
                    AppSlider(
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

        item { SectionHeader("悬浮计时窗") }
        item {
            SettingToggle(
                title = "启用悬浮计时窗",
                subtitle = "亮屏时显示可拖动的小计时窗；仅在守护清单应用下显示，或开启“所有应用都显示”（没有守护应用时可在主页手动启动服务）",
                checked = settings.widgetEnabled,
                onCheckedChange = viewModel::setWidgetEnabled
            )
        }
        item {
            SettingToggle(
                title = "所有应用都显示",
                subtitle = "开启后不再限定守护清单，任何应用前台时都显示",
                checked = settings.widgetAllApps,
                onCheckedChange = viewModel::setWidgetAllApps
            )
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("显示方式", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf("循环切换", "固定单指标").forEachIndexed { index, label ->
                            FilterChip(
                                selected = settings.widgetMode == index,
                                onClick = { viewModel.setWidgetMode(index) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (settings.widgetMode == 0) "显示指标（点击悬浮窗循环切换）" else "固定显示的指标（点击悬浮窗不切换）",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TimerMetrics.all.forEach { (id, label) ->
                        val isSingleMode = settings.widgetMode == 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSingleMode) viewModel.setWidgetSingleMetric(id)
                                    else viewModel.toggleWidgetMetric(id, id !in settings.widgetMetrics)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            val selected = if (isSingleMode) settings.widgetSingleMetric == id else id in settings.widgetMetrics
                            if (selected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("不透明度：" + settings.widgetOpacity + "%", style = MaterialTheme.typography.titleMedium)
                    AppSlider(
                        value = settings.widgetOpacity.toFloat(),
                        onValueChange = { v -> viewModel.setWidgetOpacity(Math.round(v)) },
                        valueRange = 2f..100f
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("背景", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf("黑底", "白底", "全透明", "自动对比").forEachIndexed { index, label ->
                            FilterChip(
                                selected = settings.widgetBackground == index,
                                onClick = {
                                    if (index == 3 && !ScreenColorSampler.isActive) {
                                        launchProjection()
                                    } else {
                                        viewModel.setWidgetBackground(index)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (settings.widgetBackground == 3 && !ScreenColorSampler.isActive) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "“自动对比”需屏幕捕获授权：实时检测悬浮窗底层颜色，自动把文字调成黑色或白色保证可读。授权后每 30 秒采样一次（拖动时立即重采），会带来少量额外耗电。建议配合“全透明”背景使用。",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = { launchProjection() }) {
                            Text("授权屏幕检测")
                        }
                    }
                    if (settings.widgetBackground == 2 || settings.widgetBackground == 3) {
                        Spacer(Modifier.size(8.dp))
                        Text("文字颜色", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            listOf("白字", "黑字").forEachIndexed { index, label ->
                                FilterChip(
                                    selected = settings.widgetTextColor == index,
                                    onClick = { viewModel.setWidgetTextColor(index) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    Text("字号：" + settings.widgetFontSize + " sp（无极调节）", style = MaterialTheme.typography.titleMedium)
                    AppSlider(
                        value = settings.widgetFontSize.toFloat(),
                        onValueChange = { v -> viewModel.setWidgetFontSize(Math.round(v)) },
                        valueRange = 10f..28f
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("边距：" + settings.widgetMargin + " dp（文字到背景边沿，四边等宽）", style = MaterialTheme.typography.titleMedium)
                    AppSlider(
                        value = settings.widgetMargin.toFloat(),
                        onValueChange = { v -> viewModel.setWidgetMargin(Math.round(v)) },
                        valueRange = 0f..24f
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("“本次亮屏连续”重置阈值（息屏超过该秒数即重新计时）", style = MaterialTheme.typography.titleMedium)
                    AppSlider(
                        value = settings.screenOffResetThresholdSeconds.toFloat(),
                        onValueChange = { v -> viewModel.setScreenOffResetThresholdSeconds(Math.round(v)) },
                        valueRange = 0f..3600f
                    )
                    Text(settings.screenOffResetThresholdSeconds.toString() + " 秒", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.size(8.dp))
                    Text("对比基准（“对比上周期”指标）", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf("昨日", "上周", "上月").forEachIndexed { index, label ->
                            FilterChip(
                                selected = settings.widgetComparePeriod == index,
                                onClick = { viewModel.setWidgetComparePeriod(index) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = { viewModel.toggleTimerWidgetPreview() }, modifier = Modifier.fillMaxWidth()) {
                Text("预览/隐藏悬浮计时窗")
            }
        }

        item { SectionHeader("个性化") }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("正式限制提示语", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = settings.finalMessage,
                        onValueChange = { viewModel.setFinalMessage(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("到点后显示的提示语") }
                    )
                    Spacer(Modifier.size(12.dp))
                    Text("悬浮窗背景", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        listOf(
                            Color(0xFF1B2A41), Color(0xFF122D28), Color(0xFF261A3A), Color(0xFF141416)
                        ).forEachIndexed { index, color ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { viewModel.setOverlayBackground(index) }
                                    .then(
                                        if (settings.overlayBackground == index)
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        else Modifier
                                    )
                            )
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    )
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
