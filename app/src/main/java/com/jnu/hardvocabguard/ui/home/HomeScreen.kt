package com.jnu.hardvocabguard.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jnu.hardvocabguard.data.SettingsStore
import com.jnu.hardvocabguard.HardVocabGuardApp
import com.jnu.hardvocabguard.CrashStore
import com.jnu.hardvocabguard.perm.PermissionStatus
import com.jnu.hardvocabguard.core.AppConstants
import com.jnu.hardvocabguard.core.TargetAppLauncher
import com.jnu.hardvocabguard.domain.RuleMode
import com.jnu.hardvocabguard.security.PasswordHasher
import com.jnu.hardvocabguard.service.AlarmForegroundService
import com.jnu.hardvocabguard.service.SupervisionForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 主界面：配置监督规则并启动/结束监督。
 *
 * 说明：规则设置与实际统计由后台前台服务+无障碍共同完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenStats: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    var minutes by remember { mutableStateOf("30") }
    val ruleMode = RuleMode.DURATION

    var emergencyPwd by remember { mutableStateOf("") }
    val emergencyHash by settings.emergencyHashFlow().collectAsStateWithLifecycle(initialValue = null)

    var showPermDialog by remember { mutableStateOf(false) }
    var crashText by remember { mutableStateOf<String?>(null) }
    var showCrashDialog by remember { mutableStateOf(false) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var showPwdDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (crashText != null) return@LaunchedEffect
        val app = context.applicationContext
        crashText = if (app is HardVocabGuardApp) {
            kotlinx.coroutines.withContext(Dispatchers.IO) { CrashStore.readAndClear(app) }
        } else {
            null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("硬要背单词 · 监督") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("仅监督时长：启动后将自动打开‘不背单词’，未达标离开将触发报警。")

            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("时长目标（分钟）") },
                singleLine = true,
            )
            Button(onClick = {
                val usageOk = PermissionStatus.hasUsageAccess(context)
                val a11yOk = PermissionStatus.isAccessibilityServiceEnabled(
                    context = context,
                    serviceClassName = "com.jnu.hardvocabguard.accessibility.GuardAccessibilityService",
                )

                val pwdOk = !emergencyHash.isNullOrBlank()
                if (!pwdOk) {
                    showPwdDialog = true
                    return@Button
                }

                if (!usageOk || !a11yOk) {
                    showPermDialog = true
                    return@Button
                }

                val mins = minutes.toLongOrNull() ?: 30L
                SupervisionForegroundService.start(
                    context = context,
                    minutesGoal = mins,
                    wordsGoal = 1,
                    ruleMode = ruleMode,
                )

                val launched = TargetAppLauncher.launchTargetApp(context)
                if (!launched) {
                    showInstallDialog = true
                }
            }) {
                Text("启动监督模式")
            }

            Button(onClick = {
                AlarmForegroundService.stop(context)
                context.startService(Intent(context, SupervisionForegroundService::class.java).apply {
                    action = SupervisionForegroundService.ACTION_STOP
                    putExtra(SupervisionForegroundService.EXTRA_END_REASON, com.jnu.hardvocabguard.domain.SessionEndReason.MANUAL_STOP.name)
                })
            }) {
                Text("结束监督模式")
            }

            Button(onClick = onOpenStats) {
                Text("查看学习统计")
            }

            Text(if (emergencyHash.isNullOrBlank()) "紧急解锁：未设置" else "紧急解锁：已设置")

            if (!crashText.isNullOrBlank()) {
                Button(onClick = { showCrashDialog = true }) {
                    Text("查看上次崩溃日志")
                }
            }

            OutlinedTextField(
                value = emergencyPwd,
                onValueChange = { emergencyPwd = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("设置/修改紧急密码(6位数字)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
            )
            Button(onClick = {
                val pwd = emergencyPwd
                if (!pwd.matches(Regex("\\d{6}"))) return@Button
                val salt = PasswordHasher.createSaltBase64()
                val hash = PasswordHasher.hashSixDigitPassword(pwd, salt)
                scope.launch(Dispatchers.Default) { settings.setEmergencyPasswordHash(salt, hash) }
            }) {
                Text("保存紧急密码")
            }
        }
    }

    if (showPermDialog) {
        AlertDialog(
            onDismissRequest = { showPermDialog = false },
            title = { Text("需要先开启授权") },
            text = {
                Text(
                    "监督模式依赖两项系统授权：\n" +
                        "1) 无障碍服务：用于检测违规与显示悬浮进度\n" +
                        "2) 使用情况访问：用于统计不背单词前台时长\n\n" +
                        "请先在系统设置中开启后再回来启动监督。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPermDialog = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("去开无障碍") }
            },
            dismissButton = {
                Button(onClick = {
                    showPermDialog = false
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("去开使用情况") }
            }
        )
    }

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = { Text("未找到不背单词") },
            text = { Text("未检测到‘不背单词’应用可启动。请确认已安装官方版本（包名：${AppConstants.TARGET_PACKAGE_NAME}）。") },
            confirmButton = {
                Button(onClick = {
                    showInstallDialog = false
                    TargetAppLauncher.openTargetAppStoreOrDetails(context)
                }) { Text("去应用商店") }
            },
            dismissButton = {
                Button(onClick = { showInstallDialog = false }) { Text("知道了") }
            }
        )
    }

    if (showPwdDialog) {
        AlertDialog(
            onDismissRequest = { showPwdDialog = false },
            title = { Text("请先设置紧急密码") },
            text = {
                Text(
                    "为了避免你忘记设置紧急解锁而无法退出监督模式，必须先设置 6 位数字紧急密码后才能启动监督。"
                )
            },
            confirmButton = {
                Button(onClick = { showPwdDialog = false }) { Text("我去设置") }
            }
        )
    }

    if (showCrashDialog) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text("上次崩溃日志") },
            text = {
                OutlinedTextField(
                    value = crashText ?: "",
                    onValueChange = {},
                    readOnly = true,
                    maxLines = 12,
                    label = { Text("请复制发给我") },
                )
            },
            confirmButton = {
                Button(onClick = { showCrashDialog = false }) { Text("关闭") }
            }
        )
    }
}
