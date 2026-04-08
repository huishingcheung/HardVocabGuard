package com.jnu.hardvocabguard.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.jnu.hardvocabguard.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 无障碍悬浮层：用于在任意界面展示实时进度。
 *
 * 使用 TYPE_ACCESSIBILITY_OVERLAY，无需 SYSTEM_ALERT_WINDOW 权限。
 */
class ProgressOverlayController(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var rootView: View? = null
    private var added: Boolean = false

    private lateinit var title: TextView
    private lateinit var progress: TextView

    fun start() {
        if (rootView == null) {
            rootView = createView()
        }

        scope.launch {
            settings.supervisionStateFlow().collect { state ->
                if (state.active) {
                    ensureAdded()
                    title.text = if (state.alarmActive) "违规报警中" else "监督进行中"
                    progress.text = "时长：${state.usedMinutes}/${state.minutesGoal} 分钟\n数量：${state.wordsLearned}/${state.wordsGoal}"
                } else {
                    removeIfNeeded()
                }
            }
        }
    }

    fun stop() {
        removeIfNeeded()
        scope.cancel()
    }

    private fun createView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16)
            setBackgroundColor(0xAA000000.toInt())
        }
        title = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            text = "监督进行中"
        }
        progress = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            text = ""
        }
        val plusOne = Button(context).apply {
            text = "+1"
            setOnClickListener {
                scope.launch(Dispatchers.Default) {
                    settings.incrementWordsLearned(1)
                }
            }
        }
        container.addView(title)
        container.addView(progress)
        container.addView(plusOne)
        return container
    }

    private fun ensureAdded() {
        if (added) return
        val view = rootView ?: return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }
        val ok = runCatching { wm.addView(view, lp) }.isSuccess
        added = ok
    }

    private fun removeIfNeeded() {
        if (!added) return
        val view = rootView ?: return
        runCatching { wm.removeView(view) }
        added = false
    }
}

