package com.danjonesapps.vocabr

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import java.time.format.DateTimeFormatter

class ListAdapter(
    private val context: Context,
    private var items: MutableList<TermList>,
    private val onListUpdated: (MutableList<TermList>) -> Unit
) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val listName: TextView = itemView.findViewById(R.id.listFileName)
        val avgLearntScore: TextView = itemView.findViewById(R.id.avgLearntScore)
        val numTerms: TextView = itemView.findViewById(R.id.numTerms)
        val dateLastTested: TextView = itemView.findViewById(R.id.dateLastTested)
        val container: ConstraintLayout = itemView.findViewById(R.id.item_container)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_list, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        with(holder) {
            listName.text = item.fileName
            avgLearntScore.text = String.format("learnt score: %.0f%%", item.avgLearntScore)
            numTerms.text = "terms: ${item.numTerms}"
            dateLastTested.text = "last tested: ${item.dateLastTested.format(formatter)}"
        }

        // Highlight selected item
        if (position == 0) {
            ViewCompat.setBackgroundTintList(
                holder.container,
                ContextCompat.getColorStateList(context, R.color.term_white_def_high)
            )
            holder.avgLearntScore.setTextColor(ContextCompat.getColorStateList(context, R.color.term_white))
        } else {
            ViewCompat.setBackgroundTintList(
                holder.container,
                ContextCompat.getColorStateList(context, R.color.button_gray)
            )
            holder.avgLearntScore.setTextColor(ContextCompat.getColorStateList(context, R.color.learn_score_blue))
        }

        holder.itemView.setOnClickListener {
            moveItemToTop(position)
        }
    }

    private fun moveItemToTop(position: Int) {
        if (position != 0) {
            val clickedItem = items.removeAt(position)
            items.add(0, clickedItem)
            notifyItemMoved(position, 0)
            // Refresh the backgrounds of the moved item and old top
            for (i in 0 until position + 1) {
                notifyItemChanged(i)
            }
            onListUpdated(items)

            // Scroll to top
            (context as? androidx.appcompat.app.AppCompatActivity)?.let { activity ->
                val recyclerView = activity.findViewById<RecyclerView>(R.id.list_recycler) // replace with your RecyclerView ID
                recyclerView.scrollToPosition(0)
            }
        }
    }

    fun updateRecyclerView(delete: Boolean = false) {
        if (delete) {
            notifyItemRemoved(0)
        }
        else {
            notifyItemInserted(0)
        }
        notifyItemRangeChanged(0, items.size)

        // Scroll to top
        (context as? androidx.appcompat.app.AppCompatActivity)?.let { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.list_recycler) // replace with your RecyclerView ID
            recyclerView.scrollToPosition(0)
        }
    }
}
