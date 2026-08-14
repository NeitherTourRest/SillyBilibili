// ============================================================
// ShellService.java — Shizuku Shell 命令执行服务
// ============================================================
// 这是通过 Shizuku 创建的一个"远程服务"。
// Shizuku 让 App 能以更高权限执行 Shell 命令，从而突破
// Android 11+ 对 /Android/data/ 目录的访问限制。
//
// 这个文件是 Java（不是 Kotlin），因为 Shizuku 的 AIDL
// 接口需要 Stub 类，用 Java 写更直接。
//
// 被 ShizukuFileHelper.kt 调用 → 被 VideoScanService 和
// VideoConverterService 使用。
//
// 工作原理：
//   App 通过 Shizuku API 请求执行 Shell 命令
//   → Shizuku 创建一个独立进程运行 UserService
//   → ShellService 在此进程中运行
//   → exec() 创建 sh 子进程执行命令
//   → 返回命令输出结果
// ============================================================

package com.example.sillybilibili.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;

// IShellService.Stub — Shizuku AIDL 接口的实现类
// AIDL = Android Interface Definition Language
// 它定义了 App 和远程服务之间的通信协议
public class ShellService extends IShellService.Stub {

    /**
     * 执行一条 Shell 命令，返回 stdout + stderr。
     * 流程：创建 sh -c 子进程 → 读取标准输出和标准错误 → waitFor() 等待结束 → 销毁进程。
     */
    @Override
    public String exec(String command) {
        // 1. Runtime.getRuntime().exec() = 创建一个新进程
        // 2. "sh", "-c", command = 执行 Shell 命令
        // 3. 读取 stdout（标准输出）和 stderr（标准错误）
        // 4. 返回结果字符串
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            reader.close();
            StringBuilder error = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }
            errorReader.close();
            process.waitFor();  // 等待命令执行完毕
            process.destroy();
            String result = output.toString().trim();
            // 如果 stdout 为空，返回 stderr（可能包含错误信息）
            return result.isEmpty() ? error.toString().trim() : result;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Random-access byte reads for Media3. Keeping each response below Binder's transaction
     * limit lets the player seek and buffer an isolated cache file without copying it to MP4.
     */
    @Override
    public byte[] readFileRange(String path, long offset, int length) {
        if (path == null || offset < 0 || length <= 0) return new byte[0];
        int safeLength = Math.min(length, 256 * 1024);
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            if (offset >= file.length()) return new byte[0];
            file.seek(offset);
            int readable = (int) Math.min((long) safeLength, file.length() - offset);
            byte[] result = new byte[readable];
            int read = file.read(result);
            if (read <= 0) return new byte[0];
            if (read == result.length) return result;
            byte[] trimmed = new byte[read];
            System.arraycopy(result, 0, trimmed, 0, read);
            return trimmed;
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    // 销毁 Shizuku 用户服务进程
    @Override
    public void destroy() {
        System.exit(0);
    }
}
