package com.danjonesapps.vocabr

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView

class ListAdapter(
    private val context: Context,
    private var listsData: MutableList<VocabList>,
    private val onListUpdated: (MutableList<VocabList>) -> Unit
) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    inner class ViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        val listName: TextView = itemView.findViewById(R.id.listFileName)
        val avgLearntScore: TextView = itemView.findViewById(R.id.avgLearntScore)
        val numTerms: TextView = itemView.findViewById(R.id.numTerms)
        val dateLastTested: TextView = itemView.findViewById(R.id.dateLastTested)
        val radioImageView: ImageView = itemView.findViewById(R.id.radio_button)
        val container: ConstraintLayout = itemView.findViewById(R.id.item_container)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater
            .from(context)
            .inflate(
                R.layout.item_list,
                parent,
                false
            )

        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return listsData.size
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val item = listsData[position]

        with(holder) {
            listName.text = item.fileName.substringBeforeLast(".")
            avgLearntScore.text = "${item.cachedStats?.learntScore?.toInt() ?: 0}% (${item.cachedStats?.filteredLearntScore?.toInt() ?: 0}%)"
            numTerms.text = "${item.cachedStats?.numTerms ?: 0} (${item.cachedStats?.filteredNumTerms ?: 0}) terms"
            dateLastTested.text = item.cachedStats?.dateLastTested ?: ""

            if (position == 0) {

                ViewCompat.setBackgroundTintList(
                    container,
                    ContextCompat.getColorStateList(
                        context,
                        R.color.term_white_def_high
                    )
                )

                radioImageView.setImageResource(
                    R.drawable.radio_checked
                )

            } else {

                ViewCompat.setBackgroundTintList(
                    container,
                    ContextCompat.getColorStateList(
                        context,
                        R.color.button_gray
                    )
                )

                radioImageView.setImageResource(
                    R.drawable.radio_unchecked
                )
            }

            itemView.setOnClickListener {
                moveItemToTop(position)
            }
        }
    }

    private fun moveItemToTop(
        position: Int
    ) {
        if (position == 0) return

        val clickedItem = listsData.removeAt(position)

        listsData.add(0, clickedItem)

        notifyItemMoved(position, 0)
        notifyItemRangeChanged(
            0,
            position + 1
        )

        onListUpdated(listsData)

        (context as? AppCompatActivity)
            ?.findViewById<RecyclerView>(
                R.id.list_recycler
            )
            ?.scrollToPosition(0)
    }

    fun submitData(
        newData: MutableList<VocabList>
    ) {
        listsData = newData
        notifyDataSetChanged()
    }

    fun getSelectedList(): VocabList? {
        return listsData.firstOrNull()
    }
}