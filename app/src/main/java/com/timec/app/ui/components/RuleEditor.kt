package com.timec.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("限额", style = MaterialTheme.typography.titleMedium)
                    IntField("单次连续上限", rule.sessionLimitMinutes, "分钟") { rule = rule.copy(sessionLimitMinutes = it) }
                    IntField("每日总限额(0=不限)", rule.dailyLimitMinutes, "分钟") { rule = rule.copy(dailyLimitMinutes = it) }
                }
            }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("透支与冷却", style = MaterialTheme.typography.titleMedium)
                    IntField("保底窗口", rule.floorMinutes, "分钟") { rule = rule.copy(floorMinutes = it) }
                    FloatField("透支倍率", rule.overdraftMultiplier, "倍") { rule = rule.copy(overdraftMultiplier = it) }
                    IntField("冷却时长", rule.cooldownMinutes, "分钟") { rule = rule.copy(cooldownMinutes = it) }
                    IntField("冷却透支代价", rule.cooldownPenaltyMinutes, "分钟/分钟") { rule = rule.copy(cooldownPenaltyMinutes = it) }
                    IntField("加一分钟时长", rule.extensionSeconds, "秒") { rule = rule.copy(extensionSeconds = it) }
                }
            }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("提醒档位", style = MaterialTheme.typography.titleMedium)
                    IntField("第一档提醒(0=关)", rule.warnPct1, "%") { rule = rule.copy(warnPct1 = it) }
                    IntField("第二档提醒(0=关)", rule.warnPct2, "%") { rule = rule.copy(warnPct2 = it) }
                }
            }
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("摩擦页", style = MaterialTheme.typography.titleMedium)
                    ToggleRow("打开目标应用时先停一下", rule.frictionEnabled) { rule = rule.copy(frictionEnabled = it) }
                    if (rule.frictionEnabled) {
                        IntField("停留时长", rule.frictionSeconds, "秒") { rule = rule.copy(frictionSeconds = it) }
                    }
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
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun IntField(title: String, value: Int, unit: String, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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
        Text(unit, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FloatField(title: String, value: Float, unit: String, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(formatRate(value)) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
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
        Text(unit, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatRate(value: Float): String {
    val tenths = Math.round(value * 10f)
    val whole = tenths / 10
    val rem = tenths % 10
    return if (rem == 0) whole.toString() else whole.toString() + "." + rem
}
