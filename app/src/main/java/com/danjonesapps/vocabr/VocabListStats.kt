package com.danjonesapps.vocabr

data class VocabListStats(
    val numTerms: Int, // overall non filtered
    val filteredNumTerms: Int, // combined
    val termLearntScore: Int, // overall non filtered
    val defLearntScore: Int, // overall non filtered
    val filteredTermLearntScore: Int, // filtered
    val filteredDefLearntScore: Int, // filtered
    val termDateLastTested: String, // overall non filtered
    val defDateLastTested: String,
    val allTermTypes: List<String>,
    val minListNumber: Int,
    val maxListNumber: Int,
    val termDueCount: Int,
    val defDueCount: Int
)