package com.jnu.hardvocabguard.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jnu.hardvocabguard.domain.RuleMode
import com.jnu.hardvocabguard.domain.SupervisionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 设置与运行态持久化：
 * - 监督是否开启、目标、进度、报警状态
 * - 紧急解锁密码（仅存 hash + salt，不存明文）
 *
 * 说明：使用 DataStore（Jetpack 官方）保证本地安全、可迁移且无第三方依赖。
 */
class SettingsStore(private val context: Context) {
    fun supervisionStateFlow(): Flow<SupervisionState> {
        return context.settingsDataStore.data.map { p ->
            val active = p[Keys.SUPERVISION_ACTIVE] ?: false
            val ruleMode = (p[Keys.RULE_MODE] ?: RuleMode.DURATION.name).toRuleMode()
            val minutesGoal = p[Keys.MINUTES_GOAL] ?: 30L
            val wordsGoal = p[Keys.WORDS_GOAL] ?: 50
            val usedMillis = p[Keys.USED_MILLIS] ?: 0L
            val wordsLearned = p[Keys.WORDS_LEARNED] ?: 0
            val alarmActive = p[Keys.ALARM_ACTIVE] ?: false
            val start = p[Keys.SESSION_START_EPOCH] ?: 0L
            SupervisionState(
                active = active,
                ruleMode = ruleMode,
                minutesGoal = minutesGoal,
                wordsGoal = wordsGoal,
                usedMillis = usedMillis,
                wordsLearned = wordsLearned,
                alarmActive = alarmActive,
                sessionStartEpochMillis = start,
            )
        }
    }

    suspend fun startSupervision(ruleMode: RuleMode, minutesGoal: Long, wordsGoal: Int, nowEpochMillis: Long) {
        context.settingsDataStore.edit { p ->
            p[Keys.SUPERVISION_ACTIVE] = true
            p[Keys.RULE_MODE] = ruleMode.name
            p[Keys.MINUTES_GOAL] = minutesGoal
            p[Keys.WORDS_GOAL] = wordsGoal
            p[Keys.USED_MILLIS] = 0L
            p[Keys.WORDS_LEARNED] = 0
            p[Keys.ALARM_ACTIVE] = false
            p[Keys.SESSION_START_EPOCH] = nowEpochMillis
        }
    }

    suspend fun stopSupervision() {
        context.settingsDataStore.edit { p ->
            p[Keys.SUPERVISION_ACTIVE] = false
            p[Keys.ALARM_ACTIVE] = false
        }
    }

    suspend fun setAlarmActive(active: Boolean) {
        context.settingsDataStore.edit { p ->
            p[Keys.ALARM_ACTIVE] = active
        }
    }

    suspend fun addUsedMillis(delta: Long) {
        if (delta <= 0) return
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.USED_MILLIS] ?: 0L
            p[Keys.USED_MILLIS] = cur + delta
        }
    }

    suspend fun incrementWordsLearned(delta: Int = 1) {
        if (delta <= 0) return
        context.settingsDataStore.edit { p ->
            val cur = p[Keys.WORDS_LEARNED] ?: 0
            p[Keys.WORDS_LEARNED] = cur + delta
        }
    }

    fun emergencySaltFlow(): Flow<String?> {
        return context.settingsDataStore.data.map { p -> p[Keys.EMERGENCY_SALT] }
    }

    fun emergencyHashFlow(): Flow<String?> {
        return context.settingsDataStore.data.map { p -> p[Keys.EMERGENCY_HASH] }
    }

    suspend fun setEmergencyPasswordHash(saltBase64: String, hashBase64: String) {
        context.settingsDataStore.edit { p ->
            p[Keys.EMERGENCY_SALT] = saltBase64
            p[Keys.EMERGENCY_HASH] = hashBase64
        }
    }

    private fun String.toRuleMode(): RuleMode {
        return runCatching { RuleMode.valueOf(this) }.getOrDefault(RuleMode.DURATION)
    }

    private object Keys {
        val SUPERVISION_ACTIVE = booleanPreferencesKey("supervision_active")
        val RULE_MODE = stringPreferencesKey("rule_mode")
        val MINUTES_GOAL = longPreferencesKey("minutes_goal")
        val WORDS_GOAL = intPreferencesKey("words_goal")
        val USED_MILLIS = longPreferencesKey("used_millis")
        val WORDS_LEARNED = intPreferencesKey("words_learned")
        val ALARM_ACTIVE = booleanPreferencesKey("alarm_active")
        val SESSION_START_EPOCH = longPreferencesKey("session_start_epoch")

        val EMERGENCY_SALT = stringPreferencesKey("emergency_salt")
        val EMERGENCY_HASH = stringPreferencesKey("emergency_hash")
    }
}
