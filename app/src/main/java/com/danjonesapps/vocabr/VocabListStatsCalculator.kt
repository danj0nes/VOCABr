package com.danjonesapps.vocabr

import android.content.Context
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit
import java.util.UUID

fun calculateListStats(
    terms: List<TermData>,
    vocabList: VocabList
): VocabList {
    val learntScore =
        if (terms.isEmpty()) {
            0.0
        } else {
            terms.flatMap {
                listOf(
                    it.learntScore.toDouble(),
                    it.revLearntScore.toDouble()
                )
            }.average() * 100.0
        }

    val lastTested = terms
        .flatMap { listOfNotNull(it.dateLastTested(false), it.dateLastTested(true)) }
        .maxOrNull()?.toString() ?: ""

    val filteredTerms = terms.filter { term ->
        term.listNumber > vocabList.minListNumber &&
                term.listNumber < vocabList.maxListNumber &&
                term.termType in vocabList.termTypes
    }

    val filteredLearntScore =
        if (filteredTerms.isEmpty()) {
            0.0
        } else {
            filteredTerms.flatMap {
                listOf(
                    it.learntScore.toDouble(),
                    it.revLearntScore.toDouble()
                )
            }.average() * 100.0
        }

    vocabList.cachedStats = VocabListStats(
        numTerms = terms.size,
        filteredNumTerms = filteredTerms.size,
        learntScore = learntScore,
        filteredLearntScore = filteredLearntScore,
        dateLastTested = lastTested
    )
    return vocabList
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

        val terms = sortTerms(loadTermDataFromCsv(file).toMutableList())
        calcLearntScore(terms)
        calcLearntScore(terms, reverse = true)
        saveTermDataToCsv(terms, file)
        calculateListStats(terms, vocabList)
    }
    AppSettings.settings.setLists(allLists)
}

fun calculateNewList(file: File, fileName: String): VocabList {
    val terms = sortTerms(loadTermDataFromCsv(file).toMutableList())
    calcLearntScore(terms)
    calcLearntScore(terms, reverse = true)
    saveTermDataToCsv(terms, file)

    val termTypes = terms.mapNotNull { it.termType }.filter { it.isNotBlank() }.distinct()

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

fun sortTerms(df: MutableList<TermData>, reverse: Boolean=false): MutableList<TermData> {
    val shuffledList = df.shuffled()
    return shuffledList.sortedBy { it.learntScore(reverse) }.toMutableList()
}

fun calcLearntScore(df: MutableList<TermData>, uniqueIds: List<Int>?= null, reverse: Boolean=false): MutableList<TermData> {
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
        df.mapIndexedNotNull { index, item ->
            if (uniqueIds.contains(item.uniqueId)) index else null
        }
    } else {
        df.indices.toList()
    }

    // Get the max for days_since_last_test and tested_count columns
    val earliestDate = df.mapNotNull { it.dateLastTested(reverse) }.minOrNull()
    var maxDays: Int = if (earliestDate != null) {
        ChronoUnit.DAYS.between(earliestDate, todayDate).toInt()
    } else {
        0
    }
    maxDays = maxOf(daysSinceMinCap, maxDays)
    val maxTestedCount = df.maxOfOrNull { it.testedCount(reverse) } ?: 0

    // Update learnt_score for each term
    for (row in rowsToUpdate){
        val termData: TermData = df[row]

        // Skip calculation if tested_count == 0
        if (termData.testedCount(reverse) == 0){
            continue
        }

        // Min-max normalize days_since_last_test
        var daysSinceLastTestNormalised: Float
        if (termData.dateLastTested(reverse) != null) {
            val daysSinceLastTest = ChronoUnit.DAYS.between(termData.dateLastTested(reverse), todayDate).toInt()
            daysSinceLastTestNormalised = minMax(min=0f, max=maxDays.toFloat(), value=daysSinceLastTest.toFloat(), inverse=true)
        } else {
            daysSinceLastTestNormalised = 0f
        }

        // Calculate percentage correct
        val correctPercentage: Float = if (termData.latestResults(reverse) != BLANK_RESULTS_STRING) {
            termData.latestResults(reverse).count { it == 'O'}.toFloat() / maxOf(
                latestResultsLength, termData.latestResults(reverse).length).toFloat()
        } else {
            0f
        }

        // Piecewise weighted Min-max normalize tested_count
        val lowerPiece: Float = ((minOf(termData.testedCount(reverse), testedMaxCap).toFloat() / testedMaxCap.toFloat()) * testedCapWeighting).toFloat()
        val upperPiece: Float = (minMax(min= testedMaxCap.toFloat(), max= maxOf(maxTestedCount, testedMaxCap).toFloat(), value= maxOf(termData.testedCount(reverse), testedMaxCap).toFloat()) * (1 - testedCapWeighting)).toFloat()

        val testedCountNormalised = lowerPiece + upperPiece

        // Compute new learntScore
        val denominator = (AppSettings.settings.getWeightDaysSince() + AppSettings.settings.getWeightCorrect() + AppSettings.settings.getWeightTested())

        val numerator = (daysSinceLastTestNormalised * AppSettings.settings.getWeightDaysSince()) +
                (correctPercentage * AppSettings.settings.getWeightCorrect()) +
                (testedCountNormalised * AppSettings.settings.getWeightTested())

        val result = numerator / denominator

        termData.setLearntScore(reverse, BigDecimal(result.toString()).setScale(4, RoundingMode.HALF_UP).toFloat())
    }

    return df
}