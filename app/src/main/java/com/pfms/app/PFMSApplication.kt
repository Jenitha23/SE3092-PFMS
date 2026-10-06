package com.pfms.app

import android.app.Application
import com.pfms.app.data.local.CacheClearFlag
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PFMSApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        CacheClearFlag.applyIfRequested(this)
    }
}
