package com.jnu.hardvocabguard.ui.launch

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.jnu.hardvocabguard.MainActivity
import com.jnu.hardvocabguard.core.TargetAppLauncher
import com.jnu.hardvocabguard.domain.RuleMode
import com.jnu.hardvocabguard.service.SupervisionForegroundService

class LaunchTargetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val minutesGoal = intent.getLongExtra(EXTRA_MINUTES_GOAL, 30L).coerceAtLeast(1L)

        val handler = Handler(Looper.getMainLooper())

        TargetAppLauncher.launchTargetApp(this)

        handler.postDelayed({
            startActivity(
                android.content.Intent(this, MainActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }, 800)

        handler.postDelayed({
            SupervisionForegroundService.start(
                context = this,
                minutesGoal = minutesGoal,
                wordsGoal = 1,
                ruleMode = RuleMode.DURATION,
            )
        }, 1000)

        handler.postDelayed({
            TargetAppLauncher.launchTargetApp(this)
        }, 1400)

        handler.postDelayed({
            finish()
        }, 1800)
    }

    companion object {
        const val EXTRA_MINUTES_GOAL = "extra_minutes_goal"
    }
}

