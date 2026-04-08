package com.jnu.hardvocabguard.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.jnu.hardvocabguard.core.AppConstants

/**
 * 白名单判定：
 * - 需求强制允许：本应用 + 不背单词
 * - 合规要求：保留系统紧急呼叫能力（不同ROM包名可能不同，做多候选兜底）
 */
object AllowedPackages {
    fun isAllowed(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return true
        if (pkg == context.packageName) return true
        if (pkg == AppConstants.TARGET_PACKAGE_NAME) return true
        if (isEmergencyOrDialer(context, pkg)) return true
        return false
    }

    private fun isEmergencyOrDialer(context: Context, pkg: String): Boolean {
        val candidates = setOf(
            "com.android.phone",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.miui.voip",
            "com.android.server.telecom",
        )
        if (pkg in candidates) return true

        val pm = context.packageManager
        val dialerIntent = Intent(Intent.ACTION_DIAL)
        val dialer = pm.resolveActivity(dialerIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        return dialer == pkg
    }
}

