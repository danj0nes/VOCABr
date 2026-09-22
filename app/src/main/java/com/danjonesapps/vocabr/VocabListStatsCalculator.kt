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

const val minsInDay = 1440.0
const val multiFactor = AppSettings.FINAL_TARGET_GAP.toDouble() * minsInDay
// coordinate 1 is (0, multiFactor)
val decayCoordX = AppSettings.decayDefiningCoord.first / AppSettings.MASTERY_AGE
val decayCoordY = (1.0 / (AppSettings.decayDefiningCoord.second * minsInDay)) * multiFactor

val decayConstantA = ((decayCoordX * decayCoordY) * (multiFactor - 1)) / (multiFactor - decayCoordY)
val decayConstantB = (decayCoordX * decayCoordY) / (multiFactor - decayCoordY)

val targetArea = calcArea(decayCoordX, AppSettings.decayDefiningCoord.second / AppSettings.MASTERY_AGE)
val probabilityConstant = (1.0 - AppSettings.TARGET_GAP_PROBABILITY) / (targetArea * AppSettings.TARGET_GAP_PROBABILITY)

fun calculateListStats(
    terms: List<TermData>,
    vocabList: VocabList
) {
    val termLearntScore =
        if (terms.isEmpty()) {
            0
        } else {
            (terms.map {
                minOf(it.predictedLearntScore(true), 1.0)
            }.average() * 100.0).toInt()
        }

    val defLearntScore =
        if (terms.isEmpty()) {
            0
        } else {
            (terms.map {
                minOf(it.predictedLearntScore(false), 1.0)
            }.average() * 100.0).toInt()
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
            0
        } else {
            (filteredTerms.map {
                minOf(it.predictedLearntScore(true), 1.0)
            }.average() * 100.0).toInt()
        }

    val filteredDefLearntScore =
        if (filteredTerms.isEmpty()) {
            0
        } else {
            (filteredTerms.map {
                minOf(it.predictedLearntScore(false), 1.0)
            }.average() * 100.0).toInt()
        }

    val filteredTermLastTested = filteredTerms
        .mapNotNull { it.dateLastTested(true) }
        .maxOrNull()
        ?.let { formatLastTested(it) }
        ?: ""

    val filteredDefLastTested = filteredTerms
        .mapNotNull { it.dateLastTested(false) }
        .maxOrNull()
        ?.let { formatLastTested(it) }
        ?: ""

    val termDueCount = terms.count { it.rememberingProbability(true) <= AppSettings.TARGET_GAP_PROBABILITY }
    val defDueCount = terms.count { it.rememberingProbability(false) <= AppSettings.TARGET_GAP_PROBABILITY }
    val filteredTermDueCount = filteredTerms.count { it.rememberingProbability(true) <= AppSettings.TARGET_GAP_PROBABILITY }
    val filteredDefDueCount = filteredTerms.count { it.rememberingProbability(false) <= AppSettings.TARGET_GAP_PROBABILITY }

    vocabList.cachedStats = VocabListStats(
        numTerms = terms.size,
        filteredNumTerms = filteredTerms.size,
        termLearntScore = termLearntScore,
        defLearntScore = defLearntScore,
        filteredTermLearntScore = filteredTermLearntScore,
        filteredDefLearntScore = filteredDefLearntScore,
        termDateLastTested = termLastTested,
        defDateLastTested = defLastTested,
        filteredTermDateLastTested = filteredTermLastTested,
        filteredDefDateLastTested = filteredDefLastTested,
        allTermTypes = allTermTypes,
        minListNumber = terms.minOf { it.listNumber },
        maxListNumber = terms.maxOf { it.listNumber},
        termDueCount = termDueCount,
        defDueCount = defDueCount,
        filteredTermDueCount = filteredTermDueCount,
        filteredDefDueCount = filteredDefDueCount
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
        calcScores(terms, predictedLearntScoreOnly = true)
        calcScores(terms, showTermFirst = true, predictedLearntScoreOnly = true)
        sortTerms(terms)
        saveTermDataToCsv(terms, file)
        calculateListStats(terms, vocabList)
    }
    AppSettings.settings.setLists(allLists)
}

fun calculateNewList(file: File, fileName: String): VocabList {
    val terms = loadTermDataFromCsv(file).toMutableList()
    calcScores(terms, predictedLearntScoreOnly = true)
    calcScores(terms, showTermFirst = true, predictedLearntScoreOnly = true)
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

fun recalculateList(context: Context, listId: String) {
    val allLists = AppSettings.settings.getAllLists()
    val vocabList = allLists.find { it.id == listId } ?: return
    val file = File(context.filesDir, vocabList.fileName)
    if (!file.exists()) return
    val terms = loadTermDataFromCsv(file).toMutableList()
    calcScores(terms, predictedLearntScoreOnly = true)
    calcScores(terms, showTermFirst = true, predictedLearntScoreOnly = true)
    sortTerms(terms)
    saveTermDataToCsv(terms, file)
    calculateListStats(terms, vocabList)
    AppSettings.settings.setLists(allLists)
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

fun calcGap(dateLastTested: Instant?): Double {
    return dateLastTested?.let {
        abs(Duration.between(it, Instant.now()).toMillis() / 86_400_000.0) / AppSettings.MASTERY_AGE
    } ?: 0.0
}

fun gapToWhenDue(dateLastTested: Instant?, gap: Double): Instant? {
    val days = gap * AppSettings.MASTERY_AGE
    return dateLastTested?.plusMillis((days * 86_400_000).toLong())
}

fun calcFrequency(learntScore: Double): Double {
    return 1 + decayConstantA / (learntScore + decayConstantB)
}

fun calcArea(learntScore: Double, gap: Double): Double {
    return calcFrequency(learntScore) * gap
}

fun calcProbability(area: Double): Double {
    return 1.0 / (1.0 + probabilityConstant * area)
}

fun calcProbability(frequency: Double, gap: Double): Double {
    if (gap == 0.0) return 1.0
    val area = frequency * gap
    return calcProbability(area)
}

fun calcScores(terms: MutableList<TermData>, uniqueIds: List<Int>?= null, showTermFirst: Boolean=false, predictedLearntScoreOnly: Boolean=true) {
    fun calcLearntScores(learntScore: Double, avgLearntScore: Double, probability: Double, gap: Double): Pair<Double, Double> {
        val boost = maxOf(avgLearntScore - learntScore, 0.0)
        val score = learntScore + gap * (probability + boost)
        return Pair(score, score * probability)
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
        val frequency = calcFrequency(termData.learntScore(showTermFirst))
        val probability = calcProbability(frequency, gap)
        termData.setRememberingProbability(showTermFirst, probability)

        val dueGap = targetArea / frequency
        termData.setDueInstant(showTermFirst, gapToWhenDue(termData.dateLastTested(showTermFirst), dueGap))

        val (learntScore, predictedLearntScore) = calcLearntScores(
            termData.learntScore(showTermFirst),
            termData.avgLearntScore(showTermFirst),
            probability = probability,
            gap = gap
        )

        termData.setPredictedLearntScore(showTermFirst, predictedLearntScore)
        if (!predictedLearntScoreOnly) {
            termData.setLearntScore(showTermFirst, learntScore)
        }
    }
}