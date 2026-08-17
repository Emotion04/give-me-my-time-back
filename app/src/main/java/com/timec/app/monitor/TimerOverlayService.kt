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
}

class TimerOverlayService : Service() {
    private lateinit var windowManager: WindowManager
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

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateText()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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

        var lastRawX = 0f
        var lastRawY = 0f
        var totalDx = 0f
        var totalDy = 0f
        var moved = false
        var lastMoveTime = System.currentTimeMillis()
        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    totalDx = 0f
                    totalDy = 0f
                    moved = false
                    dragging = false
                    lastMoveTime = System.currentTimeMillis()
                    velocityX = 0f
                    velocityY = 0f
                    stopFling()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val now = System.currentTimeMillis()
                    val dt = (now - lastMoveTime).coerceAtLeast(1L)
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    totalDx += dx
                    totalDy += dy
                    if (!moved && Math.abs(totalDx) + Math.abs(totalDy) > dp(8)) {
                        moved = true
                        dragging = true
                    }
                    if (moved) {
                        p.x += Math.round(dx)
                        p.y += Math.round(dy)
                        try { windowManager.updateViewLayout(tv, p) } catch (_: Exception) {}
                        velocityX = 0.6f * velocityX + 0.4f * (dx.toFloat() * 1000f / dt)
                        velocityY = 0.6f * velocityY + 0.4f * (dy.toFloat() * 1000f / dt)
                    }
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    lastMoveTime = now
                    true
                }
                MotionEvent.ACTION_UP -> {
                    dragging = false
                    if (moved) {
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
                velocityX = vx * 0.90f
                velocityY = vy * 0.90f
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
                formatTimer(MonitorEngine.state.value.activeSnapshot?.sessionActiveMillis ?: 0L)
            TimerMetrics.SCREEN_ON ->
                formatTimer(MonitorEngine.state.value.screenOnSessionMillis)
            TimerMetrics.TODAY -> "今日 " + formatDurationShort(todayMillis)
            TimerMetrics.WEEK -> "本周 " + formatDurationShort(weekMillis)
            TimerMetrics.MONTH -> "本月 " + formatDurationShort(monthMillis)
            TimerMetrics.PERIOD_CMP -> {
                val v = cmpValue
                val periodName = when (settings.widgetComparePeriod) {
                    1 -> "上周"
                    2 -> "上月"
                    else -> "昨日"
                }
                if (v == null) "较" + periodName + " 暂无数据"
                else "较" + periodName + " " + (if (v >= 0f) "+" else "") + Math.round(v * 100) + "%"
            }
            else -> "--:--"
        }
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
