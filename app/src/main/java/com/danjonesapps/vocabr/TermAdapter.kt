package com.danjonesapps.vocabr

import android.annotation.SuppressLint
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.TextView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class TermAdapter(
    private var terms: List<TermData>,
    private val showTermFirst: Boolean
) : RecyclerView.Adapter<TermAdapter.TermViewHolder>() {

    private var currentQuery: String = ""

    inner class TermViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val termText: TextView = itemView.findViewById(R.id.termText)
        val definitionText: TextView = itemView.findViewById(R.id.definitionText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TermViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_term, parent, false)
        return TermViewHolder(view)
    }

    override fun onBindViewHolder(holder: TermViewHolder, position: Int) {
        val termData = terms[position]
        val highlightColorTerm = ContextCompat.getColor(
            holder.itemView.context,
            if (showTermFirst) {
                R.color.term_white_high
            } else {
                R.color.def_white_high
            }
        )

        val highlightColorDef = ContextCompat.getColor(
            holder.itemView.context,
            if (showTermFirst) {
                R.color.term_white_def_high
            } else {
                R.color.def_white_term_high
            }
        )

        holder.termText.text = highlightTextColor(termData.vocab(showTermFirst), currentQuery, highlightColorTerm)
        holder.definitionText.text = highlightTextColor(termData.vocabDef(showTermFirst), currentQuery, highlightColorDef)
    }

    override fun getItemCount() = terms.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<TermData>, query: String = "") {
        terms = newList
        currentQuery = query
        notifyDataSetChanged()
    }

    // Helper function to highlight matching letters
    private fun highlightTextColor(text: String, query: String, highlightColor: Int): SpannableString {
        val spannable = SpannableString(text)
        if (query.isEmpty()) return spannable

        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()

        var startIndex = lowerText.indexOf(lowerQuery)
        while (startIndex >= 0) {
            val endIndex = startIndex + query.length
            spannable.setSpan(
                ForegroundColorSpan(highlightColor),
                startIndex,
                endIndex,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = lowerText.indexOf(lowerQuery, endIndex)
        }
        return spannable
    }
}
