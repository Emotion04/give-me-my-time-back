package com.timec.app.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.timec.app.data.AppSettings
import com.timec.app.data.SettingsRepository
import com.timec.app.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

object TimerMetrics {
    const val APP_SESSION = "app_session"
    const val SCREEN_ON = "screen_on"
    const val TODAY = "today"
    const val WEEK = "week"
    const val MONTH = "month"
    const val PERIOD_CMP = "period_cmp"

    val all = listOf(
        APP_SESSION to "本次使用",
        SCREEN_ON to "本次亮屏连续",
        TODAY to "今日使用",
        WEEK to "本周使用",
        MONTH to "本月使用",
        PERIOD_CMP to "对比上周期"
    )

    /** 默认前缀（可在设置里覆盖，支持留空/emoji） */
    fun defaultPrefix(metric: String): String = when (metric) {
        APP_SESSION -> ""
        SCREEN_ON -> ""
        TODAY -> "今日"
        WEEK -> "本周"
        MONTH -> "本月"
        PERIOD_CMP -> "较"
        else -> ""
    }
}

class TimerOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var powerManager: PowerManager
    private var textView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var usageRepository: UsageRepository

    private var settings = AppSettings()
    private var metricIndex = 0

    private var todayMillis = 0L
    private var weekMillis = 0L
    private var monthMillis = 0L
    private var cmpValue: Float? = null
    private var lastStatsRefresh = 0L
    private var params: WindowManager.LayoutParams? = null
    private var flingRunnable: Runnable? = null
    private var dragging = false
    private var velocityX = 0f
    private var velocityY = 0f
    // 本次使用（悬浮窗本地计时，不依赖守护引擎）
    private var localSessionPackage: String? = null
    private var localSessionMillis = 0L
    private var lastTickRealtime = 0L
    // 本次亮屏连续（悬浮窗本地）
    private var screenOnState = false
    private var screenSessionStart = 0L
    private var screenOffAtRealtime = 0L
    private var screenSessionMillisLocal = 0L
    // 弹性跟随拖动
    private var followTargetX = 0f
    private var followTargetY = 0f
    private var followX = 0f
    private var followY = 0f
    private var followVX = 0f
    private var followVY = 0f
    private var followLoop: Runnable? = null
    private var followRunning = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateLocalTrackers()
            updateText()
            handler.postDelayed(this, (settings.widgetRefreshSeconds * 1000L).coerceAtLeast(500L))
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        settingsRepository = SettingsRepository(this)
        usageRepository = UsageRepository(this)
        serviceScope.launch {
            settingsRepository.settings.collect { s ->
                val metricsChanged = s.widgetMetrics != settings.widgetMetrics
                settings = s
                applyStyle()
                if (metricsChanged) {
                    metricIndex = metricIndex % enabledMetrics().size.coerceAtLeast(1)
                }
                updateText()
            }
        }
        showOverlay()
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showOverlay()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        stopFling()
        stopFollowLoop()
        serviceScope.cancel()
        removeOverlay()
        super.onDestroy()
    }

    private fun enabledMetrics(): List<String> {
        val order = TimerMetrics.all.map { it.first }
        return order.filter { it in settings.widgetMetrics }
    }

    private fun currentMetricId(): String? {
        return if (settings.widgetMode == 1) {
            if (settings.widgetSingleMetric in TimerMetrics.all.map { it.first }) {
                settings.widgetSingleMetric
            } else {
                TimerMetrics.APP_SESSION
            }
        } else {
            enabledMetrics().takeIf { it.isNotEmpty() }?.let { it[metricIndex % it.size] }
        }
    }

    private fun showOverlay() {
        if (textView != null) return

        val tv = TextView(this).apply {
            text = "--:--"
            textSize = settings.widgetFontSize.toFloat()
            setTextColor(currentTextColor())
            gravity = Gravity.CENTER
            val m = dp(settings.widgetMargin)
            setPadding(m, m, m, m)
            background = GradientDrawable().apply {
                setColor(currentBgColor())
                cornerRadius = dp(14).toFloat()
            }
            alpha = settings.widgetOpacity / 100f
        }

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = resources.displayMetrics
            val maxX = (dm.widthPixels - dp(48)).coerceAtLeast(0)
            val minY = dp(40)
            val maxY = (dm.heightPixels - dp(120)).coerceAtLeast(minY)
            x = (if (settings.widgetPosX >= 0) settings.widgetPosX else dm.widthPixels - dp(160)).coerceIn(0, maxX)
            y = (if (settings.widgetPosY >= 0) settings.widgetPosY else dp(90)).coerceIn(minY, maxY)
        }

        var grabDX = 0f
        var grabDY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var totalDx = 0f
        var totalDy = 0f
        var moved = false
        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    grabDX = event.rawX - p.x
                    grabDY = event.rawY - p.y
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    totalDx = 0f
                    totalDy = 0f
                    moved = false
                    dragging = false
                    velocityX = 0f
                    velocityY = 0f
                    followTargetX = p.x.toFloat()
                    followTargetY = p.y.toFloat()
                    followX = p.x.toFloat()
                    followY = p.y.toFloat()
                    followVX = 0f
                    followVY = 0f
                    stopFling()
                    stopFollowLoop()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    totalDx += event.rawX - lastRawX
                    totalDy += event.rawY - lastRawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    if (!moved && Math.abs(totalDx) + Math.abs(totalDy) > dp(3)) {
                        moved = true
                        dragging = true
                    }
                    if (moved) {
                        followTargetX = event.rawX - grabDX
                        followTargetY = event.rawY - grabDY
                        startFollowLoop()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    dragging = false
                    if (moved) {
                        stopFollowLoop()
                        velocityX = followVX
                        velocityY = followVY
                        startFling()
                    } else if (settings.widgetMode == 0) {
                        cycleMetric()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(tv, p)
            textView = tv
            params = p
            overlayVisible = true
        } catch (_: Exception) {
        }
    }

    private fun applyStyle() {
        val tv = textView ?: return
        tv.textSize = settings.widgetFontSize.toFloat()
        tv.alpha = settings.widgetOpacity / 100f
        val m = dp(settings.widgetMargin)
        tv.setPadding(m, m, m, m)
        (tv.background as? GradientDrawable)?.setColor(currentBgColor())
        tv.setTextColor(currentTextColor())
    }

    private fun currentBgColor(): Int = when (settings.widgetBackground) {
        1 -> Color.WHITE
        2 -> Color.TRANSPARENT
        else -> Color.rgb(18, 18, 22)
    }

    private fun currentTextColor(): Int = when (settings.widgetBackground) {
        0 -> Color.WHITE
        1 -> Color.BLACK
        else -> colorForIndex(settings.widgetTextColor)
    }

    private fun colorForIndex(index: Int): Int = if (index == 1) Color.BLACK else Color.WHITE

    private fun cycleMetric() {
        val metrics = enabledMetrics()
        if (metrics.isEmpty()) return
        metricIndex = (metricIndex + 1) % metrics.size
        updateText()
    }

    private fun startFling() {
        stopFling()
        val dm = resources.displayMetrics
        val maxX = (dm.widthPixels - dp(48)).coerceAtLeast(0)
        val minY = dp(40)
        val maxY = (dm.heightPixels - dp(120)).coerceAtLeast(minY)
        val friction = 0.85f + settings.widgetInertia.coerceIn(0, 10) * 0.012f
        val frame = object : Runnable {
            override fun run() {
                val tv = textView ?: return
                val lp = params ?: return
                val vx = velocityX
                val vy = velocityY
                if (Math.abs(vx) + Math.abs(vy) < 60f) {
                    snapToEdge()
                    return
                }
                velocityX = vx * friction
                velocityY = vy * friction
                lp.x += Math.round(vx * 0.016f)
                lp.y += Math.round(vy * 0.016f)
                lp.x = lp.x.coerceIn(0, maxX)
                lp.y = lp.y.coerceIn(minY, maxY)
                if (lp.x <= 0 || lp.x >= maxX) velocityX = 0f
                if (lp.y <= minY || lp.y >= maxY) velocityY = 0f
                try { windowManager.updateViewLayout(tv, lp) } catch (_: Exception) {}
                handler.postDelayed(this, 16L)
            }
        }
        flingRunnable = frame
        handler.post(frame)
    }

    private fun startFollowLoop() {
        if (followRunning) return
        followRunning = true
        val loop = object : Runnable {
            override fun run() {
                if (!followRunning) return
                val tv = textView ?: run { followRunning = false; return }
                val lp = params ?: run { followRunning = false; return }
                val inertia = settings.widgetInertia.coerceIn(0, 10)
                val k = 400f - inertia * 25f
                val zeta = 0.85f
                val c = 2f * Math.sqrt(k.toDouble()).toFloat() * zeta
                val dt = 0.016f
                followVX += (k * (followTargetX - followX) - c * followVX) * dt
                followVY += (k * (followTargetY - followY) - c * followVY) * dt
                followX += followVX * dt
                followY += followVY * dt
                lp.x = Math.round(followX)
                lp.y = Math.round(followY)
                try { windowManager.updateViewLayout(tv, lp) } catch (_: Exception) {}
                handler.postDelayed(this, 16L)
            }
        }
        followLoop = loop
        handler.post(loop)
    }

    private fun stopFollowLoop() {
        followRunning = false
        followLoop?.let { handler.removeCallbacks(it) }
        followLoop = null
    }

    private fun updateLocalTrackers() {
        val nowRt = SystemClock.elapsedRealtime()
        val screenOn = powerManager.isInteractive
        // 亮屏连续（息屏超过阈值则重新计时）
        if (screenOn && !screenOnState) {
            if (screenOffAtRealtime > 0L &&
                nowRt - screenOffAtRealtime >= settings.screenOffResetThresholdSeconds * 1000L
            ) {
                screenSessionStart = nowRt
            } else if (screenOffAtRealtime == 0L) {
                screenSessionStart = nowRt
            }
            screenOnState = true
        } else if (!screenOn && screenOnState) {
            screenOffAtRealtime = nowRt
            screenOnState = false
        }
        if (screenOnState) {
            screenSessionMillisLocal = nowRt - screenSessionStart
        }
        // 本次使用（任意前台应用都计时，切换应用或首次则归零）
        val fg = usageRepository.detectForegroundPackage()
        if (fg != localSessionPackage) {
            localSessionPackage = fg
            localSessionMillis = 0L
        }
        if (screenOn && fg != null && fg == localSessionPackage && lastTickRealtime > 0L) {
            localSessionMillis += (nowRt - lastTickRealtime).coerceAtLeast(0L)
        }
        lastTickRealtime = nowRt
    }

    private fun snapToEdge() {
        stopFling()
        val tv = textView ?: return
        val lp = params ?: return
        val dm = resources.displayMetrics
        val maxX = (dm.widthPixels - dp(48)).coerceAtLeast(0)
        val targetX = if (lp.x + tv.width / 2 < dm.widthPixels / 2) 0 else maxX
        val startX = lp.x
        val duration = 260L
        val startTime = System.currentTimeMillis()
        val anim = object : Runnable {
            override fun run() {
                val cur = params ?: return
                val t = (System.currentTimeMillis() - startTime).toFloat() / duration.toFloat()
                val eased = if (t >= 1f) 1f else 1f - (1f - t) * (1f - t) * (1f - t)
                cur.x = Math.round(startX + (targetX - startX) * eased)
                try { windowManager.updateViewLayout(tv, cur) } catch (_: Exception) {}
                if (t < 1f) {
                    handler.postDelayed(this, 16L)
                } else {
                    flingRunnable = null
                    persistPosition(cur.x, cur.y)
                }
            }
        }
        flingRunnable = anim
        handler.post(anim)
    }

    private fun stopFling() {
        flingRunnable?.let { handler.removeCallbacks(it) }
        flingRunnable = null
    }

    private fun persistPosition(x: Int, y: Int) {
        serviceScope.launch { settingsRepository.setWidgetPosition(x, y) }
    }

    private fun updateText() {
        if (dragging) return
        val tv = textView ?: return
        val id = currentMetricId()
        if (id == null) {
            tv.text = "--:--"
            return
        }
        refreshStatsIfNeeded()
        tv.text = textFor(id)
    }

    private fun refreshStatsIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastStatsRefresh < 30_000L) return
        lastStatsRefresh = now
        serviceScope.launch(Dispatchers.Default) {
            val t = usageRepository.getTotalTodayNow()
            val w = usageRepository.getTotalWeekNow()
            val mo = usageRepository.getTotalMonthNow()
            val c = usageRepository.getPeriodComparison(settings.widgetComparePeriod)
            handler.post {
                todayMillis = t
                weekMillis = w
                monthMillis = mo
                cmpValue = c
            }
        }
    }

    private fun textFor(metric: String): String {
        return when (metric) {
            TimerMetrics.APP_SESSION ->
                withPrefix(metric, formatTimer(localSessionMillis))
            TimerMetrics.SCREEN_ON ->
                withPrefix(metric, formatTimer(screenSessionMillisLocal))
            TimerMetrics.TODAY -> withPrefix(metric, formatDurationShort(todayMillis))
            TimerMetrics.WEEK -> withPrefix(metric, formatDurationShort(weekMillis))
            TimerMetrics.MONTH -> withPrefix(metric, formatDurationShort(monthMillis))
            TimerMetrics.PERIOD_CMP -> {
                val v = cmpValue
                val periodName = when (settings.widgetComparePeriod) {
                    1 -> "上周"
                    2 -> "上月"
                    else -> "昨日"
                }
                val prefix = prefixFor(metric)
                if (v == null) prefix + periodName + " 暂无数据"
                else prefix + periodName + " " + (if (v >= 0f) "+" else "") + Math.round(v * 100) + "%"
            }
            else -> "--:--"
        }
    }

    private fun prefixFor(metric: String): String {
        val custom = settings.widgetMetricPrefixes[metric]
        return if (custom != null) custom else TimerMetrics.defaultPrefix(metric)
    }

    private fun withPrefix(metric: String, value: String): String {
        val prefix = prefixFor(metric)
        return if (prefix.isEmpty()) value else prefix + " " + value
    }

    private fun formatTimer(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) h.toString() + "h" + pad2(m) + "m" else m.toString() + "m" + pad2(s) + "s"
    }

    private fun pad2(v: Long): String = if (v < 10) "0" + v else v.toString()

    private fun formatDurationShort(millis: Long): String {
        val totalMinutes = millis / 60_000L
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) h.toString() + "h" + pad2(m) + "m" else m.toString() + "m"
    }

    private fun removeOverlay() {
        textView?.let { tv ->
            try { windowManager.removeView(tv) } catch (_: Exception) {}
        }
        textView = null
        overlayVisible = false
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        @Volatile
        var overlayVisible = false

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                context.startService(Intent(context, TimerOverlayService::class.java))
            } else {
                android.widget.Toast.makeText(context, "请先授予“悬浮窗”权限", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimerOverlayService::class.java))
        }
    }
}
