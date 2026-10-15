package com.pfms.app.data.sync

/**
 * Coordinates background synchronization across pending income records and recurring salary templates.
 */
interface PendingSyncCoordinator {
    /**
     * Executes pending synchronization for the active user session.
     * Safely exits with success if no user is signed in.
     * Returns failure if retryable remote synchronization errors occur.
     */
    suspend fun syncAllPending(): Result<Unit>
}
