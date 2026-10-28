package com.ymcloud.web.remote;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.RemoteProblemHttpException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 远程公开题面的共用单次请求客户端，不登录、不重试、不跟随重定向
 */
@Slf4j
@Component
public class RemoteProblemHttpClient {

    private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;

    private RestClient client;

    /**
     * 初始化有限超时的公开页面客户端，不携带用户凭据。
     */
    @PostConstruct
    public void initialize() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "YMOJ-ProblemImporter/1.0")
                .build();
    }

    /**
     * 读取适配器根据已验证题号构造的固定平台地址。
     * @param uri 适配器生成的公开题目地址，不接受用户任意网址
     * @param platform 平台展示名称，用于失败提示
     * @return UTF-8 解码的公开页面
     */
    public String get(URI uri, String platform) {
        try {
            return client.get().uri(uri).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (status == HttpStatus.NOT_FOUND) {
                    throw new ProblemException(platform + "题目不存在或未公开", HttpStatus.NOT_FOUND);
                }
                if (status != HttpStatus.SUCCESS) {
                    throw new RemoteProblemHttpException(platform + "暂时无法访问该题目（HTTP " + status + "），请稍后重试",
                            status);
                }
                byte[] page = response.getBody().readNBytes(MAX_PAGE_BYTES + 1);
                if (page.length > MAX_PAGE_BYTES) {
                    throw new ProblemException(platform + "题面页面过大，无法复制", HttpStatus.BAD_REQUEST);
                }
                return new String(page, StandardCharsets.UTF_8);
            });
        } catch (RestClientException exception) {
            log.warn("远程题面请求失败，平台：{}，异常类型：{}", platform, exception.getClass().getSimpleName());
            throw new ProblemException(platform + "连接失败或超时，请稍后重试",
                    HttpStatus.SERVICE_UNAVAILABLE, exception);
        }
    }
}
