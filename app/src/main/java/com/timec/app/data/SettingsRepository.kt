package com.timec.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "timec_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val themeIndex = intPreferencesKey("theme_index")
        val overdraftDelaySeconds = intPreferencesKey("overdraft_delay_seconds")
        val finalMessage = stringPreferencesKey("final_message")
        val overlayBackground = intPreferencesKey("overlay_background")
        val widgetEnabled = booleanPreferencesKey("widget_enabled")
        val widgetAllApps = booleanPreferencesKey("widget_all_apps")
        val widgetMetrics = stringSetPreferencesKey("widget_metrics")
        val widgetOpacity = intPreferencesKey("widget_opacity")
        val widgetFontSize = intPreferencesKey("widget_font_size")
        val screenOffResetThresholdSeconds = intPreferencesKey("screen_off_reset_threshold_seconds")
        val widgetComparePeriod = intPreferencesKey("widget_compare_period")
        val widgetMode = intPreferencesKey("widget_mode")
        val widgetSingleMetric = stringPreferencesKey("widget_single_metric")
        val widgetPosX = intPreferencesKey("widget_pos_x")
        val widgetPosY = intPreferencesKey("widget_pos_y")
        val defaultRule = stringPreferencesKey("default_rule")
        val appRules = stringPreferencesKey("app_rules")
        val templates = stringPreferencesKey("templates")
        val appTemplateNames = stringPreferencesKey("app_template_names")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            enabled = p[Keys.enabled] ?: true,
            themeIndex = p[Keys.themeIndex] ?: 0,
            overdraftDelaySeconds = p[Keys.overdraftDelaySeconds] ?: 0,
            finalMessage = p[Keys.finalMessage] ?: "你不是在被惩罚，而是在拿回选择权。",
            overlayBackground = p[Keys.overlayBackground] ?: 0,
            widgetEnabled = p[Keys.widgetEnabled] ?: false,
            widgetAllApps = p[Keys.widgetAllApps] ?: false,
            widgetMetrics = p[Keys.widgetMetrics] ?: setOf("app_session"),
            widgetOpacity = p[Keys.widgetOpacity] ?: 80,
            widgetFontSize = p[Keys.widgetFontSize] ?: 1,
            screenOffResetThresholdSeconds = p[Keys.screenOffResetThresholdSeconds] ?: 30,
            widgetComparePeriod = p[Keys.widgetComparePeriod] ?: 0,
            widgetMode = p[Keys.widgetMode] ?: 0,
            widgetSingleMetric = p[Keys.widgetSingleMetric] ?: "app_session",
            widgetPosX = p[Keys.widgetPosX] ?: -1,
            widgetPosY = p[Keys.widgetPosY] ?: -1,
            defaultRule = p[Keys.defaultRule]?.let(::appRuleFromJson) ?: AppRule(),
            appRules = p[Keys.appRules]?.let(::ruleMapFromJson) ?: emptyMap(),
            templates = p[Keys.templates]?.let(::ruleMapFromJson) ?: emptyMap(),
            appTemplateNames = p[Keys.appTemplateNames]?.let(::stringMapFromJson) ?: emptyMap()
        )
    }

    suspend fun setEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.enabled] = value }
    }

    suspend fun setThemeIndex(value: Int) {
        context.settingsDataStore.edit { it[Keys.themeIndex] = value.coerceIn(0, 100) }
    }

    suspend fun setOverdraftDelaySeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.overdraftDelaySeconds] = value.coerceIn(0, 300) }
    }

    suspend fun setFinalMessage(value: String) {
        context.settingsDataStore.edit { it[Keys.finalMessage] = value }
    }

    suspend fun setOverlayBackground(value: Int) {
        context.settingsDataStore.edit { it[Keys.overlayBackground] = value.coerceIn(0, 10) }
    }

    suspend fun setWidgetEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.widgetEnabled] = value }
    }

    suspend fun setWidgetAllApps(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.widgetAllApps] = value }
    }

    suspend fun setWidgetMetrics(value: Set<String>) {
        context.settingsDataStore.edit { it[Keys.widgetMetrics] = value }
    }

    suspend fun setWidgetOpacity(value: Int) {
        context.settingsDataStore.edit { it[Keys.widgetOpacity] = value.coerceIn(20, 100) }
    }

    suspend fun setWidgetFontSize(value: Int) {
        context.settingsDataStore.edit { it[Keys.widgetFontSize] = value.coerceIn(0, 2) }
    }

    suspend fun setScreenOffResetThresholdSeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.screenOffResetThresholdSeconds] = value.coerceIn(0, 3600) }
    }

    suspend fun setWidgetComparePeriod(value: Int) {
        context.settingsDataStore.edit { it[Keys.widgetComparePeriod] = value.coerceIn(0, 2) }
    }

    suspend fun setWidgetMode(value: Int) {
        context.settingsDataStore.edit { it[Keys.widgetMode] = value.coerceIn(0, 1) }
    }

    suspend fun setWidgetSingleMetric(value: String) {
        context.settingsDataStore.edit { it[Keys.widgetSingleMetric] = value }
    }

    suspend fun setWidgetPosition(x: Int, y: Int) {
        context.settingsDataStore.edit {
            it[Keys.widgetPosX] = x
            it[Keys.widgetPosY] = y
        }
    }

    suspend fun setDefaultRule(rule: AppRule) {
        context.settingsDataStore.edit { it[Keys.defaultRule] = rule.toJson() }
    }

    suspend fun addApp(packageName: String, rule: AppRule) {
        addApps(setOf(packageName), rule, null)
    }

    suspend fun addApps(packageNames: Set<String>, rule: AppRule, templateName: String? = null) {
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.appRules]?.let(::ruleMapFromJson) ?: emptyMap()
            val next = cur.toMutableMap()
            packageNames.forEach { next[it] = rule }
            p[Keys.appRules] = ruleMapToJson(next)

            val tcur = p[Keys.appTemplateNames]?.let(::stringMapFromJson) ?: emptyMap()
            val tnext = tcur.toMutableMap()
            packageNames.forEach { pkg ->
                if (templateName != null) tnext[pkg] = templateName else tnext.remove(pkg)
            }
            p[Keys.appTemplateNames] = stringMapToJson(tnext)
        }
    }

    suspend fun updateAppRule(packageName: String, rule: AppRule) {
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.appRules]?.let(::ruleMapFromJson) ?: emptyMap()
            p[Keys.appRules] = ruleMapToJson(cur + (packageName to rule))
            val tcur = p[Keys.appTemplateNames]?.let(::stringMapFromJson) ?: emptyMap()
            p[Keys.appTemplateNames] = stringMapToJson(tcur - packageName)
        }
    }

    suspend fun removeApp(packageName: String) {
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.appRules]?.let(::ruleMapFromJson) ?: emptyMap()
            p[Keys.appRules] = ruleMapToJson(cur - packageName)
            val tcur = p[Keys.appTemplateNames]?.let(::stringMapFromJson) ?: emptyMap()
            p[Keys.appTemplateNames] = stringMapToJson(tcur - packageName)
        }
    }

    suspend fun setTemplate(name: String, rule: AppRule) {
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.templates]?.let(::ruleMapFromJson) ?: emptyMap()
            p[Keys.templates] = ruleMapToJson(cur + (name to rule))
        }
    }

    suspend fun deleteTemplate(name: String) {
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.templates]?.let(::ruleMapFromJson) ?: emptyMap()
            p[Keys.templates] = ruleMapToJson(cur - name)
            val tcur = p[Keys.appTemplateNames]?.let(::stringMapFromJson) ?: emptyMap()
            p[Keys.appTemplateNames] = stringMapToJson(tcur.filterValues { it != name })
        }
    }

    suspend fun applyTemplate(name: String, packageNames: Set<String>) {
        context.settingsDataStore.edit { p ->
            val templates = p[Keys.templates]?.let(::ruleMapFromJson) ?: emptyMap()
            val rule = templates[name] ?: return@edit
            val cur = p[Keys.appRules]?.let(::ruleMapFromJson) ?: emptyMap()
            val next = cur.toMutableMap()
            packageNames.forEach { next[it] = rule }
            p[Keys.appRules] = ruleMapToJson(next)

            val tcur = p[Keys.appTemplateNames]?.let(::stringMapFromJson) ?: emptyMap()
            val tnext = tcur.toMutableMap()
            packageNames.forEach { tnext[it] = name }
            p[Keys.appTemplateNames] = stringMapToJson(tnext)
        }
    }
}
