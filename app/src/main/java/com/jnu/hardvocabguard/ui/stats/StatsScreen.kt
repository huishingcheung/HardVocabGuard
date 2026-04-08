package com.jnu.hardvocabguard.ui.stats

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jnu.hardvocabguard.data.StudySessionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 统计页：此处先提供占位 UI，后续接入 Room 记录与导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { StudySessionRepository(context) }
    val sessions = repo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList()).value
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                exportCsv(context, uri, sessions)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("学习统计") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("总记录：${sessions.size} 次")

            val weekly = remember(sessions) { buildLast7DaysMinutes(sessions) }
            val weeklyTotal = weekly.values.sum()
            Text("最近7天：${weeklyTotal} 分钟")
            WeeklyBarChart(weekly)

            Button(onClick = {
                val fileName = "hard_vocab_guard_sessions_${System.currentTimeMillis()}.csv"
                exportLauncher.launch(fileName)
            }) {
                Text("导出CSV")
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions) { s ->
                    val dt = formatEpoch(s.startEpochMillis)
                    val minutes = s.durationMillis / 60_000L
                    Text(
                        text = "$dt  ·  ${minutes}分钟  ·  ${s.wordsLearned}个  ·  ${s.endReason}",
                    )
                }
            }

            Button(onClick = onBack) {
                Text("返回")
            }
        }
    }
}

@Composable
private fun WeeklyBarChart(dayToMinutes: LinkedHashMap<LocalDate, Long>) {
    val max = (dayToMinutes.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(
        modifier = Modifier.height(64.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dayToMinutes.forEach { (day, minutes) ->
            val ratio = minutes.toFloat() / max.toFloat()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(modifier = Modifier
                    .height((52.dp.value * ratio).dp)
                    .width(18.dp)) {
                    drawRect(Color(0xFF4CAF50))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(day.dayOfMonth.toString())
            }
        }
    }
}

private fun buildLast7DaysMinutes(sessions: List<com.jnu.hardvocabguard.data.db.StudySessionEntity>): LinkedHashMap<LocalDate, Long> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val map = LinkedHashMap<LocalDate, Long>()
    days.forEach { map[it] = 0L }

    sessions.forEach { s ->
        val day = Instant.ofEpochMilli(s.startEpochMillis).atZone(zone).toLocalDate()
        if (day in map.keys) {
            val minutes = s.durationMillis / 60_000L
            map[day] = (map[day] ?: 0L) + minutes
        }
    }
    return map
}

private fun formatEpoch(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

private fun exportCsv(context: Context, uri: android.net.Uri, sessions: List<com.jnu.hardvocabguard.data.db.StudySessionEntity>) {
    val header = "startEpochMillis,endEpochMillis,durationMillis,wordsLearned,minutesGoal,wordsGoal,ruleMode,endReason\n"
    val lines = buildString {
        append(header)
        sessions.asReversed().forEach { s ->
            append(s.startEpochMillis).append(',')
            append(s.endEpochMillis).append(',')
            append(s.durationMillis).append(',')
            append(s.wordsLearned).append(',')
            append(s.minutesGoal).append(',')
            append(s.wordsGoal).append(',')
            append(s.ruleMode).append(',')
            append(s.endReason)
            append('\n')
        }
    }
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(lines.toByteArray(Charsets.UTF_8))
        }
    }
}
