package com.cometncloud.houndhabit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.cometncloud.houndhabit.shared.notifications.NotificationManager as HoundNotificationManager
import com.cometncloud.houndhabit.shared.util.HapticManager
import com.cometncloud.houndhabit.ui.theme.HoundHabitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        HapticManager.init(this)
        HoundNotificationManager.ensureChannels(this)
        enableEdgeToEdge()
        setContent {
            HoundHabitTheme {
                AppRouter()
            }
        }
    }
}
