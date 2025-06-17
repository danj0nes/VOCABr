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

data class TermData(
    @CsvBindByName(column = "unique_id")
    val uniqueId: Int = 0,
    @CsvBindByName(column = "learnt_score")
    val learntScore: Double = 0.0,
    @CsvBindByName(column = "term")
    val term: String = "term",
    @CsvBindByName(column = "definition")
    val definition: String = "definition",
    @CsvBindByName(column = "list_number")
    val listNumber: Int = 0,
    @CsvBindByName(column = "term_type")
    val termType: String = "term_type",
    @CsvBindByName(column = "dateLast_tested")
    val dateLastTested: LocalDate? = null,
    @CsvBindByName(column = "latest_results")
    val latestResults: String = "No Recent Results",
    @CsvBindByName(column = "tested_count")
    val testedCount: Int = 0
)

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
        val df = loadTermDataFromCsv(file)

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