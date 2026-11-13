package com.example.gebetszeitenwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val db = AppDatabase.getDatabase(applicationContext)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val inputCity = findViewById<EditText>(R.id.input_field_city)
        val inputURL = findViewById<EditText>(R.id.input_field_url)
        val btnApply = findViewById<Button>(R.id.btn_apply)
        val btnDeleteDb = findViewById<Button>(R.id.btn_delete_db)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val context: Context = applicationContext
        val sharedPreferences =
            context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)

        var dataList: MutableList<String>

        // Beispiel-Daten
        lifecycleScope.launch {
            dataList = db.prayingCityDao().allPrayingCities().map { it.city }.toMutableList()

            // Adapter zuweisen und Klick-Verhalten definieren
            val adapter = SelectableAdapter(
                dataList,
                context,
                db
            ) { selectedIndex ->
                val selectedItem = dataList[selectedIndex]
                sharedPreferences.edit().putInt("selectedPosition", selectedIndex).apply()
                sharedPreferences.edit().putString("city", selectedItem).apply()
                lifecycleScope.launch {
                    val regionId = db.prayingCityDao().getRegionId(selectedItem)
                    sharedPreferences.edit().putString("regionId", regionId).apply()
                    updateWidget()
                }
            }

            recyclerView.adapter = adapter

            btnApply.setOnClickListener {
                lifecycleScope.launch {
                    val regionId = loadRegionId(inputURL.text.toString())
                    if (regionId != null) {
                        db.prayingCityDao().insert(
                            PrayingCity(
                                city = inputCity.text.toString(),
                                regionId = regionId
                            )
                        )
                        dataList.add(inputCity.text.toString())
                        adapter.updateList()
                    }else {
                        Toast.makeText(context, "Could not add city!", Toast.LENGTH_SHORT).show()

                        // button jsons delete
                        // sync button funktionlaität
                        // alte monate löschen
                    }
                }
            }
            btnDeleteDb.setOnClickListener {
                lifecycleScope.launch {
                    db.prayingCityDao().deleteAllCities()
                    adapter.updateList()
                }
            }
        }
    }

    private suspend fun loadRegionId(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Webseite laden
                val doc = Jsoup.connect(url).get()

                // 2. Alle <script>-Tags durchsuchen
                val scripts = doc.select("script")
                var variableWert: String? = null

                for (script in scripts) {
                    val scriptText = script.html()

                    // Prüfen, ob das Skript unsere Variable enthält (z. B. "meineVariable = '123'")
                    if (scriptText.contains("salat_times_widget_settings")) {
                        // Regex sucht nach dem Variablennamen, gefolgt von = und einem String in Anführungszeichen
                        val regex = Regex(""""region"\s*:\s*"([^"]+)"""")
                        val match = regex.find(scriptText)

                        if (match != null) {
                            variableWert = match.groupValues[1]
                            break
                        }
                    }
                }

                if (variableWert != null) {
                    println("Gefundener Variablenwert: $variableWert")
                    return@withContext variableWert
                } else {
                    println("Variable im Script-Tag nicht gefunden.")
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }

    private fun updateWidget() {
        val context: Context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Ersetze 'MyWidgetProvider' mit dem Klassennamen deines eigenen Widget-Providers
        val thisWidget = ComponentName(context, GebetszeitenWidgetProvider::class.java)

        // Widget beim AppWidgetManager aktualisieren lassen
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        for (widgetId in appWidgetIds) {
            GebetszeitenWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
        }
    }
}
