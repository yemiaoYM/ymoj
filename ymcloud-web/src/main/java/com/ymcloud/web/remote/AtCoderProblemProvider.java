package com.ymcloud.web.remote;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: AtCoder 公开题面适配，优先英文并保留原始公式与样例
 */
@Component
public class AtCoderProblemProvider implements RemoteProblemProvider {

    private static final Pattern LIMITS = Pattern.compile("Time Limit:\\s*([\\d.]+)\\s*sec\\s*/\\s*Memory Limit:\\s*(\\d+)\\s*MiB");

    @Autowired
    private RemoteProblemHttpClient httpClient;

    /**
     * 返回 AtCoder 平台标识。
     * @return atcoder
     */
    @Override
    public String platform() {
        return "atcoder";
    }

    /**
     * 按题号或明确的比赛/题目标识读取公开题面。
     * @param problemId 如 abc480_a，非同名前缀比赛可输入 past202010-open/past202010_a
     * @return 未保存的题面草稿
     */
    @Override
    public RemoteProblemVO fetch(String problemId) {
        String id = problemId.toLowerCase(Locale.ROOT);
        if (!id.matches("(?:[a-z0-9][a-z0-9-]*/)?[a-z0-9]+(?:_[a-z0-9]+)+")) {
            throw new ProblemException("AtCoder 题号格式不正确，例如 abc480_a 或 abc480/abc480_a", HttpStatus.BAD_REQUEST);
        }
        String[] parts = id.split("/", 2);
        String task = parts[parts.length - 1];
        String contest = parts.length == 2 ? parts[0] : task.substring(0, task.lastIndexOf('_')).replace('_', '-');
        String url = "https://atcoder.jp/contests/" + contest + "/tasks/" + task;
        return parse(task, url, httpClient.get(URI.create(url + "?lang=en"), "AtCoder"));
    }

    /**
     * 解析题面、输入输出、限制和前三组样例；没有英文时保留日文原文。
     * @param task 平台任务标识
     * @param url 公开题目地址
     * @param html 公开页面
     * @return 统一题面草稿
     */
    public RemoteProblemVO parse(String task, String url, String html) {
        var document = Jsoup.parse(html, url);
        Element statement = document.selectFirst("#task-statement");
        Element title = document.selectFirst("#main-container .h2");
        var limits = LIMITS.matcher(document.text());
        if (statement == null || title == null || !limits.find()) {
            throw new ProblemException("未取得 AtCoder 公开题面，请确认题目已公开", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Element language = statement.selectFirst(".lang-en");
        if (language == null) {
            language = statement.selectFirst(".lang-ja");
        }
        if (language == null) {
            language = statement;
        }
        var cases = new ArrayList<ProblemCaseVO>();
        StringBuilder content = new StringBuilder();
        String input = "";
        String output = "";
        for (Element section : language.select("section")) {
            Element heading = section.selectFirst("h3");
            if (heading == null) {
                continue;
            }
            String name = heading.text();
            Element body = section.clone();
            body.select("h3").remove();
            if (name.equals("Input") || name.equals("入力")) {
                input = ProblemHtmlMarkdown.convert(body);
            } else if (name.equals("Output") || name.equals("出力")) {
                output = ProblemHtmlMarkdown.convert(body);
            } else if (name.matches("(?:Sample Input|入力例)\\s*\\d+")) {
                if (cases.size() == 3) {
                    continue;
                }
                Element pre = body.selectFirst("pre");
                if (pre == null) {
                    throw new ProblemException("AtCoder 样例输入格式异常", HttpStatus.SERVICE_UNAVAILABLE);
                }
                ProblemCaseVO sample = new ProblemCaseVO();
                sample.setInput(ProblemHtmlMarkdown.sampleText(pre));
                sample.setNote("");
                cases.add(sample);
            } else if (name.matches("(?:Sample Output|出力例)\\s*\\d+")) {
                int index = Integer.parseInt(name.replaceAll("\\D", "")) - 1;
                if (index >= 0 && index < cases.size()) {
                    Element pre = body.selectFirst("pre");
                    if (pre == null) {
                        throw new ProblemException("AtCoder 样例输出格式异常", HttpStatus.SERVICE_UNAVAILABLE);
                    }
                    cases.get(index).setOutput(ProblemHtmlMarkdown.sampleText(pre));
                    body.select("pre").remove();
                    String explanation = ProblemHtmlMarkdown.convert(body);
                    if (!explanation.isBlank()) {
                        content.append("\n\n### ").append(name).append(" — 说明\n\n").append(explanation);
                    }
                }
            } else {
                content.append("\n\n### ").append(name).append("\n\n").append(ProblemHtmlMarkdown.convert(body));
            }
        }
        if (content.toString().isBlank() || input.isBlank() || output.isBlank()
                || cases.stream().anyMatch(sample -> sample.getOutput() == null)) {
            throw new ProblemException("AtCoder 题面结构不完整，无法复制", HttpStatus.SERVICE_UNAVAILABLE);
        }
        RemoteProblemVO draft = new RemoteProblemVO();
        draft.setProblemId("AT_" + task);
        draft.setTitle(title.ownText().replaceFirst("^[A-Za-z0-9]+\\s*-\\s*", ""));
        draft.setContent(content.toString().strip());
        draft.setInput(input);
        draft.setOutput(output);
        draft.setTimeLimit(Math.round(Double.parseDouble(limits.group(1)) * 1000));
        draft.setMemoryLimit(Long.parseLong(limits.group(2)));
        draft.setProblemCaseList(cases);
        draft.setSource("本题来源于 AtCoder " + task + " 题目（" + url + "）");
        return draft;
    }
}
