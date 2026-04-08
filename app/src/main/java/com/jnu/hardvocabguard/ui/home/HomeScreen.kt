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
import com.jnu.hardvocabguard.domain.RuleMode
import com.jnu.hardvocabguard.security.PasswordHasher
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
    var words by remember { mutableStateOf("50") }
    var ruleMode by remember { mutableStateOf(RuleMode.DURATION) }

    var emergencyPwd by remember { mutableStateOf("") }
    val emergencyHash by settings.emergencyHashFlow().collectAsStateWithLifecycle(initialValue = null)

    var showPermDialog by remember { mutableStateOf(false) }
    var crashText by remember { mutableStateOf<String?>(null) }

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
            Text("达标规则二选一：时长统计更稳定；数量统计依赖无障碍识别（不同版本界面可能需微调关键词）。")

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ruleMode == RuleMode.DURATION,
                    onClick = { ruleMode = RuleMode.DURATION },
                    label = { Text("按时长达标") },
                    colors = FilterChipDefaults.filterChipColors(),
                )
                FilterChip(
                    selected = ruleMode == RuleMode.WORD_COUNT,
                    onClick = { ruleMode = RuleMode.WORD_COUNT },
                    label = { Text("按数量达标") },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }

            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("时长目标（分钟）") },
                singleLine = true,
            )
            OutlinedTextField(
                value = words,
                onValueChange = { words = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("数量目标（个）") },
                singleLine = true,
            )

            Button(onClick = {
                val usageOk = PermissionStatus.hasUsageAccess(context)
                val a11yOk = PermissionStatus.isAccessibilityServiceEnabled(
                    context = context,
                    serviceClassName = "com.jnu.hardvocabguard.accessibility.GuardAccessibilityService",
                )

                if (!usageOk || !a11yOk) {
                    showPermDialog = true
                    return@Button
                }

                val mins = minutes.toLongOrNull() ?: 30L
                val w = words.toIntOrNull() ?: 50
                SupervisionForegroundService.start(
                    context = context,
                    minutesGoal = mins,
                    wordsGoal = w,
                    ruleMode = ruleMode,
                )
            }) {
                Text("启动监督模式")
            }

            Button(onClick = {
                context.startService(Intent(context, SupervisionForegroundService::class.java).apply {
                    action = SupervisionForegroundService.ACTION_STOP
                })
            }) {
                Text("结束监督模式")
            }

            Button(onClick = onOpenStats) {
                Text("查看学习统计")
            }

            Text(if (emergencyHash.isNullOrBlank()) "紧急解锁：未设置" else "紧急解锁：已设置")

            if (!crashText.isNullOrBlank()) {
                Text("上次启动发生崩溃（已记录本地日志）")
                OutlinedTextField(
                    value = crashText ?: "",
                    onValueChange = {},
                    label = { Text("崩溃日志") },
                    readOnly = true,
                    maxLines = 6,
                )
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
}
