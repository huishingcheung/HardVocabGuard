package com.jnu.hardvocabguard.admin

import android.app.admin.DeviceAdminReceiver

/**
 * 防卸载保护（合规版本）：
 * - 启用“设备管理器”后，用户卸载前需要先到系统设置里停用设备管理器。
 *
 * 重要限制：
 * - 普通用户授权的“设备管理器”并不等同“设备所有者( Device Owner )”。
 * - 未成为 Device Owner 的情况下，无法做到真正意义上的“禁止卸载”。
 */
class UninstallProtectionAdminReceiver : DeviceAdminReceiver()

