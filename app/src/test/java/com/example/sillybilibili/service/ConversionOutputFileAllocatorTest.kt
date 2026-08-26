package com.example.sillybilibili.service

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversionOutputFileAllocatorTest {
    @Test
    fun `uses a safe id-suffixed name and preserves an existing export`() {
        val directory = Files.createTempDirectory("silly-conversion-test").toFile()
        try {
            val first = allocateConversionOutputFile(directory, "A/B: title?", 42)
            assertEquals("A_B_ title_ [cache-42].mp4", first.name)
            check(first.createNewFile())

            val second = allocateConversionOutputFile(directory, "A/B: title?", 42)
            assertEquals("A_B_ title_ [cache-42] (2).mp4", second.name)
        } finally {
            directory.deleteRecursively()
        }
    }
}
