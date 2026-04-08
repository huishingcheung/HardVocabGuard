package com.jnu.hardvocabguard.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * 使用情况统计：基于 UsageStatsManager 的事件流计算“目标应用前台有效时长”。
 *
 * 关键点：
 * - 仅累计 MOVE_TO_FOREGROUND 到 MOVE_TO_BACKGROUND 之间的时间段
 * - 依赖“使用情况访问权限”（ACTION_USAGE_ACCESS_SETTINGS）
 */
class ForegroundUsageTracker(context: Context) {
    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private var lastQueryTime: Long = 0L
    private var foregroundPackage: String? = null
    private var foregroundStartTime: Long = 0L

    /**
     * 拉取最近的 UsageEvents 并返回本次新增的目标应用前台时长（毫秒）。
     */
    fun poll(nowMillis: Long, targetPackage: String): Long {
        if (lastQueryTime == 0L) {
            lastQueryTime = nowMillis - 5_000L
        }

        val events = runCatching { usm.queryEvents(lastQueryTime, nowMillis) }.getOrNull() ?: run {
            lastQueryTime = nowMillis
            return 0L
        }
        lastQueryTime = nowMillis
        return consume(events, nowMillis, targetPackage)
    }

    private fun consume(events: UsageEvents, nowMillis: Long, targetPackage: String): Long {
        var delta = 0L
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    foregroundPackage = event.packageName
                    foregroundStartTime = event.timeStamp
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val pkg = foregroundPackage
                    if (pkg != null && pkg == targetPackage && foregroundStartTime > 0L) {
                        val end = event.timeStamp
                        if (end > foregroundStartTime) {
                            delta += (end - foregroundStartTime)
                        }
                    }
                    foregroundPackage = null
                    foregroundStartTime = 0L
                }
            }
        }

        val pkg = foregroundPackage
        if (pkg != null && pkg == targetPackage && foregroundStartTime > 0L) {
            if (nowMillis > foregroundStartTime) {
                delta += (nowMillis - foregroundStartTime)
                foregroundStartTime = nowMillis
            }
        }
        return delta
    }
}
