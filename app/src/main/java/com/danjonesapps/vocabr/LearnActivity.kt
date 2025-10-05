package com.danjonesapps.vocabr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.time.LocalDate

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
    // buttons and text views
    private lateinit var quitButton: Button
    private lateinit var saveButton: Button
    private lateinit var backButton: Button
    private lateinit var correctButton: Button
    private lateinit var showButton: Button
    private lateinit var incorrectButton: Button
    private lateinit var learntScoreTextView: TextView
    private lateinit var termTypeTextView: TextView
    private lateinit var termTextView: TextView
    private lateinit var quittingTextView: TextView
    private lateinit var correctTextView: TextView
    private lateinit var incorrectTextView: TextView

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TermAdapter

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
    private var resetFlip: Boolean = true

    private var waitingForCommand: Boolean = false

    private var file: File? = null

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

        initViews()

        val fileName = intent.getStringExtra("fileName") ?: run {
            terminate()
            return
        }

        val file = File(filesDir, fileName)
        this.file = file

        // add checks!!!!!!!!!!!!!!
        val terms = loadTermDataFromCsv(file).toMutableList()

        val updatedDF = calcLearntScore(terms)
        df = updatedDF
        recentLength = minOf(updatedDF.size - 1, allowRepeatsAfter)

        showTerm()

        // search bar
        adapter = TermAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupSearch()
    }

    private fun showTerm(){
        val quad = getTop(df, recent, repeatIncorrectIds, futureTerms, reversing, quitting)
        recent = quad.recent
        futureTerms = quad.futureTerms
        repeatIncorrectIds = quad.repeatIncorrectIds

        val topTerm: SelectedTerm = quad.selectedTerm ?: run {
            saveAndTerminate()
            return
        }
        selectedTerm = topTerm

        reversing = false

        // SET LEARNT SCORE
        val learntScorePercent = (topTerm.learntScore * 100).toInt()
        learntScoreTextView.text = getString(R.string.learnt_score, learntScorePercent)

        // SET TERM TYPE
        termTypeTextView.text = topTerm.termType

        // ENSURE TERM IS SHOWN IF NEW TERM
        if (resetFlip) {
            showingTerm = true
        }
        else {
            resetFlip = true
        }

        // SET TERM
        if (showingTerm) {
            if (topTerm.repeatIncorrect) {
                termTextView.setTextColor(getColor(R.color.quitting_yellow))
            }
            else {
                termTextView.setTextColor(getColor(R.color.term_white))
            }
            termTextView.text = topTerm.term
        }
        else {
            if (topTerm.repeatIncorrect) {
                termTextView.setTextColor(getColor(R.color.quitting_yellow_def))
            }
            else {
                termTextView.setTextColor(getColor(R.color.term_white_def))
            }
            termTextView.text = topTerm.definition
        }


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
            if (!quitting) {
                quitting = true

                // adds all incorrect terms to repeat_incorrect
                df = saveResult(
                    df = df,
                    recent = recent,
                    repeatIncorrectIds = repeatIncorrectIds
                )

                // keep only repeat incorrect terms of future_terms
                futureTerms = futureTerms.filter { it.second }.toMutableList()

                // ensure than term on screen is chosen again
                if (topTerm.repeatIncorrect) {
                    futureTerms.add(0, Pair(topTerm.id, true))
                    resetFlip = false
                }

                recent.clear()
                correct = 0
                incorrect = 0
            }
            else { // TERMINATE
                // ensure that term on screen if gotten wrong is saved as gotten wrong
                if (topTerm.repeatIncorrect) {
                    repeatIncorrectIds.add(0, Pair(topTerm.id, 0))
                }

                // add all repeat incorrect terms in future_terms to repeat_incorrect_ids
                repeatIncorrectIds.addAll(
                    futureTerms.filter { it.second }.map { Pair(it.first, 0) }
                )

                saveAndTerminate()
                return
            }
        }
        else if (buttonCommand == ButtonCommand.SAVE) {
            df = saveResult(
                df = df,
                recent = recent,
                repeatIncorrectIds = repeatIncorrectIds,
                recentGap = (recentLength - recent.size)
            )
            saveFile(verbose = true)

            // ensure than term on screen is chosen again
            futureTerms.add(0, Pair(topTerm.id, topTerm.repeatIncorrect))

            recent.clear()
            correct = 0
            incorrect = 0
            resetFlip = false
        }
        else if (buttonCommand == ButtonCommand.BACK) {
            if (recent.isNotEmpty()) {
                if (!recent.last().third) {
                    if (recent.last().second) {
                        correct--
                    } else {
                        incorrect--
                    }
                }
                futureTerms.add(0, Pair(topTerm.id, topTerm.repeatIncorrect))
                reversing = true
            }
            else {
                waitingForCommand = true
                return
            }
        }
        else if (buttonCommand == ButtonCommand.NOT) {
            recent.add(Triple(topTerm.id, false, topTerm.repeatIncorrect))
            if (!topTerm.repeatIncorrect) {
                incorrect++
            }
            continueToNext()
        }
        else if (buttonCommand == ButtonCommand.GOT) {
            recent.add(Triple(topTerm.id, true, topTerm.repeatIncorrect))
            if (!topTerm.repeatIncorrect) {
                correct++
            }
            continueToNext()
        }
        else if (buttonCommand == ButtonCommand.SHOW) {
            showingTerm = !showingTerm
            if (showingTerm) {
                if (topTerm.repeatIncorrect) {
                    termTextView.setTextColor(getColor(R.color.quitting_yellow))
                }
                else {
                    termTextView.setTextColor(getColor(R.color.term_white))
                }
                termTextView.text = topTerm.term
            }
            else {
                if (topTerm.repeatIncorrect) {
                    termTextView.setTextColor(getColor(R.color.quitting_yellow_def))
                }
                else {
                    termTextView.setTextColor(getColor(R.color.term_white_def))
                }
                termTextView.text = topTerm.definition
            }
            waitingForCommand = true
            return
        }
        showTerm()
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

    private fun terminate() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // closes the current activity so user cannot use back
    }

    private fun saveFile(verbose: Boolean = false){
        val tempFile: File = file ?: run {
            Toast.makeText(this, "Saving Error.", Toast.LENGTH_SHORT).show()
            return
        }
        saveTermDataToCsv(df, tempFile)

        if (verbose) {
            Toast.makeText(this, "Saved Successfully.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndTerminate() {
        df = saveResult(
            df = df,
            recent = recent,
            repeatIncorrectIds = repeatIncorrectIds,
            terminating = true
        )
        saveFile()
        terminate()
    }

    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupSearch() {
        val searchInput = findViewById<EditText>(R.id.search_input)
        val clearIcon = findViewById<ImageView>(R.id.clear_icon)

        // Text change listener
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.isEmpty()) {
                    clearIcon.visibility = View.GONE
                    adapter.updateList(emptyList())
                    return
                }
                clearIcon.visibility = View.VISIBLE

                val searchQuery = query.lowercase()

                val filteredList = df.filter {
                    it.definition.lowercase().contains(searchQuery) ||
                            it.term.lowercase().contains(searchQuery)
                }.sortedWith(compareByDescending {
                    // Prioritize items where term or definition starts with the query
                    it.definition.lowercase().startsWith(searchQuery) || it.term.lowercase().startsWith(searchQuery)
                }).take(5)

                adapter.updateList(filteredList, searchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Clear icon click
        clearIcon.setOnClickListener {
            searchInput.text.clear()
            searchInput.clearFocus()
            hideKeyboard(searchInput)
        }

        // Handle Enter key to close keyboard
        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                hideKeyboard(searchInput)
                searchInput.clearFocus()
                true
            } else false
        }
    }

    private fun initViews() {
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
        termTypeTextView = findViewById(R.id.text_term_type)
        termTextView = findViewById(R.id.text_term)
        quittingTextView = findViewById(R.id.text_quitting)
        correctTextView = findViewById(R.id.text_correct)
        incorrectTextView = findViewById(R.id.text_incorrect)

        recyclerView = findViewById(R.id.term_recycler_view)
    }
}