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
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Job
import java.io.File
import java.time.Instant
import kotlin.math.abs
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

class LearnActivity : AppCompatActivity() {
    // buttons and text views
    private lateinit var quitButton: Button
    private lateinit var backButton: Button
    private lateinit var correctButton: Button
    private lateinit var incorrectButton: Button
    private lateinit var termCard: MaterialCardView
    private lateinit var examplesCard: ConstraintLayout
    private lateinit var examplesScroll: NestedScrollView
    private lateinit var termLayout: ConstraintLayout

    private lateinit var termTypeTextView: TextView
    private lateinit var termTextView: TextView
    private lateinit var quittingTextView: TextView
    private lateinit var correctTextView: TextView
    private lateinit var incorrectTextView: TextView
    private lateinit var dueTextView: TextView
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
    private var terms: MutableList<TermData> = mutableListOf()
    private var filteredTerms: MutableList<TermData> = mutableListOf()
    private var recent: MutableList<Quint<Int, Boolean, Boolean, Instant, Boolean>> = mutableListOf()
    private var futureTerms: MutableList<Pair<Int, Boolean>> = mutableListOf()
    private var repeatIncorrectIds: MutableList<Pair<Int, Int>> = mutableListOf()
    private var selectedTerm: SelectedTerm? = null
    private var correct: Int = 0
    private var incorrect: Int = 0
    private var recentLength: Int = 0
    private var reversing: Boolean = false
    private var quitting: Boolean = false
    private var flipped: Boolean = true
    private var resetFlip: Boolean = true
    private var waitingForCommand: Boolean = false
    private lateinit var csvFile: File
    private lateinit var listId: String
    private var showTermFirst: Boolean = true
    private var dueJob: Job? = null

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

        val vocabListObj = AppSettings.settings.getFirstList()
        csvFile = File(filesDir, vocabListObj.fileName)
        listId = vocabListObj.id
        showTermFirst = AppSettings.settings.getShowTermFirst()

        terms = loadTermDataFromCsv(csvFile).toMutableList()

        filteredTerms = terms.filter {
            it.listNumber in vocabListObj.minListNumber..vocabListObj.maxListNumber &&
                    it.termType in vocabListObj.termTypes
        }.toMutableList()

        calcScores(filteredTerms, showTermFirst = showTermFirst, predictedLearntScoreOnly = true)
        sortTerms(filteredTerms, showTermFirst)

        recentLength = minOf(filteredTerms.size - 1, AppSettings.settings.getAllowRepeatsAfter())

        showTerm()

        // search bar
        adapter = TermAdapter(emptyList(), showTermFirst)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupSearch()
    }

    private fun setCard(topTerm: SelectedTerm){
        val primaryTextColour = ContextCompat.getColor(
            this,
            if (topTerm.repeatIncorrect) R.color.quitting_yellow else R.color.term_white
        )

        val secondaryTextColour = ContextCompat.getColor(
            this,
            if (topTerm.repeatIncorrect) R.color.quitting_yellow_def else R.color.term_white_def
        )

        // SET TERM
        termTextView.setTextColor(primaryTextColour)
        termSubTextView.setTextColor(secondaryTextColour)

        if (!flipped) {
            val params = termLayout.layoutParams as ConstraintLayout.LayoutParams
            params.horizontalBias = 0.46f
            params.verticalBias = 0.46f
            termLayout.layoutParams = params
            termTextView.text = topTerm.termData.vocab(showTermFirst)
            if (topTerm.termData.ipa.isNullOrEmpty() || !showTermFirst) {
                termSubTextView.visibility = View.GONE
            } else {
                termSubTextView.text = topTerm.termData.ipa
                termSubTextView.visibility = View.VISIBLE
            }
            examplesCard.visibility = View.GONE
        }
        else {
            val params = termLayout.layoutParams as ConstraintLayout.LayoutParams
            params.horizontalBias = 0f
            params.verticalBias = 1f
            termLayout.layoutParams = params
            termTextView.text = topTerm.termData.vocabDef(showTermFirst)
            if (topTerm.termData.ipa.isNullOrEmpty() || showTermFirst) {
                termSubTextView.text = topTerm.termData.vocab((showTermFirst))
            } else {
                termSubTextView.text = topTerm.termData.ipa
            }
            termSubTextView.visibility = View.VISIBLE
            examplesCard.visibility = View.VISIBLE

            // SET EXAMPLES
            val examples = listOf(
                Triple(topTerm.termData.exampleOne, topTerm.termData.exampleOneDef, Pair(exampleOneTextView, exampleOneDefTextView)),
                Triple(topTerm.termData.exampleTwo, topTerm.termData.exampleTwoDef, Pair(exampleTwoTextView, exampleTwoDefTextView)),
                Triple(topTerm.termData.exampleThree, topTerm.termData.exampleThreeDef, Pair(exampleThreeTextView, exampleThreeDefTextView))
            )
            val hasNoExamples = examples.all { it.first.isNullOrEmpty() || it.second.isNullOrEmpty() }

            if (hasNoExamples) {
                examplesHintTextView.visibility = View.VISIBLE
                examples.forEach { (_, _, views) ->
                    views.first.visibility = View.GONE
                    views.second.visibility = View.GONE
                }
            } else {
                examplesHintTextView.visibility = View.GONE
                examples.forEach { (example, def, views) ->
                    if (!example.isNullOrEmpty() && !def.isNullOrEmpty()) {
                        views.first.apply {
                            text = example
                            visibility = View.VISIBLE
                            setTextColor(primaryTextColour)
                        }
                        views.second.apply {
                            text = def
                            visibility = View.VISIBLE
                            setTextColor(secondaryTextColour)
                        }
                    } else {
                        views.first.visibility = View.GONE
                        views.second.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun showTerm(){
        val quad = getTop(filteredTerms, recent, repeatIncorrectIds, futureTerms, reversing, quitting)
        recent = quad.recent
        futureTerms = quad.futureTerms
        repeatIncorrectIds = quad.repeatIncorrectIds

        val topTerm: SelectedTerm = quad.selectedTerm ?: run {
            saveAndTerminate()
            return
        }
        selectedTerm = topTerm

        reversing = false

        // SET TERM TYPE
        termTypeTextView.text = topTerm.termData.termType

        // ENSURE TERM IS SHOWN IF NEW TERM
        if (resetFlip) {
            flipped = false
        }
        else {
            resetFlip = true
        }

        setCard(topTerm)

        // SET QUITTING
        if (quitting) {
            quittingTextView.visibility = View.VISIBLE
            // Could have due count show how many terms left when quitting
        }

        // SET DUE COUNT
        if (!quitting) {
            calcAndSetDueCount()
        } else {
            val quittingDueCount = futureTerms.size + repeatIncorrectIds.size - 1
            if (quittingDueCount > 0) {
                dueTextView.visibility = View.VISIBLE
                dueTextView.text = quittingDueCount.toString()
            } else {
                dueTextView.visibility = View.GONE
            }
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
            dueJob?.cancel()
            if (!quitting) {
                quitting = true

                // adds all incorrect terms to repeat_incorrect
                saveResult(
                    filteredTerms = filteredTerms,
                    recent = recent,
                    repeatIncorrectIds = repeatIncorrectIds,
                    showTermFirst = showTermFirst
                )

                // keep only repeat incorrect terms of future_terms
                futureTerms = futureTerms.filter { it.second }.toMutableList()

                // ensure than term on screen is chosen again
                if (topTerm.repeatIncorrect) {
                    futureTerms.add(0, Pair(topTerm.termData.uniqueId, true))
                    resetFlip = false
                }

                dueTextView.setBackgroundColor(ContextCompat.getColor(this, R.color.quitting_yellow_def))

                recent.clear()
                correct = 0
                incorrect = 0
            }
            else { // TERMINATE
                saveAndTerminate()
                return
            }
        }
        else if (buttonCommand == ButtonCommand.BACK) {
            if (recent.isNotEmpty()) {
                dueJob?.cancel()
                if (!recent.last().repeatIncorrect) {
                    if (recent.last().wasCorrect) {
                        correct--
                    } else {
                        incorrect--
                    }
                }
                futureTerms.add(0, Pair(topTerm.termData.uniqueId, topTerm.repeatIncorrect))
                reversing = true
            }
            else {
                waitingForCommand = true
                return
            }
        }
        else if (buttonCommand == ButtonCommand.NOT) {
            dueJob?.cancel()
            val dueInstant = topTerm.termData.dueInstant(showTermFirst)
            val now = Instant.now()
            recent.add(Quint(
                topTerm.termData.uniqueId,
                false,
                topTerm.repeatIncorrect,
                now,
                dueInstant == null || !dueInstant.isAfter(now)
            ))
            if (!topTerm.repeatIncorrect) {
                incorrect++
            }
            continueToNext()
        }
        else if (buttonCommand == ButtonCommand.GOT) {
            dueJob?.cancel()
            val dueInstant = topTerm.termData.dueInstant(showTermFirst)
            val now = Instant.now()
            recent.add(Quint(
                topTerm.termData.uniqueId,
                true,
                topTerm.repeatIncorrect,
                now,
                dueInstant == null || !dueInstant.isAfter(now)
            ))
            if (!topTerm.repeatIncorrect) {
                correct++
            }
            continueToNext()
        }
        else if (buttonCommand == ButtonCommand.SHOW) {
            flipped = !flipped
            setCard(topTerm)
            waitingForCommand = true
            return
        }
        showTerm()
    }

    private fun continueToNext(){
        if (recent.size > recentLength) {
            saveResult(filteredTerms, mutableListOf(recent[0]), repeatIncorrectIds, showTermFirst)
            recent.removeAt(0)
        } else if (quitting && futureTerms.isEmpty()) {
            while (repeatIncorrectIds.isEmpty() && recent.isNotEmpty()) {
                saveResult(filteredTerms, mutableListOf(recent[0]), repeatIncorrectIds, showTermFirst)
                recent.removeAt(0)
            }
        }
    }

    private fun saveFile(verbose: Boolean = false){
        sortTerms(terms, showTermFirst)
        saveTermDataToCsv(terms, csvFile)
        if (verbose) {
            Toast.makeText(this, "Saved Successfully.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAndTerminate() {
        saveResult(
            filteredTerms = filteredTerms,
            recent = recent,
            repeatIncorrectIds = repeatIncorrectIds,
            showTermFirst = showTermFirst
        )
        saveFile()
        calculateList(listId, terms)
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // closes the current activity so user cannot use back
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

                val filteredList = filteredTerms.filter {
                    (it.vocabDef(showTermFirst).lowercase().contains(searchQuery) ||
                            it.vocab(showTermFirst).lowercase().contains(searchQuery)) &&
                            it.uniqueId != selectedTerm?.termData?.uniqueId
                }.sortedWith(compareByDescending {
                    // Prioritize items where term or definition starts with the query
                    it.vocabDef(showTermFirst).lowercase().startsWith(searchQuery) || it.vocab(showTermFirst).lowercase().startsWith(searchQuery)
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

    private fun initViews() {
        //buttons
        quitButton = findViewById(R.id.button_quit)
        quitButton.setOnClickListener { buttonPressed(ButtonCommand.QUIT) }
        backButton = findViewById(R.id.button_back)
        backButton.setOnClickListener { buttonPressed(ButtonCommand.BACK) }
        correctButton = findViewById(R.id.button_correct)
        correctButton.setOnClickListener { buttonPressed(ButtonCommand.GOT) }
        incorrectButton = findViewById(R.id.button_incorrect)
        incorrectButton.setOnClickListener { buttonPressed(ButtonCommand.NOT) }

        termCard = findViewById(R.id.term_card)
        termCard.setOnClickListener { buttonPressed(ButtonCommand.SHOW) }
        termLayout = findViewById(R.id.term_texts)
        examplesCard = findViewById(R.id.examples)
        examplesScroll = findViewById(R.id.examples_scroll)

        //text views
        termTypeTextView = findViewById(R.id.text_term_type)
        termTextView = findViewById(R.id.text_term)
        quittingTextView = findViewById(R.id.text_quitting)
        correctTextView = findViewById(R.id.text_correct)
        incorrectTextView = findViewById(R.id.text_incorrect)
        dueTextView = findViewById(R.id.text_due)
        termSubTextView = findViewById(R.id.text_sub_term)
        examplesHintTextView = findViewById(R.id.examples_hint)
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

        //examples scroll
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downY = 0f

        examplesScroll.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.y

                    // Start the card's pressed/ripple state
                    termCard.isPressed = true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Cancel ripple if the user is actually scrolling
                    if (abs(event.y - downY) > touchSlop) {
                        termCard.isPressed = false
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val isTap = abs(event.y - downY) <= touchSlop

                    termCard.isPressed = false

                    if (isTap) {
                        termCard.performClick()
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    termCard.isPressed = false
                }
            }

            false
        }
    }

    private fun calcAndSetDueCount() {
        val now = Instant.now()

        val currentId = selectedTerm?.termData?.uniqueId ?: -1
        val ids = (recent.map { it.uniqueId } + currentId).toSet()
        val dueCount = terms.count {
            val due = it.dueInstant(showTermFirst)

            it.uniqueId !in ids &&
                    (due == null || !due.isAfter(now))
        }

        // SET DUE COUNT
        if (dueCount > 0) {
            dueTextView.visibility = View.VISIBLE
            dueTextView.text = dueCount.toString()
        } else {
            dueTextView.visibility = View.GONE
        }

        // Find the next due time
        reschedule()
    }

    private fun reschedule() {
        // Cancel the previous scheduled callback
        dueJob?.cancel()

        val now = Instant.now()

        val nextDue = terms
            .mapNotNull { it.dueInstant(showTermFirst) }
            .filter { it.isAfter(now) }
            .minOrNull()

        // Nothing currently has a future due date
        if (nextDue == null) {
            return
        }

        val delayMillis = Duration
            .between(now, nextDue)
            .toMillis()

        dueJob = lifecycleScope.launch {
            delay(delayMillis)

            calcAndSetDueCount()
        }
    }
}