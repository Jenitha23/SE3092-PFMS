package com.pfms.app.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface ExpenseSyncScheduler { fun enqueue() }

class WorkManagerExpenseSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) : ExpenseSyncScheduler {
    override fun enqueue() = ExpenseSyncWorker.enqueue(context)
}
