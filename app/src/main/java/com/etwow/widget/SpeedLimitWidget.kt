package com.etwow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.etwow.R
import com.etwow.data.Connection
import com.etwow.data.ScooterBus
import com.etwow.protocol.SpeedModes
import com.etwow.service.ScooterService

/**
 * Widget 1x1 : le logo et une coche verte à 25 km/h, rouge sinon.
 * Un appui passe à 25 km/h (jamais l'inverse) et ne fait pas de BLE lui-même.
 */
class SpeedLimitWidget : AppWidgetProvider() {

    companion object {
        fun update(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SpeedLimitWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val snap = ScooterBus.state.value
            val connected = snap.connection == Connection.Connected
            val at25 = connected && snap.mode == SpeedModes.L3_25
            val check = when {
                !connected -> R.drawable.ic_dot_gray
                at25 -> R.drawable.ic_dot_green
                else -> R.drawable.ic_dot_red
            }
            val description = when {
                !connected -> R.string.widget_offline
                at25 -> R.string.widget_at25
                else -> R.string.widget_not25
            }
            val views = RemoteViews(context.packageName, R.layout.widget_speed_limit)
            views.setImageViewResource(R.id.widget_dot, check)
            if (Build.VERSION.SDK_INT >= 31) {
                views.setContentDescription(R.id.widget_root, context.getString(description))
            }
            val set25 = PendingIntent.getForegroundService(
                context,
                1,
                Intent(context, ScooterService::class.java).setAction(ScooterService.ACTION_SET_25),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, set25)
            return views
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context)
    }
}
