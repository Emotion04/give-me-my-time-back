package com.timec.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timec.app.data.AppInfo
import com.timec.app.data.AppRule
import com.timec.app.ui.AppViewModel
import com.timec.app.ui.components.AppIcon
import com.timec.app.ui.components.RuleEditor

private sealed interface AppsNav {
    object List : AppsNav
    object Picker : AppsNav
    data class Edit(val pkg: String) : AppsNav
    object EditDefault : AppsNav
    object Templates : AppsNav
    data class EditTemplate(val name: String?) : AppsNav
    data class ApplyTemplate(val name: String) : AppsNav
}

@Composable
fun TargetAppsScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    var nav by remember { mutableStateOf<AppsNav>(AppsNav.List) }

    // 系统返回键：子页面返回上级，只有主列表页交给系统（退出到桌面）
    BackHandler(enabled = nav !is AppsNav.List) {
        nav = when (val n = nav) {
            is AppsNav.EditTemplate -> AppsNav.Templates
            is AppsNav.ApplyTemplate -> AppsNav.Templates
            else -> AppsNav.List
        }
    }

    when (val n = nav) {
        is AppsNav.List -> GuardedList(viewModel, modifier) { nav = it }
        is AppsNav.Picker -> AppPicker(viewModel, modifier, onBack = { nav = AppsNav.List })
        is AppsNav.Edit -> {
            val settings by viewModel.settings.collectAsState()
            RuleScreen(
                title = viewModel.appLabel(n.pkg),
                initial = settings.appRules[n.pkg] ?: settings.defaultRule,
                onSave = { viewModel.updateAppRule(n.pkg, it); nav = AppsNav.List },
                onBack = { nav = AppsNav.List },
                modifier = modifier
            )
        }
        is AppsNav.EditDefault -> {
            val settings by viewModel.settings.collectAsState()
            RuleScreen(
                title = "默认规则",
                initial = settings.defaultRule,
                onSave = { viewModel.setDefaultRule(it); nav = AppsNav.List },
                onBack = { nav = AppsNav.List },
                modifier = modifier
            )
        }
        is AppsNav.Templates -> TemplateList(viewModel, modifier) { nav = it }
        is AppsNav.EditTemplate -> {
            val settings by viewModel.settings.collectAsState()
            TemplateEditor(
                name = n.name,
                initial = n.name?.let { settings.templates[it] } ?: AppRule(),
                onSave = { name, rule -> viewModel.saveTemplate(name, rule); nav = AppsNav.Templates },
                onBack = { nav = AppsNav.Templates },
                modifier = modifier
            )
        }
        is AppsNav.ApplyTemplate -> ApplyTemplate(viewModel, n.name, modifier) { nav = AppsNav.Templates }
    }
}

@Composable
private fun RuleScreen(title: String, initial: AppRule, onSave: (AppRule) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        TopBar(title, onBack)
        RuleEditor(initial = initial, onSave = onSave)
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GuardedList(viewModel: AppViewModel, modifier: Modifier, nav: (AppsNav) -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val templateNames = settings.appTemplateNames
    val entries = settings.appRules.entries.sortedBy { viewModel.appLabel(it.key) }
    val groups = entries.groupBy { templateNames[it.key] ?: "默认" }
    val orderedGroups = groups.toList().sortedBy { (name, _) -> if (name == "默认") "0" else name }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { nav(AppsNav.Picker) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("添加应用")
                }
                Button(onClick = { nav(AppsNav.Templates) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Layers, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("模板")
                }
            }
        }

        item {
            Text("已守护 " + entries.size + " 个应用", style = MaterialTheme.typography.titleMedium)
            if (entries.isEmpty()) {
                Text("点“添加应用”开始守护", style = MaterialTheme.typography.bodyMedium)
            }
        }

        orderedGroups.forEach { (templateName, groupEntries) ->
            item(key = "h_" + templateName) {
                Text(
                    if (templateName == "默认") "默认规则" else "模板：" + templateName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(groupEntries, key = { it.key }) { (pkg, rule) ->
                Card(modifier = Modifier.fillMaxWidth().clickable { nav(AppsNav.Edit(pkg)) }) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(appInfo(apps, pkg)?.icon, Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(viewModel.appLabel(pkg), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(ruleSummary(rule), style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { viewModel.removeApp(pkg) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "移除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth().clickable { nav(AppsNav.EditDefault) }) {
                Column(Modifier.padding(12.dp)) {
                    Text("默认规则（新应用）", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(ruleSummary(settings.defaultRule), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AppPicker(viewModel: AppViewModel, modifier: Modifier, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val apps by viewModel.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var ruleKey by remember { mutableStateOf("默认") }

    val ruleOptions = listOf("默认") + settings.templates.keys.toList()
    val candidates = apps.filter { it.packageName !in settings.appRules }
        .filter { it.label.contains(query, ignoreCase = true) }

    Column(modifier.fillMaxSize()) {
        TopBar("添加应用", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索应用") },
            singleLine = true
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("应用规则：", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterVertically))
            ruleOptions.forEach { name ->
                FilterChip(selected = ruleKey == name, onClick = { ruleKey = name }, label = { Text(name) })
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(candidates, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.icon, Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Checkbox(checked = app.packageName in selected, onCheckedChange = null)
                }
            }
        }
        Button(
            onClick = {
                val rule = if (ruleKey == "默认") settings.defaultRule else settings.templates[ruleKey] ?: settings.defaultRule
                val templateName = if (ruleKey == "默认") null else ruleKey
                viewModel.addAppsWithRule(selected, rule, templateName)
                onBack()
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text("添加选中（" + selected.size + "）") }
    }
}

@Composable
private fun TemplateList(viewModel: AppViewModel, modifier: Modifier, nav: (AppsNav) -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val apps by viewModel.apps.collectAsState()
    val entries = settings.templates.entries.toList()
    val templateNames = settings.appTemplateNames

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TopBar("模板", onBack = { nav(AppsNav.List) })
        }
        item {
            Button(onClick = { nav(AppsNav.EditTemplate(null)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("新建模板")
            }
        }
        item {
            Text("已建 " + entries.size + " 个模板", style = MaterialTheme.typography.titleMedium)
            if (entries.isEmpty()) Text("模板可用于批量给应用套用规则", style = MaterialTheme.typography.bodyMedium)
        }
        items(entries, key = { it.key }) { (name, rule) ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { nav(AppsNav.EditTemplate(name)) }) { Text("编辑") }
                        TextButton(onClick = { viewModel.deleteTemplate(name) }) { Text("删除") }
                    }
                    Text(ruleSummary(rule), style = MaterialTheme.typography.bodyMedium)
                    val using = templateNames.filterValues { it == name }.keys.toList()
                    if (using.isNotEmpty()) {
                        Text("已应用到 " + using.size + " 个应用：", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            using.take(8).forEach { pkg ->
                                AppIcon(appInfo(apps, pkg)?.icon, Modifier.size(28.dp))
                            }
                        }
                    } else {
                        Text("还没有应用使用此模板", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(onClick = { nav(AppsNav.ApplyTemplate(name)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("应用到应用")
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateEditor(name: String?, initial: AppRule, onSave: (String, AppRule) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var templateName by remember { mutableStateOf(name ?: "") }
    Column(modifier.fillMaxSize()) {
        TopBar(if (name == null) "新建模板" else "编辑模板", onBack)
        OutlinedTextField(
            value = templateName,
            onValueChange = { templateName = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("模板名称") },
            singleLine = true
        )
        RuleEditor(
            initial = initial,
            onSave = { rule -> if (templateName.isNotBlank()) onSave(templateName.trim(), rule) },
            saveLabel = "保存模板"
        )
    }
}

@Composable
private fun ApplyTemplate(viewModel: AppViewModel, name: String, modifier: Modifier, onBack: () -> Unit) {
    val apps by viewModel.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val candidates = apps.filter { it.label.contains(query, ignoreCase = true) }

    Column(modifier.fillMaxSize()) {
        TopBar("应用模板：" + name, onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索应用") },
            singleLine = true
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(candidates, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(app.icon, Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Checkbox(checked = app.packageName in selected, onCheckedChange = null)
                }
            }
        }
        Button(
            onClick = { viewModel.applyTemplate(name, selected); onBack() },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text("应用到选中（" + selected.size + "）") }
    }
}

private fun ruleSummary(rule: AppRule): String {
    val m = formatMultiplier(rule.overdraftMultiplier)
    val modeText = if (rule.mode == 0) "正常" else "时间银行"
    return modeText + " · 单次" + rule.sessionLimitMinutes + "分 · 每日" + rule.dailyLimitMinutes + "分 · 倍率" + m + "x"
}

private fun formatMultiplier(value: Float): String {
    val tenths = Math.round(value * 10f)
    val whole = tenths / 10
    val rem = tenths % 10
    return if (rem == 0) whole.toString() else whole.toString() + "." + rem
}

private fun appInfo(apps: List<AppInfo>, packageName: String): AppInfo? {
    return apps.firstOrNull { it.packageName == packageName }
}
