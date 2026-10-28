package com.ymcloud.web.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 力扣中国公开题面适配，优先中文并明确函数式题目的本地判题边界
 */
@Component
public class LeetCodeProblemProvider implements RemoteProblemProvider {

    private static final Pattern EXAMPLE = Pattern.compile("^(?:示例|Example)\\s*\\d+\\s*[:：]", Pattern.CASE_INSENSITIVE);
    private static final Pattern END_EXAMPLES = Pattern.compile("^(?:提示|约束|Constraints|Follow[- ]?up|进阶)\\s*[:：]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SAMPLE = Pattern.compile(
            "(?:输入|Input)\\s*[:：]\\s*(.*?)\\s*(?:输出|Output)\\s*[:：]\\s*(.*?)(?:\\s*(?:解释|说明|Explanation)\\s*[:：]\\s*(.*))?$",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    @Autowired
    private RemoteProblemHttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 返回力扣中国平台标识。
     * @return leetcode
     */
    @Override
    public String platform() {
        return "leetcode";
    }

    /**
     * 使用公开题目链接中的名称读取题面，不调用会员接口。
     * @param problemId 链接中的题目标识，如 two-sum
     * @return 未保存的题面草稿
     */
    @Override
    public RemoteProblemVO fetch(String problemId) {
        String slug = problemId.toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") || slug.matches("[0-9]+")) {
            throw new ProblemException("请输入力扣题目链接中的名称，例如 two-sum", HttpStatus.BAD_REQUEST);
        }
        String url = "https://leetcode.cn/problems/" + slug + "/description/";
        return parse(slug, url, httpClient.get(URI.create(url), "力扣"));
    }

    /**
     * 解析公开页面中的题目数据，保留原文及函数定义，不虚构标准输入输出格式。
     * @param slug 题目链接标识
     * @param url 公开题目地址
     * @param html 未登录页面
     * @return 中文优先的题面草稿，限制未知时为 null
     */
    public RemoteProblemVO parse(String slug, String url, String html) {
        Element script = Jsoup.parse(html, url).selectFirst("script#__NEXT_DATA__");
        if (script == null) {
            throw new ProblemException("未取得力扣公开题面，页面格式可能已变化", HttpStatus.SERVICE_UNAVAILABLE);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(script.data());
        } catch (JsonProcessingException exception) {
            throw new ProblemException("力扣题面数据格式异常", HttpStatus.SERVICE_UNAVAILABLE, exception);
        }
        JsonNode question = null;
        if (root == null || root.isMissingNode()) {
            throw new ProblemException("力扣题面数据为空", HttpStatus.SERVICE_UNAVAILABLE);
        }
        for (JsonNode query : root.path("props").path("pageProps").path("dehydratedState").path("queries")) {
            JsonNode candidate = query.path("state").path("data").path("question");
            if (slug.equals(candidate.path("titleSlug").asText())) {
                question = candidate;
                break;
            }
        }
        if (question == null || question.path("title").asText().isBlank()) {
            throw new ProblemException("力扣题目不存在或未公开", HttpStatus.NOT_FOUND);
        }
        if (question.path("isPaidOnly").asBoolean()) {
            throw new ProblemException("该力扣题目需要会员访问，不能复制", HttpStatus.FORBIDDEN);
        }
        String raw = question.path("translatedContent").asText("");
        String title = question.path("translatedTitle").asText("");
        if (raw.isBlank()) {
            raw = question.path("content").asText("");
        }
        if (title.isBlank()) {
            title = question.path("title").asText();
        }
        if (raw.isBlank()) {
            throw new ProblemException("力扣未返回公开题面内容", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Element body = Jsoup.parseBodyFragment(raw, url).body();
        Element statement = new Element("div");
        statement.setBaseUri(url);
        var groups = new ArrayList<Element>();
        Element group = null;
        for (Node node : body.childNodes()) {
            String text = node instanceof Element element ? element.text() : "";
            if (EXAMPLE.matcher(text).find()) {
                group = new Element("div");
                group.setBaseUri(url);
                groups.add(group);
            } else if (END_EXAMPLES.matcher(text).find()) {
                group = null;
            }
            (group == null ? statement : group).appendChild(node.clone());
        }
        StringBuilder content = new StringBuilder(ProblemHtmlMarkdown.convert(statement));
        var cases = new ArrayList<ProblemCaseVO>();
        for (int index = 0; index < Math.min(3, groups.size()); index++) {
            var match = SAMPLE.matcher(ProblemHtmlMarkdown.sampleText(groups.get(index)).replace('\u00a0', ' ').strip());
            if (!match.find()) {
                // 图片或交互式示例无法组成输入输出时，保留在题干供管理员编辑
                content.append("\n\n").append(ProblemHtmlMarkdown.convert(groups.get(index)));
                continue;
            }
            ProblemCaseVO sample = new ProblemCaseVO();
            sample.setInput(match.group(1).strip());
            sample.setOutput(match.group(2).strip());
            sample.setNote("");
            cases.add(sample);
            if (match.group(3) != null && !match.group(3).isBlank()) {
                content.append("\n\n### 样例 ").append(index + 1).append(" 说明\n\n").append(match.group(3).strip());
            }
            for (Element image : groups.get(index).select("img")) {
                content.append("\n\n").append(ProblemHtmlMarkdown.convert(image));
            }
        }
        String metadata = question.path("metaData").asText("");
        if (!metadata.isBlank()) {
            content.append("\n\n### 原题函数定义\n\n```json\n").append(metadata).append("\n```");
        }
        RemoteProblemVO draft = new RemoteProblemVO();
        draft.setProblemId("LC" + question.path("questionFrontendId").asText().replaceAll("[^A-Za-z0-9_]", "_"));
        draft.setTitle(title);
        draft.setContent(content.toString());
        draft.setInput("");
        draft.setOutput("");
        draft.setProblemCaseList(cases);
        draft.setSource("本题来源于力扣 " + question.path("questionFrontendId").asText() + " 题目（" + url + "）");
        draft.setNotice("力扣题目通常采用函数式接口；请补充本站的标准输入、输出约定，并准备对应驱动代码及测试数据后保存。");
        return draft;
    }
}
