package com.jnu.hardvocabguard.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object TargetAppLauncher {
    fun launchTargetApp(context: Context): Boolean {
        val pkg = AppConstants.TARGET_PACKAGE_NAME
        val pm = context.packageManager

        val intent = pm.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (intent != null) {
            return runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }

        val main = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val resolved = pm.queryIntentActivities(main, 0)
        val act = resolved.firstOrNull()?.activityInfo
        if (act != null) {
            val explicit = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(act.packageName, act.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            return runCatching {
                context.startActivity(explicit)
                true
            }.getOrDefault(false)
        }

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, "未检测到‘不背单词’已安装（包名：$pkg）", Toast.LENGTH_LONG).show()
        }
        return false
    }

    fun openTargetAppStoreOrDetails(context: Context) {
        val pkg = AppConstants.TARGET_PACKAGE_NAME
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (market.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(market) }
            return
        }

        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(web) }
    }
}

