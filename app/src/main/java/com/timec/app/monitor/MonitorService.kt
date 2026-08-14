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

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        usageRepository = UsageRepository(this)
        powerManager = getSystemService(POWER_SERVICE) as PowerManager
        createChannels()
        serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                latestSettings = settings
                MonitorEngine.updateSettings(settings)
                if (!settings.enabled || settings.selectedPackages.isEmpty()) {
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
        handler.removeCallbacks(tickRunnable)
        serviceScope.cancel()
        MonitorEngine.reset()
        super.onDestroy()
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val screenOn = powerManager.isInteractive
        val foreground = usageRepository.detectForegroundPackage(now)
        val result = MonitorEngine.tick(now, screenOn, foreground)
        handleTickResult(result)
    }

    private fun scheduleNextTick() {
        val screenOn = powerManager.isInteractive
        val activePackage = MonitorEngine.state.value.activePackage
        val delayMillis = when {
            !screenOn -> 5_000L
            activePackage != null -> 1_000L
            else -> 2_000L
        }
        handler.postDelayed(tickRunnable, delayMillis)
    }

    private fun handleTickResult(result: TickResult) {
        result.warningPackages.forEach { packageName ->
            sendWarningNotification(packageName)
        }

        result.finalPackage?.let { packageName ->
            if (!MonitorEngine.isOverlayShowingFor(packageName)) {
                startInterventionOverlay(packageName, OverlayMode.FINAL)
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
            putExtra(InterventionOverlayService.EXTRA_RECOVER_MODE, latestSettings.recoverMode)
            putExtra(InterventionOverlayService.EXTRA_EXTENSIONS_LEFT, MonitorEngine.extensionsLeftFor(packageName))
            putExtra(InterventionOverlayService.EXTRA_EXTENSION_SECONDS, latestSettings.extensionSeconds)
            putExtra(InterventionOverlayService.EXTRA_FRICTION_SECONDS, latestSettings.frictionSeconds)
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
        return Notification.Builder(this, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("还我时间 · 正在守护")
            .setContentText("正在低功耗记录连续使用时间")
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
    }
}

object OverlayMode {
    const val FINAL = "final"
    const val COOLDOWN = "cooldown"
    const val FRICTION = "friction"
}

fun goHome(context: Context) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
