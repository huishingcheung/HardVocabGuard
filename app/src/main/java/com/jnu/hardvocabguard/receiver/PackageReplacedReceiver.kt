package com.jnu.hardvocabguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jnu.hardvocabguard.data.SettingsStore
import com.jnu.hardvocabguard.service.AlarmForegroundService
import com.jnu.hardvocabguard.service.SupervisionForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用更新替换广播：
 * - 防止旧版本残留的前台服务/报警在“更新安装后但未打开应用”时继续运行
 * - 更新后默认关闭监督与报警，要求用户重新进入应用确认并启动
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        AlarmForegroundService.stop(context)
        context.startService(Intent(context, SupervisionForegroundService::class.java).apply {
            action = SupervisionForegroundService.ACTION_STOP
        })

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                SettingsStore(context).stopSupervision()
                SettingsStore(context).setAlarmActive(false)
            }
            pending.finish()
        }
    }
}

