package com.jnu.hardvocabguard.ui.launch

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import com.jnu.hardvocabguard.MainActivity
import com.jnu.hardvocabguard.core.TargetAppLauncher

class LaunchTargetActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launched = TargetAppLauncher.launchTargetApp(this)

        Handler(Looper.getMainLooper()).postDelayed({
            if (!launched) {
                startActivity(
                    android.content.Intent(this, MainActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            finish()
        }, 800)
    }
}

