package com.jnu.hardvocabguard.ui.launch

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.jnu.hardvocabguard.core.TargetAppLauncher

class LaunchTargetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launched = TargetAppLauncher.launchTargetApp(this)
        if (launched) {
            moveTaskToBack(true)
            finishAffinity()
            finishAndRemoveTask()
        } else {
            finish()
        }
        overridePendingTransition(0, 0)
    }
}

