package com.jnu.hardvocabguard.ui.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.jnu.hardvocabguard.core.AppConstants
import com.jnu.hardvocabguard.core.TargetAppLauncher
import com.jnu.hardvocabguard.data.SettingsStore
import com.jnu.hardvocabguard.security.PasswordHasher
import com.jnu.hardvocabguard.service.AlarmForegroundService
import com.jnu.hardvocabguard.service.SupervisionForegroundService
import kotlinx.coroutines.flow.firstOrNull
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * 全屏报警页：
 * - 背景闪烁 + 亮度拉满
 * - 提供“返回不背单词”快捷入口
 * - 紧急解锁：输入预设 6 位数字密码解除监督（并留档，后续在统计模块实现）
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }

        setContent {
            MaterialTheme {
                AlarmScreen(onUnlocked = { finish() })
            }
        }
    }
}

@Composable
private fun AlarmScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val scope = rememberCoroutineScope()

    val transition = rememberInfiniteTransition(label = "flash")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(450),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    var pwd by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("未达标离开不背单词，已触发报警") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red.copy(alpha = alpha))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("违规报警", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.White)

        Button(onClick = {
            val ok = TargetAppLauncher.launchTargetApp(context)
            if (!ok) {
                message = "未检测到不背单词可启动，请确认已安装官方版本"
            }
        }) {
            Text("返回不背单词")
        }

        OutlinedTextField(
            value = pwd,
            onValueChange = { pwd = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text("紧急解锁密码(6位数字)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
        )

        Button(onClick = {
            scope.launch {
                val salt = settings.emergencySaltFlow().firstOrNull()
                val hash = settings.emergencyHashFlow().firstOrNull()
                if (salt.isNullOrBlank() || hash.isNullOrBlank()) {
                    message = "未设置紧急密码，请先在主界面设置"
                    return@launch
                }

                val ok = PasswordHasher.verifySixDigitPassword(pwd, salt, hash)
                if (!ok) {
                    message = "密码错误"
                    return@launch
                }

                settings.stopSupervision()
                settings.setAlarmActive(false)
                AlarmForegroundService.stop(context)
                context.stopService(Intent(context, AlarmForegroundService::class.java))
                context.startService(Intent(context, SupervisionForegroundService::class.java).apply {
                    action = SupervisionForegroundService.ACTION_STOP
                    putExtra(SupervisionForegroundService.EXTRA_END_REASON, com.jnu.hardvocabguard.domain.SessionEndReason.EMERGENCY_UNLOCK.name)
                })
                message = "已紧急解锁"
                onUnlocked()
            }
        }) {
            Text("紧急解锁")
        }
    }
}

