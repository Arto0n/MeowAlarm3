package com.meowalarm.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import java.util.Calendar
import org.json.JSONObject

/** Shared in-process state used to notify the WebView when the alarm starts. */
object Ring {
    @Volatile var active = false
    @Volatile var listener: (() -> Unit)? = null
}

object Alarms {
    private const val PREFS = "meow"
    private const val DAILY_ID = 1
    private const val SNOOZE_ID = 2

    fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun alarmPendingIntent(c: Context, id: Int): PendingIntent {
        val i = Intent(c, AlarmService::class.java)
            .setAction(AlarmService.ACTION_RING)
            .putExtra("id", id)
        return PendingIntent.getForegroundService(
            c, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 100,
        Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun setAt(c: Context, at: Long, id: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        val info = AlarmManager.AlarmClockInfo(at, showIntent(c))
        am.setAlarmClock(info, alarmPendingIntent(c, id))
    }

    fun cancel(c: Context, id: Int) {
        c.getSystemService(AlarmManager::class.java).cancel(alarmPendingIntent(c, id))
    }

    fun scheduleDaily(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("on", false)) {
            cancel(c, DAILY_ID)
            return
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.getInt("h", 7).coerceIn(0, 23))
            set(Calendar.MINUTE, p.getInt("m", 0).coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        setAt(c, cal.timeInMillis, DAILY_ID)
    }

    fun cancelSnooze(c: Context) = cancel(c, SNOOZE_ID)

    fun scheduleSnooze(c: Context, seconds: Int) {
        cancel(c, SNOOZE_ID)
        setAt(c, System.currentTimeMillis() + seconds.coerceAtLeast(1) * 1000L, SNOOZE_ID)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Alarms.scheduleDaily(c)
        }
    }
}

/** JavaScript bridge exposed to the bundled WebView as `Android`. */
class Bridge(private val c: Context) {
    /** Native settings are authoritative; a WebView reload must never disable an alarm. */
    @JavascriptInterface
    fun getAlarmState(): String {
        val p = Alarms.prefs(c)
        return JSONObject()
            .put("h", p.getInt("h", 7).coerceIn(0, 23))
            .put("m", p.getInt("m", 0).coerceIn(0, 59))
            .put("on", p.getBoolean("on", false))
            .toString()
    }

    @JavascriptInterface
    fun setAlarm(h: Int, m: Int, on: Boolean) {
        Alarms.prefs(c).edit()
            .putInt("h", h.coerceIn(0, 23))
            .putInt("m", m.coerceIn(0, 59))
            .putBoolean("on", on)
            .apply()
        Alarms.scheduleDaily(c)
        if (!on) Alarms.cancelSnooze(c)
    }

    @JavascriptInterface
    fun pickTime() {
        val activity = c as? MainActivity ?: return
        activity.runOnUiThread { activity.showAlarmTimePicker() }
    }

    @JavascriptInterface
    fun ringIn(sec: Int) = Alarms.scheduleSnooze(c, sec)

    @JavascriptInterface
    fun silence() {
        // Stopping an already-running service is safer than creating a new one.
        c.stopService(Intent(c, AlarmService::class.java))
    }

    @JavascriptInterface
    fun dismiss() {
        silence()
        Alarms.cancelSnooze(c)
    }

    @JavascriptInterface
    fun isRinging(): Boolean = Ring.active
}
