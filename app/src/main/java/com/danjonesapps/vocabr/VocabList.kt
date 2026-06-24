package com.danjonesapps.vocabr

import com.opencsv.bean.CsvBindByName
import com.opencsv.bean.CsvDate
import java.time.LocalDate

data class VocabList(
    val id: String,
    val fileName: String,
    val termTypes: List<String>,
    val minListNumber: Int,
    val maxListNumber: Int,
    var cachedStats: VocabListStats? = null
)

data class TermData(
    @CsvBindByName(column = "UNIQUE_ID")
    var uniqueId: Int = 1, // IS ALWAYS RE-INDEXED ON LOAD
    @CsvBindByName(column = "LEARNT_SCORE")
    private var learntScore: Float = 0f,
    @CsvBindByName(column = "REV_LEARNT_SCORE")
    private var revLearntScore: Float = 0f,
    @CsvBindByName(column = "TERM")
    var term: String = "term",
    @CsvBindByName(column = "DEFINITION")
    var definition: String = "definition",
    @CsvBindByName(column = "LIST_NUMBER")
    var listNumber: Int = -1, // GETS REWRITTEN ON LOAD IF MISSING
    @CsvBindByName(column = "TERM_TYPE")
    var termType: String = "term_type",
    @CsvBindByName(column = "DATE_LAST_TESTED")
    @CsvDate("yyyy-MM-dd")
    private var dateLastTested: LocalDate? = null,
    @CsvBindByName(column = "REV_DATE_LAST_TESTED")
    @CsvDate("yyyy-MM-dd")
    private var revDateLastTested: LocalDate? = null,
    @CsvBindByName(column = "LATEST_RESULTS")
    private var latestResults: String = "No Recent Results",
    @CsvBindByName(column = "REV_LATEST_RESULTS")
    private var revLatestResults: String = "No Recent Results",
    @CsvBindByName(column = "TESTED_COUNT")
    private var testedCount: Int = 0,
    @CsvBindByName(column = "REV_TESTED_COUNT")
    private var revTestedCount: Int = 0,
    @CsvBindByName(column = "EXAMPLE_1")
    var exampleOne: String? = null,
    @CsvBindByName(column = "EXAMPLE_2")
    var exampleTwo: String? = null,
    @CsvBindByName(column = "EXAMPLE_3")
    var exampleThree: String? = null,
    @CsvBindByName(column = "EXAMPLE_DEF_1")
    var exampleDefOne: String? = null,
    @CsvBindByName(column = "EXAMPLE_DEF_2")
    var exampleDefTwo: String? = null,
    @CsvBindByName(column = "EXAMPLE_DEF_3")
    var exampleDefThree: String? = null,
    @CsvBindByName(column = "IPA")
    var ipa: String? = null,
) {
    fun learntScore(reverse: Boolean): Float =
        if (reverse) revLearntScore else learntScore

    fun setLearntScore(reverse: Boolean, value: Float) {
        if (reverse) {
            revLearntScore = value
        } else {
            learntScore = value
        }
    }

    fun dateLastTested(reverse: Boolean): LocalDate? =
        if (reverse) revDateLastTested else dateLastTested

    fun setDateLastTested(reverse: Boolean, value: LocalDate?) {
        if (reverse) {
            revDateLastTested = value
        } else {
            dateLastTested = value
        }
    }

    fun latestResults(reverse: Boolean): String =
        if (reverse) revLatestResults else latestResults

    fun setLatestResults(reverse: Boolean, value: String) {
        if (reverse) {
            revLatestResults = value
        } else {
            latestResults = value
        }
    }

    fun testedCount(reverse: Boolean): Int =
        if (reverse) revTestedCount else testedCount

    fun setTestedCount(reverse: Boolean, value: Int) {
        if (reverse) {
            revTestedCount = value
        } else {
            testedCount = value
        }
    }
}