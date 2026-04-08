package com.jnu.hardvocabguard.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 学习记录表：仅本地存储，不上传。
 */
@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val durationMillis: Long,
    val wordsLearned: Int,
    val minutesGoal: Long,
    val wordsGoal: Int,
    val ruleMode: String,
    val endReason: String,
)

