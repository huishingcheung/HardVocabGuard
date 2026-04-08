package com.jnu.hardvocabguard.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var settings: SettingsStore
    private lateinit var overlay: ProgressOverlayController

    private var lastBringBackAt: Long = 0L
    private var lastRecentsAttemptAt: Long = 0L
    private var recentsAttemptCount: Int = 0
    private var hasSeenTargetApp: Boolean = false

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

            if (pkg == AppConstants.TARGET_PACKAGE_NAME) {
                hasSeenTargetApp = true
            }

            if (inLaunchGrace && pkg != null && pkg in TRANSIENT_ALLOWED_PACKAGES) {
                return@launch
            }

            if (!AllowedPackages.isAllowed(this@GuardAccessibilityService, pkg)) {
                if (inGrace && !hasSeenTargetApp) {
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
                recentsAttemptCount = 0
            }

            if (
                pkg == packageName &&
                !state.isGoalReached() &&
                !inGrace &&
                state.sessionStartEpochMillis > 0L &&
                (now - state.sessionStartEpochMillis) < 15_000L
            ) {
                trySwitchToTargetViaRecents(now)
            }
        }
    }

    private fun bringBackToTarget(now: Long) {
        if (now - lastBringBackAt < 1_000L) return
        lastBringBackAt = now
        TargetAppLauncher.launchTargetApp(this)
    }

    private fun trySwitchToTargetViaRecents(now: Long) {
        if (recentsAttemptCount >= 2) return
        if (now - lastRecentsAttemptAt < 1_500L) return
        lastRecentsAttemptAt = now
        recentsAttemptCount += 1

        performGlobalAction(GLOBAL_ACTION_RECENTS)
        mainHandler.postDelayed({
            clickFirstNodeByText(listOf("不背单词", "不背"))
        }, 450)
    }

    private fun clickFirstNodeByText(textCandidates: List<String>): Boolean {
        val roots = windows.mapNotNull { it.root }
        for (root in roots) {
            val hit = findFirstNode(root) { node ->
                val t = node.text?.toString().orEmpty()
                val d = node.contentDescription?.toString().orEmpty()
                textCandidates.any { c -> t.contains(c) || d.contains(c) }
            } ?: continue

            val clickable = hit.findClickableParent()
            if (clickable != null) {
                val ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                clickable.recycle()
                hit.recycle()
                root.recycle()
                return ok
            }
            hit.recycle()
            root.recycle()
        }
        return false
    }

    private fun findFirstNode(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (predicate(node)) {
                return AccessibilityNodeInfo.obtain(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
            }
        }
        return null
    }

    private fun AccessibilityNodeInfo.findClickableParent(): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = this
        var steps = 0
        while (cur != null && steps < 8) {
            if (cur.isClickable) return AccessibilityNodeInfo.obtain(cur)
            cur = cur.parent
            steps += 1
        }
        return null
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

