package com.timec.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "timec_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val enabled = booleanPreferencesKey("enabled")
        val limitMinutes = intPreferencesKey("limit_minutes")
        val tieredEnabled = booleanPreferencesKey("tiered_enabled")
        val rate100x = floatPreferencesKey("rate_100x")
        val rate150x = floatPreferencesKey("rate_150x")
        val warnPct1 = intPreferencesKey("warn_pct_1")
        val warnPct2 = intPreferencesKey("warn_pct_2")
        val recoverMode = intPreferencesKey("recover_mode")
        val breakResetSeconds = intPreferencesKey("break_reset_seconds")
        val earnWorkSeconds = intPreferencesKey("earn_work_seconds")
        val earnRewardSeconds = intPreferencesKey("earn_reward_seconds")
        val cooldownSeconds = intPreferencesKey("cooldown_seconds")
        val extensionSeconds = intPreferencesKey("extension_seconds")
        val maxExtensions = intPreferencesKey("max_extensions")
        val hardBlock = booleanPreferencesKey("hard_block")
        val frictionEnabled = booleanPreferencesKey("friction_enabled")
        val frictionSeconds = intPreferencesKey("friction_seconds")
        val selectedPackages = stringSetPreferencesKey("selected_packages")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { p ->
        AppSettings(
            enabled = p[Keys.enabled] ?: true,
            limitMinutes = p[Keys.limitMinutes] ?: 15,
            tieredEnabled = p[Keys.tieredEnabled] ?: true,
            rate100x = p[Keys.rate100x] ?: 2f,
            rate150x = p[Keys.rate150x] ?: 3f,
            warnPct1 = p[Keys.warnPct1] ?: 80,
            warnPct2 = p[Keys.warnPct2] ?: 90,
            recoverMode = p[Keys.recoverMode] ?: RecoverMode.RECHARGE,
            breakResetSeconds = p[Keys.breakResetSeconds] ?: 30,
            earnWorkSeconds = p[Keys.earnWorkSeconds] ?: 3,
            earnRewardSeconds = p[Keys.earnRewardSeconds] ?: 1,
            cooldownSeconds = p[Keys.cooldownSeconds] ?: 60,
            extensionSeconds = p[Keys.extensionSeconds] ?: 60,
            maxExtensions = p[Keys.maxExtensions] ?: 1,
            hardBlock = p[Keys.hardBlock] ?: false,
            frictionEnabled = p[Keys.frictionEnabled] ?: false,
            frictionSeconds = p[Keys.frictionSeconds] ?: 4,
            selectedPackages = p[Keys.selectedPackages] ?: emptySet()
        )
    }

    suspend fun setEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.enabled] = value }
    }

    suspend fun setLimitMinutes(value: Int) {
        context.settingsDataStore.edit { it[Keys.limitMinutes] = value.coerceIn(1, 600) }
    }

    suspend fun setTieredEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.tieredEnabled] = value }
    }

    suspend fun setRate100x(value: Float) {
        context.settingsDataStore.edit { it[Keys.rate100x] = value.coerceIn(0.1f, 20f) }
    }

    suspend fun setRate150x(value: Float) {
        context.settingsDataStore.edit { it[Keys.rate150x] = value.coerceIn(0.1f, 20f) }
    }

    suspend fun setWarnPct(warnPct1: Int, warnPct2: Int) {
        context.settingsDataStore.edit {
            it[Keys.warnPct1] = warnPct1.coerceIn(0, 100)
            it[Keys.warnPct2] = warnPct2.coerceIn(0, 100)
        }
    }

    suspend fun setRecoverMode(value: Int) {
        context.settingsDataStore.edit { it[Keys.recoverMode] = value.coerceIn(0, 1) }
    }

    suspend fun setBreakResetSeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.breakResetSeconds] = value.coerceIn(1, 3600) }
    }

    suspend fun setEarnRule(workSeconds: Int, rewardSeconds: Int) {
        context.settingsDataStore.edit {
            it[Keys.earnWorkSeconds] = workSeconds.coerceIn(1, 3600)
            it[Keys.earnRewardSeconds] = rewardSeconds.coerceIn(1, 3600)
        }
    }

    suspend fun setCooldownSeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.cooldownSeconds] = value.coerceIn(5, 3600) }
    }

    suspend fun setExtensionSeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.extensionSeconds] = value.coerceIn(5, 3600) }
    }

    suspend fun setMaxExtensions(value: Int) {
        context.settingsDataStore.edit { it[Keys.maxExtensions] = value.coerceIn(0, 100) }
    }

    suspend fun setHardBlock(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.hardBlock] = value }
    }

    suspend fun setFrictionEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.frictionEnabled] = value }
    }

    suspend fun setFrictionSeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.frictionSeconds] = value.coerceIn(1, 30) }
    }

    suspend fun setSelectedPackages(packages: Set<String>) {
        context.settingsDataStore.edit { it[Keys.selectedPackages] = packages }
    }
}
