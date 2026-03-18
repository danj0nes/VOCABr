package com.danjonesapps.vocabr

import android.content.Context
import com.opencsv.bean.CsvBindByName
import com.opencsv.bean.CsvToBeanBuilder
import com.opencsv.bean.StatefulBeanToCsvBuilder
import java.io.File
import java.io.OutputStreamWriter
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import com.opencsv.bean.CsvDate
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TermData(
    @CsvBindByName(column = "UNIQUE_ID")
    var uniqueId: Int = 0,
    @CsvBindByName(column = "LEARNT_SCORE")
    var learntScore: Float = 0f,
    @CsvBindByName(column = "TERM")
    var term: String = "term",
    @CsvBindByName(column = "DEFINITION")
    var definition: String = "definition",
    @CsvBindByName(column = "LIST_NUMBER")
    var listNumber: Int = 0,
    @CsvBindByName(column = "TERM_TYPE")
    var termType: String = "term_type",
    @CsvBindByName(column = "DATE_LAST_TESTED")
    @CsvDate("yyyy-MM-dd")
    var dateLastTested: LocalDate? = null,
    @CsvBindByName(column = "LATEST_RESULTS")
    var latestResults: String = "No Recent Results",
    @CsvBindByName(column = "TESTED_COUNT")
    var testedCount: Int = 0,
    @CsvBindByName(column = "EXAMPLE_1")
    var exampleOne: String? = null,
    @CsvBindByName(column = "EXAMPLE_2")
    var exampleTwo: String? = null,
    @CsvBindByName(column = "EXAMPLE_3")
    var exampleThree: String? = null,
    @CsvBindByName(column = "EXAMPLE_EN_1")
    var exampleDefOne: String? = null,
    @CsvBindByName(column = "EXAMPLE_EN_2")
    var exampleDefTwo: String? = null,
    @CsvBindByName(column = "EXAMPLE_EN_3")
    var exampleDefThree: String? = null,
)

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
    val exampleDefThree: String?
)

data class SavedListData(
    val fileName: String,
    val numTerms: Int? = null,
    val avgLearntScore: Float? = null,
    val dateLastTested: LocalDate? = null
) {
    companion object {
        private const val DEFAULT_DELIMITER = "|"
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private fun parseParts(serialized: String, delimiter: String): SavedListData {
            val parts = serialized
                .split(delimiter)
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            return when (parts.size) {
                0 -> SavedListData("") // should never happen (raise?)
                1 -> SavedListData(parts[0])
                else -> {
                    val fileName = parts[0]
                    val numTerms = parts.getOrNull(1)?.toIntOrNull()
                    val avgScore = parts.getOrNull(2)?.toFloatOrNull()
                    val date = parts.getOrNull(3)?.let {
                        runCatching { LocalDate.parse(it) }.getOrNull()
                    }
                    SavedListData(fileName, numTerms, avgScore, date)
                }
            }
        }

        fun fromSerialized(serialized: String, delimiter: String = DEFAULT_DELIMITER): SavedListData {
            val parts = parseParts(serialized, delimiter)
            return SavedListData(parts.fileName, parts.numTerms, parts.avgLearntScore, parts.dateLastTested)
        }
    }

    fun toDelimitedString(delimiter: String = DEFAULT_DELIMITER): String {
        return listOf(
            fileName,
            numTerms?.toString(),
            avgLearntScore?.toString(),
            dateLastTested?.toString()
        ).dropLastWhile { it.isNullOrEmpty() }
            .joinToString(delimiter)
    }

    // Method to return the delimited string version (omits trailing null/empty values)
    fun toDisplayStrings(): Map<String, String> {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())

        val termsText = numTerms?.let { "terms: ${numberFormat.format(it)}" } ?: "terms:"
        val scoreText = avgLearntScore?.let { "learnt score: %.1f%%".format(it * 100f) } ?: "learnt score:"
        val dateText = dateLastTested?.format(dateFormat).orEmpty()

        return mapOf(
            "fileName" to fileName,
            "terms" to termsText,
            "score" to scoreText,
            "lastTested" to dateText
        )
    }
}


enum class ButtonCommand {
    QUIT,
    SAVE,
    BACK,
    NOT,
    SHOW,
    EXAMPLES,
    GOT
}

private const val LISTS_FILE_NAME = "lists.txt"

fun writeListDataToListsFile(context: Context, listsData: MutableList<SavedListData>) {
    val file = File(context.filesDir, LISTS_FILE_NAME)

    try {
        // Convert each TermList into its delimited string representation
        val content = listsData.joinToString(separator = "\n") { it.toDelimitedString() }
        file.writeText(content)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun readListData(context: Context): MutableList<SavedListData> {
    val file = File(context.filesDir, LISTS_FILE_NAME)

    // If the file doesn't exist, return an empty list
    if (!file.exists()) {
        return mutableListOf()
    }

    return try {
        file.readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                try {
                    SavedListData.fromSerialized(trimmed)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null // skip invalid lines
                }
            }
            .toMutableList()
    } catch (e: Exception) {
        e.printStackTrace()
        mutableListOf()
    }
}

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
        val denominator = (weightDaysSince + weightCorrect + weightTested)

        val numerator = (daysSinceLastTestNormalised * weightDaysSince) +
                (correctPercentage * weightCorrect) +
                (testedCountNormalised * weightTested)

        val result = numerator / denominator

        termData.learntScore = BigDecimal(result.toString()).setScale(4, RoundingMode.HALF_UP).toFloat()
    }

    return sortTerms(df)
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
            termType = row.termType,
            definition = row.definition,
            learntScore = row.learntScore,
            repeatIncorrect = repeat,
            exampleOne = row.exampleOne,
            exampleTwo = row.exampleTwo,
            exampleThree = row.exampleThree,
            exampleDefOne = row.exampleDefOne,
            exampleDefTwo = row.exampleDefTwo,
            exampleDefThree = row.exampleDefThree
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

fun loadTermDataFromCsv(file: File): List<TermData> {
    file.bufferedReader(Charsets.UTF_8).use { reader ->
        return CsvToBeanBuilder<TermData>(reader)
            .withType(TermData::class.java)
            .withIgnoreLeadingWhiteSpace(true)
            .build()
            .parse()
    }
}

fun saveTermDataToCsv(termDataList: List<TermData>, file: File) {
    OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
        val beanToCsv = StatefulBeanToCsvBuilder<TermData>(writer)
            .withApplyQuotesToAll(false)
            .build()
        beanToCsv.write(termDataList)
    }
}