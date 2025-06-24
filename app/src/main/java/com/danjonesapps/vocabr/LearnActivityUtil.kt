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

data class Quad<A, B, C, D>(val recent: A, val repeatIncorrectIds: B, val futureTerms: C, val selectedTerm: D)

data class SelectedTerm(
    val id: Int,
    val term: String,
    val definition: String,
    val learntScore: Float,
    val repeatIncorrect: Boolean
)

fun calcLearntScore(df: MutableList<TermData>, uniqueIds: List<Int>?= null): MutableList<TermData> {
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


fun sortTerms(df: MutableList<TermData>): MutableList<TermData> {
    val shuffledList = df.shuffled()
    return shuffledList.sortedBy { it.learntScore }.toMutableList()
}


fun saveResult(
    df: MutableList<TermData>,
    recent: MutableList<Triple<Int, Boolean, Boolean>>,
    repeatIncorrectIds: MutableList<Pair<Int, Int>>,
    terminating: Boolean = false,
    recentGap: Int = 0
): MutableList<TermData> {
    var recalculateAll: Boolean = false

    //# if terminating, save results of all incorrect terms waiting to be repeated
    if (terminating) {
        for ((repeatIncorrectId, _) in repeatIncorrectIds) {
            recent.add(Triple(repeatIncorrectId, false, true))
        }
    }

    if (recent.isEmpty()) {
        return df
    }

    for ((index, triple) in recent.withIndex()) {
        val (id, isCorrect, repeatIncorrect) = triple

        if (isCorrect || terminating) {
            // if max tested_count will be broken then recalculateAll ~all learnt scores
            val row = df.find { it.uniqueId == id } ?: continue

            if (!recalculateAll) {
                val testedCount = row.testedCount
                val maxTested = df.maxOfOrNull { it.testedCount } ?: 0

                if (testedCount == maxTested && testedCount >= testedMaxCap) {
                    recalculateAll = true
                }
            }

            // Update date_last_tested
            row.dateLastTested = todayDate

            // Update latest_results
            val prefix = if (repeatIncorrect) "X" else "O"
            row.latestResults = if (row.latestResults != BLANK_RESULTS_STRING) {
                val updated = prefix + row.latestResults
                if (updated.length > latestResultsLength) {
                    updated.substring(0, latestResultsLength)
                } else updated
            } else {
                prefix
            }

            // Increment tested_count
            row.testedCount += 1
        } else {
            // Handle incorrect term repeat
            repeatIncorrectIds.add(Pair(id, index + recentGap))
        }
    }

    // Final return
    return calcLearntScore(
        df,
        if (recalculateAll) null else recent.map { it.first } // IDs only
    )
}


fun getTop(
    df: MutableList<TermData>,
    recent: MutableList<Triple<Int, Boolean, Boolean>>,
    repeatIncorrectIds: MutableList<Pair<Int, Int>>,
    futureTerms: MutableList<Pair<Int, Boolean>>,
    reversing: Boolean = false,
    quitting: Boolean = false
): Quad<
        MutableList<Triple<Int, Boolean, Boolean>>, // recent
        MutableList<Pair<Int, Int>>,                // repeatIncorrectIds
        MutableList<Pair<Int, Boolean>>,            // futureTerms
        SelectedTerm?                               // selected term
        > {

    // Filter df by desired term types and list number range, then map by uniqueId for fast lookup
    val tempDf: MutableList<TermData> = df.filter { row ->
        row.termType in desiredTermTypes
    }.toMutableList()

    fun termData(id: Int, repeat: Boolean): SelectedTerm? {
        val row = tempDf.find { it.uniqueId == id } ?: return null
        return SelectedTerm(
            id = id,
            term = row.term,
            definition = row.definition,
            learntScore = row.learntScore,
            repeatIncorrect = repeat
        )
    }

    var data: SelectedTerm? = null

    // 1. Pick from futureTerms
    if (futureTerms.isNotEmpty()) {
        val (id, repeatIncorrect) = if (!reversing) {
            futureTerms.removeAt(0)
        } else {
            // Removing last element from recent and taking id and repeatIncorrect flags
            val (rid, _, repInc) = recent.removeAt(recent.lastIndex)
            rid to repInc
        }
        data = termData(id, repeatIncorrect)
    }

    // 2. Pick from repeatIncorrectIds
    if (data == null && repeatIncorrectIds.isNotEmpty()) {
        if (repeatIncorrectIds[0].second == 0 || quitting) {
            val (id, _) = repeatIncorrectIds.removeAt(0)
            data = termData(id, true)
        }
    }

    // 3. Pick from filtered df
    if (data == null && !quitting) {
        val avoidIds = recent.map { it.first }.toSet()
        for (term in tempDf) {
            if (term.uniqueId !in avoidIds) {
                data = termData(term.uniqueId, false)
                break
            }
        }
    }

    // Reduce delay for repeatIncorrectIds if a new term was selected
    if (data != null) {
        repeatIncorrectIds.replaceAll { (id, delay) ->
            id to if (delay > 0) delay - 1 else delay
        }
    }

    return Quad(recent, repeatIncorrectIds, futureTerms, data)
}
