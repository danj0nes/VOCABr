package com.danjonesapps.vocabr

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.MultiAutoCompleteTextView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.enableEdgeToEdge
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider

class SettingsActivity : AppCompatActivity() {
    private lateinit var listNumberSlider: RangeSlider
    private lateinit var listNumberTitle: TextView
    private lateinit var termTypesAuto: MultiAutoCompleteTextView

    private lateinit var daysSinceSlider: Slider
    private lateinit var correctSlider: Slider
    private lateinit var testedSlider: Slider
    private lateinit var showTermSwitch: MaterialSwitch
    private lateinit var delayInputLayout: TextInputLayout
    private lateinit var delayEditText: TextInputEditText

    private lateinit var radioGroup: RadioGroup
    private lateinit var radioAll: RadioButton
    private lateinit var radioTopN: RadioButton

    private lateinit var topNInputLayout: TextInputLayout
    private lateinit var topNEditText: TextInputEditText

    private lateinit var returnButton: Button

    private var topNValue: Int = 26
    private var delayValue: Int = 15

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // -------------------------
        // Find views
        // -------------------------
        listNumberSlider = findViewById(R.id.settings_list_number_slider)
        listNumberTitle = findViewById(R.id.settings_list_list_number_title)
        termTypesAuto = findViewById(R.id.settings_list_term_type_field)

        daysSinceSlider = findViewById(R.id.settings_days_since_slider)
        correctSlider = findViewById(R.id.settings_correct_slider)
        testedSlider = findViewById(R.id.settings_tested_slider)
        showTermSwitch = findViewById(R.id.settings_list_show_term_switch)
        delayInputLayout = findViewById(R.id.settings_delay_input)
        delayEditText = findViewById(R.id.delayEditText)

        radioGroup = findViewById(R.id.radioGroup)
        radioAll = findViewById(R.id.settings_radio_all)
        radioTopN = findViewById(R.id.settings_radio_topn)

        topNInputLayout = findViewById(R.id.settings_topn_input)
        topNEditText = findViewById(R.id.topNEditText)

        returnButton = findViewById(R.id.settings_return_button)

        // Set List controls
        val allLists = AppSettings.settings.getAllLists()
        val vocabList = allLists.first()
        val stats = vocabList.cachedStats ?: run {
            Toast.makeText(
                this,
                "Error Occurred.",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val onlyOneList = stats.minListNumber == stats.maxListNumber
        if (onlyOneList) {
            listNumberSlider.visibility = View.GONE
            listNumberTitle.visibility = View.GONE
        } else {
            listNumberSlider.stepSize = 1f
            listNumberSlider.isTickVisible = false
            listNumberSlider.valueFrom = stats.minListNumber.toFloat()
            listNumberSlider.valueTo = stats.maxListNumber.toFloat()
            listNumberSlider.values = listOf(vocabList.minListNumber.toFloat(), vocabList.maxListNumber.toFloat())
        }

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            stats.allTermTypes
        )
        termTypesAuto.setAdapter(adapter)
        termTypesAuto.setText(
            vocabList.termTypes.joinToString(", "),
            false
        )
        termTypesAuto.threshold = 1
        termTypesAuto.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())

        // Set initial slider values
        daysSinceSlider.value = AppSettings.settings.getWeightDaysSince().toFloat()
        correctSlider.value = AppSettings.settings.getWeightCorrect().toFloat()
        testedSlider.value = AppSettings.settings.getWeightTested().toFloat()
        showTermSwitch.isChecked = AppSettings.settings.getShowTermFirst()
        topNValue = AppSettings.settings.getCurrentTopN()
        delayValue = AppSettings.settings.getAllowRepeatsAfter()

        // Set radio button & Top N input
        if (AppSettings.settings.getIsTopNSelected()) {
            radioTopN.isChecked = true
            topNInputLayout.isEnabled = true
            topNEditText.setText(topNValue.toString())
        } else {
            radioAll.isChecked = true
            topNInputLayout.isEnabled = false
            topNEditText.setText("")
        }

        // Set delay value input
        delayEditText.setText(delayValue.toString())

        // -------------------------
        // Enable/disable Top N input based on radio selection
        // -------------------------
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == radioTopN.id) {
                // Enable the input and restore the previous value
                topNInputLayout.isEnabled = true
                topNEditText.setText(topNValue.toString())
            } else {
                // Save the user's input before disabling
                val inputText = topNEditText.text.toString()
                topNValue = inputText.toIntOrNull() ?: topNValue // keep previous if invalid

                // Disable the input and clear the field visually
                topNInputLayout.isEnabled = false
                topNEditText.setText("")
            }
        }

        returnButton.setOnClickListener {
            if (!onlyOneList) {
                vocabList.minListNumber = listNumberSlider.values[0].toInt()
                vocabList.maxListNumber = listNumberSlider.values[1].toInt()
            }
            vocabList.termTypes = termTypesAuto.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it in stats.allTermTypes }
                .distinct()
            AppSettings.settings.setSettings(
                allLists,
                daysSinceSlider.value.toDouble(),
                correctSlider.value.toDouble(),
                testedSlider.value.toDouble(),
                delayEditText.text.toString().toIntOrNull() ?: delayValue,
                showTermSwitch.isChecked,
                radioTopN.isChecked,
                topNEditText.text.toString().toIntOrNull() ?: topNValue
            )
            recalculateAllLists(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}