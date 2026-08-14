package com.timec.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timec.app.data.AppInfo
import com.timec.app.data.AppSettings
import com.timec.app.data.DayUsage
import com.timec.app.data.SettingsRepository
import com.timec.app.data.UsageRepository
import com.timec.app.monitor.MonitorEngine
import com.timec.app.monitor.MonitorService
import com.timec.app.monitor.GuardAccessibilityService
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

    // ---- settings ----
    fun setEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setEnabled(value) }
    }

    fun setLimitMinutes(value: Int) {
        viewModelScope.launch { settingsRepository.setLimitMinutes(value) }
    }

    fun setTieredEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setTieredEnabled(value) }
    }

    fun setRate100x(value: Float) {
        viewModelScope.launch { settingsRepository.setRate100x(value) }
    }

    fun setRate150x(value: Float) {
        viewModelScope.launch { settingsRepository.setRate150x(value) }
    }

    fun setWarnPct(warnPct1: Int, warnPct2: Int) {
        viewModelScope.launch { settingsRepository.setWarnPct(warnPct1, warnPct2) }
    }

    fun setRecoverMode(value: Int) {
        viewModelScope.launch { settingsRepository.setRecoverMode(value) }
    }

    fun setBreakResetSeconds(value: Int) {
        viewModelScope.launch { settingsRepository.setBreakResetSeconds(value) }
    }

    fun setEarnRule(workSeconds: Int, rewardSeconds: Int) {
        viewModelScope.launch { settingsRepository.setEarnRule(workSeconds, rewardSeconds) }
    }

    fun setCooldownSeconds(value: Int) {
        viewModelScope.launch { settingsRepository.setCooldownSeconds(value) }
    }

    fun setExtensionSeconds(value: Int) {
        viewModelScope.launch { settingsRepository.setExtensionSeconds(value) }
    }

    fun setMaxExtensions(value: Int) {
        viewModelScope.launch { settingsRepository.setMaxExtensions(value) }
    }

    fun setHardBlock(value: Boolean) {
        viewModelScope.launch { settingsRepository.setHardBlock(value) }
    }

    fun setFrictionEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setFrictionEnabled(value) }
    }

    fun setFrictionSeconds(value: Int) {
        viewModelScope.launch { settingsRepository.setFrictionSeconds(value) }
    }

    fun togglePackage(packageName: String) {
        val current = settings.value.selectedPackages
        val next = if (packageName in current) current - packageName else current + packageName
        viewModelScope.launch { settingsRepository.setSelectedPackages(next) }
    }

    fun hasUsageAccess(): Boolean = usageRepository.hasUsageAccess()

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(getApplication())

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    fun isAccessibilityEnabled(): Boolean {
        val context = getApplication<Application>()
        val expected = ComponentName(context, GuardAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { component ->
            component.equals(expected, ignoreCase = true) ||
                component.substringBefore('/').endsWith(GuardAccessibilityService::class.java.simpleName, ignoreCase = true)
        }
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

    fun openAccessibilitySettings() {
        getApplication<Application>().startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun applyServiceState(settings: AppSettings) {
        if (settings.enabled && settings.selectedPackages.isNotEmpty()) {
            MonitorService.start(getApplication())
        } else {
            MonitorService.stop(getApplication())
        }
    }
}
