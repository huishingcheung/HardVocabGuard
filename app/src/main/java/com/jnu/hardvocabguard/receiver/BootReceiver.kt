package com.jnu.hardvocabguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jnu.hardvocabguard.service.SupervisionForegroundService

/**
 * 开机广播：用于在监督未正常结束的情况下尝试恢复前台服务。
 *
 * 说明：Android 14+ 对后台启动限制更严格，此处只做“尽力而为”。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        SupervisionForegroundService.tryAutoResume(context)
    }
}

