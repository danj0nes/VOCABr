package com.danjonesapps.vocabr

import java.time.Instant

data class Quad<A, B, C, D>(val recent: A, val repeatIncorrectIds: B, val futureTerms: C, val selectedTerm: D)
data class Quint<A, B, C, D, E>(val uniqueId: A, val wasCorrect: B, val repeatIncorrect: C, val dateTested: D, val wasDue: E)

data class SelectedTerm (
    val termData: TermData,
    val repeatIncorrect: Boolean
)

enum class ButtonCommand {
    QUIT,
    BACK,
    NOT,
    SHOW,
    GOT
}

fun saveResult(
    filteredTerms: MutableList<TermData>,
    recent: MutableList<Quint<Int, Boolean, Boolean, Instant, Boolean>>,
    repeatIncorrectIds: MutableList<Pair<Int, Int>>,
    showTermFirst: Boolean,
    recentGap: Int = 0,
) {
    if (recent.isEmpty()) { return }

    for ((index, quint) in recent.withIndex()) {
        val (id, isCorrect, _, dateTested, _) = quint

        val row = filteredTerms.find { it.uniqueId == id } ?: continue

        if (isCorrect) {
            calcScores(
                filteredTerms,
                uniqueIds = recent.map { it.uniqueId },
                showTermFirst = showTermFirst,
                predictedLearntScoreOnly = false
            )
        } else {
            row.setLearntScore(showTermFirst, 0.0)
            row.setPredictedLearntScore(showTermFirst, 0.0)
            row.setRememberingProbability(showTermFirst, 0.0)
            repeatIncorrectIds.add(Pair(id, index + recentGap))
        }

        row.setDateLastTested(showTermFirst, dateTested)
        row.setAvgLearntScore(
            showTermFirst,
            AppSettings.BOOST_NEW_WEIGHT * row.learntScore(showTermFirst) + (1 - AppSettings.BOOST_NEW_WEIGHT) * row.avgLearntScore(showTermFirst)
        )
    }
    calcScores(
        filteredTerms,
        showTermFirst = showTermFirst,
        predictedLearntScoreOnly = true
    )
    sortTerms(filteredTerms, showTermFirst)
}


fun getTop(
    filteredTerms: MutableList<TermData>,
    recent: MutableList<Quint<Int, Boolean, Boolean, Instant, Boolean>>,
    repeatIncorrectIds: MutableList<Pair<Int, Int>>,
    futureTerms: MutableList<Pair<Int, Boolean>>,
    reversing: Boolean = false,
    quitting: Boolean = false
): Quad<
        MutableList<Quint<Int, Boolean, Boolean, Instant, Boolean>>, // recent
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
        val avoidIds = (recent.map { it.uniqueId } + repeatIncorrectIds.map { it.first }).toSet()
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