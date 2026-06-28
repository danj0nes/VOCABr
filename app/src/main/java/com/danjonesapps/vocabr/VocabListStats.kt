package com.danjonesapps.vocabr

data class VocabListStats(
    val numTerms: Int, // overall non filtered
    val filteredNumTerms: Int, // combined
    val learntScore: Double, // overall non filtered combined with rev
    val filteredLearntScore: Double, // filtered combined with rev
    val dateLastTested: String, // overall non filtered
    val allTermTypes: List<String>,
    val minListNumber: Int,
    val maxListNumber: Int,
)