package com.hydrafit.app

import android.app.Application

class HydraFitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(this)
    }
}
