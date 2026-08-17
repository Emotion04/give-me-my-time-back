package com.timec.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.timec.app.ui.App
import com.timec.app.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private var overlayWarned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            App()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.ensureWidget()
        if (!overlayWarned && viewModel.settings.value.widgetEnabled &&
            !Settings.canDrawOverlays(this)
        ) {
            overlayWarned = true
            Toast.makeText(this, "悬浮计时窗需要“悬浮窗”权限，请到设置页「权限与后台」授权", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
