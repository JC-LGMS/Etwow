package com.etwow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.etwow.MainActivity
import com.etwow.R
import com.etwow.data.Connection
import com.etwow.data.ScooterBus
import com.etwow.protocol.SpeedLimitToggle
import com.etwow.protocol.SpeedModes
import com.etwow.service.ScooterService

/**
 * Widget « 25 km/h » : RemoteViews simple, une seule action.
 * Le clic ne fait pas de BLE lui-même, il passe par le service de premier plan.
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
            val views = RemoteViews(context.packageName, R.layout.widget_speed_limit)

            val status = when (snap.connection) {
                Connection.Connected -> snap.mode?.let { SpeedModes.label(it) } ?: "connecté"
                Connection.Connecting -> context.getString(R.string.widget_connecting)
                else -> context.getString(R.string.widget_offline)
            }
            views.setTextViewText(R.id.widget_status, snap.pending ?: status)

            val target25 = snap.mode != SpeedLimitToggle.MODE_25
            views.setTextViewText(
                R.id.widget_hint,
                context.getString(
                    if (target25) R.string.widget_hint_25 else R.string.widget_hint_restore
                ),
            )

            val toggle = PendingIntent.getForegroundService(
                context,
                1,
                Intent(context, ScooterService::class.java).setAction(ScooterService.ACTION_TOGGLE_25),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Tout le widget est cliquable : plus de bouton à viser.
            views.setOnClickPendingIntent(R.id.widget_root, toggle)

            val open = PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_title, open)
            return views
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context)
    }
}
