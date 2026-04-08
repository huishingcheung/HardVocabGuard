package com.jnu.hardvocabguard.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.jnu.hardvocabguard.R
import com.jnu.hardvocabguard.core.AppConstants

/**
 * 通知相关：创建渠道、统一管理。
 */
object Notifications {
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = NotificationManagerCompat.from(context)

        val supervision = NotificationChannel(
            AppConstants.CHANNEL_SUPERVISION,
            context.getString(R.string.notif_channel_supervision),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "监督进行中：展示学习进度与快捷操作"
            setShowBadge(false)
        }

        val alarm = NotificationChannel(
            AppConstants.CHANNEL_ALARM,
            context.getString(R.string.notif_channel_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "违规报警：强提醒直到回到目标应用或紧急解锁"
            enableVibration(true)
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        nm.createNotificationChannel(supervision)
        nm.createNotificationChannel(alarm)
    }
}

