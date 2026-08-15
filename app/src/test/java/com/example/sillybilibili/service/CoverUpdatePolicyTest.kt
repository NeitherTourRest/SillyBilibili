package com.example.sillybilibili.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverUpdatePolicyTest {
    @Test
    fun `only a different cached cover path needs a database update`() {
        assertFalse(shouldPersistCoverPath("/cache/cover.jpg", "/cache/cover.jpg"))
        assertFalse(shouldPersistCoverPath(null, null))
        assertFalse(shouldPersistCoverPath("/cache/cover.jpg", ""))
        assertTrue(shouldPersistCoverPath(null, "/cache/cover.jpg"))
        assertTrue(shouldPersistCoverPath("/cache/old.jpg", "/cache/new.jpg"))
    }
}
