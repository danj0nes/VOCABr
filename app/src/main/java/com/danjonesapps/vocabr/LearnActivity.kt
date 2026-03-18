package com.danjonesapps.vocabr

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
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.time.LocalDate

var weightDaysSince: Float = 1f
var weightCorrect: Float = 1f
var weightTested: Float = 1f

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
    private lateinit var termCard: MaterialCardView
    private lateinit var examplesCard: ConstraintLayout
    private lateinit var incorrectButton: Button

    private lateinit var termTypeTextView: TextView
    private lateinit var termTextView: TextView
    private lateinit var quittingTextView: TextView
    private lateinit var correctTextView: TextView
    private lateinit var incorrectTextView: TextView
    private lateinit var termSubTextView: TextView
    private lateinit var examplesHintTextView: TextView
    private lateinit var exampleOneTextView: TextView
    private lateinit var exampleTwoTextView: TextView
    private lateinit var exampleThreeTextView: TextView
    private lateinit var exampleOneDefTextView: TextView
    private lateinit var exampleTwoDefTextView: TextView
    private lateinit var exampleThreeDefTextView: TextView

    private lateinit var recyclerView: CustomRecyclerView
    private lateinit var adapter: TermAdapter
    private lateinit var searchInput: EditText
    private lateinit var clearIcon: ImageView
    private lateinit var dimOverlay: View

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
    private var showingExamples: Boolean = false

    private var waitingForCommand: Boolean = false

    private var file: File? = null
    private lateinit var fileName: String
    private lateinit var listsData: MutableList<SavedListData>

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

        fileName = intent.getStringExtra("fileName") ?: run {
            terminate()
            return
        }
        weightDaysSince = intent.getFloatExtra("DAYS_SINCE", 1f)
        weightCorrect = intent.getFloatExtra("CORRECT", 1f)
        weightTested = intent.getFloatExtra("TESTED", 1f)

        listsData = readListData(this)

        val file = File(filesDir, fileName)
        this.file = file

        // add checks!!!!!!!!!!!!!!
        val terms = loadTermDataFromCsv(file).toMutableList()

        val updatedDF = calcLearntScore(terms)
        df = updatedDF
        recentLength = minOf(updatedDF.size - 1, allowRepeatsAfter)

        saveListData()

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
        //learntScoreTextView.text = "learnt score: ${String.format("%.1f%%", topTerm.learntScore * 100f)}"

        // SET TERM TYPE
        termTypeTextView.text = topTerm.termType

        // ENSURE TERM IS SHOWN IF NEW TERM
        if (resetFlip) {
            showingTerm = true
            showingExamples = false
        }
        else {
            resetFlip = true
        }

        // SET TERM
        if (topTerm.repeatIncorrect) {
            termTextView.setTextColor(getColor(R.color.quitting_yellow))
            termSubTextView.setTextColor(getColor(R.color.quitting_yellow_def))
        }
        else {
            termTextView.setTextColor(getColor(R.color.term_white))
            termSubTextView.setTextColor(getColor(R.color.term_white_def))
        }
        if (showingTerm) {
            termTextView.text = topTerm.term
            termSubTextView.visibility = View.GONE
        }
        else {
            termTextView.text = topTerm.definition
            termSubTextView.text = topTerm.term
            termSubTextView.visibility = View.VISIBLE
        }

        // SET EXAMPLES
        val examples = listOf(
            Triple(topTerm.exampleOne, topTerm.exampleDefOne, Pair(exampleOneTextView, exampleOneDefTextView)),
            Triple(topTerm.exampleTwo, topTerm.exampleDefTwo, Pair(exampleTwoTextView, exampleTwoDefTextView)),
            Triple(topTerm.exampleThree, topTerm.exampleDefThree, Pair(exampleThreeTextView, exampleThreeDefTextView))
        )

        val hasNoExamples = examples.all { it.first == null && it.second == null }

        if (hasNoExamples) {
            examplesHintTextView.apply {
                visibility = View.VISIBLE
                text = "no examples found"
            }
            examples.forEach { (_, _, views) ->
                views.first.visibility = View.GONE
                views.second.visibility = View.GONE
            }
        } else if (showingExamples) {
            examplesHintTextView.visibility = View.GONE

            examples.forEach { (example, def, views) ->
                if (example != null && def != null) {
                    views.first.apply {
                        text = example
                        visibility = View.VISIBLE
                    }
                    views.second.apply {
                        text = def
                        visibility = View.VISIBLE
                    }
                } else {
                    views.first.visibility = View.GONE
                    views.second.visibility = View.GONE
                }
            }
        } else {
            examplesHintTextView.apply {
                visibility = View.VISIBLE
                text = "* click to see examples *"
            }
            examples.forEach { (_, _, views) ->
                views.first.visibility = View.GONE
                views.second.visibility = View.GONE
            }
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
            if (topTerm.repeatIncorrect) {
                termTextView.setTextColor(getColor(R.color.quitting_yellow))
                termSubTextView.setTextColor(getColor(R.color.quitting_yellow_def))
            }
            else {
                termTextView.setTextColor(getColor(R.color.term_white))
                termSubTextView.setTextColor(getColor(R.color.term_white_def))
            }
            if (showingTerm) {
                termTextView.text = topTerm.term
                termSubTextView.visibility = View.GONE
            }
            else {
                termTextView.text = topTerm.definition
                termSubTextView.text = topTerm.term
                termSubTextView.visibility = View.VISIBLE
            }
            waitingForCommand = true
            return
        }
        else if (buttonCommand == ButtonCommand.EXAMPLES) {
            showingExamples = !showingExamples
            val examples = listOf(
                Triple(topTerm.exampleOne, topTerm.exampleDefOne, Pair(exampleOneTextView, exampleOneDefTextView)),
                Triple(topTerm.exampleTwo, topTerm.exampleDefTwo, Pair(exampleTwoTextView, exampleTwoDefTextView)),
                Triple(topTerm.exampleThree, topTerm.exampleDefThree, Pair(exampleThreeTextView, exampleThreeDefTextView))
            )

            val hasNoExamples = examples.all { it.first == null && it.second == null }

            if (hasNoExamples) {
                examplesHintTextView.apply {
                    visibility = View.VISIBLE
                    text = "no examples found"
                }
                examples.forEach { (_, _, views) ->
                    views.first.visibility = View.GONE
                    views.second.visibility = View.GONE
                }
            } else if (showingExamples) {
                examplesHintTextView.visibility = View.GONE

                examples.forEach { (example, def, views) ->
                    if (example != null && def != null) {
                        views.first.apply {
                            text = example
                            visibility = View.VISIBLE
                        }
                        views.second.apply {
                            text = def
                            visibility = View.VISIBLE
                        }
                    } else {
                        views.first.visibility = View.GONE
                        views.second.visibility = View.GONE
                    }
                }
            } else {
                examplesHintTextView.apply {
                    visibility = View.VISIBLE
                    text = "* click to see examples *"
                }
                examples.forEach { (_, _, views) ->
                    views.first.visibility = View.GONE
                    views.second.visibility = View.GONE
                }
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
        saveListData()


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
        val imm = view.context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setupSearch() {
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
                }).take(20)

                adapter.updateList(filteredList, searchQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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

        clearIcon.setOnClickListener {
            closeSearch()
        }
        dimOverlay.setOnClickListener {
            closeSearch()
        }

        // When user clicks or focuses the search bar
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                dimOverlay.fadeIn(200)
                recyclerView.visibility = View.VISIBLE
            }
        }

        recyclerView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val child = recyclerView.findChildViewUnder(event.x, event.y)
                if (child == null) {
                    closeSearch()
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            false
        }
    }

    private fun closeSearch() {
        searchInput.text.clear()
        searchInput.clearFocus()
        dimOverlay.fadeOut(200)
        recyclerView.visibility = View.GONE
        hideKeyboard(searchInput)
    }

    // Simple fade animation helpers
    private fun View.fadeIn(duration: Long = 200) {
        animate().alpha(1f).setDuration(duration)
            .withStartAction {
                visibility = View.VISIBLE
                alpha = 0f
            }.start()
    }

    private fun View.fadeOut(duration: Long = 200) {
        animate().alpha(0f).setDuration(duration)
            .withEndAction { visibility = View.GONE }
            .start()
    }

    private fun saveListData() {
        // 1. Calculate average learnt score
        val avgLearntScore = if (df.isNotEmpty()) {
            df.map { it.learntScore }.average().toFloat()
        } else 0f

        // 3. Create a new SavedListData object
        val newSavedData = SavedListData(
            fileName = fileName,
            numTerms= df.size,
            avgLearntScore = avgLearntScore,
            dateLastTested = todayDate  // today's date
        )

        // 4. Replace the first item or add if empty
        if (listsData.isNotEmpty()) {
            listsData[0] = newSavedData
        } else {
            listsData.add(newSavedData)
        }

        // 5. Write the updated list back to file
        writeListDataToListsFile(this, listsData)
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

        termCard = findViewById(R.id.header_card)
        termCard.setOnClickListener { buttonPressed(ButtonCommand.SHOW) }
        examplesCard = findViewById(R.id.examples)
        examplesCard.setOnClickListener { buttonPressed(ButtonCommand.EXAMPLES) }

        //text views
        termTypeTextView = findViewById(R.id.text_term_type)
        termTextView = findViewById(R.id.text_term)
        quittingTextView = findViewById(R.id.text_quitting)
        correctTextView = findViewById(R.id.text_correct)
        incorrectTextView = findViewById(R.id.text_incorrect)
        termSubTextView = findViewById(R.id.text_sub_term)
        examplesHintTextView = findViewById(R.id.click_to_hint)
        exampleOneTextView = findViewById(R.id.example_1)
        exampleTwoTextView = findViewById(R.id.example_2)
        exampleThreeTextView = findViewById(R.id.example_3)
        exampleOneDefTextView = findViewById(R.id.example_1_def)
        exampleTwoDefTextView = findViewById(R.id.example_2_def)
        exampleThreeDefTextView = findViewById(R.id.example_3_def)

        //others
        searchInput = findViewById(R.id.search_input)
        clearIcon = findViewById(R.id.clear_icon)
        dimOverlay = findViewById(R.id.dim_overlay)
        recyclerView = findViewById(R.id.term_recycler_view)
    }
}