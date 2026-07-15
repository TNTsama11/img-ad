package com.imgad.ui.create

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePreviewStateTest {
    @Test
    fun unitScaleAlwaysCentersImage() {
        assertEquals(
            Offset.Zero,
            constrainOffset(Offset(100f, -100f), 1f, IntSize(200, 100)),
        )
    }

    @Test
    fun scaledImageCannotBeDraggedPastViewportBounds() {
        assertEquals(
            Offset(100f, -50f),
            constrainOffset(Offset(500f, -500f), 2f, IntSize(200, 100)),
        )
    }
}
