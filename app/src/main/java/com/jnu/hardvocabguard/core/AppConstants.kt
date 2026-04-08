package com.jnu.hardvocabguard.core

/**
 * 全局常量集中管理，避免散落硬编码。
 */
object AppConstants {
    /**
     * 目标白名单应用：不背单词官方包名（需求强制指定）。
     */
    const val TARGET_PACKAGE_NAME = "cn.com.langeasy.LangEasyLexis"

    /**
     * 监督通知渠道 ID。
     */
    const val CHANNEL_SUPERVISION = "supervision"

    /**
     * 报警通知渠道 ID。
     */
    const val CHANNEL_ALARM = "alarm"
}

