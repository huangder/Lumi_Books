package com.huangder.lumibooks.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderBackgroundPresetTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun matchingProcessedBlurUsesBakedImageWithoutRuntimeEffect() {
        val original = temporaryFolder.newFile("original.jpg")
        val processed = temporaryFolder.newFile("blurred.jpg")
        val preset = imagePreset(
            originalPath = original.absolutePath,
            processedPath = processed.absolutePath,
            processedBlurDp = 12f
        )

        val source = preset.resolveImageSource(12f)

        assertEquals(processed.absolutePath, source?.path)
        assertEquals(0f, source?.runtimeBlurDp)
    }

    @Test
    fun staleProcessedBlurFallsBackToOriginalAndRequestedBlur() {
        val original = temporaryFolder.newFile("original.jpg")
        val processed = temporaryFolder.newFile("blurred.jpg")
        val preset = imagePreset(
            originalPath = original.absolutePath,
            processedPath = processed.absolutePath,
            processedBlurDp = 8f
        )

        val source = preset.resolveImageSource(20f)

        assertEquals(original.absolutePath, source?.path)
        assertEquals(20f, source?.runtimeBlurDp)
    }

    @Test
    fun missingProcessedFileFallsBackToOriginal() {
        val original = temporaryFolder.newFile("original.jpg")
        val preset = imagePreset(
            originalPath = original.absolutePath,
            processedPath = temporaryFolder.root.resolve("missing.jpg").absolutePath,
            processedBlurDp = 16f
        )

        val source = preset.resolveImageSource(16f)

        assertEquals(original.absolutePath, source?.path)
        assertEquals(16f, source?.runtimeBlurDp)
    }

    @Test
    fun zeroBlurAlwaysUsesOriginalImage() {
        val original = temporaryFolder.newFile("original.jpg")
        val processed = temporaryFolder.newFile("blurred.jpg")
        val preset = imagePreset(
            originalPath = original.absolutePath,
            processedPath = processed.absolutePath,
            processedBlurDp = 12f
        )

        val source = preset.resolveImageSource(0f)

        assertEquals(original.absolutePath, source?.path)
        assertEquals(0f, source?.runtimeBlurDp)
    }

    @Test
    fun colorPresetHasNoImageSource() {
        val preset = ReaderBackgroundPreset(
            id = "color",
            type = ReaderBackgroundType.COLOR,
            value = "#FFFFFFFF"
        )

        assertNull(preset.resolveImageSource(12f))
    }

    private fun imagePreset(
        originalPath: String,
        processedPath: String,
        processedBlurDp: Float
    ) = ReaderBackgroundPreset(
        id = "image",
        type = ReaderBackgroundType.IMAGE,
        value = originalPath,
        processedValue = processedPath,
        processedBlurDp = processedBlurDp
    )
}
