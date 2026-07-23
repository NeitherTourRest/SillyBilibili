package com.example.sillybilibili.service;

interface IShellService {
    String exec(String command) = 1;
    void destroy() = 16777114;
}
