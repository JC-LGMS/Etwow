package com.etwow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.etwow.data.Settings

/** Relance la connexion automatique après un redémarrage du téléphone. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Settings(context).autoConnect) return
        ScooterService.start(context)
    }
}
