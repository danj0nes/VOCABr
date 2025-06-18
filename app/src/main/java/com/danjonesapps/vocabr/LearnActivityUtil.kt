package com.danjonesapps.vocabr

import com.opencsv.bean.CsvBindByName
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.math.BigDecimal
import java.math.RoundingMode

data class TermData(
    @CsvBindByName(column = "unique_id")
    var uniqueId: Int = 0,
    @CsvBindByName(column = "learnt_score")
    var learntScore: Float = 0f,
    @CsvBindByName(column = "term")
    var term: String = "term",
    @CsvBindByName(column = "definition")
    var definition: String = "definition",
    @CsvBindByName(column = "list_number")
    var listNumber: Int = 0,
    @CsvBindByName(column = "term_type")
    var termType: String = "term_type",
    @CsvBindByName(column = "dateLast_tested")
    var dateLastTested: LocalDate? = null,
    @CsvBindByName(column = "latest_results")
    var latestResults: String = "No Recent Results",
    @CsvBindByName(column = "tested_count")
    var testedCount: Int = 0
)

fun calcLearntScore(df: MutableList<TermData>, uniqueIds: MutableList<Int>?= null): MutableList<TermData> {
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
    val earliestDate = df.mapNotNull { it.dateLastTested }.minOrNull()
    var maxDays: Int = if (earliestDate != null) {
        ChronoUnit.DAYS.between(earliestDate, todayDate).toInt()
    } else {
        0
    }
    maxDays = maxOf(daysSinceMinCap, maxDays)
    val maxTestedCount = df.maxOfOrNull { it.testedCount } ?: 0

    // Update learnt_score for each term
    for (row in rowsToUpdate){
        val termData: TermData = df[row]

        // Skip calculation if tested_count == 0
        if (termData.testedCount == 0){
            continue
        }

        // Min-max normalize days_since_last_test
        var daysSinceLastTestNormalised: Float
        if (termData.dateLastTested != null) {
            val daysSinceLastTest = ChronoUnit.DAYS.between(termData.dateLastTested, todayDate).toInt()
            daysSinceLastTestNormalised = minMax(min=0f, max=maxDays.toFloat(), value=daysSinceLastTest.toFloat(), inverse=true)
        } else {
            daysSinceLastTestNormalised = 0f
        }

        // Calculate percentage correct
        val correctPercentage: Float = if (termData.latestResults != BLANK_RESULTS_STRING) {
            termData.latestResults.count { it == 'O'}.toFloat() / maxOf(
                latestResultsLength, termData.latestResults.length).toFloat()
        } else {
            0f
        }

        // Piecewise weighted Min-max normalize tested_count
        val lowerPiece: Float = ((minOf(termData.testedCount, testedMaxCap).toFloat() / testedMaxCap.toFloat()) * testedCapWeighting).toFloat()
        val upperPiece: Float = (minMax(min= testedMaxCap.toFloat(), max= maxOf(maxTestedCount, testedMaxCap).toFloat(), value= maxOf(termData.testedCount, testedMaxCap).toFloat()) * (1 - testedCapWeighting)).toFloat()

        val testedCountNormalised = lowerPiece + upperPiece

        // Compute new learntScore
        val denominator = (weightDaysSince + weightCorrect + weightTested).toFloat()

        val numerator = (daysSinceLastTestNormalised * weightDaysSince) +
                (correctPercentage * weightCorrect) +
                (testedCountNormalised * weightTested)

        val result = numerator / denominator

        termData.learntScore = BigDecimal(result.toString()).setScale(4, RoundingMode.HALF_UP).toFloat()
    }

    return df
}