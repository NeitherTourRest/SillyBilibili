package com.example.sillybilibili.service

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellServiceBatchIoTest {
    @Test
    fun `batch entry reader returns every cid entry without launching a shell`() {
        val root = Files.createTempDirectory("silly-scan").toFile()
        try {
            val cidDirectory = File(root, "100/200").apply { mkdirs() }
            File(cidDirectory, "entry.json").writeText("""{"title":"sample"}""")

            val result = ShellBatchFileReader.readEntryJsonBatch(root.absolutePath, arrayOf("100"))

            assertTrue(result.contains("100"))
            assertTrue(result.contains("200"))
            assertTrue(result.contains("sample"))
            assertTrue(result.contains("__SILLY_ENTRY_COMPLETE__"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `batch file information keeps each video audio pair aligned`() {
        val root = Files.createTempDirectory("silly-stat").toFile()
        try {
            val video = File(root, "video.m4s").apply { writeBytes(ByteArray(13)) }
            val audio = File(root, "audio.m4s").apply { writeBytes(ByteArray(7)) }

            val result = ShellBatchFileReader.getVideoFileInfoBatch(
                arrayOf(video.absolutePath),
                arrayOf(audio.absolutePath)
            )

            assertEquals(listOf(13L, 7L), result.toList())
        } finally {
            root.deleteRecursively()
        }
    }
}
