package com.timec.app.monitor

import android.os.SystemClock
import com.timec.app.data.AppSettings
import com.timec.app.data.RecoverMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SessionPhase {
    IDLE,
    RUNNING,
    FINAL,
    COOLDOWN,
    DONE
}

data class SessionSnapshot(
    val packageName: String,
    val phase: SessionPhase,
    val activeMillis: Long,
    val consumedMillis: Long,
    val allowedMillis: Long,
    val baseLimitMillis: Long,
    val bankedMillis: Long,
    val extensionMillis: Long,
    val extensionsLeft: Int,
    val currentRateX: Double,
    val cooldownRemainingMillis: Long
)

data class MonitorUiState(
    val foregroundPackage: String? = null,
    val activePackage: String? = null,
    val activeSnapshot: SessionSnapshot? = null,
    val enabled: Boolean = true,
    val selectedPackages: Set<String> = emptySet()
)

data class TickResult(
    val warningPackages: Set<String> = emptySet(),
    val finalPackage: String? = null,
    val cooldownPackage: String? = null,
    val cooldownRemainingMillis: Long = 0L,
    val frictionPackage: String? = null
)

data class ChoiceResult(
    val dismissOverlay: Boolean,
    val goHome: Boolean
)

enum class FinalChoice {
    EXTEND,
    SKIP,
    CONFIRM
}

object MonitorEngine {
    private val _state = MutableStateFlow(MonitorUiState())
    val state = _state.asStateFlow()

    private var settings = AppSettings()
    private val sessions = mutableMapOf<String, Session>()
    private var foregroundPackage: String? = null
    private var overlayPackage: String? = null
    private var overlayMode: String? = null

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        val validPackages = newSettings.selectedPackages
        sessions.keys.retainAll(validPackages)
        sessions.values.forEach { session ->
            session.baseLimitMillis = baseLimitMillis()
        }
        publishState()
    }

    fun tick(
        nowRealtime: Long = SystemClock.elapsedRealtime(),
        screenOn: Boolean,
        detectedForegroundPackage: String?
    ): TickResult {
        foregroundPackage = detectedForegroundPackage
        val warningPackages = mutableSetOf<String>()
        var finalPackage: String? = null
        var cooldownPackage: String? = null
        var cooldownRemaining = 0L
        var frictionPackage: String? = null

        if (screenOn && settings.enabled) {
            val activePackage = detectedForegroundPackage?.takeIf { it in settings.selectedPackages }
            activePackage?.let { ensureSession(it) }

            sessions.values.forEach { session ->
                val isActive = session.packageName == activePackage
                if (isActive) {
                    when (session.phase) {
                        SessionPhase.COOLDOWN -> {
                            val remaining = (session.cooldownUntil ?: 0L) - nowRealtime
                            if (remaining > 0) {
                                cooldownPackage = session.packageName
                                cooldownRemaining = remaining
                            } else {
                                resetSession(session, SessionPhase.IDLE)
                                if (beginActiveSession(session, nowRealtime)) {
                                    frictionPackage = session.packageName
                                }
                            }
                        }
                        SessionPhase.FINAL -> {
                            if (overlayPackage != session.packageName) {
                                finalPackage = session.packageName
                            }
                        }
                        SessionPhase.DONE, SessionPhase.IDLE -> {
                            if (beginActiveSession(session, nowRealtime)) {
                                frictionPackage = session.packageName
                            }
                        }
                        SessionPhase.RUNNING -> {
                            session.lastActiveRealtime?.let { last ->
                                val elapsed = (nowRealtime - last).coerceAtLeast(0L)
                                session.activeMillis += elapsed
                                val rate = rateFor(session.activeMillis)
                                session.currentRateX = rate
                                session.consumedMillis += (elapsed * rate).toLong()
                            }
                            session.lastActiveRealtime = nowRealtime
                            session.awayStartRealtime = null

                            maybeSendWarnings(session, warningPackages)

                            val suppressed = session.finalSuppressUntil?.let { nowRealtime < it } ?: false
                            if (!suppressed && session.consumedMillis >= session.allowedMillis &&
                                overlayPackage != session.packageName) {
                                session.phase = SessionPhase.FINAL
                                finalPackage = session.packageName
                            }
                        }
                    }
                } else {
                    handleAway(session, nowRealtime)
                    if (session.phase == SessionPhase.COOLDOWN) {
                        val remaining = (session.cooldownUntil ?: 0L) - nowRealtime
                        if (remaining > 0) {
                            cooldownPackage = session.packageName
                            cooldownRemaining = remaining
                        } else {
                            resetSession(session, SessionPhase.IDLE)
                        }
                    }
                }
            }
        } else {
            sessions.values.forEach { session ->
                if (session.phase == SessionPhase.COOLDOWN) {
                    val remaining = (session.cooldownUntil ?: 0L) - nowRealtime
                    if (remaining > 0) {
                        cooldownPackage = session.packageName
                        cooldownRemaining = remaining
                    } else {
                        resetSession(session, SessionPhase.IDLE)
                    }
                } else if (session.phase == SessionPhase.RUNNING) {
                    session.lastActiveRealtime = null
                    session.awayStartRealtime = null
                }
            }
        }

        publishState()
        return TickResult(
            warningPackages = warningPackages,
            finalPackage = finalPackage,
            cooldownPackage = cooldownPackage,
            cooldownRemainingMillis = cooldownRemaining,
            frictionPackage = frictionPackage
        )
    }

    fun choose(packageName: String, choice: FinalChoice): ChoiceResult {
        val session = sessions[packageName]
            ?: return ChoiceResult(dismissOverlay = true, goHome = true)
        return when (choice) {
            FinalChoice.EXTEND -> {
                if (session.extensionCount < settings.maxExtensions) {
                    session.extensionMillis += settings.extensionSeconds * 1000L
                    session.extensionCount++
                    session.phase = SessionPhase.RUNNING
                    session.lastActiveRealtime = SystemClock.elapsedRealtime()
                    session.awayStartRealtime = null
                }
                publishState()
                ChoiceResult(dismissOverlay = true, goHome = false)
            }
            FinalChoice.SKIP -> {
                if (settings.recoverMode == RecoverMode.RECHARGE) {
                    // 柔和模式：忽略本次提醒，进入透支（保持运行，阶梯高费率），60 秒后再次提醒
                    session.phase = SessionPhase.RUNNING
                    session.lastActiveRealtime = SystemClock.elapsedRealtime()
                    session.awayStartRealtime = null
                    session.finalSuppressUntil = SystemClock.elapsedRealtime() + SKIP_GRACE_MILLIS
                    publishState()
                    ChoiceResult(dismissOverlay = true, goHome = false)
                } else {
                    startCooldown(session, settings.cooldownSeconds * 1000L)
                    publishState()
                    ChoiceResult(dismissOverlay = true, goHome = true)
                }
            }
            FinalChoice.CONFIRM -> {
                if (settings.recoverMode == RecoverMode.COOLDOWN) {
                    startCooldown(session, settings.cooldownSeconds * 1000L)
                } else {
                    startCooldown(session, settings.breakResetSeconds * 1000L)
                }
                publishState()
                ChoiceResult(dismissOverlay = true, goHome = true)
            }
        }
    }

    fun extensionsLeftFor(packageName: String): Int {
        val session = sessions[packageName] ?: return 0
        return (settings.maxExtensions - session.extensionCount).coerceAtLeast(0)
    }

    fun shouldHardBlock(packageName: String, nowRealtime: Long = SystemClock.elapsedRealtime()): Boolean {
        if (!settings.hardBlock) return false
        val session = sessions[packageName] ?: return false
        return when (session.phase) {
            SessionPhase.FINAL -> true
            SessionPhase.COOLDOWN -> (session.cooldownUntil ?: 0L) > nowRealtime
            else -> false
        }
    }

    fun setOverlayShowing(packageName: String, mode: String) {
        overlayPackage = packageName
        overlayMode = mode
    }

    fun clearOverlay(packageName: String) {
        if (overlayPackage == packageName) {
            overlayPackage = null
            overlayMode = null
        }
    }

    fun isOverlayShowingFor(packageName: String): Boolean = overlayPackage == packageName

    fun currentOverlayMode(): String? = overlayMode

    fun reset() {
        sessions.clear()
        overlayPackage = null
        overlayMode = null
        publishState()
    }

    private fun baseLimitMillis(): Long = settings.limitMinutes * 60_000L

    private fun rateFor(activeMillis: Long): Double {
        if (!settings.tieredEnabled) return 1.0
        val base = baseLimitMillis()
        return when {
            activeMillis >= base * 150 / 100 -> settings.rate150x.toDouble()
            activeMillis >= base * 100 / 100 -> settings.rate100x.toDouble()
            else -> 1.0
        }
    }

    private fun maybeSendWarnings(session: Session, warningPackages: MutableSet<String>) {
        val base = baseLimitMillis()
        if (settings.warnPct1 > 0 && !session.warning1Sent &&
            session.activeMillis >= base * settings.warnPct1 / 100) {
            session.warning1Sent = true
            warningPackages += session.packageName
        }
        if (settings.warnPct2 > 0 && !session.warning2Sent &&
            session.activeMillis >= base * settings.warnPct2 / 100) {
            session.warning2Sent = true
            warningPackages += session.packageName
        }
    }

    private fun ensureSession(packageName: String) {
        if (sessions[packageName] == null) {
            sessions[packageName] = Session(
                packageName = packageName,
                baseLimitMillis = baseLimitMillis()
            )
        }
    }

    // 开始一个新的活跃会话；返回是否应展示打开摩擦页
    private fun beginActiveSession(session: Session, nowRealtime: Long): Boolean {
        val shouldFriction = settings.frictionEnabled && !session.frictionShown
        session.frictionShown = true
        session.phase = SessionPhase.RUNNING
        session.lastActiveRealtime = nowRealtime
        session.awayStartRealtime = null
        session.activeMillis = 0L
        session.consumedMillis = 0L
        session.bankedMillis = 0L
        session.extensionMillis = 0L
        session.extensionCount = 0
        session.earnedWorkBlocks = 0L
        session.warning1Sent = false
        session.warning2Sent = false
        session.finalSuppressUntil = null
        session.cooldownUntil = null
        session.currentRateX = 1.0
        return shouldFriction
    }

    private fun handleAway(session: Session, nowRealtime: Long) {
        if (session.phase == SessionPhase.COOLDOWN || session.phase == SessionPhase.FINAL) return
        if (session.lastActiveRealtime == null) return

        val awayStart = session.awayStartRealtime ?: nowRealtime.also {
            session.awayStartRealtime = it
            session.earnedWorkBlocks = 0L
        }
        val awayMillis = (nowRealtime - awayStart).coerceAtLeast(0L)

        val resetMillis = settings.breakResetSeconds * 1000L
        if (awayMillis >= resetMillis) {
            resetSession(session, SessionPhase.IDLE)
            return
        }

        if (settings.recoverMode == RecoverMode.RECHARGE) {
            val workMillis = settings.earnWorkSeconds * 1000L
            if (workMillis > 0) {
                val earnedBlocks = awayMillis / workMillis
                if (earnedBlocks > session.earnedWorkBlocks) {
                    val addedBlocks = earnedBlocks - session.earnedWorkBlocks
                    val rewardMillis = settings.earnRewardSeconds * 1000L
                    session.bankedMillis += addedBlocks * rewardMillis
                    session.earnedWorkBlocks = earnedBlocks
                }
            }
        }
    }

    private fun startCooldown(session: Session, durationMillis: Long) {
        resetSession(session, SessionPhase.COOLDOWN)
        session.cooldownUntil = SystemClock.elapsedRealtime() + durationMillis
    }

    private fun resetSession(session: Session, phase: SessionPhase) {
        session.phase = phase
        session.activeMillis = 0L
        session.consumedMillis = 0L
        session.bankedMillis = 0L
        session.extensionMillis = 0L
        session.extensionCount = 0
        session.lastActiveRealtime = null
        session.awayStartRealtime = null
        session.earnedWorkBlocks = 0L
        session.warning1Sent = false
        session.warning2Sent = false
        session.finalSuppressUntil = null
        session.cooldownUntil = null
        session.currentRateX = 1.0
        session.frictionShown = false
    }

    private fun publishState() {
        val activePackage = foregroundPackage?.takeIf { it in settings.selectedPackages }
        val snapshot = activePackage?.let { pkg ->
            sessions[pkg]?.snapshot(nowRealtime = SystemClock.elapsedRealtime(), maxExtensions = settings.maxExtensions)
        }
        _state.update {
            MonitorUiState(
                foregroundPackage = foregroundPackage,
                activePackage = activePackage,
                activeSnapshot = snapshot,
                enabled = settings.enabled,
                selectedPackages = settings.selectedPackages
            )
        }
    }

    private class Session(
        val packageName: String,
        var baseLimitMillis: Long
    ) {
        var phase: SessionPhase = SessionPhase.IDLE
        var activeMillis: Long = 0L
        var consumedMillis: Long = 0L
        var bankedMillis: Long = 0L
        var extensionMillis: Long = 0L
        var extensionCount: Int = 0
        var currentRateX: Double = 1.0
        var lastActiveRealtime: Long? = null
        var awayStartRealtime: Long? = null
        var earnedWorkBlocks: Long = 0L
        var warning1Sent: Boolean = false
        var warning2Sent: Boolean = false
        var frictionShown: Boolean = false
        var finalSuppressUntil: Long? = null
        var cooldownUntil: Long? = null

        val allowedMillis: Long
            get() = baseLimitMillis + bankedMillis + extensionMillis

        fun snapshot(nowRealtime: Long, maxExtensions: Int): SessionSnapshot {
            val remaining = when (phase) {
                SessionPhase.COOLDOWN -> ((cooldownUntil ?: 0L) - nowRealtime).coerceAtLeast(0L)
                else -> 0L
            }
            return SessionSnapshot(
                packageName = packageName,
                phase = phase,
                activeMillis = activeMillis,
                consumedMillis = consumedMillis,
                allowedMillis = allowedMillis,
                baseLimitMillis = baseLimitMillis,
                bankedMillis = bankedMillis,
                extensionMillis = extensionMillis,
                extensionsLeft = (maxExtensions - extensionCount).coerceAtLeast(0),
                currentRateX = currentRateX,
                cooldownRemainingMillis = remaining
            )
        }
    }

    private const val SKIP_GRACE_MILLIS = 60_000L
}
