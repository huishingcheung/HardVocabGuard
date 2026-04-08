package com.jnu.hardvocabguard.ui.launch

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.jnu.hardvocabguard.core.TargetAppLauncher

class LaunchTargetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TargetAppLauncher.launchTargetApp(this)
        finish()
        overridePendingTransition(0, 0)
    }
}

