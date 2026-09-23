package com.paylensu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.paylensu.hud.HudTheme
import com.paylensu.ui.AppRoot

/**
 * Shell de la actividad: OLED negro puro + edge-to-edge. La cámara vive en
 * ScannerScreen y se libera sola en ON_STOP (CameraX bindToLifecycle).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(modifier = Modifier.fillMaxSize().background(HudTheme.Black))
        }
    }
}
