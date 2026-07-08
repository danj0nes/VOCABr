package com.danjonesapps.vocabr

import android.content.Context
import com.opencsv.bean.CsvToBeanBuilder
import com.opencsv.bean.StatefulBeanToCsvBuilder
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.io.FileOutputStream
import org.json.JSONArray

class SettingsManager(context: Context) {
    private val appContext = context.applicationContext
    companion object {
        private const val FILE_NAME = "settings.json"
    }
    private var settings: JSONObject = loadSettings()
    private var cachedLists: MutableList<VocabList>? = null
    private fun loadSettings(): JSONObject {
        return try {
            val file = File(appContext.filesDir, FILE_NAME)
            if (!file.exists()) {
                val defaults = createDefaultSettings()
                saveJson(defaults)
                defaults
            } else {
                JSONObject(file.readText())
            }

        } catch (e: Exception) {
            e.printStackTrace()
            val defaults = createDefaultSettings()
            saveJson(defaults)
            defaults
        }
    }

    private fun createDefaultSettings(): JSONObject {
        return JSONObject().apply {
            put("lists", JSONArray())
            put("weightDaysSince", 1.0)
            put("weightCorrect", 1.0)
            put("weightTested", 1.0)
            put("allowRepeatsAfter", 15)
            put("showTermFirst", true)
            put("isTopNSelected", false)
            put("currentTopN", 20)
        }
    }

    private fun saveJson(json: JSONObject) {
        File(
            appContext.filesDir,
            FILE_NAME
        ).writeText(
            json.toString(4)
        )
    }

    fun save() { saveJson(settings) }

    // ==================
    // SETTINGS
    // ==================

    fun getWeightDaysSince() = settings.optDouble("weightDaysSince", 1.0)
    fun getWeightCorrect() = settings.optDouble("weightCorrect", 1.0)
    fun getWeightTested() = settings.optDouble("weightTested", 1.0)
    fun getAllowRepeatsAfter() = settings.optInt("allowRepeatsAfter", 15)
    fun getShowTermFirst() = settings.optBoolean("showTermFirst", true)
    fun getIsTopNSelected() = settings.optBoolean("isTopNSelected", false)
    fun getCurrentTopN() = settings.optInt("currentTopN", 20)

    fun setSettings(
        lists: MutableList<VocabList>,
        weightDaysSince: Double,
        weightCorrect: Double,
        weightTested: Double,
        allowRepeatsAfter: Int,
        isTopNSelected: Boolean,
        currentTopN: Int,
    ) {
        val array = JSONArray()
        cachedLists = lists
        lists.forEach { list ->
            array.put(list.toJson())
        }
        settings.put("lists", array)
        settings.put("weightDaysSince", weightDaysSince)
        settings.put("weightCorrect", weightCorrect)
        settings.put("weightTested", weightTested)
        settings.put("allowRepeatsAfter", allowRepeatsAfter)
        settings.put("isTopNSelected", isTopNSelected)
        settings.put("currentTopN", currentTopN)
        save()
    }

    fun setShowTermFirst(showTermFirst: Boolean) {
        settings.put("showTermFirst", showTermFirst)
        save()
    }

    // ==================
    // LISTS
    // ==================

    fun setLists(lists: MutableList<VocabList>) {
        cachedLists = lists

        val array = JSONArray()
        lists.forEach { array.put(it.toJson()) }

        settings.put("lists", array)
        save()
    }

    private fun getListsArray(): JSONArray {
        return settings
            .optJSONArray("lists")
            ?: JSONArray()
    }

    fun getAllLists(): MutableList<VocabList> {
        if (cachedLists == null) {
            val lists = mutableListOf<VocabList>()
            val array = getListsArray()
            for (i in 0 until array.length()) {
                lists.add(
                    array.getJSONObject(i).toVocabList()
                )
            }
            cachedLists = lists
        }
        return cachedLists!!
    }

    fun getFirstList(): VocabList {
        return if (cachedLists == null) {
            getListsArray().getJSONObject(0).toVocabList()
        } else {
            cachedLists!!.first()
        }
    }

    fun hasLoadedLists(): Boolean {
        return getListsArray().length() != 0
    }
}

fun loadTermDataFromCsv(file: File): List<TermData> {
    file.bufferedReader(Charsets.UTF_8).use { reader ->
        val terms = CsvToBeanBuilder<TermData>(reader)
            .withType(TermData::class.java)
            .withIgnoreLeadingWhiteSpace(true)
            .build()
            .parse()

        // fill missing UNIQUE_ID
        val nextId = (terms.maxOfOrNull { it.uniqueId.takeIf { id -> id > 0 } ?: 0 } ?: 0)
        var currentId = nextId + 1

        terms.forEach { term ->
            if (term.uniqueId < 0) {
                term.uniqueId = currentId++
            }
        }

        // Assign ONE shared LIST_NUMBER
        // to all missing rows
        val nextListNumber = (
                terms
                    .map { it.listNumber }
                    .filter { it >= 0 }
                    .maxOrNull() ?: 0
                ) + 1

        terms.forEach { term ->
            if (term.listNumber < 0) { // -1 is the default
                term.listNumber = nextListNumber
            }
        }

        return terms
    }
}

fun saveTermDataToCsv(termDataList: List<TermData>, file: File) {
    OutputStreamWriter(FileOutputStream(file), Charsets.UTF_8).use { writer ->
        val beanToCsv = StatefulBeanToCsvBuilder<TermData>(writer)
            .withApplyQuotesToAll(false)
            .build()
        beanToCsv.write(termDataList)
    }
}