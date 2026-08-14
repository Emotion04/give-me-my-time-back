package com.timec.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timec.app.ui.AppViewModel
import com.timec.app.ui.components.AppIcon

@Composable
fun TargetAppsScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()
    val apps by viewModel.apps.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("搜索应用") },
            singleLine = true
        )

        val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    "已选择 ${settings.selectedPackages.size} 个目标应用",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = app.packageName in settings.selectedPackages,
                        onCheckedChange = { viewModel.togglePackage(app.packageName) }
                    )
                    AppIcon(app.icon, Modifier.size(30.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        app.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
