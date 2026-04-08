package com.jnu.hardvocabguard.domain

/**
 * 监督状态快照：用于 UI/服务/无障碍之间对齐进度展示。
 */
data class SupervisionState(
    val active: Boolean,
    val ruleMode: RuleMode,
    val minutesGoal: Long,
    val wordsGoal: Int,
    val usedMillis: Long,
    val wordsLearned: Int,
    val alarmActive: Boolean,
    val sessionStartEpochMillis: Long,
) {
    val usedMinutes: Long
        get() = usedMillis / 60_000L

    fun isGoalReached(): Boolean {
        return when (ruleMode) {
            RuleMode.DURATION -> usedMillis >= minutesGoal * 60_000L
            RuleMode.WORD_COUNT -> wordsLearned >= wordsGoal
        }
    }
}

