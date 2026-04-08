package com.jnu.hardvocabguard

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import com.jnu.hardvocabguard.notify.Notifications

/**
 * 应用入口：仅做全局初始化。
 *
 * 注意：本项目不做任何联网、不收集任何用户数据。
 */
class HardVocabGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        CrashStore.install(this)
    }
}

object CrashStore {
    private const val FILE_NAME = "last_crash.txt"

    fun install(app: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val content = "Thread=${t.name}\n\n${sw}"
                app.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { it.write(content.toByteArray()) }
            }
            defaultHandler?.uncaughtException(t, e)
        }
    }

    fun readAndClear(app: Application): String? {
        val file = app.getFileStreamPath(FILE_NAME)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        runCatching { file.delete() }
        return text
    }
}
