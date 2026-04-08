package com.jnu.hardvocabguard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StudySessionEntity): Long

    @Query("SELECT * FROM study_sessions ORDER BY startEpochMillis DESC")
    fun observeAll(): Flow<List<StudySessionEntity>>

    @Query("DELETE FROM study_sessions")
    suspend fun deleteAll()
}

