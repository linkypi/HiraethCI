package com.hiraethci.deployer.constant

enum BuildStatus {
    // 构建成功
    SUCCESS("构建成功 ✅"),
    // 构建失败
    FAILED("构建失败 ❌"),
    // 构建取消
    ABORT("构建中止 ❌");

    private String msg;

    BuildStatus(String msg) {
        this.msg = msg;
    }
}