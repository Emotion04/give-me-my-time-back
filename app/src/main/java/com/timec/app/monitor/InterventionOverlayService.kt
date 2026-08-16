package com.timec.app.monitor

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class InterventionOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var rootView: LinearLayout? = null
    private var countdownView: TextView? = null
    private var packageName: String = ""
    private var mode: String = OverlayMode.FINAL
    private var remainingMillis: Long = 0L
    private var frictionSeconds: Int = 4
    private var limitMode: Int = 1
    private var overdraftDelaySeconds: Int = 0
    private var extensionsLeft: Int = -1
    private var extensionsUsed: Int = 0
    private var finalMessage: String = "你不是在被惩罚，而是在拿回选择权。"
    private var backgroundIndex: Int = 0
    private var continueButton: Button? = null
    private var continueDelayRemaining: Int = 0
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

    private val continueDelayRunnable = object : Runnable {
        override fun run() {
            continueDelayRemaining -= 1
            if (continueDelayRemaining <= 0) {
                continueButton?.isEnabled = true
                continueButton?.text = "继续（透支）"
            } else {
                continueButton?.text = "继续（" + continueDelayRemaining + "秒后）"
                handler.postDelayed(this, 1_000L)
            }
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
        frictionSeconds = intent?.getIntExtra(EXTRA_FRICTION_SECONDS, 4) ?: 4
        limitMode = intent?.getIntExtra(EXTRA_LIMIT_MODE, 1) ?: 1
        overdraftDelaySeconds = intent?.getIntExtra(EXTRA_OVERDRAFT_DELAY_SECONDS, 0) ?: 0
        extensionsLeft = intent?.getIntExtra(EXTRA_EXTENSIONS_LEFT, -1) ?: -1
        extensionsUsed = intent?.getIntExtra(EXTRA_EXTENSIONS_USED, 0) ?: 0
        finalMessage = intent?.getStringExtra(EXTRA_FINAL_MESSAGE) ?: "你不是在被惩罚，而是在拿回选择权。"
        backgroundIndex = intent?.getIntExtra(EXTRA_BACKGROUND_INDEX, 0) ?: 0
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
        handler.removeCallbacks(continueDelayRunnable)
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
                backgroundColors(backgroundIndex)
            ).apply { cornerRadius = dp(24).toFloat() }
            isClickable = true
        }

        val title = TextView(this).apply {
            text = when (mode) {
                OverlayMode.FRICTION -> "先停一下"
                OverlayMode.FINAL -> "本次额度用完了"
                OverlayMode.OVERDRAFT_EXHAUSTED -> "透支已达极限"
                OverlayMode.DAILY_EXHAUSTED -> "今日额度已用完"
                OverlayMode.TEST -> "悬浮窗正常"
                else -> "给自己一段时间"
            }
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val message = TextView(this).apply {
            text = when (mode) {
                OverlayMode.FRICTION -> "吸气……呼气……\n你真的需要现在打开吗？"
                OverlayMode.FINAL -> if (limitMode == 0) extensionMessage(extensionsUsed) else finalMessage
                OverlayMode.OVERDRAFT_EXHAUSTED -> "继续透支的代价会越来越大。"
                OverlayMode.DAILY_EXHAUSTED -> "今天已经用够多了，明天再来吧。"
                OverlayMode.TEST -> "能看到这个页面，说明悬浮窗权限和渲染都正常。"
                else -> "现在可以继续等待，也可以透支使用（代价更大）。"
            }
            textSize = 17f
            setTextColor(Color.parseColor("#E8F1F8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
        }
        root.addView(message, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
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
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        when (mode) {
            OverlayMode.FRICTION -> {
                root.addView(createButton("跳过等待，继续使用", Color.rgb(127, 209, 174)) {
                    finishOverlay(goHome = false)
                })
            }
            OverlayMode.FINAL -> {
                if (extensionsLeft != 0) {
                    val extendLabel = if (extensionsLeft > 0 && limitMode == 0) "加一分钟（剩" + extensionsLeft + "次）" else "加一分钟"
                    root.addView(createButton(extendLabel, Color.rgb(127, 209, 174)) {
                        val r = MonitorEngine.choose(packageName, FinalChoice.EXTEND)
                        finishOverlay(r.goHome)
                    })
                }
                if (limitMode == 1) {
                    val btn = createButton("继续（透支）", Color.rgb(230, 183, 106)) {
                        val r = MonitorEngine.choose(packageName, FinalChoice.CONTINUE)
                        finishOverlay(r.goHome)
                    }
                    continueButton = btn
                    root.addView(btn)
                    if (overdraftDelaySeconds > 0) {
                        continueDelayRemaining = overdraftDelaySeconds
                        btn.isEnabled = false
                        btn.text = "继续（" + overdraftDelaySeconds + "秒后）"
                        handler.post(continueDelayRunnable)
                    }
                }
                root.addView(createButton("确定，返回桌面", Color.rgb(219, 104, 104)) {
                    val r = MonitorEngine.choose(packageName, FinalChoice.CONFIRM)
                    finishOverlay(r.goHome)
                })
            }
            OverlayMode.OVERDRAFT_EXHAUSTED -> {
                root.addView(createButton("冷却期透支（代价更大）", Color.rgb(230, 183, 106)) {
                    val r = MonitorEngine.chooseOverdraft(packageName, OverdraftChoice.COOLDOWN_OVERDRAFT)
                    finishOverlay(r.goHome)
                })
                root.addView(createButton("确定，返回桌面", Color.rgb(219, 104, 104)) {
                    val r = MonitorEngine.chooseOverdraft(packageName, OverdraftChoice.CONFIRM)
                    finishOverlay(r.goHome)
                })
            }
            OverlayMode.DAILY_EXHAUSTED -> {
                root.addView(createButton("返回桌面", Color.rgb(219, 104, 104)) {
                    finishOverlay(goHome = true)
                })
            }
            OverlayMode.TEST -> {
                root.addView(createButton("关闭", Color.rgb(127, 209, 174)) {
                    finishOverlay(goHome = false)
                })
            }
            else -> {
                // COOLDOWN：时间银行模式下支持透支使用
                if (limitMode == 1) {
                    root.addView(createButton("透支使用（代价更大）", Color.rgb(230, 183, 106)) {
                        MonitorEngine.chooseCooldownOverdraft(packageName)
                        finishOverlay(goHome = false)
                    })
                }
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        try {
            windowManager.addView(root, params)
            Log.d("TimeGuard", "overlay shown mode=" + mode + " pkg=" + packageName)
            if (mode == OverlayMode.FRICTION || mode == OverlayMode.COOLDOWN) {
                handler.post(countdownRunnable)
            }
        } catch (e: Exception) {
            Log.e("TimeGuard", "overlay addView failed: " + e.message)
            Toast.makeText(this, "悬浮窗显示失败，请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
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
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
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

    private fun extensionMessage(used: Int): String = when {
        used <= 0 -> "时间到了，休息一下吧。"
        used == 1 -> "已经延长一次了，差不多该放下了。"
        used == 2 -> "第 3 次了！还在刷？"
        used == 3 -> "已经是第 4 次了！眼睛不累吗？"
        used == 4 -> "第 5 次！！手指停不下来了吗？"
        used == 5 -> "第 6 次了！！！快放下手机！"
        else -> "第 " + (used + 1) + " 次了！！！真的够了，快去干点别的！"
    }

    private fun backgroundColors(index: Int): IntArray = when (index) {
        1 -> intArrayOf(Color.rgb(18, 45, 40), Color.rgb(34, 82, 68))
        2 -> intArrayOf(Color.rgb(38, 26, 58), Color.rgb(82, 52, 108))
        3 -> intArrayOf(Color.rgb(20, 20, 22), Color.rgb(50, 50, 54))
        else -> intArrayOf(Color.rgb(27, 42, 65), Color.rgb(45, 82, 96))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_MODE = "mode"
        const val EXTRA_REMAINING_MILLIS = "remaining_millis"
        const val EXTRA_FRICTION_SECONDS = "friction_seconds"
        const val EXTRA_LIMIT_MODE = "limit_mode"
        const val EXTRA_OVERDRAFT_DELAY_SECONDS = "overdraft_delay_seconds"
        const val EXTRA_FINAL_MESSAGE = "final_message"
        const val EXTRA_BACKGROUND_INDEX = "background_index"
        const val EXTRA_EXTENSIONS_LEFT = "extensions_left"
        const val EXTRA_EXTENSIONS_USED = "extensions_used"
    }
}
