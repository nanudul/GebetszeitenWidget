package com.example.gebetszeitenwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.RequiresPermission

class WidgetUpdateReceiver : BroadcastReceiver() {
    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onReceive(context: Context, intent: Intent) {
        // Prüfen, ob das Event ausgelöst wurde
        when (intent.action) {
            "ACTION_TIMER_EXPIRED", "ACTION_SETTINGS_CHANGED" -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, GebetszeitenWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

                val intentPrayed = Intent(context, GebetszeitenWidgetProvider::class.java).apply {
                    action = "ACTION_PRAYED"
                }
                context.sendBroadcast(intentPrayed)

                // Aktualisierungs-Methode deines WidgetProviders aufrufen
                for (widgetId in appWidgetIds) {
                    GebetszeitenWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
                }
            }
        }
    }
}