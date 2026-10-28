package com.ymcloud.web.remote;

import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.RemoteProblemHttpException;
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
 * @Description: Codeforces 标准题库公开题面适配，不绕过访问限制
 */
@Component
public class CodeforcesProblemProvider implements RemoteProblemProvider {

    private static final Pattern ID = Pattern.compile("(?:CF)?([1-9][0-9]*)([A-Z][0-9]*)");
    private static final Pattern TIME = Pattern.compile("([\\d.]+)\\s*seconds?");
    private static final Pattern MEMORY = Pattern.compile("(\\d+)\\s*megabytes?");

    @Autowired
    private RemoteProblemHttpClient httpClient;

    @Autowired
    private LuoguProblemProvider luoguProvider;

    /**
     * 返回 Codeforces 平台标识。
     * @return codeforces
     */
    @Override
    public String platform() {
        return "codeforces";
    }

    /**
     * 读取主站公开题面；主站返回 403 时读取洛谷公开收录并注明转载出处。
     * @param problemId 如 2194A 或 CF2194A
     * @return 未保存的题面草稿
     */
    @Override
    public RemoteProblemVO fetch(String problemId) {
        var matcher = ID.matcher(problemId.toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new ProblemException("Codeforces 题号格式不正确，例如 2194A", HttpStatus.BAD_REQUEST);
        }
        String id = matcher.group(1) + matcher.group(2);
        String url = "https://codeforces.com/problemset/problem/" + matcher.group(1) + "/" + matcher.group(2);
        String html;
        try {
            html = httpClient.get(URI.create(url + "?locale=en"), "Codeforces");
        } catch (RemoteProblemHttpException exception) {
            if (exception.getRemoteStatus() != HttpStatus.FORBIDDEN) {
                throw exception;
            }
            RemoteProblemVO draft = luoguProvider.fetch("CF" + id);
            draft.setSource("本题来源于 Codeforces " + id + " 题目（" + url
                    + "）；题面转载自洛谷（https://www.luogu.com.cn/problem/CF" + id + "）");
            return draft;
        }
        return parse(id, url, html);
    }

    /**
     * 解析标准题面区域，保留注释与图片，最多读取前三组样例。
     * @param id 规范化题号，不含 CF 前缀
     * @param url 公开题目地址
     * @param html 公开页面
     * @return 统一题面草稿
     */
    public RemoteProblemVO parse(String id, String url, String html) {
        var document = Jsoup.parse(html, url);
        Element statement = document.selectFirst(".problem-statement");
        if (statement == null) {
            throw new ProblemException("未取得 Codeforces 公开题面，平台可能限制访问", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Element title = statement.selectFirst(".header .title");
        Element input = statement.selectFirst(".input-specification");
        Element output = statement.selectFirst(".output-specification");
        Element time = statement.selectFirst(".time-limit");
        Element memory = statement.selectFirst(".memory-limit");
        if (title == null || input == null || output == null || time == null || memory == null) {
            throw new ProblemException("Codeforces 题面结构不完整，无法复制", HttpStatus.SERVICE_UNAVAILABLE);
        }
        var timeMatcher = TIME.matcher(time.text());
        var memoryMatcher = MEMORY.matcher(memory.text());
        if (!timeMatcher.find() || !memoryMatcher.find()) {
            throw new ProblemException("Codeforces 资源限制格式异常", HttpStatus.SERVICE_UNAVAILABLE);
        }
        var inputs = statement.select(".sample-test > .input > pre");
        var outputs = statement.select(".sample-test > .output > pre");
        if (Math.min(3, inputs.size()) != Math.min(3, outputs.size())) {
            throw new ProblemException("Codeforces 样例输入输出不匹配", HttpStatus.SERVICE_UNAVAILABLE);
        }
        var cases = new ArrayList<ProblemCaseVO>();
        for (int index = 0; index < Math.min(3, inputs.size()); index++) {
            ProblemCaseVO sample = new ProblemCaseVO();
            sample.setInput(ProblemHtmlMarkdown.sampleText(inputs.get(index)));
            sample.setOutput(ProblemHtmlMarkdown.sampleText(outputs.get(index)));
            sample.setNote("");
            cases.add(sample);
        }
        Element content = statement.clone();
        content.select(".header, .input-specification, .output-specification, .sample-tests, .sample-test").remove();
        input.select(".section-title").remove();
        output.select(".section-title").remove();
        RemoteProblemVO draft = new RemoteProblemVO();
        draft.setProblemId("CF" + id);
        draft.setTitle(title.text().replaceFirst("^[A-Z][0-9]*\\.\\s*", ""));
        draft.setContent(ProblemHtmlMarkdown.convert(content));
        draft.setInput(ProblemHtmlMarkdown.convert(input));
        draft.setOutput(ProblemHtmlMarkdown.convert(output));
        draft.setTimeLimit(Math.round(Double.parseDouble(timeMatcher.group(1)) * 1000));
        draft.setMemoryLimit(Long.parseLong(memoryMatcher.group(1)));
        draft.setProblemCaseList(cases);
        draft.setSource("本题来源于 Codeforces " + id + " 题目（" + url + "）");
        return draft;
    }
}
