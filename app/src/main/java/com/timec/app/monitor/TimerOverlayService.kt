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
    private var lastAutoColor: Int? = null
    private var lastAutoSampleAt = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            maybeSampleAuto()
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
        serviceScope.cancel()
        ColorSamplerService.stop(this)
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

        var downRawX = 0f
        var downRawY = 0f
        var startX = p.x
        var startY = p.y
        var moved = false
        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = p.x
                    startY = p.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (Math.abs(dx) + Math.abs(dy) > 20) moved = true
                    if (moved) {
                        p.x = startX + dx
                        p.y = startY + dy
                        try { windowManager.updateViewLayout(tv, p) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        serviceScope.launch { settingsRepository.setWidgetPosition(p.x, p.y) }
                        lastAutoSampleAt = 0L
                        maybeSampleAuto()
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
        if (settings.widgetBackground != 3) {
            ColorSamplerService.stop(this)
        }
    }

    private fun currentBgColor(): Int = when (settings.widgetBackground) {
        1 -> Color.WHITE
        2, 3 -> Color.TRANSPARENT
        else -> Color.rgb(18, 18, 22)
    }

    private fun currentTextColor(): Int = when (settings.widgetBackground) {
        0 -> Color.WHITE
        1 -> Color.BLACK
        3 -> lastAutoColor ?: colorForIndex(settings.widgetTextColor)
        else -> colorForIndex(settings.widgetTextColor)
    }

    private fun colorForIndex(index: Int): Int = if (index == 1) Color.BLACK else Color.WHITE

    private fun maybeSampleAuto() {
        if (settings.widgetBackground != 3) return
        if (!ScreenColorSampler.isActive) return
        val now = System.currentTimeMillis()
        if (now - lastAutoSampleAt < 1_000L) return
        val tv = textView ?: return
        val lp = params ?: return
        val pad = dp(16)
        val w = (tv.width + 2 * pad).coerceAtLeast(2)
        val h = (tv.height + 2 * pad).coerceAtLeast(2)
        val avg = ScreenColorSampler.sampleAverage(lp.x - pad, lp.y - pad, w, h) ?: return
        lastAutoSampleAt = now
        val lum = 0.299f * Color.red(avg) + 0.587f * Color.green(avg) + 0.114f * Color.blue(avg)
        val color = if (lum > 128f) Color.BLACK else Color.WHITE
        if (color != lastAutoColor) {
            lastAutoColor = color
            tv.setTextColor(color)
        }
    }

    private fun cycleMetric() {
        val metrics = enabledMetrics()
        if (metrics.isEmpty()) return
        metricIndex = (metricIndex + 1) % metrics.size
        updateText()
    }

    private fun updateText() {
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
        todayMillis = usageRepository.getTotalTodayNow()
        weekMillis = usageRepository.getTotalWeekNow()
        monthMillis = usageRepository.getTotalMonthNow()
        cmpValue = usageRepository.getPeriodComparison(settings.widgetComparePeriod)
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
