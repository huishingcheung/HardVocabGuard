package com.jnu.hardvocabguard.ui.perm

import android.Manifest
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jnu.hardvocabguard.admin.UninstallProtectionAdminReceiver
import com.jnu.hardvocabguard.perm.PermissionStatus

/**
 * 权限引导页：针对 Hyper OS 的权限回收/电池管控，做分步引导。
 *
 * 说明：无障碍与使用情况访问权限均需用户手动在系统设置开启，无法静默授予。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGuideScreen(
    onContinue: () -> Unit,
) {
    val context = LocalContext.current

    var notificationsGranted by remember { mutableStateOf(isPostNotificationsGranted(context)) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("权限引导") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "为了实现‘仅允许不背单词可用’的自律监督，本应用仅依赖系统授权权限，不需要ROOT。",
                style = MaterialTheme.typography.bodyMedium
            )

            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) {
                Text("1. 开启无障碍服务")
            }

            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) {
                Text(if (PermissionStatus.hasUsageAccess(context)) "2. 使用情况访问已开启" else "2. 开启使用情况访问")
            }

            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsGranted) {
                Text(if (notificationsGranted) "3. 通知权限已授予" else "3. 授予通知权限")
            }

            Button(onClick = {
                requestIgnoreBatteryOptimizations(context)
            }) {
                Text("4. 允许忽略电池优化（强烈建议）")
            }

            Button(onClick = {
                requestDeviceAdmin(context)
            }) {
                Text("5. 启用设备管理器(防卸载)")
            }

            Button(onClick = onContinue) {
                Text("进入主界面")
            }

            Text(
                text = "提示：Hyper OS 可能会自动回收权限/限制后台，请在系统设置中将本应用设为‘无限制’并允许自启动。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun isPostNotificationsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        true
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val pkg = context.packageName
    if (pm.isIgnoringBatteryOptimizations(pkg)) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:$pkg")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun requestDeviceAdmin(context: Context) {
    val cn = ComponentName(context, UninstallProtectionAdminReceiver::class.java)
    val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
        putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
        putExtra(
            android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "启用后，卸载本应用前需要先在系统设置中停用设备管理器。"
        )
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
