package com.pfms.app.data.error

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OperationErrorsTest {
    @Test fun unavailableAndNetworkErrorsNeverRevealServerDetails() {
        for (failure in listOf(IOException("secret host stack trace"))) {
            val error = OperationErrors.present(failure)
            assertTrue(error.canRetry)
            assertEquals("Please check your internet connection and try again.", error.message)
        }
    }

    @Test fun permissionsAndMissingRecordsGiveAnActionWithoutRetry() {
        for (failure in listOf(SecurityException("Firebase stack secret"), NoSuchElementException("Firebase stack secret"))) {
            val result = OperationErrors.present(failure)
            assertFalse(result.canRetry)
            assertFalse(result.message.contains("Firebase"))
            assertFalse(result.message.contains("secret"))
        }
    }

    @Test fun unknownFailuresUseContextualApprovedText() {
        val error = IllegalStateException("java.lang.IllegalStateException: document users/private")
        assertEquals("Unable to load your data. Please try again.", OperationErrors.present(error, ErrorAction.LOAD).message)
        assertEquals("Something went wrong while saving. Please try again.", OperationErrors.present(error).message)
        assertEquals("Unable to delete this record. Please try again.", OperationErrors.present(error, ErrorAction.DELETE).message)
    }

    @Test fun wrappedFirestoreErrorStillHasCorrectRecoveryPolicy() {
        val error = RuntimeException("internal", SecurityException("private"))
        assertFalse(OperationErrors.present(error).canRetry)
    }

    @Test fun invalidInputAndOneActiveGoalNeedCorrection() {
        assertFalse(OperationErrors.present(IllegalArgumentException("internal field x")).canRetry)
        val goal = OperationErrors.present(IllegalStateException("An active savings goal already exists"))
        assertFalse(goal.canRetry)
        assertTrue(goal.message.contains("archive"))
    }

    @Test fun cancellationPropagatesInsteadOfShowingAnError() {
        val cancelled = CancellationException("cancelled")
        assertSame(cancelled, runCatching { OperationErrors.present(cancelled) }.exceptionOrNull())
        assertSame(cancelled, runCatching { repositoryResult<Unit> { throw cancelled } }.exceptionOrNull())
    }
}
