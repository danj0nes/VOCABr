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

class MainActivity : AppCompatActivity() {
    // Launcher for file picker
    private val csvFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uri: Uri? = result.data?.data
            uri?.let {
                saveCsvToInternalStorage(it)
            }
        } else {
            Toast.makeText(this, "File selection cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val loadButton = findViewById<Button>(R.id.load_button)
        loadButton.setOnClickListener {
            openCsvFilePicker()
        }

        val readButton = findViewById<Button>(R.id.read_button)
        val textView = findViewById<TextView>(R.id.status_text)

        readButton.setOnClickListener {
            val fileName = "imported.csv"  // or use a constant/shared name
            val file = File(filesDir, fileName)

            if (file.exists()) {
                val content = readFirstValueFromCsv(file)
                textView.text = content ?: "No data found"
            } else {
                Toast.makeText(this, "CSV file not found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openCsvFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
        }
        csvFilePicker.launch(intent)
    }

    private fun saveCsvToInternalStorage(uri: Uri) {
        try {
            val fileName = getFileNameFromUri(uri) ?: "imported.csv"
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val outputFile = File(filesDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(this, "CSV saved: ${outputFile.absolutePath}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    private fun readFirstValueFromCsv(file: File): String? {
        return file.useLines { lines ->
            lines.firstOrNull()?.split(",")?.getOrNull(0)
        }
    }
}