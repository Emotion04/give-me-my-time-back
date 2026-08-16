package com.timec.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

data class DayUsage(
    val dayStartMillis: Long,
    val totalMillis: Long
)

data class AppActivity(
    val packageName: String,
    val hourBuckets: List<Long>,
    val openCount: Int,
    val totalMillis: Long
)

class UsageRepository(private val context: Context) {
    private var lastKnownForegroundPackage: String? = null
    private var foregroundInitialized = false

    private val usageStatsManager: UsageStatsManager?
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm).toString()
                val icon = try { info.loadIcon(pm) } catch (_: Exception) { null }
                AppInfo(pkg, label, icon)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun getTodayUsageByPackage(): Map<String, Long> = withContext(Dispatchers.IO) {
        val manager = usageStatsManager ?: return@withContext emptyMap()
        val start = startOfDay(System.currentTimeMillis())
        manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis())
            .associate { it.packageName to it.totalTimeInForeground }
    }

    fun getTodayUsageByPackageNow(): Map<String, Long> {
        val manager = usageStatsManager ?: return emptyMap()
        val start = startOfDay(System.currentTimeMillis())
        return manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis())
            .associate { it.packageName to it.totalTimeInForeground }
    }

    suspend fun getTotalToday(): Long = withContext(Dispatchers.IO) {
        getTodayUsageByPackage().values.sum()
    }

    suspend fun getLast7Days(packageName: String): List<DayUsage> = withContext(Dispatchers.IO) {
        val manager = usageStatsManager ?: return@withContext emptyList()
        val today = startOfDay(System.currentTimeMillis())
        (6 downTo 0).map { offset ->
            val start = today - offset * DAY_MILLIS
            val end = start + DAY_MILLIS - 1
            val total = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .firstOrNull { it.packageName == packageName }
                ?.totalTimeInForeground ?: 0L
            DayUsage(start, total)
        }
    }

    suspend fun getRangePerApp(days: Int): Map<String, Long> = withContext(Dispatchers.IO) {
        val manager = usageStatsManager ?: return@withContext emptyMap()
        val today = startOfDay(System.currentTimeMillis())
        val totals = mutableMapOf<String, Long>()
        for (offset in (days - 1) downTo 0) {
            val start = today - offset * DAY_MILLIS
            val end = start + DAY_MILLIS - 1
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end).forEach { us ->
                totals[us.packageName] = (totals[us.packageName] ?: 0L) + us.totalTimeInForeground
            }
        }
        totals
    }

    suspend fun getRangeDailyTotals(days: Int): List<DayUsage> = withContext(Dispatchers.IO) {
        val manager = usageStatsManager ?: return@withContext emptyList()
        val today = startOfDay(System.currentTimeMillis())
        (days - 1 downTo 0).map { offset ->
            val start = today - offset * DAY_MILLIS
            val end = start + DAY_MILLIS - 1
            val total = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .sumOf { it.totalTimeInForeground }
            DayUsage(start, total)
        }
    }

    suspend fun getTodayHourly(packageName: String): Map<Int, Long> = withContext(Dispatchers.IO) {
        val manager = usageStatsManager ?: return@withContext emptyMap()
        val start = startOfDay(System.currentTimeMillis())
        val end = System.currentTimeMillis()
        val buckets = LongArray(24)
        var lastForegroundStart = -1L
        val event = UsageEvents.Event()
        try {
            val events = manager.queryEvents(start, end)
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> lastForegroundStart = event.timeStamp
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (lastForegroundStart >= 0L) {
                            addToHourlyBuckets(buckets, lastForegroundStart, event.timeStamp, start)
                            lastForegroundStart = -1L
                        }
                    }
                }
            }
            if (lastForegroundStart >= 0L) {
                addToHourlyBuckets(buckets, lastForegroundStart, end, start)
            }
        } catch (_: Exception) {
        }
        buckets.mapIndexed { hour, millis -> hour to millis }
            .filter { it.second > 0L }
            .toMap()
    }

    private fun addToHourlyBuckets(buckets: LongArray, from: Long, to: Long, dayStart: Long) {
        var t = from.coerceAtLeast(dayStart)
        val end = to
        while (t < end) {
            val hour = ((t - dayStart) / HOUR_MILLIS).toInt()
            if (hour < 0 || hour >= 24) {
                break
            }
            val hourEnd = dayStart + (hour + 1) * HOUR_MILLIS
            val segmentEnd = minOf(end, hourEnd)
            buckets[hour] += (segmentEnd - t).coerceAtLeast(0L)
            t = segmentEnd
        }
    }

    fun getRecent24hActivityAll(): Map<String, AppActivity> {
        val manager = usageStatsManager ?: return emptyMap()
        val now = System.currentTimeMillis()
        val start = now - 24 * HOUR_MILLIS
        val hourBuckets = mutableMapOf<String, LongArray>()
        val sessionStart = mutableMapOf<String, Long>()
        val openCount = mutableMapOf<String, Int>()
        val event = UsageEvents.Event()
        try {
            val events = manager.queryEvents(start, now)
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> sessionStart[pkg] = event.timeStamp
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val s = sessionStart.remove(pkg) ?: continue
                        addToWindowBuckets(hourBuckets.getOrPut(pkg) { LongArray(24) }, s, event.timeStamp, start)
                        if (event.timeStamp - s >= 3_000L) openCount[pkg] = (openCount[pkg] ?: 0) + 1
                    }
                }
            }
            sessionStart.forEach { (pkg, s) ->
                addToWindowBuckets(hourBuckets.getOrPut(pkg) { LongArray(24) }, s, now, start)
                if (now - s >= 3_000L) openCount[pkg] = (openCount[pkg] ?: 0) + 1
            }
        } catch (_: Exception) {
        }
        return hourBuckets.mapValues { (pkg, arr) ->
            AppActivity(pkg, arr.toList(), openCount[pkg] ?: 0, arr.sum())
        }.filterValues { it.totalMillis > 0 }
    }

    private fun addToWindowBuckets(buckets: LongArray, from: Long, to: Long, windowStart: Long) {
        var t = from.coerceAtLeast(windowStart)
        val end = to
        while (t < end) {
            val idx = ((t - windowStart) / HOUR_MILLIS).toInt()
            if (idx < 0 || idx >= buckets.size) break
            val slotEnd = windowStart + (idx + 1) * HOUR_MILLIS
            val segEnd = minOf(end, slotEnd)
            buckets[idx] += (segEnd - t).coerceAtLeast(0L)
            t = segEnd
        }
    }

    /**
     * Uses a short, fixed UsageEvents window so polling stays lightweight. The caller is expected
     * to invoke this every second while the screen is on, and every few seconds when idle.
     */
    fun detectForegroundPackage(): String? {
        val manager = usageStatsManager ?: return null
        val nowEpoch = System.currentTimeMillis()
        val eventWindowMillis = if (foregroundInitialized) 10_000L else 10 * 60 * 1000L
        var foreground = lastKnownForegroundPackage
        try {
            val events = manager.queryEvents(nowEpoch - eventWindowMillis, nowEpoch)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.ACTIVITY_RESUMED -> foreground = event.packageName
                }
            }
            lastKnownForegroundPackage = foreground
            foregroundInitialized = true
        } catch (_: Exception) {
            // Usage access may have been revoked between checks.
        }
        return lastKnownForegroundPackage
    }

    private fun startOfDay(time: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        private const val HOUR_MILLIS = 60L * 60L * 1000L
    }
}
