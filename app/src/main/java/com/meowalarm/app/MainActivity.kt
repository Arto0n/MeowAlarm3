package com.meowalarm.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        web = WebView(this)
        setContentView(web)
        web.setBackgroundColor(0xFF8FD3F4.toInt())
        web.webViewClient = WebViewClient()
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = false
        }
        web.addJavascriptInterface(Bridge(this), "Android")
        web.loadUrl("file:///android_asset/index.html")
        askPermissions()
    }

    /** Only wake/hold the screen while an alarm is actually ringing. */
    private fun updateAlarmWindow(ringing: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(ringing)
            setTurnScreenOn(ringing)
        } else {
            @Suppress("DEPRECATION")
            val legacyFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (ringing) window.addFlags(legacyFlags) else window.clearFlags(legacyFlags)
        }
        if (ringing) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun askPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }

        // Android 14+ can restrict full-screen notifications. Alarm-clock apps are
        // an allowed use case, but the user may still need to grant it in Settings.
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        }
    }

    private fun syncRingJs() {
        if (!::web.isInitialized) return
        val js = if (Ring.active) "window.nativeRing&&window.nativeRing()"
                 else "window.nativeStopped&&window.nativeStopped()"
        web.evaluateJavascript(js, null)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateAlarmWindow(Ring.active)
        if (Ring.active) syncRingJs()
    }

    override fun onResume() {
        super.onResume()
        Ring.listener = { runOnUiThread {
            updateAlarmWindow(Ring.active)
            syncRingJs()
        } }
        updateAlarmWindow(Ring.active)
        if (Ring.active) syncRingJs()
    }

    override fun onPause() {
        Ring.listener = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onPause()
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
