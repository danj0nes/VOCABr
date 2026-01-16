package com.danjonesapps.vocabr

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.enableEdgeToEdge
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var daysSinceSlider: Slider
    private lateinit var correctSlider: Slider
    private lateinit var testedSlider: Slider

    private lateinit var radioGroup: RadioGroup
    private lateinit var radioAll: RadioButton
    private lateinit var radioTopN: RadioButton

    private lateinit var topNInputLayout: TextInputLayout
    private lateinit var topNEditText: TextInputEditText

    private lateinit var returnButton: Button

    private var topNValue: Int = 26

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
        // 1. Find views
        // -------------------------
        daysSinceSlider = findViewById(R.id.settings_days_since_slider)
        correctSlider = findViewById(R.id.settings_correct_slider)
        testedSlider = findViewById(R.id.settings_tested_slider)

        radioGroup = findViewById(R.id.radioGroup)
        radioAll = findViewById(R.id.settings_radio_all)
        radioTopN = findViewById(R.id.settings_radio_topn)

        topNInputLayout = findViewById(R.id.settings_topn_input)
        topNEditText = findViewById(R.id.topNEditText)

        returnButton = findViewById(R.id.settings_return_button)

        // -------------------------
        // 2. Receive current values from MainActivity
        // -------------------------
        val daysSince = intent.getFloatExtra("DAYS_SINCE", 1f)
        val correct = intent.getFloatExtra("CORRECT", 1f)
        val tested = intent.getFloatExtra("TESTED", 1f)
        val topNSelected = intent.getBooleanExtra("TOP_N_SELECTED", false)
        topNValue = intent.getIntExtra("TOP_N_VALUE", 26)

        // Set initial slider values
        daysSinceSlider.value = daysSince
        correctSlider.value = correct
        testedSlider.value = tested

        // Set radio button & Top N input
        if (topNSelected) {
            radioTopN.isChecked = true
            topNInputLayout.isEnabled = true
            topNEditText.setText(topNValue.toString())
        } else {
            radioAll.isChecked = true
            topNInputLayout.isEnabled = false
            topNEditText.setText("")
        }

        // -------------------------
        // 3. Enable/disable Top N input based on radio selection
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

        // -------------------------
        // 4. Return button: pass updated values back
        // -------------------------
        returnButton.setOnClickListener {
            val intent = Intent().apply {
                putExtra("DAYS_SINCE", daysSinceSlider.value)
                putExtra("CORRECT", correctSlider.value)
                putExtra("TESTED", testedSlider.value)
                putExtra("TOP_N_SELECTED", radioTopN.isChecked)

                val nValue = topNEditText.text.toString().toIntOrNull() ?: topNValue
                putExtra("TOP_N_VALUE", nValue)
            }
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }
}