package com.jnu.hardvocabguard.data

import android.content.Context
import com.jnu.hardvocabguard.data.db.DatabaseProvider
import com.jnu.hardvocabguard.data.db.StudySessionEntity
import com.jnu.hardvocabguard.domain.SessionEndReason
import com.jnu.hardvocabguard.domain.SupervisionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 学习记录仓库：封装 Room 访问。
 */
class StudySessionRepository(context: Context) {
    private val dao = DatabaseProvider.get(context).studySessionDao()

    fun observeAll(): Flow<List<StudySessionEntity>> = dao.observeAll()

    suspend fun insertFromState(state: SupervisionState, endEpochMillis: Long, reason: SessionEndReason) {
        val start = state.sessionStartEpochMillis
        val duration = (endEpochMillis - start).coerceAtLeast(0L)
        dao.insert(
            StudySessionEntity(
                startEpochMillis = start,
                endEpochMillis = endEpochMillis,
                durationMillis = duration,
                wordsLearned = state.wordsLearned,
                minutesGoal = state.minutesGoal,
                wordsGoal = state.wordsGoal,
                ruleMode = state.ruleMode.name,
                endReason = reason.name,
            )
        )
    }
}

