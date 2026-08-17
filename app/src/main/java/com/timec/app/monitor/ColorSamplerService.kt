package com.timec.app.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.timec.app.R

class ColorSamplerService : Service() {
    override fun onCreate() {
        super.onCreate()
        ScreenColorSampler.init(applicationContext)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "守护与悬浮窗", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        ScreenColorSampler.activate(resultCode, resultData)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ScreenColorSampler.release()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("还我时间 · 悬浮窗自动对比")
            .setContentText("正在检测屏幕颜色以自动调整文字颜色")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL = "monitor"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, resultData: Intent?) {
            val intent = Intent(context, ColorSamplerService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                if (resultData != null) putExtra(EXTRA_RESULT_DATA, resultData)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ColorSamplerService::class.java))
        }
    }
}
