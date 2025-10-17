package com.danjonesapps.vocabr

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView

class ListAdapter(
    private val context: Context,
    private var listsData: MutableList<SavedListData>,
    private val onListUpdated: (MutableList<SavedListData>) -> Unit
) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val listName: TextView = itemView.findViewById(R.id.listFileName)
        val avgLearntScore: TextView = itemView.findViewById(R.id.avgLearntScore)
        val numTerms: TextView = itemView.findViewById(R.id.numTerms)
        val dateLastTested: TextView = itemView.findViewById(R.id.dateLastTested)
        val radioImageView: ImageView = itemView.findViewById(R.id.radio_button)
        val container: ConstraintLayout = itemView.findViewById(R.id.item_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_list, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = listsData.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listStringData = listsData[position].toDisplayStrings()

        with(holder) {
            listName.text = listStringData["fileName"]
            avgLearntScore.text = listStringData["score"]
            numTerms.text = listStringData["terms"]
            dateLastTested.text = listStringData["lastTested"]

            // Highlight selected item
            if (position == 0) {
                ViewCompat.setBackgroundTintList(
                    container,
                    ContextCompat.getColorStateList(context, R.color.term_white_def_high)
                )

                // Change image to "checked" icon
                radioImageView.setImageResource(R.drawable.radio_checked)
            } else {
                ViewCompat.setBackgroundTintList(
                    container,
                    ContextCompat.getColorStateList(context, R.color.button_gray)
                )

                // Change image to "unchecked" icon
                radioImageView.setImageResource(R.drawable.radio_unchecked)
            }

            itemView.setOnClickListener {
                moveItemToTop(position)
            }
        }
    }

    private fun moveItemToTop(position: Int) {
        if (position != 0) {
            val clickedItem = listsData.removeAt(position)
            listsData.add(0, clickedItem)
            notifyItemMoved(position, 0)
            notifyItemRangeChanged(0, position + 1)

            // Update listsData in MainActivity.kt
            onListUpdated(listsData)

            // Scroll to top
            (context as? androidx.appcompat.app.AppCompatActivity)?.let { activity ->
                val recyclerView = activity.findViewById<RecyclerView>(R.id.list_recycler) // replace with your RecyclerView ID
                recyclerView.scrollToPosition(0)
            }

            writeListDataToListsFile(context, listsData)
        }
    }

    fun updateRecyclerView(delete: Boolean = false) {
        if (delete) {
            notifyItemRemoved(0)
        }
        else {
            notifyItemInserted(0)
        }
        notifyItemRangeChanged(0, listsData.size)

        // Scroll to top
        (context as? androidx.appcompat.app.AppCompatActivity)?.let { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_recycler) // replace with your RecyclerView ID
            recyclerView.scrollToPosition(0)
        }

        writeListDataToListsFile(context, listsData)
    }
}
