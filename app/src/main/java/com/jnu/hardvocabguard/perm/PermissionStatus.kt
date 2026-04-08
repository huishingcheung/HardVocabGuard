package com.jnu.hardvocabguard.perm

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * 权限/授权状态检查：用于在 UI 层给出“缺什么、去哪里开”的明确引导，避免直接崩溃。
 */
object PermissionStatus {
    fun hasUsageAccess(context: Context): Boolean {
        val aom = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = aom.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityServiceEnabled(context: Context, serviceClassName: String): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val cn = ComponentName(context, serviceClassName)
        return enabled.split(':').any { it.equals(cn.flattenToString(), ignoreCase = true) }
    }

    fun isPostNotificationsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

