package com.danjonesapps.vocabr
import org.json.JSONArray
import org.json.JSONObject

fun VocabList.toJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("fileName", fileName)
        put("termTypes", JSONArray(termTypes))
        put("minListNumber", minListNumber)
        put("maxListNumber", maxListNumber)
    }
}

fun JSONObject.toVocabList(): VocabList {
    val termTypes: List<String> = optJSONArray("termTypes")
        ?.let { array ->
            List(array.length()) { index ->
                array.getString(index)
            }
        }
        ?: emptyList()
    return VocabList(
        id = optString("id"),
        fileName = optString("fileName"),
        termTypes = termTypes,
        minListNumber = optInt("minListNumber"),
        maxListNumber = optInt("maxListNumber"),
        cachedStats = null
    )
}