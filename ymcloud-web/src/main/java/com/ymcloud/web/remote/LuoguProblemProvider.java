package com.ymcloud.web.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 从洛谷公开页面复制原始题面，不访问题解或隐藏测试数据
 */
@Component
public class LuoguProblemProvider implements RemoteProblemProvider {

    private static final Pattern CONTEXT = Pattern.compile(
            "<script\\b[^>]*\\bid=[\"']lentille-context[\"'][^>]*>(.*?)</script>", Pattern.DOTALL);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RemoteProblemHttpClient httpClient;

    /**
     * 返回洛谷的平台标识。
     * @return luogu
     */
    @Override
    public String platform() {
        return "luogu";
    }

    /**
     * 单次读取洛谷公开题目页面，将网络失败转换为明确的业务提示。
     * @param problemId 洛谷题号，如 P1002、B2001、CF1A 或 AT_abc001_1
     * @return 保留原文、样例与出处的题面草稿
     */
    @Override
    public RemoteProblemVO fetch(String problemId) {
        if (!problemId.matches("(?i)(?:[PBTU][1-9][0-9]*|CF[1-9][0-9]*[A-Z][0-9]*|SP[1-9][0-9]*|AT_[A-Za-z0-9_]+)")) {
            throw new ProblemException("洛谷题号格式不正确，例如 P1002", HttpStatus.BAD_REQUEST);
        }
        String id = problemId.regionMatches(true, 0, "AT_", 0, 3)
                ? "AT_" + problemId.substring(3) : problemId.toUpperCase(Locale.ROOT);
        String html = httpClient.get(URI.create("https://www.luogu.com.cn/problem/" + id), "洛谷");
        return parse(id, html);
    }

    /**
     * 解析公开页面内的结构化题面；缺失字段不能伪装为成功的空题目。
     * @param id 请求的规范化题号
     * @param html 未登录访问的页面内容
     * @return 统一题面草稿，仅取前三组公开样例
     */
    public RemoteProblemVO parse(String id, String html) {
        var matcher = CONTEXT.matcher(html);
        if (!matcher.find()) {
            throw new ProblemException("未取得公开题面，洛谷可能限制访问或页面格式已变化", HttpStatus.SERVICE_UNAVAILABLE);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(matcher.group(1));
        } catch (JsonProcessingException exception) {
            throw new ProblemException("洛谷题面数据格式异常", HttpStatus.SERVICE_UNAVAILABLE, exception);
        }
        JsonNode data = root.path("data").path("problem");
        JsonNode content = data.path("content");
        JsonNode samples = data.path("samples");
        JsonNode times = data.path("limits").path("time");
        JsonNode memories = data.path("limits").path("memory");
        if (root.path("status").asInt() != HttpStatus.SUCCESS
                || !id.equals(data.path("pid").asText())
                || data.path("name").asText().isBlank()
                || content.path("description").asText().isBlank()
                || !samples.isArray() || !times.isArray() || times.isEmpty()
                || !memories.isArray() || memories.isEmpty()) {
            throw new ProblemException("未取得完整的公开题面，请确认题目可公开访问", HttpStatus.SERVICE_UNAVAILABLE);
        }
        StringBuilder statement = new StringBuilder();
        String background = content.path("background").asText("");
        if (!background.isBlank()) {
            statement.append("## 题目背景\n\n").append(background).append("\n\n## 题目描述\n\n");
        }
        statement.append(content.path("description").asText());
        String hint = content.path("hint").asText("");
        if (!hint.isBlank()) {
            statement.append("\n\n## 说明/提示\n\n").append(hint);
        }
        var cases = new ArrayList<ProblemCaseVO>();
        for (JsonNode sample : samples) {
            if (cases.size() == 3) {
                break;
            }
            if (!sample.isArray() || sample.size() != 2
                    || !sample.get(0).isTextual() || !sample.get(1).isTextual()) {
                throw new ProblemException("洛谷样例数据格式异常", HttpStatus.SERVICE_UNAVAILABLE);
            }
            ProblemCaseVO example = new ProblemCaseVO();
            example.setInput(sample.get(0).asText());
            example.setOutput(sample.get(1).asText());
            example.setNote("");
            cases.add(example);
        }
        long timeLimit = 0;
        long memoryLimit = 0;
        for (JsonNode time : times) {
            timeLimit = Math.max(timeLimit, time.asLong());
        }
        for (JsonNode memory : memories) {
            memoryLimit = Math.max(memoryLimit, memory.asLong());
        }
        if (timeLimit <= 0 || memoryLimit <= 0) {
            throw new ProblemException("洛谷题目资源限制格式异常", HttpStatus.SERVICE_UNAVAILABLE);
        }
        RemoteProblemVO draft = new RemoteProblemVO();
        draft.setProblemId(id);
        draft.setTitle(data.path("name").asText());
        draft.setContent(statement.toString());
        draft.setInput(content.path("formatI").asText(""));
        draft.setOutput(content.path("formatO").asText(""));
        draft.setSource("本题来源于洛谷 " + id + " 题目（https://www.luogu.com.cn/problem/" + id + "）");
        draft.setTimeLimit(timeLimit);
        draft.setMemoryLimit((memoryLimit + 1023) / 1024);
        draft.setProblemCaseList(cases);
        return draft;
    }
}
