package com.example.gebetszeitenwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
        // set main layout
        setContentView(R.layout.activity_main)
        val db = AppDatabase.getDatabase(applicationContext)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView) // This is the selectable list
        recyclerView.layoutManager = LinearLayoutManager(this)
        val inputCity = findViewById<EditText>(R.id.input_field_city)
        val inputURL = findViewById<EditText>(R.id.input_field_url)
        val btnApply = findViewById<Button>(R.id.btn_apply)
        val btnDeleteDb = findViewById<Button>(R.id.btn_delete_db)

        val context: Context = applicationContext
        val sharedPreferences = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        var dataList: MutableList<String>

        lifecycleScope.launch {
            // Get the saved cities in the database
            dataList = db.prayingCityDao().getAllPrayingCities().map { it.city }.toMutableList()

            // Assign adapter and define click behavior
            val adapter = SelectableAdapter(
                dataList,
                context,
                db
            ) { selectedIndex -> // This happens when a new city is selected in the app
                val selectedItem = dataList[selectedIndex]
                // Set selected position and city
                sharedPreferences.edit().putInt("selectedPosition", selectedIndex).apply()
                sharedPreferences.edit().putString("city", selectedItem).apply()
                lifecycleScope.launch {
                    // Set region id and update widget
                    val regionId = db.prayingCityDao().getRegionId(selectedItem)
                    sharedPreferences.edit().putString("regionId", regionId).apply()
                    updateWidget()
                }
            }

            // Update the adapter
            recyclerView.adapter = adapter

            btnApply.setOnClickListener {
                lifecycleScope.launch {
                    val regionId = loadRegionId(inputURL.text.toString())
                    if (regionId != null) {
                        // Insert new city into the database
                        db.prayingCityDao().insert(PrayingCity(city = inputCity.text.toString(), regionId = regionId))
                        // add new city into the dalalist
                        dataList.add(inputCity.text.toString())
                        // Empty input fields
                        inputURL.setText("")
                        inputCity.setText("")
                        adapter.updateList()
                    }else {
                        Toast.makeText(context, "Could not add city!", Toast.LENGTH_SHORT).show()
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
                // 1. Load url
                val doc = Jsoup.connect(url).get()

                // 2. Scan all <script>-Tags
                val scripts = doc.select("script")
                var variableWert: String? = null

                for (script in scripts) {
                    val scriptText = script.html()

                    // Check if the script has the variables we need
                    if (scriptText.contains("salat_times_widget_settings")) {
                        // Regex looks for the variable name followed by a : and a string in ""
                        val regex = Regex(""""region"\s*:\s*"([^"]+)"""")
                        val match = regex.find(scriptText)

                        if (match != null) {
                            variableWert = match.groupValues[1]
                            break
                        }
                    }
                }
                if (variableWert != null) {
                    return@withContext variableWert
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

        val thisWidget = ComponentName(context, GebetszeitenWidgetProvider::class.java)

        // Widget get updated by the AppWidgetManager
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
        for (widgetId in appWidgetIds) {
            GebetszeitenWidgetProvider.updateAppWidget(context, appWidgetManager, widgetId)
        }
    }
}
