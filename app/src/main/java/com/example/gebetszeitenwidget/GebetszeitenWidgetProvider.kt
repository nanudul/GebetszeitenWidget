package com.example.gebetszeitenwidget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresPermission
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.io.File


class GebetszeitenWidgetProvider : AppWidgetProvider() {


    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private val myStaticScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val prayingNames = arrayOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha", "Fajr")

        @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val gson = Gson()
            val sharedPreferences =
                context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
            var views = RemoteViews(context.packageName, R.layout.gebetszeiten_widget)

            views.setTextViewText(R.id.city, sharedPreferences.getString("city", "Stadt"))

            createIntents(context, views)

            val current = LocalDate.now()
            val jsonThisMonth = loadJsonFromFile(context, current.monthValue.toString() + "-" + current.year.toString() + "-" + sharedPreferences.getString("city", "notFound"))
            var jsonNextMonth: String?
            if (current.monthValue == 12){
                jsonNextMonth = loadJsonFromFile(context, "1-" + (current.year+1).toString() + "-" + sharedPreferences.getString("city", "notFound"))
            } else {
                jsonNextMonth = loadJsonFromFile(context, (current.monthValue+1).toString() + "-" + current.year.toString() + "-" + sharedPreferences.getString("city", "notFound"))
            }

            myStaticScope.launch {
                if (jsonThisMonth == null || jsonNextMonth == null){//LocalDate.now().dayOfMonth // wenn dieser monat und dieser monat+1 datei noch nicht da, dann holen + wenn abbruch dann button rot werden lassen

                    //sharedPreferences.edit().putString("prayingTimes", jsonString).apply()

                    val jsonCurrentMonth = gson.toJson(getPrayingTimes(context, current.monthValue, current.year))
                    saveJsonToFile(context, jsonCurrentMonth, current.monthValue.toString() + "-" + current.year.toString() + "-" + sharedPreferences.getString("city", "notFound"))
                    if (current.monthValue == 12){
                        val jsonNextMonth = gson.toJson(getPrayingTimes(context, 1, current.year+1))
                        saveJsonToFile(context, jsonNextMonth, "1-" + (current.year+1).toString() + "-" + sharedPreferences.getString("city", "notFound"))
                    } else {
                        val jsonNextMonth = gson.toJson(getPrayingTimes(context, current.monthValue+1, current.year))
                        saveJsonToFile(context, jsonNextMonth, (current.monthValue+1).toString() + "-" + current.year.toString() + "-" + sharedPreferences.getString("city", "notFound"))
                    }
                }

                // Auslesen von sharedPreferences
                val geladenerJsonPrayingTimes = sharedPreferences.getString("prayingTimes", null)
                val geladenePrayingTimes = if (geladenerJsonPrayingTimes != null) {
                    gson.fromJson(geladenerJsonPrayingTimes, Array<String>::class.java)
                } else {
                    arrayOf("")
                }

                val prayingIndex = calculatePrayingTime(geladenePrayingTimes)
                views.setTextViewText(R.id.prayer_now_or_next, "Now: ")
                views.setTextViewText(R.id.prayer_name, prayingNames.get(prayingIndex))
                views.setTextViewText(R.id.prayer_time, geladenePrayingTimes.get(prayingIndex + 1))

                when (prayingIndex) {
                    // Sonderfall: Sunrise übersprungen
                    1 -> {
                        views.setTextViewText(R.id.prayer_name, prayingNames.get(prayingIndex + 1))
                        views.setTextViewText(R.id.prayer_now_or_next, "Next: ")
                    }
                    // Sonderfall: Bei jetzigem Isha Fajr-Beginn und Endzeit angezeigen
                    5 -> views.setTextViewText(
                        R.id.prayer_time,
                        geladenePrayingTimes.get(prayingIndex + 1) + " - " + geladenePrayingTimes.get(
                            prayingIndex + 2
                        )
                    )
                }

                makeTimer(geladenePrayingTimes.get(prayingIndex + 1), context, views, false)

                views = prayedBehavior(context, views)

                // Widget aktualisieren
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        fun saveJsonToFile(context: Context, jsonString: String, fileName: String) {
            try {
                // Erstellt/Öffnet die Datei im internen Verzeichnis der App
                val file = File(context.filesDir, fileName)

                // Schreibt den JSON-Text in die Datei
                file.writeText(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun loadJsonFromFile(context: Context, fileName: String): String? {
            return try {
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    file.readText() // Liest den gesamten Inhalt als String aus
                } else {
                    null // Datei existiert noch nicht
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        fun createIntents(context: Context, views: RemoteViews){
            // 1. Erstellen Sie einen Intent, der an Ihren Provider gesendet wird
            val dummyIntent = Intent(context, GebetszeitenWidgetProvider::class.java).apply {
                action = "ACTION_DUMMY_CLICK"
            }
            // 2. Erstellen Sie einen getBroadcast-PendingIntent (wichtig: KEIN getActivity!)
            val dummyPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                dummyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.outerLayer, dummyPendingIntent)

            val syncIntent = Intent(context, GebetszeitenWidgetProvider::class.java).apply {
                action = "ACTION_SYNC"
            }
            val syncPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                syncIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_sync, syncPendingIntent)

            val settingsIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val settingsPendingIntent = PendingIntent.getActivity(
                context,
                0,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_settings, settingsPendingIntent)

            // 1. Intent für den Klick erstellen
            val intentPrayed = Intent(context, GebetszeitenWidgetProvider::class.java).apply {
                action = "ACTION_PRAYED"
            }
            // 2. PendingIntent für Widgets erzeugen (Wichtig: FLAG_IMMUTABLE oder FLAG_MUTABLE)
            val pendingIntentPrayed = PendingIntent.getBroadcast(
                context,
                0,
                intentPrayed,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            // 3. Dem Button den Klick-Listener zuweisen
            views.setOnCheckedChangeResponse(
                R.id.checkbox_gebetet,
                RemoteViews.RemoteResponse.fromPendingIntent(pendingIntentPrayed)
            )
        }

        @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
        fun makeTimer(
            prayingTime: String,
            context: Context,
            views: RemoteViews,
            edgeCaseOtherTriggerTime: Boolean
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Intent, der an unseren Receiver geschickt wird
            val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
                action = "ACTION_TIMER_EXPIRED"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val currentTime = LocalDateTime.now()
            val givenPrayingTime = LocalDateTime.of(
                LocalDate.now(),
                LocalTime.parse(prayingTime, DateTimeFormatter.ofPattern("HH:mm"))
            )
            val givenPrayingTimeNextDay = LocalDateTime.of(
                LocalDate.now().plusDays(1),
                LocalTime.parse(prayingTime, DateTimeFormatter.ofPattern("HH:mm"))
            )
            var durationInMillis: Long
            if (currentTime < givenPrayingTime) {
                durationInMillis = Duration.between(currentTime, givenPrayingTime).seconds * 1000
            } else {
                durationInMillis =
                    Duration.between(currentTime, givenPrayingTimeNextDay).seconds * 1000
            }

            // Zielzeitpunkt berechnen (Aktuelle Zeit + Dauer des Timers)
            // ELAPSED_REALTIME_WAKEUP weckt das Gerät auch aus dem Energiesparmodus auf
            var triggerTime = SystemClock.elapsedRealtime() + durationInMillis

            views.setChronometer(R.id.timer, triggerTime, null, true)

            if (edgeCaseOtherTriggerTime) {
                // Auslesen von sharedPreferences
                val gson = Gson()
                val sharedPreferences =
                    context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
                val geladenerJsonPrayingTimes = sharedPreferences.getString("prayingTimes", null)
                val geladenePrayingTimes = if (geladenerJsonPrayingTimes != null) {
                    gson.fromJson(geladenerJsonPrayingTimes, Array<String>::class.java)
                } else {
                    arrayOf("")
                }
                triggerTime = SystemClock.elapsedRealtime() + Duration.between(
                    currentTime, LocalDateTime.of(
                        LocalDate.now().plusDays(1),
                        LocalTime.parse(
                            geladenePrayingTimes.get(6),
                            DateTimeFormatter.ofPattern("HH:mm")
                        )
                    )
                ).seconds * 1000
            }

            // Exakten Alarm setzen
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }

        fun calculatePrayingTime(prayingTimeArray: Array<String>): Int {
            val currentTime = LocalDateTime.now()
            prayingTimeArray.forEachIndexed { index, prayingTime ->
                val givenPrayingTime = LocalDateTime.of(
                    LocalDate.now(),
                    LocalTime.parse(prayingTime, DateTimeFormatter.ofPattern("HH:mm"))
                )
                val givenPrayingTimeNextDay = LocalDateTime.of(
                    LocalDate.now().plusDays(1),
                    LocalTime.parse(prayingTime, DateTimeFormatter.ofPattern("HH:mm"))
                )
                if (currentTime < givenPrayingTime && index < 6) {
                    return index - 1
                } else if (index == 6 && currentTime < givenPrayingTimeNextDay) {
                    return index - 1
                }
            }
            return -1
        }

        suspend fun getPrayingTimes(context: Context, month: Int, year: Int): String? {
            return withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                // 1. Die URL des POST-Requests aus dem Netzwerk-Tab
                //val babenhausen = "10898"
                //val darmstadt = "11079"
                val sharedPreferences =
                    context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
                val regionId = sharedPreferences.getString("regionId", "11012")
                val url =
                    "https://diegebetszeiten.de/wp-content/plugins/salat-times/ajax.php?region=" + regionId + "&calculation_method=diyanet&time_zone=Europe%2FBerlin&time_format=24h&" +
                            "unix_timestamp=" + (System.currentTimeMillis() / 1000).toString() + "&calendar=gregorian&year=" + year.toString() + "&month=" + month.toString() + "&action=get_month&locale=de_DE"

                // 2. Da Content-Length: 0 ist, übergeben wir einen leeren JSON-Body
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = "".toRequestBody(mediaType)

                // 3. Den Request zusammenbauen (inklusive optionaler Header wie Cookies oder User-Agent)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36 Edg/156.0.0.0"
                    )
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .build()

                // 4. Request im Hintergrund ausführen
                val responseData = try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            // Handle or throw when the server returns an error code (e.g., 404, 500)
                            throw java.io.IOException("Unexpected HTTP response: ${response.code}")
                        }
                        response.body?.string()
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Log the timeout or fall back to cached prayer times
                    null
                } catch (e: java.io.IOException) {
                    // Handle other network/IO errors (e.g., no internet connection)
                    null
                }
                return@withContext responseData//parseJsonData(context, responseData, dayOfMonth)
            }
        }

        fun parseJsonData(context: Context, jsonString: String?, dayofMonth: Int): Array<String> {
            try {
                val jsonObject =
                    JSONObject(jsonString).getJSONObject("details").getJSONArray("days")
                        .getJSONObject(dayofMonth - 1).getJSONObject("times")
                val jsonObjectNextDay =
                    JSONObject(jsonString).getJSONObject("details").getJSONArray("days")
                        .getJSONObject(dayofMonth).getJSONObject("times")
                val fajr = jsonObject.get("fajr").toString()
                val sunrise = jsonObject.get("sunrise").toString()
                val dhuhr = jsonObject.get("dhuhr").toString()
                val asr = jsonObject.get("asr").toString()
                val maghrib = jsonObject.get("maghrib").toString()
                val isha = jsonObject.get("isha").toString()
                val nextFajr = jsonObjectNextDay.get("fajr").toString()
                val nextSunrise = jsonObjectNextDay.get("sunrise").toString()
                val today = JSONObject(jsonString).getJSONObject("details").getJSONObject("today")
                    .get("day").toString().toInt()
                val sharedPreferences =
                    context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
                sharedPreferences.edit().putInt("today", today).apply()
                return arrayOf(fajr, sunrise, dhuhr, asr, maghrib, isha, nextFajr, nextSunrise)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return arrayOf("parseJsonData() failed")
        }

        fun prayedBehavior(context: Context, views: RemoteViews): RemoteViews{
            // Auslesen von sharedPreferences
            val gson = Gson()
            val sharedPreferences = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
            val geladenerJsonPrayingTimes = sharedPreferences.getString("prayingTimes", null)
            val geladenePrayingTimes = if (geladenerJsonPrayingTimes != null) {
                gson.fromJson(geladenerJsonPrayingTimes, Array<String>::class.java)
            } else {
                arrayOf("")
            }

            val isChecked = sharedPreferences.getBoolean("prayed", false)

            val prayingIndex = calculatePrayingTime(geladenePrayingTimes)
            run edgeCaseSunrise@{
                if (isChecked) {
                    if (prayingIndex == 1) {
                        views.setCompoundButtonChecked(R.id.checkbox_gebetet, false)
                        sharedPreferences.edit().putBoolean("prayed", false).apply()
                        return@edgeCaseSunrise
                    }
                    views.setTextViewText(R.id.prayer_now_or_next, "Next: ")
                    views.setTextViewText(
                        R.id.prayer_name,
                        prayingNames.get(prayingIndex + 1)
                    )
                    views.setTextViewText(
                        R.id.prayer_time,
                        geladenePrayingTimes.get(prayingIndex + 1) + " - " + geladenePrayingTimes.get(
                            prayingIndex + 2
                        )
                    )

                    // Sonderfall: Sunrise überspringen
                    if (prayingIndex == 0) {
                        views.setTextViewText(
                            R.id.prayer_name,
                            prayingNames.get(prayingIndex + 2)
                        )
                        views.setTextViewText(
                            R.id.prayer_time,
                            geladenePrayingTimes.get(prayingIndex + 2) + " - " + geladenePrayingTimes.get(
                                prayingIndex + 3
                            )
                        )
                        makeTimer(geladenePrayingTimes.get(prayingIndex + 2), context, views, false)
                    }
                    if (prayingIndex == 5) {
                        makeTimer(geladenePrayingTimes.get(prayingIndex + 2), context, views, true)
                        views.setViewVisibility(R.id.info_text, View.VISIBLE)
                    }
                } else {
                    views.setCompoundButtonChecked(R.id.checkbox_gebetet, false)
                    sharedPreferences.edit().putBoolean("prayed", false).apply()
                    views.setTextViewText(R.id.prayer_now_or_next, "Now: ")
                    views.setTextViewText(
                        R.id.prayer_name,
                        prayingNames.get(prayingIndex)
                    )
                    views.setTextViewText(
                        R.id.prayer_time,
                        geladenePrayingTimes.get(prayingIndex + 1)
                    )
                    views.setViewVisibility(R.id.info_text, View.INVISIBLE)
                    if (prayingIndex == 5 || prayingIndex == 0) {
                        // Sonderfall: Bei jetzigem Isha Fajr-Beginn und Endzeit angezeigen
                        if (prayingIndex == 5) {
                            views.setTextViewText(
                                R.id.prayer_time,
                                geladenePrayingTimes.get(prayingIndex + 1) + " - " + geladenePrayingTimes.get(
                                    prayingIndex + 2
                                )
                            )
                        }
                        makeTimer(geladenePrayingTimes.get(prayingIndex + 1), context, views, false)
                    }
                }
            }
            return views
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Prüfen, ob unsere Aktion ausgelöst wurde
        if (intent.action == "ACTION_PRAYED") {
            val isChecked = intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false)
            val sharedPreferences = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("prayed", isChecked).apply()
            var views = RemoteViews(context.packageName, R.layout.gebetszeiten_widget)
            views = prayedBehavior(context, views)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget: ComponentName =
                ComponentName(context, GebetszeitenWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            appWidgetManager.updateAppWidget(allWidgetIds, views)
        }
        if (intent.action == "ACTION_SYNC"){

        }
    }
}