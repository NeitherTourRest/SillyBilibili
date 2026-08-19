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
 * 去掉 document id 的最后一个路径段，得到父目录 id；根目录返回 null。
 * 例如 "primary:a/b/c/cover.jpg" -> "primary:a/b/c"。
 */
internal fun parentDocumentId(documentId: String): String? {
    val slash = documentId.lastIndexOf('/')
    if (slash <= 0) return null
    return documentId.substring(0, slash).takeIf { it.isNotEmpty() }
}

/**
 * SAF (Storage Access Framework) file access — alternative to Shizuku.
 * User selects the Bilibili download directory via system file picker.
 */
@Singleton
class SafFileHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class DirectoryListResult(val directories: List<String>, val completed: Boolean)

    /** Tree roots and child document URIs are both used by the scanner. */
    private fun document(uri: Uri): DocumentFile? =
        DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)

    fun isDirectory(uri: Uri): Boolean = document(uri)?.isDirectory == true
    fun exists(uri: Uri): Boolean = document(uri)?.exists() == true
    fun fileLength(uri: Uri): Long = document(uri)?.length() ?: 0L

    fun listDirectories(parentUri: Uri): List<String> = listDirectoriesResult(parentUri).directories

    /** Keeps a provider error distinct from a genuinely empty user-selected directory. */
    fun listDirectoriesResult(parentUri: Uri): DirectoryListResult {
        return try {
            val parent = document(parentUri) ?: return DirectoryListResult(emptyList(), false)
            DirectoryListResult(parent.listFiles().filter { it.isDirectory }.mapNotNull { it.name }, true)
        } catch (_: Exception) {
            DirectoryListResult(emptyList(), false)
        }
    }

    fun listSubDirectoriesWithEntryJson(parentUri: Uri): List<String> {
        val p = document(parentUri) ?: return emptyList()
        return p.listFiles().filter { it.isDirectory && it.findFile("entry.json")?.exists() == true }.mapNotNull { it.name }
    }

    fun listEntries(parentUri: Uri): List<String> {
        val p = document(parentUri) ?: return emptyList()
        return p.listFiles().mapNotNull { it.name }
    }

    fun readFileContent(fileUri: Uri): String? = try {
        context.contentResolver.openInputStream(fileUri)?.use { BufferedReader(InputStreamReader(it)).readText() }
    } catch (_: Exception) { null }

    fun readBinaryFile(fileUri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
    } catch (_: Exception) { null }

    fun checkVideoFilesExist(parentUri: Uri): Boolean {
        val p = document(parentUri) ?: return false
        return p.findFile("video.m4s")?.exists() == true && p.findFile("audio.m4s")?.exists() == true
    }

    fun getVideoFileInfo(parentUri: Uri): Pair<Long, Long>? {
        val p = document(parentUri) ?: return null
        val v = p.findFile("video.m4s") ?: return null
        val a = p.findFile("audio.m4s") ?: return null
        if (!v.exists() || !a.exists()) return null
        return if (v.length() > 0 && a.length() > 0) v.length() to a.length() else null
    }

    fun findChild(parentUri: Uri, name: String): Uri? =
        document(parentUri)?.findFile(name)?.uri

    /**
     * 由子文件的 document URI 推导同一 provider 下父目录的 URI。
     * 用于封面文件名不是 cover.jpg 时，回退到 cid 目录里寻找真实封面。
     */
    fun parentDocumentUri(uri: Uri): Uri? {
        val id = DocumentsContract.getDocumentId(uri) ?: return null
        val parent = parentDocumentId(id) ?: return null
        return DocumentsContract.buildDocumentUri(uri.authority, parent)
    }

    /** Stable document URI for the root of a persisted SAF tree. */
    fun rootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    fun resolvePath(treeUri: Uri, path: String): Uri? =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, path)
}
