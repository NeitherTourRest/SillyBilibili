// ============================================================
// ShizukuFileHelper.kt — 文件访问"特权通道"
// ============================================================
// 需要这个文件的原因是：Android 11+ 禁止 App 直接读取
// /Android/data/ 下其他 App 的文件。B 站缓存视频存在这里。
// 我们用 Shizuku（一个第三方 App）来执行 Shell 命令，
// 从而绕过这个限制。
//
// 核心原理：
//   1. Shizuku 让 App 启动一个"远程服务"（ShellService）
//   2. 这个服务可以执行 Shell 命令（ls / cat / cp 等）
//   3. 执行结果跨进程返回给 App
//
// 被 VideoScanService（扫描）和 VideoConverterService（转换）调用
// ============================================================

package com.example.sillybilibili.util

// ComponentName = Android 组件名称（包名 + 类名），用于绑定 Shizuku 服务
import android.content.ComponentName
// Context = Android 上下文
import android.content.Context
// ServiceConnection = 服务连接回调，绑定 Shizuku 成功/失败时触发
import android.content.ServiceConnection
// PackageManager = 包管理器，用于检查 Shizuku 是否已安装
import android.content.pm.PackageManager
// IBinder = Android 跨进程通信的接口，Shizuku 返回的远程服务句柄
import android.os.IBinder
// Log = 日志输出
import android.util.Log
// @ApplicationContext = Hilt 注入
import dagger.hilt.android.qualifiers.ApplicationContext
// Shizuku = Shizuku 的核心 API，用于检查状态、绑定服务、请求权限
import rikka.shizuku.Shizuku
// BufferedReader / InputStreamReader = Java 读取 Shell 命令输出
import java.io.BufferedReader
// ByteArrayOutputStream = 字节数组输出流，用于合并分块读取的文件数据
import java.io.ByteArrayOutputStream
// File = Java 文件操作
import java.io.File
// FileOutputStream = Java 文件输出流，用于分块拷贝时直接写入文件
import java.io.FileOutputStream
import java.io.InputStreamReader
// @Inject / @Singleton = Hilt 注解
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuFileHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ShizukuFileHelper"
        private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
    }

    private val permissionListeners = mutableListOf<(Boolean) -> Unit>()
    private var shellService: com.example.sillybilibili.service.IShellService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellService = com.example.sillybilibili.service.IShellService.Stub.asInterface(service)
            Log.d(TAG, "ShellService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            Log.d(TAG, "ShellService disconnected")
        }
    }

    init {
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 0) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Shizuku permission result: granted=$granted")
                permissionListeners.forEach { it(granted) }
                if (granted) bindShellService()
            }
        }
        if (isShizukuAvailable()) bindShellService()
    }

    // 向 Shizuku 注册 ShellService 用户服务，建立跨进程 Shell 通道
    private fun bindShellService() {
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(
                    context.packageName,
                    com.example.sillybilibili.service.ShellService::class.java.name
                )
            )
                .daemon(false)
                .processNameSuffix("shell_svc")
                .debuggable(true)
                .version(1)

            Shizuku.bindUserService(args, serviceConnection)
            Log.d(TAG, "ShellService binding requested")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind ShellService", e)
        }
    }

    // 解除 Shizuku ShellService 绑定，释放跨进程资源
    private fun unbindShellService() {
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(
                    context.packageName,
                    com.example.sillybilibili.service.ShellService::class.java.name
                )
            )
                .daemon(false)
                .processNameSuffix("shell_svc")
                .debuggable(true)
                .version(1)

            Shizuku.unbindUserService(args, serviceConnection, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind ShellService", e)
        }
    }

    // 注册 Shizuku 权限授予/拒绝回调
    fun addPermissionListener(listener: (Boolean) -> Unit) {
        permissionListeners.add(listener)
    }

    // 移除已注册的权限回调
    fun removePermissionListener(listener: (Boolean) -> Unit) {
        permissionListeners.remove(listener)
    }

    // 发起 Shizuku 权限请求（如未安装或已授权则跳过）
    fun requestPermission() {
        if (!isShizukuInstalled()) return
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
        try {
            Shizuku.requestPermission(0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    // 检查 Shizuku 是否已安装 + 已授权 + Binder 连通
    fun isShizukuAvailable(): Boolean {
        if (!isShizukuInstalled()) return false
        return try {
            Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.d(TAG, "Shizuku not available: ${e.message}")
            false
        }
    }

    // 检查 Shizuku App 是否已安装
    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Shizuku not installed")
            false
        } catch (e: Exception) {
            Log.d(TAG, "Shizuku check failed: ${e.message}")
            false
        }
    }

    // 检查文件/目录是否存在（Shizuku 优先，失败回退 Java File API）
    fun fileExists(path: String, useShizuku: Boolean = true): Boolean {
        if (!useShizuku || !isShizukuAvailable()) {
            return File(path).exists()
        }
        return try {
            execSh("test -e '${escapeSingleQuote(path)}' && echo OK || echo FAIL", useShizuku)
                .contains("OK")
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku exists check failed, fallback to File API", e)
            File(path).exists()
        }
    }

    // 判断路径是否为目录
    fun isDirectory(path: String, useShizuku: Boolean = true): Boolean {
        if (!useShizuku || !isShizukuAvailable()) {
            return File(path).isDirectory
        }
        return try {
            execSh("test -d '${escapeSingleQuote(path)}' && echo OK || echo FAIL", useShizuku)
                .contains("OK")
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku isDir check failed, fallback to File API", e)
            File(path).isDirectory
        }
    }

    // 列出目录下所有条目名称（文件和子目录）
    fun listDirectoryEntries(path: String, useShizuku: Boolean = true): List<String> {
        if (!useShizuku || !isShizukuAvailable()) {
            return File(path).list()?.toList() ?: emptyList()
        }
        return try {
            execSh("ls -1 '${escapeSingleQuote(path)}'", useShizuku)
                .lines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku listDir failed, fallback to File API", e)
            File(path).list()?.toList() ?: emptyList()
        }
    }

    // 列出目录下所有子目录名称（不含文件）
    fun listDirectories(path: String, useShizuku: Boolean = true): List<String> {
        if (!useShizuku || !isShizukuAvailable()) {
            return File(path).listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                ?: emptyList()
        }
        return try {
            execSh(
                "for item in '${escapeSingleQuote(path)}'/*; do " +
                        "if [ -d \"\$item\" ]; then basename \"\$item\"; fi; done",
                useShizuku
            )
                .lines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku listDirs failed, fallback to File API", e)
            File(path).listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                ?: emptyList()
        }
    }

    // 列出目录下含有 entry.json 的子目录（用于定位 B 站 cid 文件夹）
    fun listSubDirectoriesWithEntryJson(path: String, useShizuku: Boolean = true): List<String> {
        if (!useShizuku || !isShizukuAvailable()) {
            return File(path).listFiles()
                ?.filter { it.isDirectory }
                ?.filter { File(it, "entry.json").exists() }
                ?.map { it.name }
                ?: emptyList()
        }
        return try {
            execSh(
                "for item in '${escapeSingleQuote(path)}'/*; do " +
                        "if [ -d \"\$item\" ] && [ -f \"\$item/entry.json\" ]; then basename \"\$item\"; fi; done",
                useShizuku
            )
                .lines().filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku entryJson search failed, fallback to File API", e)
            File(path).listFiles()
                ?.filter { it.isDirectory }
                ?.filter { File(it, "entry.json").exists() }
                ?.map { it.name }
                ?: emptyList()
        }
    }

    // 获取文件大小（字节），Shizuku 不可用时回退 Java File API
    fun fileLength(path: String, useShizuku: Boolean = true): Long {
        if (!useShizuku || !isShizukuAvailable()) {
            return File(path).length()
        }
        return try {
            execSh("stat -c %s '${escapeSingleQuote(path)}'", useShizuku)
                .trim().toLongOrNull() ?: File(path).length()
        } catch (e: Exception) {
            File(path).length()
        }
    }

    // 读取文本文件全部内容（Shizuku 优先，失败回退）
    fun readFileContent(path: String, useShizuku: Boolean = true): String? {
        if (!useShizuku || !isShizukuAvailable()) {
            return try { File(path).readText() } catch (e: Exception) { null }
        }
        return try {
            execSh("cat '${escapeSingleQuote(path)}'", useShizuku)
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku read file failed, fallback to File API", e)
            try { File(path).readText() } catch (e2: Exception) { null }
        }
    }

    // 判断路径是否为普通文件
    fun isFile(path: String, useShizuku: Boolean = true): Boolean {
        if (!useShizuku || !isShizukuAvailable()) {
            return File(path).isFile
        }
        return try {
            execSh("test -f '${escapeSingleQuote(path)}' && echo OK || echo FAIL", useShizuku)
                .contains("OK")
        } catch (e: Exception) {
            File(path).isFile
        }
    }

    // 同时获取视频和音频文件大小，一次 Shell 调用返回两个 stat 结果
    fun getVideoFileInfo(videoPath: String, audioPath: String, useShizuku: Boolean = true): Pair<Long, Long>? {
        if (!useShizuku || !isShizukuAvailable()) {
            val videoLen = File(videoPath).length()
            val audioLen = File(audioPath).length()
            return if (videoLen > 0 && audioLen > 0) videoLen to audioLen else null
        }
        return try {
            val output = execSh(
                "stat -c %s '${escapeSingleQuote(videoPath)}' 2>/dev/null; echo '---'; stat -c %s '${escapeSingleQuote(audioPath)}' 2>/dev/null",
                useShizuku
            )
            val parts = output.split("---").map { it.trim() }
            if (parts.size < 2) return null
            val videoLen = parts[0].toLongOrNull() ?: return null
            val audioLen = parts[1].toLongOrNull() ?: return null
            if (videoLen == 0L || audioLen == 0L) return null
            videoLen to audioLen
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku getVideoFileInfo failed", e)
            val videoLen = File(videoPath).length()
            val audioLen = File(audioPath).length()
            if (videoLen > 0 && audioLen > 0) videoLen to audioLen else null
        }
    }

    // 检查视频+音频两个 m4s 文件是否都存在
    fun checkVideoFilesExist(videoPath: String, audioPath: String, useShizuku: Boolean = true): Boolean {
        if (!useShizuku || !isShizukuAvailable()) {
            return File(videoPath).exists() && File(audioPath).exists()
        }
        return try {
            execSh(
                "test -f '${escapeSingleQuote(videoPath)}' && test -f '${escapeSingleQuote(audioPath)}' && echo OK || echo FAIL",
                useShizuku
            ).contains("OK")
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku checkVideoFiles failed", e)
            File(videoPath).exists() && File(audioPath).exists()
        }
    }

    // 复制文件（Shizuku cp 命令优先，校验长度一致性，失败回退 Java copyTo）
    fun copyFile(src: String, dest: String, useShizuku: Boolean = true): Boolean {
        return try {
            if (useShizuku && isShizukuAvailable()) {
                // Step 1: copy the file (cp alone, without chmod dependency)
                execSh("cp '${escapeSingleQuote(src)}' '${escapeSingleQuote(dest)}'", useShizuku)
                val srcLen = fileLength(src)
                val dstLen = File(dest).length()
                if (srcLen <= 0 || dstLen != srcLen) return false
                // Step 2: try to make it readable (may fail on some devices, that's OK)
                try { execSh("chmod 644 '${escapeSingleQuote(dest)}'", useShizuku) } catch (_: Exception) {}
                true
            } else {
                File(src).copyTo(File(dest), overwrite = true)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "copyFile failed", e)
            false
        }
    }

    /**
     * Try to make a file readable by the app process.
     * Uses Shizuku to run chmod 644 on the file.
     * This can skip the need for file copying during conversion.
     * @return true if file is now readable
     */
    fun makeReadable(path: String, useShizuku: Boolean = true): Boolean {
        if (!useShizuku || !isShizukuAvailable()) return false
        return try {
            execSh("chmod 644 '${escapeSingleQuote(path)}'", useShizuku)
            // May also need SELinux fix on some devices
            try { execSh("restorecon '${escapeSingleQuote(path)}'", useShizuku) } catch (_: Exception) {}
            File(path).canRead()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Execute a shell command directly via Shizuku.
     * For commands that produce no meaningful output (like cat, cp, chmod).
     * @return true if command executed without exception
     */
    fun execShell(command: String, useShizuku: Boolean = true): Boolean {
        return try {
            execSh(command, useShizuku)
            true
        } catch (e: Exception) {
            Log.w(TAG, "execShell failed: $command", e)
            false
        }
    }

    // 读取二进制文件，通过 base64 编码跨 Binder 传输（小文件适用）
    fun readBinaryFile(path: String, useShizuku: Boolean = true): ByteArray? {
        if (!useShizuku || !isShizukuAvailable()) {
            return try { File(path).readBytes() } catch (e: Exception) { null }
        }
        return try {
            val base64 = execSh("base64 '${escapeSingleQuote(path)}'", useShizuku)
                .replace("\\s".toRegex(), "")
            android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku readBinaryFile failed", e)
            try { File(path).readBytes() } catch (e2: Exception) { null }
        }
    }

    /**
     * Copy a file via Shizuku using chunked dd + base64.
     * Unlike copyFile() which uses the cp shell command, this method reads raw bytes
     * in small chunks and writes them directly to the destination stream.
     * This avoids loading the entire file into memory and works even when cp fails.
     * @return true if the destination file exists and its length matches the source
     */
    fun copyFileChunked(src: String, dest: String, useShizuku: Boolean = true): Boolean {
        if (!useShizuku || !isShizukuAvailable()) {
            return try {
                File(src).copyTo(File(dest), overwrite = true)
                File(dest).length() == File(src).length()
            } catch (e: Exception) {
                Log.w(TAG, "copyFileChunked local fallback failed", e)
                false
            }
        }

        val srcLen = fileLength(src, useShizuku)
        if (srcLen <= 0) {
            Log.w(TAG, "copyFileChunked: source length is zero or unavailable")
            return false
        }

        val destFile = File(dest)
        return try {
            FileOutputStream(destFile).use { out ->
                val chunkSize = 524288L // 512KB per chunk, well under 1MB Binder limit
                var offset = 0L
                while (offset < srcLen) {
                    val remaining = minOf(chunkSize, srcLen - offset)
                    val base64 = execSh(
                        "dd if='${escapeSingleQuote(src)}' bs=1 skip=$offset count=$remaining 2>/dev/null | base64",
                        useShizuku
                    ).replace("\\s".toRegex(), "")
                    val decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                    if (decoded.isEmpty()) {
                        Log.w(TAG, "copyFileChunked: empty chunk at offset $offset, aborting")
                        throw IllegalStateException("Empty chunk at offset $offset")
                    }
                    out.write(decoded)
                    offset += remaining
                }
            }
            val dstLen = destFile.length()
            if (dstLen != srcLen) {
                Log.w(TAG, "copyFileChunked: length mismatch src=$srcLen dst=$dstLen")
                destFile.delete()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "copyFileChunked failed", e)
            destFile.delete()
            false
        }
    }

    // 分块读取二进制文件，逐 chunk 解码 base64 → 拼接（大文件适用，不一次加载全部到内存）
    fun readBinaryFileChunked(path: String, useShizuku: Boolean = true): ByteArray? {
        if (!useShizuku || !isShizukuAvailable()) {
            return try { File(path).readBytes() } catch (e: Exception) { null }
        }
        return try {
            val fileSize = fileLength(path)
            if (fileSize <= 0) return null

            val output = ByteArrayOutputStream()
            val chunkSize = 524288L // 512KB per chunk, well under 1MB Binder limit
            var offset = 0L

            while (offset < fileSize) {
                val remaining = minOf(chunkSize, fileSize - offset)
                val base64 = execSh(
                    "dd if='${escapeSingleQuote(path)}' bs=1 skip=$offset count=$remaining 2>/dev/null | base64",
                    useShizuku
                ).replace("\\s".toRegex(), "")
                val decoded = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                if (decoded.isEmpty()) break
                output.write(decoded)
                offset += remaining
            }

            output.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku readBinaryFileChunked failed", e)
            try { File(path).readBytes() } catch (e2: Exception) { null }
        }
    }

    // 通过 Shizuku 或本地 shell 执行命令，返回 stdout+stderr
    private fun execSh(command: String, useShizuku: Boolean): String {
        return if (useShizuku && isShizukuAvailable() && shellService != null) {
            try {
                val result = shellService!!.exec(command)
                if (result.isBlank() && !useShizuku) "" else result
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku exec via UserService failed, fallback to local", e)
                execLocal(command)
            }
        } else {
            execLocal(command)
        }
    }

    // 本地执行 shell 命令（无 Shizuku 时的回退路径）
    private fun execLocal(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val output = reader.readText().trim()
            val error = errorReader.readText().trim()
            process.waitFor()
            reader.close()
            errorReader.close()
            process.destroy()
            if (error.isNotEmpty()) {
                Log.w(TAG, "Command stderr: $error")
            }
            output.ifEmpty { error }
        } catch (e: Exception) {
            Log.e(TAG, "execLocal failed", e)
            ""
        }
    }

    // 转义单引号，防止 shell 注入：' → '\''（关闭引号 → 转义引号 → 重开引号）
    fun escapeSingleQuote(path: String): String {
        return path.replace("'", "'\\''")
    }
}

