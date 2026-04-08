package com.jnu.hardvocabguard.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jnu.hardvocabguard.R
import com.jnu.hardvocabguard.core.AppConstants
import com.jnu.hardvocabguard.data.SettingsStore
import com.jnu.hardvocabguard.ui.alarm.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 违规报警前台服务：负责持续响铃 + 震动，并弹出全屏报警页。
 *
 * 合规说明：
 * - 普通应用无法保证“绝对无法静音/无法关闭”。这里通过：
 *   1) Alarm Stream + AudioFocus
 *   2) 尝试拉满闹钟音量（需要 MODIFY_AUDIO_SETTINGS，普通权限）
 *   3) 前台服务常驻
 *   达到“尽力而为”的强提醒效果。
 */
class AlarmForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: SettingsStore

    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAlarm()
            }
            ACTION_STOP -> {
                stopAlarm()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                scope.launch {
                    val state = settings.supervisionStateFlow().first()
                    if (state.active && state.alarmActive && !state.isGoalReached()) {
                        startAlarm()
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlarm() {
        startForeground(
            NOTIF_ID,
            buildAlarmNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        startVibration()
        startSound()
        launchAlarmActivity()

        scope.launch {
            while (true) {
                val state = settings.supervisionStateFlow().first()
                if (!state.active || !state.alarmActive) {
                    stopAlarm()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }
                delay(1_000L)
            }
        }
    }

    private fun stopAlarm() {
        stopVibration()
        stopSound()
    }

    private fun startVibration() {
        val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vm.defaultVibrator
        val pattern = longArrayOf(0, 800, 200, 800)
        val effect = VibrationEffect.createWaveform(pattern, 0)
        vibrator.vibrate(effect)
    }

    private fun stopVibration() {
        val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator.cancel()
    }

    private fun startSound() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }

        val alarmUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            ?: Uri.EMPTY

        val p = MediaPlayer()
        runCatching {
            p.setDataSource(this, alarmUri)
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            p.isLooping = true
            p.prepare()
            p.start()
            player = p
        }.onFailure {
            runCatching { p.release() }
        }
    }

    private fun stopSound() {
        val p = player
        player = null
        if (p != null) {
            runCatching { p.stop() }
            runCatching { p.release() }
        }
    }

    private fun launchAlarmActivity() {
        val intent = Intent(this, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }

    private fun buildAlarmNotification(): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AppConstants.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("违规报警")
            .setContentText("未达标离开不背单词/切换应用，已触发强提醒")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAlarm()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 2001
        private const val ACTION_START = "com.jnu.hardvocabguard.action.ALARM_START"
        private const val ACTION_STOP = "com.jnu.hardvocabguard.action.ALARM_STOP"

        fun start(context: Context) {
            val intent = Intent(context, AlarmForegroundService::class.java).apply { action = ACTION_START }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmForegroundService::class.java).apply { action = ACTION_STOP }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

