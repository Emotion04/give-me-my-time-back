package com.timec.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timec.app.data.AppInfo
import com.timec.app.data.AppRule
import com.timec.app.data.AppSettings
import com.timec.app.data.DayUsage
import com.timec.app.data.SettingsRepository
import com.timec.app.data.UsageRepository
import com.timec.app.monitor.MonitorEngine
import com.timec.app.monitor.MonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val usageRepository = UsageRepository(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val monitorState = MonitorEngine.state

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _rangeDays = MutableStateFlow(1)
    val rangeDays: StateFlow<Int> = _rangeDays.asStateFlow()

    private val _rangeUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    val rangeUsage: StateFlow<Map<String, Long>> = _rangeUsage.asStateFlow()

    private val _rangeTotals = MutableStateFlow<List<DayUsage>>(emptyList())
    val rangeTotals: StateFlow<List<DayUsage>> = _rangeTotals.asStateFlow()

    private val _detailPackage = MutableStateFlow<String?>(null)
    val detailPackage: StateFlow<String?> = _detailPackage.asStateFlow()

    private val _hourlyDetail = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val hourlyDetail: StateFlow<Map<Int, Long>> = _hourlyDetail.asStateFlow()

    private val _usageGranted = MutableStateFlow(false)
    val usageGranted: StateFlow<Boolean> = _usageGranted.asStateFlow()

    init {
        refreshApps()
        setRangeDays(1)
        refreshPermissionState()
        viewModelScope.launch {
            settings.collect { applyServiceState(it) }
        }
    }

    fun refreshApps() {
        viewModelScope.launch { _apps.value = usageRepository.getInstalledApps() }
    }

    fun setRangeDays(days: Int) {
        _rangeDays.value = days
        refreshRange()
    }

    fun refreshRange() {
        viewModelScope.launch {
            val days = _rangeDays.value
            _rangeUsage.value = usageRepository.getRangePerApp(days)
            _rangeTotals.value = usageRepository.getRangeDailyTotals(days)
        }
    }

    fun openDetail(packageName: String) {
        _detailPackage.value = packageName
        viewModelScope.launch {
            _hourlyDetail.value = usageRepository.getTodayHourly(packageName)
        }
    }

    fun closeDetail() {
        _detailPackage.value = null
        _hourlyDetail.value = emptyMap()
    }

    fun refreshPermissionState() {
        _usageGranted.value = usageRepository.hasUsageAccess()
    }

    fun refresh() {
        refreshApps()
        refreshRange()
        refreshPermissionState()
    }

    fun appLabel(packageName: String): String {
        return apps.value.firstOrNull { it.packageName == packageName }?.label ?: packageName
    }

    // ---- 规则 / 应用 / 模板 ----
    fun setEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setEnabled(value) }
    }

    fun setThemeIndex(value: Int) {
        viewModelScope.launch { settingsRepository.setThemeIndex(value) }
    }

    fun setOverdraftDelaySeconds(value: Int) {
        viewModelScope.launch { settingsRepository.setOverdraftDelaySeconds(value) }
    }

    fun setDefaultRule(rule: AppRule) {
        viewModelScope.launch { settingsRepository.setDefaultRule(rule) }
    }

    fun addApp(packageName: String) {
        viewModelScope.launch { settingsRepository.addApp(packageName, settings.value.defaultRule) }
    }

    fun addApps(packageNames: Set<String>) {
        viewModelScope.launch { settingsRepository.addApps(packageNames, settings.value.defaultRule) }
    }

    fun addAppsWithRule(packageNames: Set<String>, rule: AppRule, templateName: String? = null) {
        viewModelScope.launch { settingsRepository.addApps(packageNames, rule, templateName) }
    }

    fun removeApp(packageName: String) {
        viewModelScope.launch { settingsRepository.removeApp(packageName) }
    }

    fun updateAppRule(packageName: String, rule: AppRule) {
        viewModelScope.launch { settingsRepository.updateAppRule(packageName, rule) }
    }

    fun saveTemplate(name: String, rule: AppRule) {
        viewModelScope.launch { settingsRepository.setTemplate(name, rule) }
    }

    fun deleteTemplate(name: String) {
        viewModelScope.launch { settingsRepository.deleteTemplate(name) }
    }

    fun applyTemplate(name: String, packageNames: Set<String>) {
        viewModelScope.launch { settingsRepository.applyTemplate(name, packageNames) }
    }

    // ---- 权限 ----
    fun hasUsageAccess(): Boolean = usageRepository.hasUsageAccess()
    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(getApplication())

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    fun openUsageAccessSettings() {
        getApplication<Application>().startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getApplication<Application>().packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openOverlaySettings() {
        getApplication<Application>().startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:" + getApplication<Application>().packageName)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openBatterySettings() {
        getApplication<Application>().startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(android.net.Uri.parse("package:" + getApplication<Application>().packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun showTestOverlay() {
        MonitorService.showTestOverlay(getApplication())
    }

    fun showFinalOverlayTest() {
        MonitorService.showFinalOverlayTest(getApplication())
    }

    fun restartService() {
        MonitorService.restart(getApplication())
    }

    private fun applyServiceState(settings: AppSettings) {
        if (settings.enabled && settings.appRules.isNotEmpty()) {
            MonitorService.start(getApplication())
        } else {
            MonitorService.stop(getApplication())
        }
    }
}
