package com.ymcloud.web;

import cn.hutool.core.util.RuntimeUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * @PackageName: com.ymcloud.web
 * @ClassName: WebTest
 * @Author: Yemiao
 * @CreateTime: 2025-07-18  21:55
 * @Description: ≤‚ ‘¿‡
 */
@SpringBootTest
public class WebTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void generatePassword() {
        String encode = passwordEncoder.encode("ojamdin+#");
        System.out.println(encode);

        encode = passwordEncoder.encode("12345");
        System.out.println(encode);
    }

    @Test
    public void testCMD() {
        String string = RuntimeUtil.execForStr("ipconfig");
        System.out.println(string);
    }
}
