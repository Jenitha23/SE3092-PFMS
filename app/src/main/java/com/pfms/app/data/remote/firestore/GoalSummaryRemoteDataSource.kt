package com.pfms.app.data.remote.firestore

import com.pfms.app.domain.model.dashboard.ActiveGoalSnapshot
import kotlinx.coroutines.flow.Flow

/** Read-only, real-time source for the dashboard's savings-goal card. */
interface GoalSummaryRemoteDataSource {
    /** Emits the active goal with its derived saved amount, or null when there is none. */
    fun observeActiveGoal(): Flow<ActiveGoalSnapshot?>
}
