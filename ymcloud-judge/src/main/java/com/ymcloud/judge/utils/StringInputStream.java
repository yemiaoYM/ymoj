package com.ymcloud.judge.utils;

import java.io.InputStream;

/**
 * @PackageName: com.ymcloud.judge.utils
 * @ClassName: StringInputStream
 * @Author: Yemiao
 * @CreateTime: 2025-08-29  00:10
 * @Description: String缓存流
 */
public class StringInputStream extends InputStream {
    private final String str;
    private int pos = 0;

    public StringInputStream(String str) {
        this.str = str;
    }

    @Override
    public int read() {
        if (pos >= str.length()) return -1;
        return str.charAt(pos++);
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (pos >= str.length()) return -1;
        int count = Math.min(len, str.length() - pos);
        for (int i = 0; i < count; i++) {
            b[off + i] = (byte) str.charAt(pos++);
        }
        return count;
    }
}
