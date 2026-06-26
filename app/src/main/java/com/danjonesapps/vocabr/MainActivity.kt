package com.danjonesapps.vocabr

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ListAdapter

    // Launcher for file picker
    private val csvFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
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
        val deleteButton = findViewById<Button>(R.id.delete_button)
        val exportButton = findViewById<Button>(R.id.export_button)
        val learnButton = findViewById<Button>(R.id.learn_button)
        val settingsButton = findViewById<Button>(R.id.settings_button)

        val allLists = AppSettings.settings.getAllLists()

        if (allLists.any { it.cachedStats == null }) {
            recalculateAllLists(this)
        }

        adapter = ListAdapter(
            this,
            allLists
        ) { updatedLists ->
            AppSettings.settings.setLists(updatedLists)
        }
        // adapter.submitData(AppSettings.settings.getAllLists()) maybe needed

        recyclerView = findViewById(R.id.list_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
            }
            csvFilePicker.launch(intent)
        }

        deleteButton.setOnClickListener {
            val selected = adapter.getSelectedList()

            if (selected == null) {
                Toast.makeText(
                    this,
                    "Load VOCAB first.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            saveFileToDownloads(selected.fileName)

            val updatedLists =
                AppSettings.settings
                    .getAllLists()
                    .filter {
                        it.id != selected.id
                    }

            AppSettings.settings.setLists(updatedLists)

            adapter.submitData(updatedLists.toMutableList())
        }

        exportButton.setOnClickListener {
            if (AppSettings.settings.getAllLists().isNotEmpty()) {
                saveFileToDownloads(fileName = AppSettings.settings.getFirstList().fileName)
            }
            else {
                Toast.makeText(this, "Load VOCAB first.", Toast.LENGTH_SHORT).show()
            }
        }

        learnButton.setOnClickListener {
            val selected = adapter.getSelectedList()

            if (selected == null) {
                Toast.makeText(
                    this,
                    "Load VOCAB first.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val intent = Intent(this, LearnActivity::class.java)
            startActivity(intent)
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun saveCsvToInternalStorage(
        uri: Uri
    ) {
        try {
            val fileName = getFileNameFromUri(uri) ?: "imported.csv"
            val inputStream = contentResolver.openInputStream(uri)
            val outputFile = File(filesDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            val newList = calculateNewList(outputFile, fileName)
            val updatedLists = AppSettings.settings.getAllLists()
            updatedLists.add(0, newList)
            AppSettings.settings.setLists(updatedLists)

            adapter.submitData(updatedLists)

            Toast.makeText(
                this,
                "File Successfully Loaded.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {

            e.printStackTrace()

            Toast.makeText(
                this,
                "Failed to save file.",
                Toast.LENGTH_SHORT
            ).show()
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

    private fun saveFileToDownloads(fileName: String) {
        val inputFile = File(filesDir, fileName)
        if (!inputFile.exists()) {
            Toast.makeText(this, "File not found: $fileName.", Toast.LENGTH_SHORT).show()
        }

        try {
            val allLines = inputFile.readLines()
            val header = allLines.firstOrNull() ?: ""
            val dataLines = if (allLines.size > 1) allLines.drop(1) else emptyList()

            val linesToSave: List<String> = if (!AppSettings.settings.getIsTopNSelected()) {
                // Top N not selected → return all rows
                allLines
            } else {
                // Top N selected → return header + top N rows
                val n = minOf(AppSettings.settings.getCurrentTopN(), dataLines.size)
                listOf(header) + dataLines.take(n)
            }

            val mimeType = "text/csv"
            val outputStream: OutputStream?
            val resolver = contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1) //mark as being written
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    Toast.makeText(this, "Failed to create download file.", Toast.LENGTH_SHORT).show()
                    return
                }

                outputStream = resolver.openOutputStream(uri)
                outputStream?.bufferedWriter().use { writer ->
                    linesToSave.forEach { writer?.write(it + "\n") }
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0) //mark as no longer being written
                resolver.update(uri, values, null, null) //now visible to other apps

            } else { //if legacy
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outputFile = File(downloadsDir, fileName)
                outputFile.bufferedWriter().use { writer ->
                    linesToSave.forEach { writer.write(it + "\n") }
                }
            }

            Toast.makeText(this, "File saved to Downloads.", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving file: ${e.message}.", Toast.LENGTH_LONG).show()
        }
    }
}