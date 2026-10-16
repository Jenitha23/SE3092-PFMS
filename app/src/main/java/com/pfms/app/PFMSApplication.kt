package com.pfms.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pfms.app.data.local.CacheClearFlag
import com.pfms.app.data.sync.IncomeSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PFMSApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        CacheClearFlag.applyIfRequested(this)

        // Enqueue constrained one-time sync on application start
        IncomeSyncWorker.enqueue(this)

        // Enqueue periodic daily checks for recurring expense and subscription reminders
        com.pfms.app.data.sync.ExpenseReminderWorker.enqueuePeriodic(this)
    }
}
