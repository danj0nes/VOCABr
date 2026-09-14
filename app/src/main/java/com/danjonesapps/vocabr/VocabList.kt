package com.danjonesapps.vocabr

import com.opencsv.bean.CsvBindByName
import java.time.Instant

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
    var uniqueId: Int = -1,  // GETS REWRITTEN ON LOAD IF MISSING
    @CsvBindByName(column = "TERM_LEARNT_SCORE")
    private var termLearntScore: Double = 0.0,
    @CsvBindByName(column = "DEF_LEARNT_SCORE")
    private var defLearntScore: Double = 0.0,
    @CsvBindByName(column = "TERM")
    private var term: String = "term",
    @CsvBindByName(column = "DEFINITION")
    private var definition: String = "definition",
    @CsvBindByName(column = "LIST_NUMBER")
    var listNumber: Int = -1, // GETS REWRITTEN ON LOAD IF MISSING
    @CsvBindByName(column = "TERM_TYPE")
    var termType: String = "term_type",
    @CsvBindByName(column = "TERM_DATE_LAST_TESTED")
    private var termDateLastTested: Instant? = null,
    @CsvBindByName(column = "DEF_DATE_LAST_TESTED")
    private var defDateLastTested: Instant? = null,
    @CsvBindByName(column = "TERM_AVG_LEARNT_SCORE")
    private var termAvgLearntScore: Double = 0.0,
    @CsvBindByName(column = "DEF_AVG_LEARNT_SCORE")
    private var defAvgLearntScore: Double = 0.0,
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
    fun learntScore(termFirst: Boolean): Double =
        if (termFirst) termLearntScore else defLearntScore

    fun setLearntScore(termFirst: Boolean, value: Double) {
        if (termFirst) {
            termLearntScore = value
        } else {
            defLearntScore = value
        }
    }

    fun dateLastTested(termFirst: Boolean): Instant? =
        if (termFirst) termDateLastTested else defDateLastTested

    fun setDateLastTested(termFirst: Boolean, value: Instant?) {
        if (termFirst) {
            termDateLastTested = value
        } else {
            defDateLastTested = value
        }
    }

    fun avgLearntScore(termFirst: Boolean): Double =
        if (termFirst) termAvgLearntScore else defAvgLearntScore

    fun setAvgLearntScore(termFirst: Boolean, value: Double) {
        if (termFirst) {
            termAvgLearntScore = value
        } else {
            defAvgLearntScore = value
        }
    }

    fun vocab(termFirst: Boolean): String =
        if (termFirst) term else definition

    fun vocabDef(termFirst: Boolean): String =
        if (termFirst) definition else term
}