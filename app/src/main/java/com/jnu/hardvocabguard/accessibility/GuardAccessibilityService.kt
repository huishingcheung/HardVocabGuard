package com.jnu.hardvocabguard.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.jnu.hardvocabguard.core.AppConstants
import com.jnu.hardvocabguard.core.TargetAppLauncher
import com.jnu.hardvocabguard.data.SettingsStore
import com.jnu.hardvocabguard.service.AlarmForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 无障碍服务：
 * 1) 监听前台窗口切换，发现非白名单应用即判定违规
 * 2) 在“不背单词”内尝试识别“完成/掌握”等文案，累计单词数量
 * 3) 提供 TYPE_ACCESSIBILITY_OVERLAY 悬浮进度层（无需悬浮窗权限）
 *
 * 重要说明：
 * - Android 16 上，普通应用无法做到“系统级强制锁定/绝对阻止返回桌面”。本实现为合规前提下的最强可行方案：
 *   发现违规后立即报警，并尽力拉回目标应用。
 */
class GuardAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settings: SettingsStore
    private lateinit var overlay: ProgressOverlayController

    private var lastBringBackAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsStore(this)
        overlay = ProgressOverlayController(this, settings)
        overlay.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        scope.launch {
            val state = settings.supervisionStateFlow().first()
            if (!state.active) return@launch
            if (state.isGoalReached()) return@launch

            val lastLaunch = settings.lastTargetLaunchEpochFlow().first()

            val now = System.currentTimeMillis()
            val inGrace = state.sessionStartEpochMillis > 0L && (now - state.sessionStartEpochMillis) < START_GRACE_MS
            val inLaunchGrace = lastLaunch > 0L && (now - lastLaunch) < TARGET_LAUNCH_GRACE_MS

            val pkg = event.packageName?.toString()

            if (inLaunchGrace && pkg != null && pkg in TRANSIENT_ALLOWED_PACKAGES) {
                return@launch
            }

            if (!AllowedPackages.isAllowed(this@GuardAccessibilityService, pkg)) {
                if (inGrace) {
                    return@launch
                }

                if (!state.alarmActive) {
                    settings.setAlarmActive(true)
                    AlarmForegroundService.start(this@GuardAccessibilityService)
                    bringBackToTarget(now)
                }
                return@launch
            }

            if (pkg == AppConstants.TARGET_PACKAGE_NAME && state.alarmActive) {
                settings.setAlarmActive(false)
                AlarmForegroundService.stop(this@GuardAccessibilityService)
            }
        }
    }

    private fun bringBackToTarget(now: Long) {
        if (now - lastBringBackAt < 1_000L) return
        lastBringBackAt = now
        TargetAppLauncher.launchTargetApp(this)
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        overlay.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val START_GRACE_MS = 10_000L
        private const val TARGET_LAUNCH_GRACE_MS = 30_000L

        private val TRANSIENT_ALLOWED_PACKAGES = setOf(
            "com.miui.home",
            "com.miui.securitycenter",
            "com.miui.permcenter",
            "com.lbe.security.miui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.android.settings",
        )
    }
}

