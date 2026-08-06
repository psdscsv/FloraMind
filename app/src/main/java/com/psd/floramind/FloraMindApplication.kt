package com.psd.floramind

import android.app.Application
import com.psd.floramind.data.DataStoreManager

class FloraMindApplication : Application() {
    lateinit var dataStoreManager: DataStoreManager
        private set

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)
    }
}