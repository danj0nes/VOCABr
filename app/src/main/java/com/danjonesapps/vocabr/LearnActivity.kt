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

var weightDaysSince: Int = 1
var weightCorrect: Int = 1
var weightTested: Int = 1

var testedMaxCap: Int = 15
var testedCapWeighting: Double = 0.9
var latestResultsLength: Int = 10
var daysSinceMinCap: Int = 30

var desiredTermTypes: MutableList<String> = mutableListOf("verbe", "mot", "nom", "adjectif", "phrase", "other")
var allowRepeatsAfter: Int = 15
var minListNumber: Int? = null
var maxListNumber: Int? = null
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
}