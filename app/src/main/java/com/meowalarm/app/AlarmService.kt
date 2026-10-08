package com.meowalarm.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.*
import kotlin.math.*

class AlarmService : Service() {
    companion object {
        const val ACTION_RING = "com.meowalarm.app.action.RING"
        const val ACTION_STOP = "com.meowalarm.app.action.STOP"
        private const val CHANNEL_ID = "alarm_ring"
        private const val NOTIFICATION_ID = 1001
    }

    private var track: AudioTrack? = null
    private var meowPlayer: MediaPlayer? = null
    private var vol = 0.8f
    private val handler = Handler(Looper.getMainLooper())
    private val ramp = object : Runnable {
        override fun run() {
            if (track == null) return
            vol = min(1f, vol + 0.05f)
            runCatching { track?.setVolume(vol) }
            handler.postDelayed(this, 2500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRing()
            stopSelf()
            return START_NOT_STICKY
        }

        // id=1 is the normal daily alarm. Arm tomorrow before starting playback.
        if (intent?.getIntExtra("id", 0) == 1) Alarms.scheduleDaily(this)

        // The service is started directly from AlarmManager's exact alarm PendingIntent,
        // which is the reliable background entry point for an alarm clock.
        goForeground()
        startSound()
        Ring.active = true
        Ring.listener?.invoke()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Meow alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("میو میو! ⏰")
            .setContentText("گربه رو ناز کن تا ساکت بشه")
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            // This type exists on Android 14+ and applies to qualifying alarm apps.
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun startSound() {
        if (meowPlayer != null || track != null) return

        // Play an offline recording of a real cat through Android's ALARM audio stream.
        // The recording is bundled in res/raw by install-real-meow.ps1.
        var candidate: MediaPlayer? = null
        try {
            val alarmAudio = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            candidate = MediaPlayer.create(this, R.raw.real_cat_meow, alarmAudio, 0)
                ?: error("Could not decode bundled cat-meowing audio")
            candidate.isLooping = true
            candidate.setVolume(1f, 1f)
            candidate.setOnErrorListener { failedPlayer, what, extra ->
                android.util.Log.e("MeowAlarm", "Real cat audio error ($what, $extra); trying synthesized meow")
                if (meowPlayer === failedPlayer) meowPlayer = null
                runCatching { failedPlayer.release() }
                startSynthesizedMeow()
                true
            }
            candidate.start()
            meowPlayer = candidate
            candidate = null  // service now owns the MediaPlayer
        } catch (ex: Exception) {
            android.util.Log.e("MeowAlarm", "Unable to play recorded cat audio", ex)
            startSynthesizedMeow()
        } finally {
            runCatching { candidate?.release() }
        }

        // Continue vibrating alongside the meows, including when the screen is locked.
        val vibrator = getSystemService(Vibrator::class.java)
        if (vibrator?.hasVibrator() == true) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 400, 300), 0)
            if (Build.VERSION.SDK_INT >= 31) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(effect)
            }
        }
    }

    /** Emergency audio fallback: a synthetic meow, never the phone's default ringtone. */
    private fun startSynthesizedMeow() {
        if (track != null || meowPlayer != null) return
        val sampleRate = 22050
        val samples = Meow.make(sampleRate)
        var candidate: AudioTrack? = null
        try {
            candidate = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            check(candidate.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack not initialized" }
            check(candidate.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING) == samples.size) {
                "Could not write full alarm audio buffer"
            }
            candidate.setLoopPoints(0, samples.size, -1)
            vol = 0.8f
            candidate.setVolume(vol)
            candidate.play()
            track = candidate
            candidate = null // ownership moves to the service
            handler.removeCallbacks(ramp)
            handler.postDelayed(ramp, 1500)
        } catch (ex: Exception) {
            android.util.Log.e("MeowAlarm", "Unable to start synthesized meow alarm audio", ex)
            android.util.Log.e("MeowAlarm", "Synthesized meow also failed; alarm will still vibrate")
        } finally {
            // A failed AudioTrack must not leak its underlying native audio resources.
            runCatching { candidate?.release() }
        }

    }

    private fun stopRing() {
        handler.removeCallbacks(ramp)
        track?.let { runCatching { it.pause() }; runCatching { it.flush() }; runCatching { it.release() } }
        track = null
        meowPlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
        meowPlayer = null
        getSystemService(Vibrator::class.java)?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Ring.active = false
        Ring.listener?.invoke()
    }

    override fun onDestroy() {
        stopRing()
        super.onDestroy()
    }
}

object Meow {
    fun make(sr: Int): ShortArray {
        val out = ShortArray((sr * 1.9).toInt())
        val dur = 0.7
        var ph = 0.0
        for (i in 0 until (sr * dur).toInt()) {
            val t = i / sr.toDouble()
            val u = t / dur
            val f0 = when {
                u < .25 -> 420 + 300 * (u / .25)
                u < .57 -> 720 - 120 * ((u - .25) / .32)
                else -> 600 - 260 * ((u - .57) / .43)
            }
            val sw = sin(PI * min(1.0, u * 1.2))
            val f1 = 600 + 400 * sw
            val f2 = 1800 + 700 * sw
            ph += 2 * PI * (f0 + 10 * sin(2 * PI * 7 * t)) / sr
            var s = 0.0
            for (k in 1..24) {
                val fk = k * f0
                if (fk > 6000) break
                val a = exp(-((fk - f1) / 260).pow(2)) + .6 * exp(-((fk - f2) / 520).pow(2))
                s += a * sin(k * ph) / k.toDouble().pow(.2)
            }
            val env = min(1.0, t / .08) * min(1.0, (dur - t) / .12)
            out[i] = (s * env * 2600).coerceIn(-32767.0, 32767.0).toInt().toShort()
        }
        // Original synthesis can be too quiet on phone speakers. Normalize
        // the waveform without clipping; Android's alarm stream sets final loudness.
        val peak = out.maxOfOrNull { kotlin.math.abs(it.toInt()) } ?: 0
        if (peak > 0) {
            val gain = 28000.0 / peak
            for (i in out.indices) {
                out[i] = (out[i].toDouble() * gain)
                    .coerceIn(-30000.0, 30000.0).toInt().toShort()
            }
        }
        return out
    }
}
