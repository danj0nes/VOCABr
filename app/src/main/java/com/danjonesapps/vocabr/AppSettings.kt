package com.danjonesapps.vocabr

object AppSettings {
    lateinit var settings: SettingsManager

    const val BOOST_NEW_WEIGHT = 0.1
    const val FINAL_TARGET_GAP = 120 // in days
    const val MASTERY_AGE = 540 // in days (how long learning before learnt score is 1.0
    const val TARGET_GAP_PROBABILITY = 0.8

    val decayDefiningCoord = Pair(1.0, 0.5)
}