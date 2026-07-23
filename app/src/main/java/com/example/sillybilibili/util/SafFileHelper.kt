package com.example.sillybilibili.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF (Storage Access Framework) file access — alternative to Shizuku.
 * User selects the Bilibili download directory via system file picker.
 */
@Singleton
class SafFileHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isDirectory(uri: Uri): Boolean = DocumentFile.fromSingleUri(context, uri)?.isDirectory == true
    fun exists(uri: Uri): Boolean = DocumentFile.fromSingleUri(context, uri)?.exists() == true
    fun fileLength(uri: Uri): Long = DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L

    fun listDirectories(parentUri: Uri): List<String> {
        val p = DocumentFile.fromTreeUri(context, parentUri) ?: return emptyList()
        return p.listFiles().filter { it.isDirectory }.mapNotNull { it.name }
    }

    fun listSubDirectoriesWithEntryJson(parentUri: Uri): List<String> {
        val p = DocumentFile.fromTreeUri(context, parentUri) ?: return emptyList()
        return p.listFiles().filter { it.isDirectory && it.findFile("entry.json")?.exists() == true }.mapNotNull { it.name }
    }

    fun listEntries(parentUri: Uri): List<String> {
        val p = DocumentFile.fromTreeUri(context, parentUri) ?: return emptyList()
        return p.listFiles().mapNotNull { it.name }
    }

    fun readFileContent(fileUri: Uri): String? = try {
        context.contentResolver.openInputStream(fileUri)?.use { BufferedReader(InputStreamReader(it)).readText() }
    } catch (_: Exception) { null }

    fun readBinaryFile(fileUri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
    } catch (_: Exception) { null }

    fun checkVideoFilesExist(parentUri: Uri): Boolean {
        val p = DocumentFile.fromTreeUri(context, parentUri) ?: return false
        return p.findFile("video.m4s")?.exists() == true && p.findFile("audio.m4s")?.exists() == true
    }

    fun getVideoFileInfo(parentUri: Uri): Pair<Long, Long>? {
        val p = DocumentFile.fromTreeUri(context, parentUri) ?: return null
        val v = p.findFile("video.m4s") ?: return null
        val a = p.findFile("audio.m4s") ?: return null
        if (!v.exists() || !a.exists()) return null
        return if (v.length() > 0 && a.length() > 0) v.length() to a.length() else null
    }

    fun findChild(parentUri: Uri, name: String): Uri? =
        DocumentFile.fromTreeUri(context, parentUri)?.findFile(name)?.uri

    fun resolvePath(treeUri: Uri, path: String): Uri? =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, path)
}
