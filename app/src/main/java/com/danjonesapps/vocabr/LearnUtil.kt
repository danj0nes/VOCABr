package com.danjonesapps.vocabr

import java.time.LocalDate

data class Quad<A, B, C, D>(val recent: A, val repeatIncorrectIds: B, val futureTerms: C, val selectedTerm: D)

data class SelectedTerm (
    val termData: TermData,
    val repeatIncorrect: Boolean,
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
    filteredTerms: MutableList<TermData>,
    recent: MutableList<Triple<Int, Boolean, Boolean>>,
    repeatIncorrectIds: MutableList<Pair<Int, Int>>,
    showTermFirst: Boolean,
    terminating: Boolean = false,
    recentGap: Int = 0,
) {
    var recalculateAll = false

    // if terminating, save results of all incorrect terms waiting to be repeated
    if (terminating) {
        for ((repeatIncorrectId, _) in repeatIncorrectIds) {
            recent.add(Triple(repeatIncorrectId, false, true))
        }
    }

    if (recent.isEmpty()) { return }

    for ((index, triple) in recent.withIndex()) {
        val (id, isCorrect, repeatIncorrect) = triple

        if (isCorrect || terminating) {
            // if max tested_count will be broken then recalculateAll ~all learnt scores
            val row = filteredTerms.find { it.uniqueId == id } ?: continue

            if (!recalculateAll) {
                val testedCount = row.testedCount(showTermFirst)
                val maxTested = filteredTerms.maxOfOrNull { it.testedCount(showTermFirst) } ?: 0

                if (testedCount == maxTested && testedCount >= testedMaxCap) {
                    recalculateAll = true
                }
            }

            // Update date_last_tested
            row.setDateLastTested(showTermFirst, todayDate)

            // Update latest_results
            val prefix = if (repeatIncorrect) "X" else "O"
            if (row.latestResults(showTermFirst) != BLANK_RESULTS_STRING) {
                val updated = prefix + row.latestResults(showTermFirst)
                if (updated.length > latestResultsLength) {
                    row.setLatestResults(showTermFirst, updated.substring(0, latestResultsLength))
                } else {
                    row.setLatestResults(showTermFirst, updated)
                }
            } else {
                row.setLatestResults(showTermFirst, prefix)
            }

            // Increment tested_count
            row.setTestedCount(showTermFirst, row.testedCount(showTermFirst) + 1)
        } else {
            // Handle incorrect term repeat
            repeatIncorrectIds.add(Pair(id, index + recentGap))
        }
    }
    calcLearntScore(
        filteredTerms,
        if (recalculateAll) null else recent.map { it.first } // IDs only
    )
    sortTerms(filteredTerms)
}


fun getTop(
    filteredTerms: MutableList<TermData>,
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

    fun termData(id: Int, repeat: Boolean): SelectedTerm? {
        val row = filteredTerms.find { it.uniqueId == id } ?: return null
        return SelectedTerm(
            termData = row,
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
        val avoidIds = (recent.map { it.first } + repeatIncorrectIds.map { it.first }).toSet()
        for (term in filteredTerms) {
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