package com.timec.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.timec.app.ui.screens.OverviewScreen
import com.timec.app.ui.screens.SettingsScreen
import com.timec.app.ui.screens.TargetAppsScreen

private data class TabItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val tabs = listOf(
    TabItem("概览", Icons.Outlined.BarChart, "overview"),
    TabItem("应用", Icons.Outlined.Apps, "apps"),
    TabItem("设置", Icons.Outlined.Settings, "settings")
)

@Composable
fun App(viewModel: AppViewModel = viewModel()) {
    var selectedRoute by rememberSaveable { mutableStateOf("overview") }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedRoute == tab.route,
                        onClick = { selectedRoute = tab.route },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedRoute) {
            "overview" -> OverviewScreen(viewModel, modifier)
            "apps" -> TargetAppsScreen(viewModel, modifier)
            "settings" -> SettingsScreen(viewModel, modifier)
        }
    }
}
