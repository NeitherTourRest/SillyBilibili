package com.example.sillybilibili.service;

interface IShellService {
    String exec(String command) = 1;
    byte[] readFileRange(String path, long offset, int length) = 2;
    String readEntryJsonBatch(String basePath, in String[] avidNames) = 3;
    long[] getVideoFileInfoBatch(in String[] videoPaths, in String[] audioPaths) = 4;
    void destroy() = 16777114;
}
