package com.danjonesapps.vocabr

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.math.BigDecimal
import java.math.RoundingMode

data class Quad<A, B, C, D>(val recent: A, val repeatIncorrectIds: B, val futureTerms: C, val selectedTerm: D)

data class SelectedTerm(
    val id: Int,
    val term: String,
    val termType: String,
    val definition: String,
    val learntScore: Float,
    val repeatIncorrect: Boolean,
    val exampleOne: String?,
    val exampleTwo: String?,
    val exampleThree: String?,
    val exampleDefOne: String?,
    val exampleDefTwo: String?,
    val exampleDefThree: String?,
    val ipa: String?,
)

enum class ButtonCommand {
    QUIT,
    SAVE,
    BACK,
    NOT,
    SHOW,
    GOT
}

const val BLANK_RESULTS_STRING: String = "No Recent Results"
var todayDate: LocalDate = LocalDate.now()
var testedMaxCap: Int = 15
var testedCapWeighting: Double = 0.9
var latestResultsLength: Int = 10
var daysSinceMinCap: Int = 30


fun saveResult(
    df: MutableList<TermData>,
    recent: MutableList<Triple<Int, Boolean, Boolean>>,
    repeatIncorrectIds: MutableList<Pair<Int, Int>>,
    terminating: Boolean = false,
    recentGap: Int = 0
): MutableList<TermData> {
    var recalculateAll = false

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
    return sortTerms(calcLearntScore(
        df,
        if (recalculateAll) null else recent.map { it.first } // IDs only
    ))
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
    //val tempDf: MutableList<TermData> = df.filter { row ->
    //    row.termType in desiredTermTypes
    //}.toMutableList()
    val tempDf: MutableList<TermData> = df.toMutableList()

    fun termData(id: Int, repeat: Boolean): SelectedTerm? {
        val row = tempDf.find { it.uniqueId == id } ?: return null
        return SelectedTerm(
            id = id,
            term = row.term,
            termType = row.termType,
            definition = row.definition,
            learntScore = row.learntScore,
            repeatIncorrect = repeat,
            exampleOne = row.exampleOne,
            exampleTwo = row.exampleTwo,
            exampleThree = row.exampleThree,
            exampleDefOne = row.exampleDefOne,
            exampleDefTwo = row.exampleDefTwo,
            exampleDefThree = row.exampleDefThree,
            ipa = row.ipa
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
        val avoidIds = (recent.map { it.first } + repeatIncorrectIds.map { it.first }).toSet()
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