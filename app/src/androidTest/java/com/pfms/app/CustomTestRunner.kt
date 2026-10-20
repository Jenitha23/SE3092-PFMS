package com.pfms.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper

/**
 * A custom runner to set up the instrumented application class for tests.
 * Required by Hilt to inject dependencies into UI tests.
 */
class CustomTestRunner : AndroidJUnitRunner() {
    override fun onStart() {
        // HiltTestApplication doesn't implement production's Configuration.Provider. Initialize
        // test WorkManager so a previously scheduled JobService cannot crash ordinary UI tests.
        // Opt-in Firebase stages install their real worker factory to test persisted background work.
        if (InstrumentationRegistry.getArguments().getString("fr35Emulators") != "true") {
            WorkManagerTestInitHelper.initializeTestWorkManager(targetContext)
        }
        super.onStart()
    }

    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
