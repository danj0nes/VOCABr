package com.danjonesapps.vocabr

import android.app.Application

class VOCABr : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.settings = SettingsManager(this)
    }
}