package com.imgad.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class AsyncImageTest {
    @Test
    fun sampleSizeKeepsDecodedLongestSideWithinLimit() {
        assertEquals(1, calculateInSampleSize(1024, 768, 1024))
        assertEquals(2, calculateInSampleSize(2048, 1024, 1024))
        assertEquals(4, calculateInSampleSize(4096, 3072, 1024))
        assertEquals(8, calculateInSampleSize(4097, 1024, 1024))
    }

    @Test
    fun invalidBoundsDoNotRequestSampling() {
        assertEquals(1, calculateInSampleSize(0, 2048, 1024))
        assertEquals(1, calculateInSampleSize(2048, -1, 1024))
    }
}
