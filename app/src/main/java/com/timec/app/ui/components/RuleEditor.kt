package com.timec.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.timec.app.data.AppRule

@Composable
fun RuleEditor(
    initial: AppRule,
    onSave: (AppRule) -> Unit,
    onCancel: (() -> Unit)? = null,
    saveLabel: String = "保存"
) {
    var rule by remember(initial) { mutableStateOf(initial) }
    var infoText by remember { mutableStateOf<String?>(null) }
    val onInfo: (String) -> Unit = { infoText = it }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard("限制模式") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = rule.mode == 0,
                        onClick = { rule = rule.copy(mode = 0) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("正常") }
                    SegmentedButton(
                        selected = rule.mode == 1,
                        onClick = { rule = rule.copy(mode = 1) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("时间银行") }
                }
                Text(
                    if (rule.mode == 0) "到点只能延长一分钟，简单直接" else "到点可透支继续，用倍率与冷却管理代价",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard("限额") {
                IntField("单次连续上限", rule.sessionLimitMinutes, "分钟",
                    "一次连续使用达到这个时长就触发限制", onInfo) { rule = rule.copy(sessionLimitMinutes = it) }
                IntField("每日总限额", rule.dailyLimitMinutes, "分钟·0不限",
                    "每天累计使用上限，达到后当天只能退出", onInfo) { rule = rule.copy(dailyLimitMinutes = it) }
            }

            AnimatedVisibility(
                visible = rule.mode == 1,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SectionCard("透支与冷却") {
                    IntField("保底窗口", rule.floorMinutes, "分钟",
                        "透支不会让下次可用时长低于这个底线", onInfo) { rule = rule.copy(floorMinutes = it) }
                    FloatField("透支倍率", rule.overdraftMultiplier, "倍",
                        "可透支时长 =（单次上限 − 保底窗口）÷ 倍率。例如上限 15 分钟、保底 5 分钟、2 倍 → 可透支 5 分钟", onInfo) { rule = rule.copy(overdraftMultiplier = it) }
                    IntField("冷却时长", rule.cooldownMinutes, "分钟",
                        "触发限制后，需离开这么久连续时长才会重置", onInfo) { rule = rule.copy(cooldownMinutes = it) }
                    IntField("冷却透支代价", rule.cooldownPenaltyMinutes, "分钟/分钟",
                        "冷却期每透支使用 1 分钟，下次冷却就延长这么多分钟", onInfo) { rule = rule.copy(cooldownPenaltyMinutes = it) }
                }
            }

            SectionCard("到点操作") {
                IntField("加一分钟时长", rule.extensionSeconds, "秒",
                    "到点后点“加一分钟”能延长多少秒，用于收尾", onInfo) { rule = rule.copy(extensionSeconds = it) }
                IntField("延长次数上限", rule.maxExtensions, "次·0禁止",
                    "到点后最多能点多少次“加一分钟”，用完之后只剩确定退出", onInfo) { rule = rule.copy(maxExtensions = it) }
            }

            SectionCard("提醒") {
                IntField("第一档提醒", rule.warnPct1, "%·0关",
                    "使用到上限的这个百分比时，发一次温和提醒", onInfo) { rule = rule.copy(warnPct1 = it) }
                IntField("第二档提醒", rule.warnPct2, "%·0关",
                    "第二档提醒，通常设得比第一档更接近上限", onInfo) { rule = rule.copy(warnPct2 = it) }
            }

            SectionCard("摩擦页") {
                ToggleRow(
                    "打开应用先停一下",
                    rule.frictionEnabled,
                    "打开目标应用时先停几秒，给自己一个反悔的机会",
                    onInfo
                ) { rule = rule.copy(frictionEnabled = it) }
                AnimatedVisibility(visible = rule.frictionEnabled, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    IntField("停留时长", rule.frictionSeconds, "秒", null, onInfo) { rule = rule.copy(frictionSeconds = it) }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(rule) }, modifier = Modifier.fillMaxWidth()) {
                Text(saveLabel)
            }
            if (onCancel != null) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("取消")
                }
            }
        }
    }

    infoText?.let { text ->
        AlertDialog(
            onDismissRequest = { infoText = null },
            confirmButton = { TextButton(onClick = { infoText = null }) { Text("知道了") } },
            text = { Text(text, style = MaterialTheme.typography.bodyLarge) }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun InfoButton(info: String, onInfo: (String) -> Unit) {
    IconButton(onClick = { onInfo(info) }, modifier = Modifier.size(22.dp)) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "说明",
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, info: String?, onInfo: (String) -> Unit, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (info != null) InfoButton(info, onInfo)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun IntField(
    title: String,
    value: Int,
    unit: String,
    info: String?,
    onInfo: (String) -> Unit,
    onChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (info != null) InfoButton(info, onInfo)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { it.isDigit() }
                text.toIntOrNull()?.let(onChange)
            },
            modifier = Modifier.width(104.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.width(6.dp))
        Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FloatField(
    title: String,
    value: Float,
    unit: String,
    info: String?,
    onInfo: (String) -> Unit,
    onChange: (Float) -> Unit
) {
    var text by remember(value) { mutableStateOf(formatRate(value)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (info != null) InfoButton(info, onInfo)
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                var f = v.filter { it.isDigit() || it == '.' }
                val d = f.indexOf('.')
                if (d >= 0) f = f.substring(0, d + 1) + f.substring(d + 1).filter { it != '.' }
                text = f
                if (!f.endsWith(".")) f.toFloatOrNull()?.let(onChange)
            },
            modifier = Modifier.width(104.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Spacer(Modifier.width(6.dp))
        Text(unit, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatRate(value: Float): String {
    val tenths = Math.round(value * 10f)
    val whole = tenths / 10
    val rem = tenths % 10
    return if (rem == 0) whole.toString() else whole.toString() + "." + rem
}
