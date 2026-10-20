package com.pfms.app.data.error

import kotlinx.coroutines.CancellationException

/** Result-based repositories must never turn coroutine cancellation into a failed user operation. */
inline fun <T> repositoryResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
