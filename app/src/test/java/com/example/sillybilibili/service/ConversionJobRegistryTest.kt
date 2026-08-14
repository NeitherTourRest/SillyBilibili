package com.example.sillybilibili.service

import com.example.sillybilibili.domain.model.ConversionProgress
import com.example.sillybilibili.domain.model.ConversionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionJobRegistryTest {
    @Test
    fun `does not start a duplicate conversion for the same video`() {
        val registry = ConversionJobRegistry()
        val pending = ConversionProgress(7, "video", 0f, ConversionStatus.PENDING)

        assertTrue(registry.begin(pending))
        assertFalse(registry.begin(pending))
    }
}
