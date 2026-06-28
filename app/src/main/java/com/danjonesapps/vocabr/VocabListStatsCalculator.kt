package com.danjonesapps.vocabr

import android.content.Context
import android.util.Log
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit
import java.util.UUID

fun calculateListStats(
    terms: List<TermData>,
    vocabList: VocabList
) {
    val learntScore =
        if (terms.isEmpty()) {
            0.0
        } else {
            terms.flatMap {
                listOf(
                    it.learntScore(false).toDouble(),
                    it.learntScore(true).toDouble()
                )
            }.average() * 100.0
        }

    val lastTested = terms
        .flatMap { listOfNotNull(it.dateLastTested(false), it.dateLastTested(true)) }
        .maxOrNull()?.toString() ?: ""

    val allTermTypes = terms.map { it.termType }.distinct()

    val filteredTerms = terms.filter {
        it.listNumber in vocabList.minListNumber..vocabList.maxListNumber &&
                it.termType in vocabList.termTypes
    }

    val filteredLearntScore =
        if (filteredTerms.isEmpty()) {
            0.0
        } else {
            filteredTerms.flatMap {
                listOf(
                    it.learntScore(false).toDouble(),
                    it.learntScore(true).toDouble()
                )
            }.average() * 100.0
        }

    vocabList.cachedStats = VocabListStats(
        numTerms = terms.size,
        filteredNumTerms = filteredTerms.size,
        learntScore = learntScore,
        filteredLearntScore = filteredLearntScore,
        dateLastTested = lastTested,
        allTermTypes = allTermTypes,
        minListNumber = terms.minOf { it.listNumber },
        maxListNumber = terms.maxOf { it.listNumber}
    )
}

fun recalculateAllLists(context: Context) {
    val allLists = AppSettings.settings.getAllLists()
    allLists.forEach { vocabList ->
        val file =
            File(
                context.filesDir,
                vocabList.fileName
            )

        if (!file.exists()) return@forEach

        val terms = loadTermDataFromCsv(file).toMutableList()
        calcLearntScore(terms)
        calcLearntScore(terms, showTermFirst = true)
        sortTerms(terms)
        saveTermDataToCsv(terms, file)
        calculateListStats(terms, vocabList)
    }
    AppSettings.settings.setLists(allLists)
}

fun calculateNewList(file: File, fileName: String): VocabList {
    val terms = loadTermDataFromCsv(file).toMutableList()
    calcLearntScore(terms)
    calcLearntScore(terms, showTermFirst = true)
    sortTerms(terms)
    saveTermDataToCsv(terms, file)

    val termTypes = terms.map { it.termType }.distinct()

    val vocabList = VocabList(
        id = UUID.randomUUID().toString(),
        fileName = fileName,
        termTypes = termTypes,
        minListNumber = terms.minOf { it.listNumber },
        maxListNumber = terms.maxOf { it.listNumber }
    )
    calculateListStats(terms, vocabList)
    return vocabList
}

fun calculateList(listId: String, terms: List<TermData>) {
    val allLists = AppSettings.settings.getAllLists()
    val vocabList = allLists.find {
        it.id == listId
    } ?: allLists.first()
    calculateListStats(terms, vocabList)
    AppSettings.settings.setLists(allLists)
}

fun sortTerms(terms: MutableList<TermData>, showTermFirst: Boolean = true) {
    terms.sortBy { it.learntScore(showTermFirst) }
}

fun calcLearntScore(terms: MutableList<TermData>, uniqueIds: List<Int>?= null, showTermFirst: Boolean=false) {
    fun minMax(min: Float, max: Float, value: Float, inverse: Boolean = false): Float {
        return if (max > min) {
            val result = (value - min) / (max - min)
            if (inverse) 1 - result else result
        } else {
            if (inverse) 1f else 0f
        }
    }

    // Select rows to update
    val rowsToUpdate: List<Int> = if (!uniqueIds.isNullOrEmpty()){
        terms.mapIndexedNotNull { index, item ->
            if (uniqueIds.contains(item.uniqueId)) index else null
        }
    } else {
        terms.indices.toList()
    }

    // Get the max for days_since_last_test and tested_count columns
    val earliestDate = terms.mapNotNull { it.dateLastTested(showTermFirst) }.minOrNull()
    var maxDays: Int = if (earliestDate != null) {
        ChronoUnit.DAYS.between(earliestDate, todayDate).toInt()
    } else {
        0
    }
    maxDays = maxOf(daysSinceMinCap, maxDays)
    val maxTestedCount = terms.maxOfOrNull { it.testedCount(showTermFirst) } ?: 0

    // Update learnt_score for each term
    for (row in rowsToUpdate){
        val termData: TermData = terms[row]

        // Skip calculation if tested_count == 0
        if (termData.testedCount(showTermFirst) == 0){
            continue
        }

        // Min-max normalize days_since_last_test
        var daysSinceLastTestNormalised: Float
        if (termData.dateLastTested(showTermFirst) != null) {
            val daysSinceLastTest = ChronoUnit.DAYS.between(termData.dateLastTested(showTermFirst), todayDate).toInt()
            daysSinceLastTestNormalised = minMax(min=0f, max=maxDays.toFloat(), value=daysSinceLastTest.toFloat(), inverse=true)
        } else {
            daysSinceLastTestNormalised = 0f
        }

        // Calculate percentage correct
        val correctPercentage: Float = if (termData.latestResults(showTermFirst) != BLANK_RESULTS_STRING) {
            termData.latestResults(showTermFirst).count { it == 'O'}.toFloat() / maxOf(
                latestResultsLength, termData.latestResults(showTermFirst).length).toFloat()
        } else {
            0f
        }

        // Piecewise weighted Min-max normalize tested_count
        val lowerPiece: Float = ((minOf(termData.testedCount(showTermFirst), testedMaxCap).toFloat() / testedMaxCap.toFloat()) * testedCapWeighting).toFloat()
        val upperPiece: Float = (minMax(min= testedMaxCap.toFloat(), max= maxOf(maxTestedCount, testedMaxCap).toFloat(), value= maxOf(termData.testedCount(showTermFirst), testedMaxCap).toFloat()) * (1 - testedCapWeighting)).toFloat()

        val testedCountNormalised = lowerPiece + upperPiece

        // Compute new learntScore
        val denominator = (AppSettings.settings.getWeightDaysSince() + AppSettings.settings.getWeightCorrect() + AppSettings.settings.getWeightTested())

        val numerator = (daysSinceLastTestNormalised * AppSettings.settings.getWeightDaysSince()) +
                (correctPercentage * AppSettings.settings.getWeightCorrect()) +
                (testedCountNormalised * AppSettings.settings.getWeightTested())

        val result = numerator / denominator

        termData.setLearntScore(showTermFirst, BigDecimal(result.toString()).setScale(4, RoundingMode.HALF_UP).toFloat())
    }
}