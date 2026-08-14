package com.example.sillybilibili.service;

interface IShellService {
    String exec(String command) = 1;
    byte[] readFileRange(String path, long offset, int length) = 2;
    void destroy() = 16777114;
}
