package com.danjonesapps.vocabr

import android.content.Context
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.time.format.DateTimeFormatter
import kotlin.math.abs as abs

fun calculateListStats(
    terms: List<TermData>,
    vocabList: VocabList
) {
    val termLearntScore =
        if (terms.isEmpty()) {
            0.0
        } else {
            terms.map {
                minOf(it.learntScore(true), 1.0)
            }.average().toInt() * 100.0
        }

    val defLearntScore =
        if (terms.isEmpty()) {
            0.0
        } else {
            terms.map {
                minOf(it.learntScore(false), 1.0)
            }.average().toInt() * 100.0
        }

    fun formatLastTested(instant: Instant?): String {
        if (instant == null) return ""

        val zone = ZoneId.systemDefault()
        val dateTime = instant.atZone(zone)
        val today = LocalDate.now(zone)

        return if (dateTime.toLocalDate() == today) {
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }

    // might not need to calc every time?
    val termLastTested = terms
        .mapNotNull { it.dateLastTested(true) }
        .maxOrNull()
        ?.let { formatLastTested(it) }
        ?: ""

    val defLastTested = terms
        .mapNotNull { it.dateLastTested(false) }
        .maxOrNull()
        ?.let { formatLastTested(it) }
        ?: ""

    val allTermTypes = terms.map { it.termType }.distinct()

    val filteredTerms = terms.filter {
        it.listNumber in vocabList.minListNumber..vocabList.maxListNumber &&
                it.termType in vocabList.termTypes
    }

    val filteredTermLearntScore =
        if (filteredTerms.isEmpty()) {
            0.0
        } else {
            filteredTerms.map {
                minOf(it.learntScore(true), 0.0)
            }.average().toInt() * 100.0
        }

    val filteredDefLearntScore =
        if (filteredTerms.isEmpty()) {
            0.0
        } else {
            filteredTerms.map {
                minOf(it.learntScore(false), 0.0)
            }.average().toInt() * 100.0
        }

    val termDueCount = terms.count { it.rememberingProbability(true) < 0.7 } // need to calc threshold
    val defDueCount = terms.count { it.rememberingProbability(false) < 0.7 } // need to calc threshold

    vocabList.cachedStats = VocabListStats(
        numTerms = terms.size,
        filteredNumTerms = filteredTerms.size,
        termLearntScore = termLearntScore,
        defLearntScore = defLearntScore,
        filteredTermLearntScore = filteredTermLearntScore,
        filteredDefLearntScore = filteredDefLearntScore,
        termDateLastTested = termLastTested,
        defDateLastTested = defLastTested,
        allTermTypes = allTermTypes,
        minListNumber = terms.minOf { it.listNumber },
        maxListNumber = terms.maxOf { it.listNumber},
        termDueCount = termDueCount,
        defDueCount = defDueCount
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
        calcLearntScores(terms)
        calcLearntScores(terms, showTermFirst = true)
        sortTerms(terms)
        saveTermDataToCsv(terms, file)
        calculateListStats(terms, vocabList)
    }
    AppSettings.settings.setLists(allLists)
}

fun calculateNewList(file: File, fileName: String): VocabList {
    val terms = loadTermDataFromCsv(file).toMutableList()
    calcLearntScores(terms)
    calcLearntScores(terms, showTermFirst = true)
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

fun sortTerms(terms: MutableList<TermData>, showTermFirst: Boolean=true) {
    terms.shuffle()
    terms.sortBy { it.rememberingProbability(showTermFirst) }
}

fun calcLearntScores(terms: MutableList<TermData>, uniqueIds: List<Int>?= null, showTermFirst: Boolean=false, predicted: Boolean=true) {
    fun calcGap(dateLastTested: Instant?): Double {
        return dateLastTested?.let {
            abs(Duration.between(it, Instant.now()).toMillis() / 86_400_000.0) / AppSettings.MASTERY_AGE
        } ?: 0.0
    }

    fun calcProbability(learntScore: Double, gap: Double): Double {
        return 0.0 // !!!
    }

    fun calcLearntScore(learntScore: Double, avgLearntScore: Double, probability: Double, gap: Double, predicted: Boolean): Double {
        val boost = maxOf(avgLearntScore - learntScore, 0.0)
        val score = learntScore + gap * (probability + boost)
        return if (predicted) probability * score else probability
    }

    // Select rows to update
    val rowsToUpdate: List<Int> = if (!uniqueIds.isNullOrEmpty()){
        terms.mapIndexedNotNull { index, item ->
            if (uniqueIds.contains(item.uniqueId)) index else null
        }
    } else {
        terms.indices.toList()
    }

    // Update learnt_score for each term
    for (row in rowsToUpdate){
        val termData: TermData = terms[row]

        // Skip calculation if not tested before
        if (termData.dateLastTested(showTermFirst) == null) {
            continue
        }

        val gap = calcGap(termData.dateLastTested(showTermFirst))
        val probability = calcProbability(termData.learntScore(showTermFirst), gap)
        termData.setRememberingProbability(showTermFirst, probability)

        val learntScore = calcLearntScore(
            termData.learntScore(showTermFirst),
            termData.avgLearntScore(showTermFirst),
            probability = probability,
            gap = gap,
            predicted = predicted
        )

        termData.setLearntScore(showTermFirst, learntScore)
    }
}