package com.imgad.domain.usecase

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RunStartupRecovery(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val action: suspend () -> Unit,
) {
    private val started = AtomicBoolean(false)

    suspend fun runOnce(): Result<Unit> {
        if (!started.compareAndSet(false, true)) return Result.success(Unit)
        return withContext(dispatcher) {
            try {
                action()
                Result.success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }
}
