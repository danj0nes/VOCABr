package com.danjonesapps.vocabr

data class VocabListStats(
    val numTerms: Int, // overall non filtered
    val filteredNumTerms: Int, // combined
    val termLearntScore: Double, // overall non filtered
    val defLearntScore: Double, // overall non filtered
    val filteredTermLearntScore: Double, // filtered
    val filteredDefLearntScore: Double, // filtered
    val termDateLastTested: String, // overall non filtered
    val defDateLastTested: String,
    val allTermTypes: List<String>,
    val minListNumber: Int,
    val maxListNumber: Int,
)