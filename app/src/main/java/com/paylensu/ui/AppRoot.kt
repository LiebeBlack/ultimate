package com.paylensu.ui

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.paylensu.hud.HudTheme

/**
 * Router mínimo: visor ↔ ajustes, con transición fade+slide de 140 ms
 * (AnimatedContent: solo transforma las dos pantallas, sin Navigation lib).
 * El ScannerModel se recuerda AQUÍ para sobrevivir la ida y vuelta: la cámara
 * no se re-vincula y los flows del carrito/tasa no se re-suscriben.
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val model = remember { ScannerModel(context.applicationContext as Application) }

    AnimatedContent(
        targetState = showSettings,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(140)) + slideInVertically(tween(160)) { it / 14 }) togetherWith
                (fadeOut(tween(110)) + slideOutVertically(tween(140)) { it / 14 })
        },
        label = "rootRouter",
    ) { settings ->
        if (settings) {
            SettingsScreen(onBack = { showSettings = false })
        } else {
            ScannerScreen(
                model = model,
                onOpenSettings = { showSettings = true },
            )
        }
    }
}
