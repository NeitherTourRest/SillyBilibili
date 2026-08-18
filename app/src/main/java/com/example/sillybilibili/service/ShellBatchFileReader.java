package com.example.sillybilibili.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Pure filesystem work used by the Shizuku UserService batch APIs and JVM regression tests. */
public final class ShellBatchFileReader {
    private static final String ENTRY_COMPLETE_MARKER = "__SILLY_ENTRY_COMPLETE__";

    private ShellBatchFileReader() {}

    public static String readEntryJsonBatch(String basePath, String[] avidNames) {
        if (basePath == null || avidNames == null) return "";
        StringBuilder output = new StringBuilder();
        try {
            for (String avidName : avidNames) {
                if (avidName == null || !avidName.matches("\\d+")) return "";
                File avidDirectory = new File(basePath, avidName);
                File[] cidDirectories = avidDirectory.listFiles();
                if (cidDirectories == null) return output.toString();
                for (File cidDirectory : cidDirectories) {
                    if (!cidDirectory.isDirectory()) continue;
                    File entryFile = new File(cidDirectory, "entry.json");
                    if (!entryFile.isFile()) continue;
                    String content = new String(Files.readAllBytes(entryFile.toPath()), StandardCharsets.UTF_8);
                    output.append('\u001e').append(avidName).append('\u001f')
                            .append(cidDirectory.getName()).append('\u001f')
                            .append(content).append('\u001e').append('\u001f');
                }
            }
            return output.append(ENTRY_COMPLETE_MARKER).toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static long[] getVideoFileInfoBatch(String[] videoPaths, String[] audioPaths) {
        if (videoPaths == null || audioPaths == null || videoPaths.length != audioPaths.length) {
            return new long[0];
        }
        try {
            long[] result = new long[videoPaths.length * 2];
            for (int index = 0; index < videoPaths.length; index++) {
                File video = new File(videoPaths[index]);
                File audio = new File(audioPaths[index]);
                int offset = index * 2;
                if (video.isFile() && audio.isFile() && video.length() > 0L && audio.length() > 0L) {
                    result[offset] = video.length();
                    result[offset + 1] = audio.length();
                } else {
                    result[offset] = -1L;
                    result[offset + 1] = -1L;
                }
            }
            return result;
        } catch (Exception ignored) {
            return new long[0];
        }
    }
}
