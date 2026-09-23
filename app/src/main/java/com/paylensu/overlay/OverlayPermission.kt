package com.paylensu.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Guía y verificación del permiso "Mostrar sobre otras apps". */
object OverlayPermission {

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openSettings(context: Context) {
        val i = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }
}
