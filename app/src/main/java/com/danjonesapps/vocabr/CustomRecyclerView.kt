package com.danjonesapps.vocabr

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

class CustomRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : RecyclerView(context, attrs) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}