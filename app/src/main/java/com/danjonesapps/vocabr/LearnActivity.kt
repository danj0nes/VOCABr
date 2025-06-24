package com.danjonesapps.vocabr

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.Toast
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDate
import com.opencsv.bean.CsvBindByName
import com.opencsv.bean.CsvToBeanBuilder
import java.io.FileReader
import androidx.core.content.ContextCompat

var weightDaysSince: Int = 1
var weightCorrect: Int = 1
var weightTested: Int = 1

var testedMaxCap: Int = 15
var testedCapWeighting: Double = 0.9
var latestResultsLength: Int = 10
var daysSinceMinCap: Int = 30

var desiredTermTypes: MutableList<String> = mutableListOf("verbe", "mot", "nom", "adjectif", "phrase", "other")
var allowRepeatsAfter: Int = 15
const val BLANK_RESULTS_STRING: String = "No Recent Results"

var todayDate: LocalDate = LocalDate.now()

class LearnActivity : AppCompatActivity() {
    // buttons and textviews
    private lateinit var quitButton: Button
    private lateinit var saveButton: Button
    private lateinit var backButton: Button
    private lateinit var correctButton: Button
    private lateinit var showButton: Button
    private lateinit var incorrectButton: Button
    private lateinit var learntScoreTextView: TextView
    private lateinit var termTextView: TextView
    private lateinit var quittingTextView: TextView
    private lateinit var correctTextView: TextView
    private lateinit var incorrectTextView: TextView

    //other vars and vals
    private var df: MutableList<TermData> = mutableListOf()
    private var recent: MutableList<Triple<Int, Boolean, Boolean>> = mutableListOf()
    private var futureTerms: MutableList<Pair<Int, Boolean>> = mutableListOf()
    private var repeatIncorrectIds: MutableList<Pair<Int, Int>> = mutableListOf()
    private var selectedTerm: SelectedTerm? = null
    private var correct: Int = 0
    private var incorrect: Int = 0
    private var recentLength: Int = 0
    private var reversing: Boolean = false
    private var quitting: Boolean = false
    private var showingTerm: Boolean = false

    private var waitingForCommand: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // android studio defaults
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_learn)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // end of android studio defaults

        //buttons
        quitButton = findViewById(R.id.button_quit)
        quitButton.setOnClickListener { buttonPressed(ButtonCommand.QUIT) }
        saveButton = findViewById(R.id.button_save)
        saveButton.setOnClickListener { buttonPressed(ButtonCommand.SAVE) }
        backButton = findViewById(R.id.button_back)
        backButton.setOnClickListener { buttonPressed(ButtonCommand.BACK) }
        correctButton = findViewById(R.id.button_correct)
        correctButton.setOnClickListener { buttonPressed(ButtonCommand.GOT) }
        incorrectButton = findViewById(R.id.button_incorrect)
        incorrectButton.setOnClickListener { buttonPressed(ButtonCommand.NOT) }
        showButton = findViewById(R.id.button_show)
        showButton.setOnClickListener { buttonPressed(ButtonCommand.SHOW) }

        //text views
        learntScoreTextView = findViewById(R.id.text_learnt_score)
        termTextView = findViewById(R.id.text_term)
        quittingTextView = findViewById(R.id.text_quitting)
        correctTextView = findViewById(R.id.text_correct)
        incorrectTextView = findViewById(R.id.text_incorrect)


        val fileName = intent.getStringExtra("fileName") ?: "default.csv"
        val file = File(filesDir, fileName)
        val terms = loadTermDataFromCsv(file).toMutableList()

        val updatedDF = calcLearntScore(df)
        df = updatedDF
        recentLength = minOf(updatedDF.size - 1, allowRepeatsAfter)

        showTerm()
    }

    private fun loadTermDataFromCsv(file: File): List<TermData> {
        file.bufferedReader().use { reader ->
            return CsvToBeanBuilder<TermData>(reader)
                .withType(TermData::class.java)
                .withIgnoreLeadingWhiteSpace(true)
                .build()
                .parse()
        }
    }

    private fun showTerm(){
        val quad = getTop(df, recent, repeatIncorrectIds, futureTerms, reversing, quitting)
        recent = quad.recent
        futureTerms = quad.futureTerms
        repeatIncorrectIds = quad.repeatIncorrectIds


        val topTerm: SelectedTerm? = quad.selectedTerm
        if (topTerm == null) {
            saveAndEnd()
            return
        }
        selectedTerm = topTerm

        reversing = false

        // SET LEARNT SCORE
        val learntScorePercent = (topTerm.learntScore * 100).toInt()
        learntScoreTextView.text = getString(R.string.learnt_score, learntScorePercent)

        // SET TERM
        if (topTerm.repeatIncorrect) {
            termTextView.setTextColor(getColor(R.color.quitting_yellow))
        }
        else {
            termTextView.setTextColor(getColor(R.color.term_white))
        }
        termTextView.text = topTerm.term
        showingTerm = true

        // SET QUITTING
        if (quitting) {
            quittingTextView.visibility = View.VISIBLE
        }

        // SET CORRECT AND INCORRECT
        correctTextView.text = correct.toString()
        incorrectTextView.text = incorrect.toString()

        // GET KEY PRESS
        waitingForCommand = true
    }

    private fun buttonPressed(buttonCommand: ButtonCommand) {
        if (!waitingForCommand) { return }
        waitingForCommand = false

        val topTerm: SelectedTerm = selectedTerm ?: return // change

        if (buttonCommand == ButtonCommand.QUIT) {

        }
        else if (buttonCommand == ButtonCommand.SAVE) {

        }
        else if (buttonCommand == ButtonCommand.BACK && recent.isNotEmpty()) {
            if (!recent.last().third) {
                if (recent.last().second) {
                    correct--
                } else {
                    incorrect--
                }
            }
            futureTerms.add(0, Pair(topTerm.id, topTerm.repeatIncorrect))
            reversing = true
            showTerm()
        }
        else if (buttonCommand == ButtonCommand.NOT) {
            recent.add(Triple(topTerm.id, false, topTerm.repeatIncorrect))
            if (!topTerm.repeatIncorrect) {
                incorrect++
            }
            continueToNext()
            showTerm()
        }
        else if (buttonCommand == ButtonCommand.GOT) {
            recent.add(Triple(topTerm.id, true, topTerm.repeatIncorrect))
            if (!topTerm.repeatIncorrect) {
                correct++
            }
            continueToNext()
            showTerm()
        }
        else if (buttonCommand == ButtonCommand.SHOW) {
            if (showingTerm) {
                termTextView.text = topTerm.definition
            }
            else {
                termTextView.text = topTerm.term
            }
            showingTerm = !showingTerm
            waitingForCommand = true
        }
    }

    private fun continueToNext(){
        if (recent.size > recentLength) {
            df = saveResult(df, mutableListOf(recent[0]), repeatIncorrectIds)
            recent.removeAt(0)
        } else if (quitting && futureTerms.isEmpty()) {
            while (repeatIncorrectIds.isEmpty() && recent.isNotEmpty()) {
                df = saveResult(df, mutableListOf(recent[0]), repeatIncorrectIds)
                recent.removeAt(0)
            }
        }
    }

    private fun saveAndEnd(){
        return
    }
}