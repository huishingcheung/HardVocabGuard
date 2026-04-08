package com.jnu.hardvocabguard.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.jnu.hardvocabguard.core.AppConstants
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

    private var lastWordMatchAt: Long = 0L
    private var lastWordMatchSignature: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = SettingsStore(this)
        overlay = ProgressOverlayController(this, settings)
        overlay.start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        scope.launch {
            val state = settings.supervisionStateFlow().first()
            if (!state.active) return@launch
            if (state.isGoalReached()) return@launch

            val pkg = event.packageName?.toString()
            if (!AllowedPackages.isAllowed(this@GuardAccessibilityService, pkg)) {
                settings.setAlarmActive(true)
                AlarmForegroundService.start(this@GuardAccessibilityService)
                launchTargetAppBestEffort()
                return@launch
            }

            if (pkg == AppConstants.TARGET_PACKAGE_NAME) {
                maybeCountWord(event)
            }
        }
    }

    private suspend fun maybeCountWord(event: AccessibilityEvent) {
        val texts = buildList {
            event.text?.forEach { add(it.toString()) }
            event.contentDescription?.toString()?.let { add(it) }
        }.filter { it.isNotBlank() }

        if (texts.isEmpty()) return

        val joined = texts.joinToString("|")
        val signature = "${event.eventType}:$joined"
        val now = System.currentTimeMillis()

        if (signature == lastWordMatchSignature && now - lastWordMatchAt < 800L) return

        val matched = texts.any { t ->
            t.contains("已掌握") ||
                t.contains("学习完成") ||
                t.contains("完成") && t.contains("个") ||
                t.contains("+1")
        }

        if (!matched) return

        lastWordMatchSignature = signature
        lastWordMatchAt = now
        settings.incrementWordsLearned(1)
    }

    private fun launchTargetAppBestEffort() {
        val intent = packageManager.getLaunchIntentForPackage(AppConstants.TARGET_PACKAGE_NAME)
            ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        overlay.stop()
        scope.cancel()
        super.onDestroy()
    }
}

