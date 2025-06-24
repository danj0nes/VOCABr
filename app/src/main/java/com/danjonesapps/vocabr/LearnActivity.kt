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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_learn)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val fileName = intent.getStringExtra("fileName") ?: "default.csv"
        val file = File(filesDir, fileName)
        val df = loadTermDataFromCsv(file).toMutableList()

        val updatedDF = calcLearntScore(df)

        learn(updatedDF, file)

        Toast.makeText(this, "term loaded: ${df.size}", Toast.LENGTH_LONG).show()
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

    private fun learn(df: MutableList<TermData>, file: File){
        var recent: MutableList<Triple<Int, Boolean, Boolean>> = mutableListOf()
        var futureTerms: MutableList<Pair<Int, Boolean>> = mutableListOf()
        var repeatIncorrectIds: MutableList<Pair<Int, Int>> = mutableListOf()
        var correct: Int = 0
        var incorrect: Int = 0

        var recentLength = minOf(df.size - 1, allowRepeatsAfter)
        var reversing: Boolean = false
        var quitting: Boolean = false

        //text views
        val learntScoreTextView = findViewById<TextView>(R.id.text_learnt_score)
        val termTextView = findViewById<TextView>(R.id.text_term)
        val quittingTextView = findViewById<TextView>(R.id.text_quitting)

        //buttons
        val quitButton = findViewById<Button>(R.id.button_quit)
        val saveButton = findViewById<Button>(R.id.button_save)
        val backButton = findViewById<Button>(R.id.button_back)
        val correctButton = findViewById<Button>(R.id.button_correct)
        val incorrectButton = findViewById<Button>(R.id.button_incorrect)
        val showButton = findViewById<Button>(R.id.button_show)


        while (true) {
            val quad = getTop(df, recent, repeatIncorrectIds, futureTerms, reversing, quitting)
            recent = quad.recent
            futureTerms = quad.futureTerms
            repeatIncorrectIds = quad.repeatIncorrectIds

            val topTerm: SelectedTerm = quad.selectedTerm ?: break

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

            // SET QUITTING
            if (quitting) {
                quittingTextView.visibility = View.VISIBLE
            }

            // GET KEY PRESS

        }


    }
}