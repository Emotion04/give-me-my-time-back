package com.timec.app.monitor

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.timec.app.data.RecoverMode

class InterventionOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var rootView: LinearLayout? = null
    private var countdownView: TextView? = null
    private var packageName: String = ""
    private var mode: String = OverlayMode.FINAL
    private var remainingMillis: Long = 0L
    private var recoverMode: Int = RecoverMode.RECHARGE
    private var extensionsLeft: Int = 0
    private var extensionSeconds: Int = 60
    private var frictionSeconds: Int = 4
    private val handler = Handler(Looper.getMainLooper())

    private val countdownRunnable = object : Runnable {
        override fun run() {
            remainingMillis -= 1_000L
            if (remainingMillis <= 0L) {
                if (mode == OverlayMode.FRICTION || mode == OverlayMode.COOLDOWN) {
                    finishOverlay(goHome = false)
                }
                return
            }
            countdownView?.text = formatRemaining(remainingMillis)
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        packageName = intent?.getStringExtra(EXTRA_PACKAGE).orEmpty()
        mode = intent?.getStringExtra(EXTRA_MODE) ?: OverlayMode.FINAL
        remainingMillis = intent?.getLongExtra(EXTRA_REMAINING_MILLIS, 0L) ?: 0L
        recoverMode = intent?.getIntExtra(EXTRA_RECOVER_MODE, RecoverMode.RECHARGE) ?: RecoverMode.RECHARGE
        extensionsLeft = intent?.getIntExtra(EXTRA_EXTENSIONS_LEFT, 0) ?: 0
        extensionSeconds = intent?.getIntExtra(EXTRA_EXTENSION_SECONDS, 60) ?: 60
        frictionSeconds = intent?.getIntExtra(EXTRA_FRICTION_SECONDS, 4) ?: 4
        if (packageName.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (mode == OverlayMode.FRICTION) {
            remainingMillis = frictionSeconds * 1000L
        }
        showOverlay()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(countdownRunnable)
        removeOverlay()
        MonitorEngine.clearOverlay(packageName)
        super.onDestroy()
    }

    private fun showOverlay() {
        if (rootView != null) return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(40), dp(32), dp(40))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(27, 42, 65), Color.rgb(45, 82, 96))
            ).apply { cornerRadius = dp(24).toFloat() }
            isClickable = true
        }

        val title = TextView(this).apply {
            text = when (mode) {
                OverlayMode.FRICTION -> "先停一下"
                OverlayMode.FINAL -> "本次额度用完了"
                else -> "给自己一分钟"
            }
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val message = TextView(this).apply {
            text = when (mode) {
                OverlayMode.FRICTION -> "吸气……呼气……\n你真的需要现在打开吗？"
                OverlayMode.FINAL -> "你不是在被惩罚，而是在拿回选择权。"
                else -> "你选择了冷却。结束后可以重新开始。"
            }
            textSize = 17f
            setTextColor(Color.parseColor("#E8F1F8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
        }
        root.addView(message, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        if (mode == OverlayMode.FRICTION || mode == OverlayMode.COOLDOWN) {
            countdownView = TextView(this).apply {
                text = formatRemaining(remainingMillis)
                textSize = 42f
                setTextColor(Color.parseColor("#7FD1AE"))
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            root.addView(countdownView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        when (mode) {
            OverlayMode.FRICTION -> {
                root.addView(createButton("跳过等待，继续使用", Color.rgb(127, 209, 174)) {
                    finishOverlay(goHome = false)
                })
            }
            OverlayMode.FINAL -> {
                if (extensionsLeft > 0) {
                    root.addView(createButton("延长 " + formatExtend(extensionSeconds), Color.rgb(127, 209, 174)) {
                        val result = MonitorEngine.choose(packageName, FinalChoice.EXTEND)
                        finishOverlay(result.goHome)
                    })
                }
                if (recoverMode == RecoverMode.RECHARGE) {
                    root.addView(createButton("跳过（透支继续）", Color.rgb(230, 183, 106)) {
                        val result = MonitorEngine.choose(packageName, FinalChoice.SKIP)
                        finishOverlay(result.goHome)
                    })
                    root.addView(createButton("确定，返回桌面", Color.rgb(219, 104, 104)) {
                        val result = MonitorEngine.choose(packageName, FinalChoice.CONFIRM)
                        finishOverlay(result.goHome)
                    })
                } else {
                    root.addView(createButton("确定，返回桌面并冷却", Color.rgb(219, 104, 104)) {
                        val result = MonitorEngine.choose(packageName, FinalChoice.CONFIRM)
                        finishOverlay(result.goHome)
                    })
                }
            }
            else -> {
                root.addView(createButton("返回桌面", Color.rgb(127, 209, 174)) {
                    finishOverlay(goHome = true)
                })
            }
        }

        rootView = root
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(root, params)
            if (mode == OverlayMode.FRICTION || mode == OverlayMode.COOLDOWN) {
                handler.post(countdownRunnable)
            }
        } catch (_: Exception) {
            rootView = null
            MonitorEngine.clearOverlay(packageName)
            stopSelf()
        }
    }

    private fun createButton(label: String, color: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        }
    }

    private fun finishOverlay(goHome: Boolean) {
        removeOverlay()
        MonitorEngine.clearOverlay(packageName)
        if (goHome) goHome(this)
        stopSelf()
    }

    private fun removeOverlay() {
        rootView?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
        rootView = null
    }

    private fun formatRemaining(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0L) / 1000L).toInt()
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun formatExtend(seconds: Int): String {
        return if (seconds % 60 == 0) (seconds / 60).toString() + " 分钟" else seconds.toString() + " 秒"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_MODE = "mode"
        const val EXTRA_REMAINING_MILLIS = "remaining_millis"
        const val EXTRA_RECOVER_MODE = "recover_mode"
        const val EXTRA_EXTENSIONS_LEFT = "extensions_left"
        const val EXTRA_EXTENSION_SECONDS = "extension_seconds"
        const val EXTRA_FRICTION_SECONDS = "friction_seconds"
    }
}
