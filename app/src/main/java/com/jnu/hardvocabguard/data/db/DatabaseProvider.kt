package com.jnu.hardvocabguard.data.db

import android.content.Context
import androidx.room.Room

/**
 * 简易单例：避免引入 DI 框架。
 */
object DatabaseProvider {
    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        val existing = instance
        if (existing != null) return existing
        return synchronized(this) {
            val again = instance
            if (again != null) return@synchronized again
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "hard_vocab_guard.db"
            ).build()
            instance = db
            db
        }
    }
}

