package com.imgad.domain.usecase

import com.imgad.domain.port.GenerationStore

class RecoverInterruptedTasks(
    private val store: GenerationStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke() {
        store.runningTasks().forEach { task ->
            store.markFailed(task.id, INTERRUPTED_ERROR_JSON, clock())
        }
    }

    private companion object {
        const val INTERRUPTED_ERROR_JSON = "{\"kind\":\"INTERRUPTED\",\"message\":\"请求被应用中断\"}"
    }
}
