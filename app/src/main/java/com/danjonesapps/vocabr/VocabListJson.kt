package com.danjonesapps.vocabr
import org.json.JSONObject

fun VocabList.toJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("fileName", fileName)
    }
}

fun JSONObject.toVocabList(): VocabList {
    return VocabList(
        id = optString("id"),
        fileName = optString("fileName"),
        cachedStats = null
    )
}