package com.ymcloud.judge.bo;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * @PackageName: com.ymcloud.judge.utils
 * @ClassName: StringInputStream
 * @Author: Yemiao
 * @CreateTime: 2025-08-29  00:10
 * @Description: String缓存流
 */
public class StringInputStream extends ByteArrayInputStream {

    public StringInputStream(String str) {
        super(str.getBytes(StandardCharsets.UTF_8));
    }
}
