package com.timec.app.ui

import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.collectAsState
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
import com.timec.app.ui.theme.TimeCTheme

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
    val settings by viewModel.settings.collectAsState()
    var selectedRoute by rememberSaveable { mutableStateOf("overview") }
    TimeCTheme(themeIndex = settings.themeIndex) {
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
            Crossfade(targetState = selectedRoute, label = "tabSwitch") { route ->
                when (route) {
                    "overview" -> OverviewScreen(viewModel, modifier)
                    "apps" -> TargetAppsScreen(viewModel, modifier)
                    "settings" -> SettingsScreen(viewModel, modifier)
                }
            }
        }
    }
}
