package com.jnu.hardvocabguard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [StudySessionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studySessionDao(): StudySessionDao
}

