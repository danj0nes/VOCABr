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
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

class MainActivity : AppCompatActivity() {
    private var fileName: String = "terms.csv" //need to add fileName saving functionality

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
        val playButton = findViewById<Button>(R.id.play_button)
        val exportButton = findViewById<Button>(R.id.export_button)

        exportButton.setOnClickListener {
            saveFileToDownloads(this.fileName)
        }

        loadButton.setOnClickListener {
            openCsvFilePicker()
        }

        playButton.setOnClickListener {
            val intent = Intent(this, LearnActivity::class.java)
            intent.putExtra("fileName", this.fileName) // Pass variable
            startActivity(intent) // Start LearnActivity
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
            this.fileName = fileName
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

    private fun saveFileToDownloads(fileName: String) {
        val inputFile = File(filesDir, fileName)
        if (!inputFile.exists()) {
            Toast.makeText(this, "File not found: $fileName", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Failed to create download file", Toast.LENGTH_SHORT).show()
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

            Toast.makeText(this, "File saved to Downloads", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}