package com.imgad.domain

import com.imgad.domain.usecase.RunStartupRecovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunStartupRecoveryTest {
    @Test
    fun recoveryRunsOnlyOnceAndExposesFailure() = runBlocking {
        var calls = 0
        val recovery = RunStartupRecovery(Dispatchers.Unconfined) {
            calls++
            error("recovery failed")
        }

        val first = recovery.runOnce()
        val second = recovery.runOnce()

        assertTrue(first.isFailure)
        assertTrue(second.isSuccess)
        assertEquals(1, calls)
    }
}
