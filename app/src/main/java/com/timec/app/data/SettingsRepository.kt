package com.timec.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "timec_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val themeIndex = intPreferencesKey("theme_index")
        val mode = intPreferencesKey("mode")
        val overdraftDelaySeconds = intPreferencesKey("overdraft_delay_seconds")
        val defaultRule = stringPreferencesKey("default_rule")
        val appRules = stringPreferencesKey("app_rules")
        val templates = stringPreferencesKey("templates")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            enabled = p[Keys.enabled] ?: true,
            themeIndex = p[Keys.themeIndex] ?: 0,
            mode = p[Keys.mode] ?: 1,
            overdraftDelaySeconds = p[Keys.overdraftDelaySeconds] ?: 0,
            defaultRule = p[Keys.defaultRule]?.let(::appRuleFromJson) ?: AppRule(),
            appRules = p[Keys.appRules]?.let(::ruleMapFromJson) ?: emptyMap(),
            templates = p[Keys.templates]?.let(::ruleMapFromJson) ?: emptyMap()
        )
    }

    suspend fun setEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.enabled] = value }
    }

    suspend fun setThemeIndex(value: Int) {
        context.settingsDataStore.edit { it[Keys.themeIndex] = value.coerceIn(0, 100) }
    }

    suspend fun setMode(value: Int) {
        context.settingsDataStore.edit { it[Keys.mode] = value.coerceIn(0, 1) }
    }

    suspend fun setOverdraftDelaySeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.overdraftDelaySeconds] = value.coerceIn(0, 300) }
    }

    suspend fun setDefaultRule(rule: AppRule) {
        context.settingsDataStore.edit { it[Keys.defaultRule] = rule.toJson() }
    }

    suspend fun addApp(packageName: String, rule: AppRule) {
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.appRules]?.let(::ruleMapFromJson) ?: emptyMap()
            p[Keys.appRules] = ruleMapToJson(cur + (packageName to rule))
        }
    }

    suspend fun addApps(packageNames: Set<String>, rule: AppRule) {
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.appRules]?.let(::ruleMapFromJson) ?: emptyMap()
            val next = cur.toMutableMap()
            packageNames.forEach { next[it] = rule }
            p[Keys.appRules] = ruleMapToJson(next)
        }
    }

    suspend fun removeApp(packageName: String) {
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.appRules]?.let(::ruleMapFromJson) ?: emptyMap()
            p[Keys.appRules] = ruleMapToJson(cur - packageName)
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
        }
    }
}
