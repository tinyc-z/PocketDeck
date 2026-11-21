package com.pocketdeck.launcher

import com.pocketdeck.launcher.data.ImageImportHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageImportHelperTest {

    @Test
    fun coverAndBackgroundMaxEdgeScaleWithDeviceResolution() {
        // 960x720 掌机（Pocket Block）
        assertEquals(640, ImageImportHelper.computeCoverMaxEdge(960, 720))
        assertEquals(960, ImageImportHelper.computeBackgroundMaxEdge(960, 720))

        // 1920x1080 掌机（1080p）
        assertEquals(918, ImageImportHelper.computeCoverMaxEdge(1920, 1080))
        assertEquals(1920, ImageImportHelper.computeBackgroundMaxEdge(1920, 1080))

        // 2560x1440 高分屏（2K）
        assertEquals(1224, ImageImportHelper.computeCoverMaxEdge(2560, 1440))
        assertEquals(2560, ImageImportHelper.computeBackgroundMaxEdge(2560, 1440))

        // 4K 屏幕封顶钳制
        assertEquals(1280, ImageImportHelper.computeCoverMaxEdge(3840, 2160))
        assertEquals(2560, ImageImportHelper.computeBackgroundMaxEdge(3840, 2160))
    }

    @Test
    fun computeInSampleSizeAndScaledDimensionsNeverUpscale() {
        assertEquals(1, ImageImportHelper.computeInSampleSize(400, 300, 640))
        assertEquals(4, ImageImportHelper.computeInSampleSize(4000, 3000, 640))

        assertEquals(400 to 300, ImageImportHelper.computeScaledDimensions(400, 300, 640))
        assertEquals(640 to 480, ImageImportHelper.computeScaledDimensions(4000, 3000, 640))
        assertEquals(480 to 640, ImageImportHelper.computeScaledDimensions(3000, 4000, 640))
    }
}
