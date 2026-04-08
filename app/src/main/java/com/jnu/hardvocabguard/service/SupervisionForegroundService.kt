package com.jnu.hardvocabguard.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jnu.hardvocabguard.MainActivity
import com.jnu.hardvocabguard.R
import com.jnu.hardvocabguard.core.AppConstants
import com.jnu.hardvocabguard.data.SettingsStore
import com.jnu.hardvocabguard.data.StudySessionRepository
import com.jnu.hardvocabguard.domain.RuleMode
import com.jnu.hardvocabguard.domain.SessionEndReason
import com.jnu.hardvocabguard.notify.Notifications
import com.jnu.hardvocabguard.usage.ForegroundUsageTracker
import com.jnu.hardvocabguard.core.TargetAppLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 监督前台服务：
 * - 常驻通知展示进度
 * - 轮询 UsageEvents 累计目标应用前台有效时长
 * - 与无障碍服务共同完成“违规识别与报警”
 */
class SupervisionForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: SettingsStore
    private lateinit var usageTracker: ForegroundUsageTracker
    private lateinit var sessions: StudySessionRepository

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        settings = SettingsStore(this)
        usageTracker = ForegroundUsageTracker(this)
        sessions = StudySessionRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val minutesGoal = intent.getLongExtra(EXTRA_MINUTES_GOAL, 30L)
                val wordsGoal = intent.getIntExtra(EXTRA_WORDS_GOAL, 50)
                val ruleMode = (intent.getStringExtra(EXTRA_RULE_MODE) ?: RuleMode.DURATION.name)
                    .let { runCatching { RuleMode.valueOf(it) }.getOrDefault(RuleMode.DURATION) }
                startSupervision(minutesGoal, wordsGoal, ruleMode)
            }

            ACTION_STOP -> {
                val reason = (intent.getStringExtra(EXTRA_END_REASON) ?: SessionEndReason.MANUAL_STOP.name)
                    .let { runCatching { SessionEndReason.valueOf(it) }.getOrDefault(SessionEndReason.MANUAL_STOP) }
                scope.launch {
                    recordAndStop(reason)
                    AlarmForegroundService.stop(this@SupervisionForegroundService)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }

            else -> {
                scope.launch {
                    val state = settings.supervisionStateFlow().first()
                    if (!state.active) {
                        stopSelf()
                        return@launch
                    }
                    ensureForegroundShown()
                    runLoop()
                }
            }
        }

        return START_STICKY
    }

    private fun startSupervision(minutesGoal: Long, wordsGoal: Int, ruleMode: RuleMode) {
        scope.launch {
            settings.startSupervision(
                ruleMode = ruleMode,
                minutesGoal = minutesGoal.coerceAtLeast(1L),
                wordsGoal = wordsGoal.coerceAtLeast(1),
                nowEpochMillis = System.currentTimeMillis(),
            )
            ensureForegroundShown()
            TargetAppLauncher.launchTargetApp(this@SupervisionForegroundService)
            runLoop()
        }
    }

    private suspend fun ensureForegroundShown() {
        val state = settings.supervisionStateFlow().first()
        if (!state.active) return
        startForeground(
            NOTIF_ID,
            buildNotification(state),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private suspend fun runLoop() {
        while (true) {
            val state = settings.supervisionStateFlow().first()
            if (!state.active) return

            val now = System.currentTimeMillis()
            val delta = usageTracker.poll(now, AppConstants.TARGET_PACKAGE_NAME)
            if (delta > 0L) {
                settings.addUsedMillis(delta)
            }

            val newState = settings.supervisionStateFlow().first()
            startForeground(
                NOTIF_ID,
                buildNotification(newState),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )

            if (newState.isGoalReached()) {
                recordAndStop(SessionEndReason.COMPLETED)
                AlarmForegroundService.stop(this@SupervisionForegroundService)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            delay(1_000L)
        }
    }

    private fun buildNotification(state: com.jnu.hardvocabguard.domain.SupervisionState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SupervisionForegroundService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_END_REASON, SessionEndReason.MANUAL_STOP.name)
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val title = "监督进行中"
        val text = "时长：${state.usedMinutes}/${state.minutesGoal} 分钟"
        val alarmText = if (state.alarmActive) "（报警中）" else ""

        return NotificationCompat.Builder(this, AppConstants.CHANNEL_SUPERVISION)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text + alarmText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, "结束监督", stopIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun recordAndStop(reason: SessionEndReason) {
        val state = settings.supervisionStateFlow().first()
        if (state.active && state.sessionStartEpochMillis > 0L) {
            sessions.insertFromState(state, endEpochMillis = System.currentTimeMillis(), reason = reason)
        }
        settings.stopSupervision()
    }

    companion object {
        const val ACTION_START = "com.jnu.hardvocabguard.action.START"
        const val ACTION_STOP = "com.jnu.hardvocabguard.action.STOP"

        const val EXTRA_MINUTES_GOAL = "minutes_goal"
        const val EXTRA_WORDS_GOAL = "words_goal"
        const val EXTRA_RULE_MODE = "rule_mode"
        const val EXTRA_END_REASON = "end_reason"

        private const val NOTIF_ID = 1001

        fun start(context: Context, minutesGoal: Long, wordsGoal: Int, ruleMode: RuleMode = RuleMode.DURATION) {
            val intent = Intent(context, SupervisionForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MINUTES_GOAL, minutesGoal)
                putExtra(EXTRA_WORDS_GOAL, wordsGoal)
                putExtra(EXTRA_RULE_MODE, ruleMode.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun tryAutoResume(context: Context) {
            val intent = Intent(context, SupervisionForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
