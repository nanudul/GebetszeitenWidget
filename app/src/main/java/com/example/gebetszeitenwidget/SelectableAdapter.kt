package com.example.gebetszeitenwidget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SelectableAdapter(
    private val items: MutableList<String>,
    private val context: Context,
    private val db: AppDatabase,
    private val onItemSelected: (Int) -> Unit
) : RecyclerView.Adapter<SelectableAdapter.ViewHolder>() {

    // Saves the index of the current selected city from the sharedPreferences
    private val sharedPreferences =
        context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
    private var selectedPosition = sharedPreferences.getInt("selectedPosition", -1)

    // Saves at which position the delete button is visible (-1 if nothing is selected)
    private var deleteButtonVisiblePosition = -1

    // In this class there are the title and the buttons for one city entry
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvItemTitle)
        val radioButton: RadioButton = view.findViewById(R.id.radioBtn)
        val deleteButton: ImageButton = view.findViewById(R.id.btn_delete)

        private val myStaticScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        init {
            // Normal click-Listener
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // If a Delete-Button was visible it goes "gone" again with a normal click
                    if (deleteButtonVisiblePosition != -1) {
                        val prevDeletePos = deleteButtonVisiblePosition
                        deleteButtonVisiblePosition = -1
                        notifyItemChanged(prevDeletePos)
                    }
                    // make new selected city
                    val previousPosition = selectedPosition
                    selectedPosition = position

                    // SharedPreferences get updated
                    sharedPreferences.edit().putInt("selectedPosition", selectedPosition).apply()

                    notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)

                    // Callback to the Activity
                    onItemSelected(selectedPosition)
                }
            }

            // Long-Click-Listener
            itemView.setOnLongClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val previousDeletePos = deleteButtonVisiblePosition

                    // Toggle: If it was visible it goes "gone" again
                    deleteButtonVisiblePosition =
                        if (deleteButtonVisiblePosition == position) -1 else position

                    // Update UI (Select button goes "Gone" and delete button goes visible)
                    if (previousDeletePos != -1) notifyItemChanged(previousDeletePos)
                    notifyItemChanged(position)
                    // prohibits that a normal click happens immediately afterward
                    true
                } else {
                    false
                }
            }

            // Delete button
            deleteButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // delete element from list
                    myStaticScope.launch {
                        db.prayingCityDao().deletePrayingCity(
                            db.prayingCityDao().getAllPrayingCities().toMutableList()[position]
                        )
                    }
                    items.removeAt(position)
                    deleteButtonVisiblePosition = -1 // reset

                    // If the deleted element was the selected one, set selected element to the first one
                    if (selectedPosition == position) {
                        selectedPosition = 0
                        sharedPreferences.edit().putInt("selectedPosition", 0).apply()
                    }
                    // Index is pushed up by one
                    else if (selectedPosition > position) {
                        selectedPosition--
                        sharedPreferences.edit().putInt("selectedPosition", selectedPosition).apply()
                    }

                    // update RecyclerView
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

        // Set radio button to selected position
        holder.radioButton.isChecked = (position == selectedPosition)

        // Set buttons visibilities
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