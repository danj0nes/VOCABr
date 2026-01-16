package com.danjonesapps.vocabr

import android.app.Activity
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
import java.io.InputStream
import java.io.OutputStream


class MainActivity : AppCompatActivity() {
    private var currentDaysSince: Float = 1f
    private var currentCorrect: Float = 1f
    private var currentTested: Float = 1f
    private var isTopNSelected: Boolean = false
    private var currentTopN: Int = 26
    companion object {
        const val SETTINGS_REQUEST_CODE = 1001
        const val SETTINGS_FILE = "settings.txt"
    }
    private var listsData: MutableList<SavedListData> = mutableListOf()
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

        loadSettingsFromFile()

        listsData.addAll(readListData(this))

        adapter = ListAdapter(this, listsData) { updatedList ->
            listsData = updatedList
        }
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
            if (listsData.isNotEmpty()) {
                saveFileToDownloads(fileName = listsData[0].fileName)
                listsData.removeAt(0)
                writeListDataToListsFile(this, listsData)
                adapter.updateRecyclerView(true)
            }
            else {
                Toast.makeText(this, "Load VOCAB first.", Toast.LENGTH_SHORT).show()
            }
        }

        exportButton.setOnClickListener {
            if (listsData.isNotEmpty()) {
                saveFileToDownloads(fileName = listsData[0].fileName)
            }
            else {
                Toast.makeText(this, "Load VOCAB first.", Toast.LENGTH_SHORT).show()
            }
        }

        learnButton.setOnClickListener {
            if (listsData.isNotEmpty()) {
                val intent = Intent(this, LearnActivity::class.java).apply {
                    putExtra("fileName", listsData[0].fileName)

                    // Pass settings
                    putExtra("DAYS_SINCE", currentDaysSince)
                    putExtra("CORRECT", currentCorrect)
                    putExtra("TESTED", currentTested)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Load VOCAB first.", Toast.LENGTH_SHORT).show()
            }
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.putExtra("DAYS_SINCE", currentDaysSince)
            intent.putExtra("CORRECT", currentCorrect)
            intent.putExtra("TESTED", currentTested)
            intent.putExtra("TOP_N_SELECTED", isTopNSelected)
            intent.putExtra("TOP_N_VALUE", currentTopN)
            startActivityForResult(intent, SETTINGS_REQUEST_CODE)
        }
    }

    private fun saveCsvToInternalStorage(uri: Uri) {
        try {
            val fileName = getFileNameFromUri(uri) ?: "imported.csv"

            listsData.add(0, SavedListData(fileName))
            writeListDataToListsFile(this, listsData)
            adapter.updateRecyclerView(false)

            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val outputFile = File(filesDir, fileName)

            inputStream?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(this, "File Successfully Loaded.", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save file.", Toast.LENGTH_SHORT).show()
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

            val linesToSave: List<String> = if (!isTopNSelected) {
                // Top N not selected → return all rows
                allLines
            } else {
                // Top N selected → return header + top N rows
                val n = minOf(currentTopN, dataLines.size)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK || data == null) return

        when (requestCode) {

            SETTINGS_REQUEST_CODE -> {
                // Retrieve updated values from SettingsActivity
                currentDaysSince = data.getFloatExtra("DAYS_SINCE", currentDaysSince)
                currentCorrect = data.getFloatExtra("CORRECT", currentCorrect)
                currentTested = data.getFloatExtra("TESTED", currentTested)
                isTopNSelected = data.getBooleanExtra("TOP_N_SELECTED", isTopNSelected)
                currentTopN = data.getIntExtra("TOP_N_VALUE", currentTopN)

                saveSettingsToFile()
            }
        }
    }

    private fun saveSettingsToFile() {
        try {
            val fileOutput = openFileOutput(SETTINGS_FILE, MODE_PRIVATE)
            val content = "$currentDaysSince,$currentCorrect,$currentTested,$isTopNSelected,$currentTopN"
            fileOutput.write(content.toByteArray())
            fileOutput.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSettingsFromFile() {
        try {
            val fileInput = openFileInput(SETTINGS_FILE)
            val content = fileInput.bufferedReader().use { it.readText() }
            fileInput.close()

            val parts = content.split(",")
            if (parts.size == 5) {
                currentDaysSince = parts[0].toFloatOrNull() ?: 1f
                currentCorrect = parts[1].toFloatOrNull() ?: 1f
                currentTested = parts[2].toFloatOrNull() ?: 1f
                isTopNSelected = parts[3].toBoolean()
                currentTopN = parts[4].toIntOrNull() ?: 26
            }
        } catch (e: Exception) {
            // File might not exist yet — use default values
            e.printStackTrace()
        }
    }
}