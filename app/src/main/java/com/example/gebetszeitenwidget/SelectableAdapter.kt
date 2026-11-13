package com.example.gebetszeitenwidget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.internal.notify

class SelectableAdapter(
    private val items: MutableList<String>,
    private val context: Context,
    private val db: AppDatabase,
    private val onItemSelected: (Int) -> Unit
) : RecyclerView.Adapter<SelectableAdapter.ViewHolder>() {

    // Speichert den Index des aktuell ausgewählten Elements (-1 = keins)
    private val sharedPreferences =
        context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
    private var selectedPosition = sharedPreferences.getInt("selectedPosition", -1)

    // Speichert, bei welchem Item der Delete-Button gerade sichtbar ist (-1 = bei keinem)
    private var deleteButtonVisiblePosition = -1

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvItemTitle)
        val radioButton: RadioButton = view.findViewById(R.id.radioBtn)
        val deleteButton: ImageButton = view.findViewById(R.id.btn_delete)

        private val myStaticScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        init {
            // 1. Normaler Klick-Listener (bleibt wie er war)
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Wenn ein Delete-Button offen war, diesen beim normalen Klick wieder verstecken
                    if (deleteButtonVisiblePosition != -1) {
                        val prevDeletePos = deleteButtonVisiblePosition
                        deleteButtonVisiblePosition = -1
                        notifyItemChanged(prevDeletePos)
                        //deleteButton.visibility = View.GONE
                        //radioButton.visibility = View.VISIBLE
                    }
                    val previousPosition = selectedPosition
                    selectedPosition = position

                    // SharedPreferences direkt im Adapter aktualisieren (optional, falls gewünscht)
                    sharedPreferences.edit().putInt("selectedPosition", selectedPosition)
                        .apply()

                    // Aktualisiere nur die betroffenen Elemente für bessere Performance
                    notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)

                    // Callback an die Activity/Fragment übergeben
                    onItemSelected(selectedPosition)
                }
            }

            // 2. NEU: Long-Click-Listener (Gedrückt halten)
            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val previousDeletePos = deleteButtonVisiblePosition

                    // Toggle: Wenn es bereits offen war, wieder schließen, sonst für dieses Item öffnen
                    deleteButtonVisiblePosition =
                        if (deleteButtonVisiblePosition == position) -1 else position

                    // UI aktualisieren (alten Button verstecken, neuen anzeigen)
                    if (previousDeletePos != -1) notifyItemChanged(previousDeletePos)
                    notifyItemChanged(position)
                    // 'true' gibt zurück, dass das Event konsumiert wurde
                    // (verhindert, dass danach direkt ein normaler Klick ausgelöst wird)
                    true
                } else {
                    false
                }
            }
            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Element aus der Liste löschen
                    myStaticScope.launch {
                        db.prayingCityDao().deletePrayingCity(
                            db.prayingCityDao().allPrayingCities().toMutableList()[position]
                        )
                    }
                    items.removeAt(position)
                    deleteButtonVisiblePosition = -1 // Zurücksetzen

                    // Wenn das gelöschte Element das ausgewählte war, Auswahl zurücksetzen
                    if (selectedPosition == position) {
                        selectedPosition = -1
                        sharedPreferences.edit().putInt("selectedPosition", -1).apply()
                    } else if (selectedPosition > position) {
                        selectedPosition-- // Index verschiebt sich nach unten
                        sharedPreferences.edit().putInt("selectedPosition", selectedPosition)
                            .apply()
                    }

                    // RecyclerView über die Änderung informieren
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, items.size)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_selectable, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item

        // Zustand des RadioButtons basierend auf der Auswahl setzen
        holder.radioButton.isChecked = (position == selectedPosition)

        // Sichtbarkeit des Delete-Buttons basierend auf dem Zustand steuern
        if (position == deleteButtonVisiblePosition) {
            holder.deleteButton.visibility = View.VISIBLE
            holder.radioButton.visibility = View.GONE
        } else {
            holder.deleteButton.visibility = View.GONE
            holder.radioButton.visibility = View.VISIBLE
        }
    }

    fun updateList() {
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
}