package com.danjonesapps.vocabr

import com.opencsv.bean.CsvBindByName
import com.opencsv.bean.CsvDate
import java.time.LocalDate

data class VocabList(
    val id: String,
    val fileName: String,
    var termTypes: List<String>,
    var minListNumber: Int,
    var maxListNumber: Int,
    var cachedStats: VocabListStats? = null
)

data class TermData(
    @CsvBindByName(column = "UNIQUE_ID")
    var uniqueId: Int = 1, // IS ALWAYS RE-INDEXED ON LOAD
    @CsvBindByName(column = "TERM_LEARNT_SCORE")
    private var termLearntScore: Float = 0f,
    @CsvBindByName(column = "DEF_LEARNT_SCORE")
    private var defLearntScore: Float = 0f,
    @CsvBindByName(column = "TERM")
    private var term: String = "term",
    @CsvBindByName(column = "DEFINITION")
    private var definition: String = "definition",
    @CsvBindByName(column = "LIST_NUMBER")
    var listNumber: Int = -1, // GETS REWRITTEN ON LOAD IF MISSING
    @CsvBindByName(column = "TERM_TYPE")
    var termType: String = "term_type",
    @CsvBindByName(column = "TERM_DATE_LAST_TESTED")
    @CsvDate("yyyy-MM-dd")
    private var termDateLastTested: LocalDate? = null,
    @CsvBindByName(column = "DEF_DATE_LAST_TESTED")
    @CsvDate("yyyy-MM-dd")
    private var defDateLastTested: LocalDate? = null,
    @CsvBindByName(column = "TERM_LATEST_RESULTS")
    private var termLatestResults: String = "No Recent Results",
    @CsvBindByName(column = "DEF_LATEST_RESULTS")
    private var defLatestResults: String = "No Recent Results",
    @CsvBindByName(column = "TERM_TESTED_COUNT")
    private var termTestedCount: Int = 0,
    @CsvBindByName(column = "DEF_TESTED_COUNT")
    private var defTestedCount: Int = 0,
    @CsvBindByName(column = "EXAMPLE_1")
    var exampleOne: String? = null,
    @CsvBindByName(column = "EXAMPLE_2")
    var exampleTwo: String? = null,
    @CsvBindByName(column = "EXAMPLE_3")
    var exampleThree: String? = null,
    @CsvBindByName(column = "EXAMPLE_1_DEF")
    var exampleOneDef: String? = null,
    @CsvBindByName(column = "EXAMPLE_2_DEF")
    var exampleTwoDef: String? = null,
    @CsvBindByName(column = "EXAMPLE_3_DEF")
    var exampleThreeDef: String? = null,
    @CsvBindByName(column = "IPA")
    var ipa: String? = null,
) {
    fun learntScore(termFirst: Boolean): Float =
        if (termFirst) termLearntScore else defLearntScore

    fun setLearntScore(termFirst: Boolean, value: Float) {
        if (termFirst) {
            termLearntScore = value
        } else {
            defLearntScore = value
        }
    }

    fun dateLastTested(termFirst: Boolean): LocalDate? =
        if (termFirst) termDateLastTested else defDateLastTested

    fun setDateLastTested(termFirst: Boolean, value: LocalDate?) {
        if (termFirst) {
            termDateLastTested = value
        } else {
            defDateLastTested = value
        }
    }

    fun latestResults(termFirst: Boolean): String =
        if (termFirst) termLatestResults else defLatestResults

    fun setLatestResults(termFirst: Boolean, value: String) {
        if (termFirst) {
            termLatestResults = value
        } else {
            defLatestResults = value
        }
    }

    fun testedCount(termFirst: Boolean): Int =
        if (termFirst) termTestedCount else defTestedCount

    fun setTestedCount(termFirst: Boolean, value: Int) {
        if (termFirst) {
            termTestedCount = value
        } else {
            defTestedCount = value
        }
    }

    fun vocab(termFirst: Boolean): String =
        if (termFirst) term else definition

    fun vocabDef(termFirst: Boolean): String =
        if (termFirst) definition else term
}