package com.ymcloud.web.remote;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.RemoteProblemConnectionException;
import com.ymcloud.common.exception.problem.RemoteProblemHttpException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 远程公开题面的共用单次请求客户端，不登录、不重试、不跟随重定向
 */
@Slf4j
@Component
public class RemoteProblemHttpClient {

    private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;
    private static final Set<String> IMAGE_DOMAINS = Set.of("luogu.com.cn", "luogu.com", "luogu.org",
            "nowcoder.com", "nowcoder.net", "atcoder.jp", "codeforces.com",
            "leetcode.com", "leetcode.cn", "leetcode-cn.com");

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
        } catch (ResourceAccessException exception) {
            log.warn("远程题面请求失败，平台：{}，异常类型：{}", platform, exception.getClass().getSimpleName());
            throw new RemoteProblemConnectionException(platform + "连接失败或超时，请稍后重试", exception);
        }
    }

    /**
     * 下载公开插图，地址只允许平台图片域名及标准端口，不跟随重定向或携带凭据。
     * @param url 题面中未经信任的图片地址
     * @return 最多 3 MiB 的图片候选字节，实际格式由图片处理入口验证
     */
    public byte[] getImage(String url) {
        URI uri;
        try {
            uri = URI.create(url.startsWith("//") ? "https:" + url : url);
        } catch (IllegalArgumentException exception) {
            throw new FileException("题面图片地址格式不正确", HttpStatus.BAD_REQUEST, exception);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        boolean http = "http".equalsIgnoreCase(uri.getScheme());
        if ((!https && !http) || uri.getRawUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != (https ? 443 : 80))
                || IMAGE_DOMAINS.stream().noneMatch(domain -> host.equals(domain) || host.endsWith("." + domain))) {
            throw new FileException("题面图片地址不属于支持的平台图片域名", HttpStatus.BAD_REQUEST);
        }
        try {
            return client.get().uri(uri).exchange((request, response) -> {
                if (response.getStatusCode().value() != HttpStatus.SUCCESS) {
                    throw new FileException("题面图片暂时无法下载，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
                }
                byte[] image = response.getBody().readNBytes(FileConstants.MAX_IMAGE_BYTES + 1);
                if (image.length > FileConstants.MAX_IMAGE_BYTES) {
                    throw new FileException("题面单张图片不能超过3MB", HttpStatus.BAD_REQUEST);
                }
                return image;
            });
        } catch (ResourceAccessException exception) {
            throw new FileException("题面图片下载失败或超时，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE, exception);
        }
    }
}
