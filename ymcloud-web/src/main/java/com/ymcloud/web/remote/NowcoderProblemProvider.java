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
 * @CreateTime: 2026-10-29
 * @Description: 牛客题库及公开比赛题面复制，仅读取匿名可见内容
 */
@Component
public class NowcoderProblemProvider implements RemoteProblemProvider {

    private static final Pattern ID = Pattern.compile("(?:NC)?([1-9][0-9]*)|([1-9][0-9]*)/([A-Z]+)");
    private static final Pattern TIME = Pattern.compile("时间限制[：:]\\s*[^，,\\n]*?([\\d.]+)\\s*(毫秒|ms|秒)");
    private static final Pattern MEMORY = Pattern.compile("空间限制[：:]\\s*[^，,\\n]*?(\\d+)\\s*(M|MB|MiB)\\b");
    private static final Pattern RESTRICTED = Pattern.compile("\"(?:needPassword|needCharge)\"\\s*:\\s*true");

    @Autowired
    private RemoteProblemHttpClient httpClient;

    /**
     * 返回牛客平台标识。
     * @return nowcoder
     */
    @Override
    public String platform() {
        return "nowcoder";
    }

    /**
     * 根据题库编号或比赛编号与题序访问固定牛客地址。
     * @param problemId 如 NC326157、326157 或 142115/A
     * @return 尚未保存的题面草稿
     */
    @Override
    public RemoteProblemVO fetch(String problemId) {
        var matcher = ID.matcher(problemId.toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new ProblemException("牛客题号格式不正确，例如 NC326157 或 142115/A", HttpStatus.BAD_REQUEST);
        }
        boolean bank = matcher.group(1) != null;
        String id = bank ? matcher.group(1) : matcher.group(2) + "/" + matcher.group(3);
        String url = "https://ac.nowcoder.com/acm/" + (bank ? "problem/" : "contest/") + id;
        return parse(id, url, httpClient.get(URI.create(url), "牛客"));
    }

    /**
     * 解析公开 HTML，保留公式、图片、说明和前三组样例，资源限制采用 C/C++ 档。
     * @param id 规范化编号，不含 NC 前缀
     * @param url 原题公开地址
     * @param html 匿名请求返回的页面
     * @return 可填入新增题目表单的草稿
     */
    public RemoteProblemVO parse(String id, String url, String html) {
        var document = Jsoup.parse(html, url);
        for (Element script : document.select("script")) {
            if (script.data().contains("window.pageInfo") && RESTRICTED.matcher(script.data()).find()) {
                throw new ProblemException("仅支持牛客公开题目和公开比赛", HttpStatus.FORBIDDEN);
            }
        }
        Element title = document.selectFirst(".question-title");
        Element subject = document.selectFirst(".subject-describe");
        Element limits = document.selectFirst(".question-intr");
        if (title == null || title.text().isBlank() || subject == null || limits == null) {
            throw new ProblemException("未取得牛客公开题面，请确认题目或比赛可公开访问", HttpStatus.SERVICE_UNAVAILABLE);
        }
        var time = TIME.matcher(limits.text());
        var memory = MEMORY.matcher(limits.text());
        Element description = subject.selectFirst(".subject-question");
        if (description == null || !time.find() || !memory.find()) {
            throw new ProblemException("牛客题面或资源限制不完整，无法复制", HttpStatus.SERVICE_UNAVAILABLE);
        }
        for (Element image : subject.select("img")) {
            String formula = ProblemHtmlMarkdown.equationImage(image.absUrl("src"), image.attr("alt"));
            if (formula != null) {
                image.replaceWith(new Element("var").text(formula));
            }
        }
        StringBuilder content = new StringBuilder(ProblemHtmlMarkdown.convert(description));
        String input = "";
        String output = "";
        for (Element heading : subject.children()) {
            if (!heading.is("h2")) {
                continue;
            }
            Element body = heading.nextElementSibling();
            if (body == null || !(body.is("pre") || body.is("div"))) {
                throw new ProblemException("牛客题面段落不完整，无法复制", HttpStatus.SERVICE_UNAVAILABLE);
            }
            String name = heading.text().replaceAll("[：:]$", "");
            String text = ProblemHtmlMarkdown.convert(body);
            if (name.equals("输入描述")) {
                input = text;
            } else if (name.equals("输出描述")) {
                output = text;
            } else {
                content.append("\n\n## ").append(name).append("\n\n").append(text);
            }
        }
        var cases = new ArrayList<ProblemCaseVO>();
        for (Element example : subject.select(".question-oi")) {
            if (cases.size() == 3) {
                break;
            }
            var blocks = example.select(".question-oi-cont > pre");
            if (blocks.size() < 2) {
                throw new ProblemException("牛客样例输入输出不完整，无法复制", HttpStatus.SERVICE_UNAVAILABLE);
            }
            ProblemCaseVO sample = new ProblemCaseVO();
            sample.setInput(ProblemHtmlMarkdown.sampleText(blocks.get(0)));
            sample.setOutput(ProblemHtmlMarkdown.sampleText(blocks.get(1)));
            sample.setNote("");
            cases.add(sample);
            for (int index = 2; index < blocks.size(); index++) {
                content.append("\n\n### 示例 ").append(cases.size()).append(" 说明\n\n")
                        .append(ProblemHtmlMarkdown.convert(blocks.get(index)));
            }
        }
        if (content.toString().isBlank() || input.isBlank() || output.isBlank()) {
            throw new ProblemException("牛客题面内容不完整，无法复制", HttpStatus.SERVICE_UNAVAILABLE);
        }
        RemoteProblemVO draft = new RemoteProblemVO();
        draft.setProblemId("NC" + id.replace('/', '_'));
        draft.setTitle(title.text());
        draft.setContent(content.toString());
        draft.setInput(input);
        draft.setOutput(output);
        draft.setTimeLimit(Math.round(Double.parseDouble(time.group(1)) * (time.group(2).equals("秒") ? 1000 : 1)));
        draft.setMemoryLimit(Long.parseLong(memory.group(1)));
        draft.setProblemCaseList(cases);
        draft.setSource("本题来源于牛客 " + id + " 题目（" + url + "）");
        return draft;
    }
}
