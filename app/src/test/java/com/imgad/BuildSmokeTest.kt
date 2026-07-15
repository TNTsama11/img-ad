package com.imgad

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildSmokeTest {
    @Test
    fun applicationIdIsComImgad() {
        assertEquals("com.imgad", BuildConfig.APPLICATION_ID)
    }
}
