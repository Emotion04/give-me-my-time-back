package com.timec.app.monitor

import android.os.SystemClock
import com.timec.app.data.AppRule
import com.timec.app.data.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SessionPhase { IDLE, RUNNING, OVERDRAFT, COOLDOWN_OVERDRAFT, COOLDOWN, DAILY_EXHAUSTED }

data class SessionSnapshot(
    val packageName: String,
    val phase: SessionPhase,
    val sessionActiveMillis: Long,
    val sessionLimitMillis: Long,
    val dailyUsedMillis: Long,
    val dailyLimitMillis: Long,
    val overdraftConsumedMillis: Long,
    val overdraftAllowanceMillis: Long,
    val cooldownRemainingMillis: Long,
    val cooldownPenaltyMillis: Long
)

data class MonitorUiState(
    val foregroundPackage: String? = null,
    val activePackage: String? = null,
    val activeSnapshot: SessionSnapshot? = null,
    val enabled: Boolean = true,
    val guardedPackages: Set<String> = emptySet()
)

data class TickResult(
    val warningPackages: Set<String> = emptySet(),
    val finalPackage: String? = null,
    val overdraftExhaustedPackage: String? = null,
    val dailyExhaustedPackage: String? = null,
    val cooldownPackage: String? = null,
    val cooldownRemainingMillis: Long = 0L,
    val frictionPackage: String? = null
)

data class ChoiceResult(val dismissOverlay: Boolean, val goHome: Boolean)

enum class FinalChoice { EXTEND, CONFIRM, CONTINUE }
enum class OverdraftChoice { CONFIRM, COOLDOWN_OVERDRAFT }

object MonitorEngine {
    private val _state = MutableStateFlow(MonitorUiState())
    val state = _state.asStateFlow()

    private var settings = AppSettings()
    private val sessions = mutableMapOf<String, Session>()
    private var foregroundPackage: String? = null
    private var overlayPackage: String? = null
    private var overlayMode: String? = null
    private var dailyUsedMap: Map<String, Long> = emptyMap()

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        sessions.keys.retainAll(newSettings.appRules.keys)
        publishState()
    }

    fun tick(
        nowRealtime: Long = SystemClock.elapsedRealtime(),
        screenOn: Boolean,
        detectedForegroundPackage: String?,
        dailyUsedMap: Map<String, Long>
    ): TickResult {
        foregroundPackage = detectedForegroundPackage
        this.dailyUsedMap = dailyUsedMap

        val warningPackages = mutableSetOf<String>()
        var finalPackage: String? = null
        var overdraftExhaustedPackage: String? = null
        var dailyExhaustedPackage: String? = null
        var cooldownPackage: String? = null
        var cooldownRemaining = 0L
        var frictionPackage: String? = null

        val activePackage = if (screenOn && settings.enabled) {
            detectedForegroundPackage?.takeIf { it in settings.appRules.keys }
        } else null
        activePackage?.let { ensureSession(it) }

        sessions.values.forEach { session ->
            val pkg = session.packageName
            val isActive = session.packageName == activePackage
            val overlayShowing = overlayPackage == session.packageName

            if (isActive && !overlayShowing) {
                when (session.phase) {
                    SessionPhase.IDLE -> {
                        if (dailyLimitHit(pkg)) {
                            session.phase = SessionPhase.DAILY_EXHAUSTED
                            dailyExhaustedPackage = pkg
                        } else {
                            if (startSession(session, nowRealtime)) frictionPackage = pkg
                        }
                    }
                    SessionPhase.RUNNING -> {
                        advance(session, nowRealtime)
                        maybeWarn(session, warningPackages)
                        when {
                            dailyLimitHit(pkg) -> {
                                session.phase = SessionPhase.DAILY_EXHAUSTED
                                dailyExhaustedPackage = pkg
                            }
                            session.sessionActiveMillis >= sessionLimitMillis(pkg) + session.extensionMillis ->
                                finalPackage = pkg
                        }
                    }
                    SessionPhase.OVERDRAFT -> {
                        val elapsed = advance(session, nowRealtime)
                        session.overdraftConsumedMillis += elapsed
                        when {
                            dailyLimitHit(pkg) -> {
                                session.phase = SessionPhase.DAILY_EXHAUSTED
                                dailyExhaustedPackage = pkg
                            }
                            session.overdraftConsumedMillis >= session.overdraftAllowanceMillis ->
                                overdraftExhaustedPackage = pkg
                        }
                    }
                    SessionPhase.COOLDOWN_OVERDRAFT -> {
                        val elapsed = advance(session, nowRealtime)
                        session.cooldownOverdraftConsumedMillis += elapsed
                        if (dailyLimitHit(pkg)) {
                            session.phase = SessionPhase.DAILY_EXHAUSTED
                            dailyExhaustedPackage = pkg
                        }
                    }
                    SessionPhase.COOLDOWN -> {
                        val remaining = (session.cooldownUntil ?: 0L) - nowRealtime
                        if (remaining > 0) {
                            cooldownPackage = pkg
                            cooldownRemaining = remaining
                        } else {
                            resetSessionToIdle(session)
                        }
                    }
                    SessionPhase.DAILY_EXHAUSTED -> {
                        dailyExhaustedPackage = pkg
                    }
                }
            } else {
                handleAway(session, nowRealtime)
                if (session.phase == SessionPhase.COOLDOWN) {
                    val remaining = (session.cooldownUntil ?: 0L) - nowRealtime
                    if (remaining <= 0L) resetSessionToIdle(session)
                }
            }
        }

        publishState()
        return TickResult(
            warningPackages = warningPackages,
            finalPackage = finalPackage,
            overdraftExhaustedPackage = overdraftExhaustedPackage,
            dailyExhaustedPackage = dailyExhaustedPackage,
            cooldownPackage = cooldownPackage,
            cooldownRemainingMillis = cooldownRemaining,
            frictionPackage = frictionPackage
        )
    }

    fun choose(packageName: String, choice: FinalChoice): ChoiceResult {
        val session = sessions[packageName] ?: return ChoiceResult(true, true)
        val now = SystemClock.elapsedRealtime()
        when (choice) {
            FinalChoice.EXTEND -> {
                if (session.extensionCount < ruleFor(packageName).maxExtensions) {
                    session.extensionMillis += extensionSeconds(packageName) * 1000L
                    session.extensionCount++
                    session.phase = SessionPhase.RUNNING
                    session.lastActiveRealtime = now
                }
            }
            FinalChoice.CONFIRM -> {
                if (ruleFor(packageName).mode == 1) startCooldown(session) else resetSessionToIdle(session)
                publishState()
                return ChoiceResult(true, true)
            }
            FinalChoice.CONTINUE -> {
                session.phase = SessionPhase.OVERDRAFT
                session.overdraftConsumedMillis = 0L
                session.overdraftAllowanceMillis = computeOverdraftAllowance(packageName)
                session.lastActiveRealtime = now
            }
        }
        publishState()
        return ChoiceResult(true, false)
    }

    fun chooseOverdraft(packageName: String, choice: OverdraftChoice): ChoiceResult {
        val session = sessions[packageName] ?: return ChoiceResult(true, true)
        val now = SystemClock.elapsedRealtime()
        when (choice) {
            OverdraftChoice.CONFIRM -> {
                startCooldown(session)
                publishState()
                return ChoiceResult(true, true)
            }
            OverdraftChoice.COOLDOWN_OVERDRAFT -> {
                session.phase = SessionPhase.COOLDOWN_OVERDRAFT
                session.cooldownOverdraftConsumedMillis = 0L
                session.lastActiveRealtime = now
            }
        }
        publishState()
        return ChoiceResult(true, false)
    }

    fun extensionsLeftFor(packageName: String): Int {
        val session = sessions[packageName]
        val max = ruleFor(packageName).maxExtensions
        return if (session == null) max else (max - session.extensionCount).coerceAtLeast(0)
    }

    fun extensionUsedCount(packageName: String): Int {
        return sessions[packageName]?.extensionCount ?: 0
    }

    fun chooseCooldownOverdraft(packageName: String): ChoiceResult {
        val session = sessions[packageName] ?: return ChoiceResult(true, true)
        val now = SystemClock.elapsedRealtime()
        session.phase = SessionPhase.COOLDOWN_OVERDRAFT
        session.cooldownOverdraftConsumedMillis = 0L
        session.cooldownUntil = null
        session.lastActiveRealtime = now
        publishState()
        return ChoiceResult(true, false)
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

    // ---- helpers ----

    private fun ruleFor(packageName: String): AppRule = settings.ruleFor(packageName)
    private fun sessionLimitMillis(packageName: String): Long = ruleFor(packageName).sessionLimitMinutes * 60_000L
    private fun dailyLimitMillis(packageName: String): Long = ruleFor(packageName).dailyLimitMinutes * 60_000L
    private fun floorMillis(packageName: String): Long = ruleFor(packageName).floorMinutes * 60_000L
    private fun cooldownBaseMillis(packageName: String): Long = ruleFor(packageName).cooldownMinutes * 60_000L
    private fun extensionSeconds(packageName: String): Int = ruleFor(packageName).extensionSeconds

    private fun computeOverdraftAllowance(packageName: String): Long {
        val m = ruleFor(packageName).overdraftMultiplier
        if (m <= 0f) return 0L
        return ((sessionLimitMillis(packageName) - floorMillis(packageName)).coerceAtLeast(0L) / m).toLong()
    }

    private fun dailyLimitHit(packageName: String): Boolean {
        val limit = dailyLimitMillis(packageName)
        return limit > 0L && (dailyUsedMap[packageName] ?: 0L) >= limit
    }

    private fun cooldownPenaltyMillis(session: Session): Long {
        val minutes = session.cooldownOverdraftConsumedMillis / 60_000L
        return minutes * ruleFor(session.packageName).cooldownPenaltyMinutes * 60_000L
    }

    private fun ensureSession(packageName: String) {
        if (sessions[packageName] == null) sessions[packageName] = Session(packageName)
    }

    private fun startSession(session: Session, nowRealtime: Long): Boolean {
        val shouldFriction = ruleFor(session.packageName).frictionEnabled && !session.frictionShown
        session.frictionShown = true
        session.phase = SessionPhase.RUNNING
        session.sessionActiveMillis = 0L
        session.extensionMillis = 0L
        session.extensionCount = 0
        session.overdraftConsumedMillis = 0L
        session.overdraftAllowanceMillis = 0L
        session.cooldownOverdraftConsumedMillis = 0L
        session.cooldownUntil = null
        session.cooldownPenaltyMillis = 0L
        session.lastActiveRealtime = nowRealtime
        session.warning1Sent = false
        session.warning2Sent = false
        return shouldFriction
    }

    private fun advance(session: Session, nowRealtime: Long): Long {
        val last = session.lastActiveRealtime
        if (last == null) {
            session.lastActiveRealtime = nowRealtime
            return 0L
        }
        val elapsed = (nowRealtime - last).coerceAtLeast(0L)
        session.sessionActiveMillis += elapsed
        session.lastActiveRealtime = nowRealtime
        return elapsed
    }

    private fun maybeWarn(session: Session, warningPackages: MutableSet<String>) {
        val pkg = session.packageName
        val limit = sessionLimitMillis(pkg)
        if (ruleFor(pkg).warnPct1 > 0 && !session.warning1Sent &&
            session.sessionActiveMillis >= limit * ruleFor(pkg).warnPct1 / 100) {
            session.warning1Sent = true
            warningPackages += pkg
        }
        if (ruleFor(pkg).warnPct2 > 0 && !session.warning2Sent &&
            session.sessionActiveMillis >= limit * ruleFor(pkg).warnPct2 / 100) {
            session.warning2Sent = true
            warningPackages += pkg
        }
    }

    private fun handleAway(session: Session, nowRealtime: Long) {
        when (session.phase) {
            SessionPhase.RUNNING, SessionPhase.OVERDRAFT -> session.lastActiveRealtime = null
            SessionPhase.COOLDOWN_OVERDRAFT -> {
                session.lastActiveRealtime = null
                startCooldown(session)
            }
            else -> {}
        }
    }

    private fun startCooldown(session: Session) {
        val penalty = cooldownPenaltyMillis(session)
        session.phase = SessionPhase.COOLDOWN
        session.cooldownUntil = SystemClock.elapsedRealtime() + cooldownBaseMillis(session.packageName) + penalty
        session.cooldownPenaltyMillis = penalty
        session.sessionActiveMillis = 0L
        session.extensionMillis = 0L
        session.extensionCount = 0
        session.overdraftConsumedMillis = 0L
        session.overdraftAllowanceMillis = 0L
        session.cooldownOverdraftConsumedMillis = 0L
        session.lastActiveRealtime = null
        session.warning1Sent = false
        session.warning2Sent = false
        session.frictionShown = false
    }

    private fun resetSessionToIdle(session: Session) {
        session.phase = SessionPhase.IDLE
        session.sessionActiveMillis = 0L
        session.extensionMillis = 0L
        session.extensionCount = 0
        session.overdraftConsumedMillis = 0L
        session.overdraftAllowanceMillis = 0L
        session.cooldownOverdraftConsumedMillis = 0L
        session.cooldownUntil = null
        session.cooldownPenaltyMillis = 0L
        session.lastActiveRealtime = null
        session.warning1Sent = false
        session.warning2Sent = false
        session.frictionShown = false
    }

    private fun publishState() {
        val activePackage = foregroundPackage?.takeIf { it in settings.appRules.keys }
        val snapshot = activePackage?.let { pkg -> sessions[pkg]?.let { snapshotOf(it) } }
        _state.update {
            MonitorUiState(
                foregroundPackage = foregroundPackage,
                activePackage = activePackage,
                activeSnapshot = snapshot,
                enabled = settings.enabled,
                guardedPackages = settings.appRules.keys
            )
        }
    }

    private fun snapshotOf(s: Session): SessionSnapshot {
        val pkg = s.packageName
        val remaining = if (s.phase == SessionPhase.COOLDOWN) {
            ((s.cooldownUntil ?: 0L) - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else 0L
        val penalty = if (s.phase == SessionPhase.COOLDOWN_OVERDRAFT) {
            cooldownPenaltyMillis(s)
        } else {
            s.cooldownPenaltyMillis
        }
        return SessionSnapshot(
            packageName = pkg,
            phase = s.phase,
            sessionActiveMillis = s.sessionActiveMillis,
            sessionLimitMillis = sessionLimitMillis(pkg) + s.extensionMillis,
            dailyUsedMillis = dailyUsedMap[pkg] ?: 0L,
            dailyLimitMillis = dailyLimitMillis(pkg),
            overdraftConsumedMillis = s.overdraftConsumedMillis,
            overdraftAllowanceMillis = s.overdraftAllowanceMillis,
            cooldownRemainingMillis = remaining,
            cooldownPenaltyMillis = penalty
        )
    }

    private class Session(val packageName: String) {
        var phase: SessionPhase = SessionPhase.IDLE
        var sessionActiveMillis: Long = 0L
        var extensionMillis: Long = 0L
        var extensionCount: Int = 0
        var overdraftConsumedMillis: Long = 0L
        var overdraftAllowanceMillis: Long = 0L
        var cooldownOverdraftConsumedMillis: Long = 0L
        var cooldownUntil: Long? = null
        var cooldownPenaltyMillis: Long = 0L
        var lastActiveRealtime: Long? = null
        var warning1Sent: Boolean = false
        var warning2Sent: Boolean = false
        var frictionShown: Boolean = false
    }
}
