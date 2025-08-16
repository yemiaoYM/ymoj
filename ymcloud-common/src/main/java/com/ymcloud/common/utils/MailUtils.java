package com.ymcloud.common.utils;

import cn.hutool.extra.mail.MailAccount;
import cn.hutool.extra.mail.MailUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @PackageName: com.ymcloud.common.utils
 * @ClassName: MailUtils
 * @Author: Yemiao
 * @CreateTime: 2025-08-17  02:37
 * @Description: 邮箱工具类
 */
@Component
public class MailUtils {

    @Value("${mail.from}")
    private String from;

    @Value("${mail.pass}")
    private String pass;

    /**
     * 发送邮件
     * @param tos 发送对象
     * @param subject 主题
     * @param content 内容
     * @param isHtml 是否为html
     */
    public void sendMail(List<String> tos, String subject, String content,Boolean isHtml){
        //自定义邮件对象
        MailAccount account = new MailAccount();
        account.setHost("smtp.qq.com");
        account.setPort(465);
        account.setFrom(from);
        account.setPass(pass);
        account.setAuth(true);
        account.setSslEnable(true);
        account.setStarttlsEnable(true);
        //发送信息
        MailUtil.send(account, tos, subject, content, isHtml);
    }

    /**
     * 发送验证邮箱
     * @param tos 发送对象
     * @param code 验证码
     */
    public void sendCodeMail(List<String> tos,String code){
        String subject="邮箱验证";
        String content= """
                <!DOCTYPE html>
                <html>
                  <body style="font-family: Arial, Helvetica, sans-serif; line-height: 1.6; color: #333;">
                    <div style="max-width: 600px; margin: auto; padding: 20px; border: 1px solid #eaeaea; border-radius: 8px;">
                      <h2 style="color: #4CAF50;">Ymcloud 平台安全验证</h2>
                      <p>您好，</p>
                      <p>您正在进行 <b>账号验证</b> 操作，本次请求的验证码为：</p>
                      <p style="font-size: 24px; font-weight: bold; color: #d9534f; text-align: center; letter-spacing: 4px;">
                """+code+
                """
                      </p>
                      <p>验证码有效期为 <b>5 分钟</b>，请勿泄露给他人。</p>
                      <p>如果您未请求此验证码，请忽略本邮件。</p>
                      <hr style="margin: 20px 0;">
                      <p style="font-size: 12px; color: #888;">
                        本邮件由 Ymcloud 平台系统自动发送，请勿直接回复。<br>
                      </p>
                    </div>
                  </body>
                </html>
                """;
        sendMail(tos,subject,content,true);
    }

}
