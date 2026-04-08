package com.jnu.hardvocabguard

import android.app.Application
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
    }
}

