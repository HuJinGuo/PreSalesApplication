package com.bidcollab.util;

/**
 * 通用字符串工具方法（消除各 Service 中重复的 safe / trim / blank 判断）。
 */
public final class StringUtil {

    private StringUtil() {
        // 工具类禁止实例化
    }

    /**
     * null 安全 trim：null → ""，其余 trim 后返回。
     */
    public static String safe(String text) {
        return text == null ? "" : text.trim();
    }
}
