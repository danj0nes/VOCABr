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
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate


private const val LISTS_FILE_NAME = "lists.txt"

class MainActivity : AppCompatActivity() {
    private var listsData: MutableList<TermList> = mutableListOf()
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

        listsData.addAll(readListNames().map { readList(it) })

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
                writeFileNamesToListsFile()
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
                }
                startActivity(intent)
            }
            else {
                Toast.makeText(this, "Load VOCAB first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCsvToInternalStorage(uri: Uri) {
        try {
            val fileName = getFileNameFromUri(uri) ?: "imported.csv"

            listsData.add(0, readList(fileName))
            writeFileNamesToListsFile()
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
                inputFile.inputStream().use { input ->
                    outputStream?.use { out ->
                        input.copyTo(out)
                    }
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0) //mark as no longer being written
                resolver.update(uri, values, null, null) //now visible to other apps

            } else { //if legacy
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outputFile = File(downloadsDir, fileName)
                inputFile.copyTo(outputFile, overwrite = true)
            }

            Toast.makeText(this, "File saved to Downloads.", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving file: ${e.message}.", Toast.LENGTH_LONG).show()
        }
    }

    private fun readList(fileName: String): TermList {

        // read list and aggregate

        return TermList(fileName, 0, 0f, LocalDate.now())
    }

    private fun readListNames(): List<String> {
        val file = File(filesDir, LISTS_FILE_NAME)

        // If the file doesn't exist, return an empty list
        if (!file.exists()) {
            return emptyList()
        }

        // Read all lines safely
        return try {
            file.readLines()
                .map { it.trim() }        // remove any extra spaces
                .filter { it.isNotEmpty() } // ignore blank lines
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun writeFileNamesToListsFile() {
        val file = File(filesDir, LISTS_FILE_NAME)

        try {
            file.writeText(
                listsData.joinToString(separator = "\n") { it.fileName }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}