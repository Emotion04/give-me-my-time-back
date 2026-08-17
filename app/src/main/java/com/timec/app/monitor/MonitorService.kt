package com.timec.app.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.timec.app.MainActivity
import com.timec.app.R
import com.timec.app.data.AppSettings
import com.timec.app.data.SettingsRepository
import com.timec.app.data.UsageRepository
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var usageRepository: UsageRepository
    private lateinit var powerManager: PowerManager
    private var latestSettings = AppSettings()

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            scheduleNextTick()
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> handler.removeCallbacks(tickRunnable)
                Intent.ACTION_SCREEN_ON -> {
                    handler.removeCallbacks(tickRunnable)
                    handler.post(tickRunnable)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settingsRepository = SettingsRepository(this)
        usageRepository = UsageRepository(this)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        createChannels()
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, screenReceiver, screenFilter, ContextCompat.RECEIVER_EXPORTED)
        serviceScope.launch(Dispatchers.Main) {
            settingsRepository.settings.collect { settings ->
                latestSettings = settings
                MonitorEngine.updateSettings(settings)
                if (!settings.enabled || (settings.appRules.isEmpty() && !settings.serviceManual)) {
                    stopSelf()
                } else {
                    updateForegroundNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID_MONITOR, buildForegroundNotification())
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        TimerOverlayService.stop(this)
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        handler.removeCallbacks(tickRunnable)
        serviceScope.cancel()
        MonitorEngine.reset()
        super.onDestroy()
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val screenOn = powerManager.isInteractive
        val foreground = usageRepository.detectForegroundPackage()
        val dailyUsage = usageRepository.getTodayUsageByPackageNow()
        val result = MonitorEngine.tick(now, screenOn, foreground, dailyUsage)
        handleTickResult(result)
    }

    private fun scheduleNextTick() {
        val screenOn = powerManager.isInteractive
        if (!screenOn) return // 息屏停止轮询，由屏幕亮起广播恢复
        val activePackage = MonitorEngine.state.value.activePackage
        val delayMillis = if (activePackage != null) 1_000L else 2_000L
        handler.postDelayed(tickRunnable, delayMillis)
    }

    private fun handleTickResult(result: TickResult) {
        result.warningPackages.forEach { packageName ->
            sendWarningNotification(packageName)
        }

        result.dailyExhaustedPackage?.let { packageName ->
            if (!MonitorEngine.isOverlayShowingFor(packageName)) {
                startInterventionOverlay(packageName, OverlayMode.DAILY_EXHAUSTED)
            }
        }

        result.finalPackage?.let { packageName ->
            if (!MonitorEngine.isOverlayShowingFor(packageName)) {
                startInterventionOverlay(packageName, OverlayMode.FINAL)
            }
        }

        result.overdraftExhaustedPackage?.let { packageName ->
            if (!MonitorEngine.isOverlayShowingFor(packageName)) {
                startInterventionOverlay(packageName, OverlayMode.OVERDRAFT_EXHAUSTED)
            }
        }

        result.cooldownPackage?.let { packageName ->
            if (!MonitorEngine.isOverlayShowingFor(packageName)) {
                startInterventionOverlay(
                    packageName,
                    OverlayMode.COOLDOWN,
                    result.cooldownRemainingMillis
                )
            }
        }

        result.frictionPackage?.let { packageName ->
            if (!MonitorEngine.isOverlayShowingFor(packageName)) {
                startInterventionOverlay(packageName, OverlayMode.FRICTION)
            }
        }

        updateWidgetVisibility()
    }

    private fun updateWidgetVisibility() {
        val screenOn = powerManager.isInteractive
        val fg = MonitorEngine.state.value.foregroundPackage
        val self = packageName
        val show = latestSettings.widgetEnabled && screenOn &&
            (latestSettings.widgetAllApps || fg == self || (fg != null && fg in latestSettings.appRules.keys))
        if (show && !TimerOverlayService.overlayVisible) {
            TimerOverlayService.start(this)
        } else if (!show && TimerOverlayService.overlayVisible) {
            TimerOverlayService.stop(this)
        }
    }

    private fun startInterventionOverlay(
        packageName: String,
        mode: String,
        remainingMillis: Long = 0L
    ) {
        MonitorEngine.setOverlayShowing(packageName, mode)
        val intent = Intent(this, InterventionOverlayService::class.java).apply {
            putExtra(InterventionOverlayService.EXTRA_PACKAGE, packageName)
            putExtra(InterventionOverlayService.EXTRA_MODE, mode)
            putExtra(InterventionOverlayService.EXTRA_REMAINING_MILLIS, remainingMillis)
            putExtra(InterventionOverlayService.EXTRA_FRICTION_SECONDS, latestSettings.ruleFor(packageName).frictionSeconds)
            putExtra(InterventionOverlayService.EXTRA_LIMIT_MODE, latestSettings.ruleFor(packageName).mode)
            putExtra(InterventionOverlayService.EXTRA_OVERDRAFT_DELAY_SECONDS, latestSettings.overdraftDelaySeconds)
            putExtra(InterventionOverlayService.EXTRA_FINAL_MESSAGE, latestSettings.finalMessage)
            putExtra(InterventionOverlayService.EXTRA_BACKGROUND_INDEX, latestSettings.overlayBackground)
            putExtra(InterventionOverlayService.EXTRA_EXTENSIONS_LEFT, MonitorEngine.extensionsLeftFor(packageName))
            putExtra(InterventionOverlayService.EXTRA_EXTENSIONS_USED, MonitorEngine.extensionUsedCount(packageName))
        }
        startService(intent)
    }

    private fun createChannels() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR,
                getString(R.string.notification_channel_monitor),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WARNING,
                getString(R.string.notification_channel_warning),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun buildForegroundNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val count = latestSettings.appRules.size
        val bodyText = when {
            count > 0 -> "正在守护 " + count + " 个应用"
            latestSettings.serviceManual -> "悬浮计时窗运行中（未守护应用）"
            else -> "守护已开启，尚未添加守护应用"
        }
        return Notification.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("还我时间 · 正在守护")
            .setContentText(bodyText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_MONITOR, buildForegroundNotification())
    }

    private fun sendWarningNotification(packageName: String) {
        val continueIntent = PendingIntent.getBroadcast(
            this,
            packageName.hashCode(),
            Intent(this, WarningActionReceiver::class.java).apply {
                action = ACTION_CONTINUE
                putExtra(EXTRA_PACKAGE, packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val restIntent = PendingIntent.getBroadcast(
            this,
            packageName.hashCode() + 1,
            Intent(this, WarningActionReceiver::class.java).apply {
                action = ACTION_GO_HOME
                putExtra(EXTRA_PACKAGE, packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_WARNING)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("要不要休息一下？")
            .setContentText("你即将用完本次连续使用时间。")
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher),
                    "继续使用",
                    continueIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher),
                    "休息一下",
                    restIntent
                ).build()
            )
            .build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_WARNING + packageName.hashCode(), notification)
    }

    class WarningActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
            manager.cancel(NOTIFICATION_ID_WARNING + packageName.hashCode())
            if (intent.action == ACTION_GO_HOME) {
                goHome(context)
            }
        }
    }

    companion object {
        @Volatile var isRunning: Boolean = false
        private const val NOTIFICATION_ID_MONITOR = 1001
        private const val NOTIFICATION_ID_WARNING = 2001
        private const val CHANNEL_MONITOR = "monitor"
        private const val CHANNEL_WARNING = "warning"
        private const val ACTION_CONTINUE = "com.timec.app.action.CONTINUE"
        private const val ACTION_GO_HOME = "com.timec.app.action.GO_HOME"
        private const val EXTRA_PACKAGE = "package"

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, MonitorService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }

        fun restart(context: Context) {
            stop(context)
            Handler(Looper.getMainLooper()).postDelayed({
                context.startForegroundService(Intent(context, MonitorService::class.java))
            }, 500L)
        }

        fun showTestOverlay(context: Context) {
            val intent = Intent(context, InterventionOverlayService::class.java).apply {
                putExtra(InterventionOverlayService.EXTRA_PACKAGE, "test")
                putExtra(InterventionOverlayService.EXTRA_MODE, OverlayMode.TEST)
            }
            context.startService(intent)
        }

        fun showFinalOverlayTest(context: Context) {
            val intent = Intent(context, InterventionOverlayService::class.java).apply {
                putExtra(InterventionOverlayService.EXTRA_PACKAGE, "test")
                putExtra(InterventionOverlayService.EXTRA_MODE, OverlayMode.FINAL)
            }
            context.startService(intent)
        }
    }
}

object OverlayMode {
    const val FINAL = "final"
    const val OVERDRAFT_EXHAUSTED = "overdraft_exhausted"
    const val DAILY_EXHAUSTED = "daily_exhausted"
    const val COOLDOWN = "cooldown"
    const val FRICTION = "friction"
    const val TEST = "test"
}

fun goHome(context: Context) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
