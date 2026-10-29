package com.ymcloud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.RemoteProblemConnectionException;
import com.ymcloud.common.exception.problem.RemoteProblemHttpException;
import com.ymcloud.pojo.dto.admin.RemoteProblemDTO;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;
import com.ymcloud.web.remote.AtCoderProblemProvider;
import com.ymcloud.web.remote.CodeforcesProblemProvider;
import com.ymcloud.web.remote.LeetCodeProblemProvider;
import com.ymcloud.web.remote.LuoguProblemProvider;
import com.ymcloud.web.remote.ProblemHtmlMarkdown;
import com.ymcloud.web.remote.RemoteProblemHttpClient;
import com.ymcloud.web.service.admin.impl.RemoteProblemServiceImpl;
import com.ymcloud.web.remote.RemoteProblemImages;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 多平台题面转换、样例边界和公开页面兼容性回归
 */
class PlatformProblemTest {

    private static final String ATCODER = """
            <div id="main-container"><span class="h2">A - Grid <a>Editorial</a></span>
            <p>Time Limit: 2 sec / Memory Limit: 1024 MiB</p>
            <div id="task-statement"><span class="lang-ja"><section><h3>問題文</h3>日本語</section></span>
            <span class="lang-en"><section><h3>Problem Statement</h3><p>Find <var>S_{i,j}</var>.</p></section>
            <section><h3>Constraints</h3><ul><li><var>N \\leq 10^5</var></li></ul></section>
            <section><h3>Input</h3><pre><var>N</var> M</pre></section>
            <section><h3>Output</h3><p>Print Yes or No.</p></section>
            %s</span></div></div>
            """;

    private static final String CODEFORCES = """
            <div class="problem-statement"><div class="header"><div class="title">A. Lawn Mower</div>
            <div class="time-limit">time limit per test 1 second</div>
            <div class="memory-limit">memory limit per test 256 megabytes</div></div>
            <div><p>Choose $$$n \\leq 10^9$$$ boards.</p><img src="/image.png"></div>
            <div class="input-specification"><div class="section-title">Input</div><p>Read $$$t$$$.</p></div>
            <div class="output-specification"><div class="section-title">Output</div><p>Print result.</p></div>
            <div class="sample-tests"><div class="sample-test">%s</div></div>
            <div class="note"><div class="section-title">Note</div><p>Keep the explanation.</p></div></div>
            """;

    /**
     * 数学、相对图片、表格和代码上标在转换后仍可阅读，活动节点不进入草稿。
     */
    @Test
    void preservesMarkdownStructureAndSampleWhitespace() {
        String markdown = ProblemHtmlMarkdown.convert(Jsoup.parseBodyFragment("""
                <p><var>a_i &lt; b</var> and $$$x_1 \\leq 4$$$ <code>10<sup>5</sup></code></p>
                <img src="../plot.png" alt="plot"><a href="/details">details</a>
                <table><tr><th>A</th><th>B</th></tr><tr><td>x</td><td>y</td></tr></table>
                <script>alert(1)</script><a href="javascript:alert(1)">safe text</a>
                <pre> a  b

                c</pre>
                """, "https://atcoder.jp/contests/test/").body());
        assertTrue(markdown.contains("$a_i < b$"));
        assertTrue(markdown.contains("$x_1 \\leq 4$"));
        assertTrue(markdown.contains("` 10^5 `"));
        assertTrue(markdown.contains("https://atcoder.jp/contests/plot.png"));
        assertTrue(markdown.contains("| --- | --- |"));
        assertTrue(markdown.contains(" a  b\n\nc"));
        assertFalse(markdown.contains("alert(1)"));
        assertEquals("1  2\n\n3\n", ProblemHtmlMarkdown.sampleText(
                Jsoup.parse("<pre><div>1  2</div><div></div><div>3</div></pre>").selectFirst("pre")));
    }

    /**
     * 预格式输入中的数学变量可渲染，普通代码与实际样例仍保留原始空白。
     */
    @Test
    void preservesMathInPreformattedInputWithoutChangingCode() {
        String html = "<pre><var>S_{1,1}S_{1,2}</var>\n<var>S_{2,1}S_{2,2}</var>\n</pre>";
        String converted = ProblemHtmlMarkdown.convert(Jsoup.parseBodyFragment(html).body());
        assertEquals("$S_{1,1}S_{1,2}$  \n$S_{2,1}S_{2,2}$", converted);
        assertEquals("```\n  a  b\n\n c\n```", ProblemHtmlMarkdown.convert(
                Jsoup.parseBodyFragment("<pre>  a  b\n\n c</pre>").body()));
        assertEquals("```\nS_{1,1}\n```", ProblemHtmlMarkdown.convert(
                Jsoup.parseBodyFragment("<pre><code><var>S_{1,1}</var></code></pre>").body()));
        assertEquals("S_{1,1}S_{1,2}\nS_{2,1}S_{2,2}\n", ProblemHtmlMarkdown.sampleText(
                Jsoup.parseBodyFragment(html).selectFirst("pre")));
    }

    /**
     * AtCoder 优先英文，保留限制、公式和说明，并忽略第四组。
     */
    @Test
    void parsesAtCoderAndOnlyFirstThreeSamples() {
        StringBuilder samples = new StringBuilder();
        for (int index = 1; index <= 3; index++) {
            samples.append("<section><h3>Sample Input ").append(index).append("</h3><pre>").append(index)
                    .append("\n</pre></section><section><h3>Sample Output ").append(index)
                    .append("</h3><pre>Yes\n</pre><p>Explanation ").append(index).append("</p></section>");
        }
        samples.append("<section><h3>Sample Input 4</h3>ignored</section><section><h3>Sample Output 4</h3>ignored</section>");
        RemoteProblemVO draft = new AtCoderProblemProvider().parse("abc480_a", "https://atcoder.jp/", ATCODER.formatted(samples));
        assertEquals("Grid", draft.getTitle());
        assertEquals(2000, draft.getTimeLimit());
        assertEquals(1024, draft.getMemoryLimit());
        assertEquals(3, draft.getProblemCaseList().size());
        assertEquals("3\n", draft.getProblemCaseList().get(2).getInput());
        assertTrue(draft.getContent().contains("$S_{i,j}$"));
        assertTrue(draft.getInput().contains("$N$&nbsp;M"));
        assertFalse(draft.getInput().contains("```"));
        assertTrue(draft.getContent().contains("Explanation 3"));
        assertFalse(draft.getContent().contains("日本語"));
        assertFalse(draft.getContent().contains("ignored"));
        assertTrue(draft.getSource().contains("AtCoder abc480_a"));
    }

    /**
     * Codeforces 行式样例不能被压成一行，题干不重复输入输出及样例。
     */
    @Test
    void parsesCodeforcesSamplesMathAndNotes() {
        String sample = "<div class='input'><pre><div>5</div><div>9 3</div></pre></div>"
                + "<div class='output'><pre>2<br>4</pre></div>";
        RemoteProblemVO draft = new CodeforcesProblemProvider().parse("2194A", "https://codeforces.com/problemset/problem/2194/A",
                CODEFORCES.formatted(sample.repeat(4)));
        assertEquals("CF2194A", draft.getProblemId());
        assertEquals("Lawn Mower", draft.getTitle());
        assertEquals(1000, draft.getTimeLimit());
        assertEquals(256, draft.getMemoryLimit());
        assertEquals(3, draft.getProblemCaseList().size());
        assertEquals("5\n9 3\n", draft.getProblemCaseList().get(0).getInput());
        assertEquals("2\n4", draft.getProblemCaseList().get(0).getOutput());
        assertTrue(draft.getContent().contains("$n \\leq 10^9$"));
        assertTrue(draft.getContent().contains("Keep the explanation."));
        assertTrue(draft.getContent().contains("https://codeforces.com/image.png"));
        assertFalse(draft.getContent().contains("Read"));
    }

    /**
     * 主站连接失败或返回 403 时使用公开转载，其他错误仍交给调用方。
     */
    @Test
    void usesPublicReprintAfterConnectionFailureOrCodeforces403() {
        RemoteProblemHttpClient client = mock(RemoteProblemHttpClient.class);
        LuoguProblemProvider luogu = mock(LuoguProblemProvider.class);
        CodeforcesProblemProvider provider = new CodeforcesProblemProvider();
        ReflectionTestUtils.setField(provider, "httpClient", client);
        ReflectionTestUtils.setField(provider, "luoguProvider", luogu);
        URI url = URI.create("https://codeforces.com/problemset/problem/2194/A?locale=en");
        when(client.get(url, "Codeforces")).thenReturn(CODEFORCES.formatted(""));
        assertEquals("Lawn Mower", provider.fetch("2194A").getTitle());
        verifyNoInteractions(luogu);
        for (int status : List.of(429, 500, 302)) {
            var failure = new RemoteProblemHttpException("不可用", status);
            doThrow(failure).when(client).get(url, "Codeforces");
            assertSame(failure, assertThrows(RemoteProblemHttpException.class, () -> provider.fetch("2194A")));
        }
        var missing = new ProblemException("不存在", 404);
        doThrow(missing).when(client).get(url, "Codeforces");
        assertSame(missing, assertThrows(ProblemException.class, () -> provider.fetch("2194A")));
        verifyNoInteractions(luogu);
        doThrow(new RemoteProblemHttpException("受限", 403)).when(client).get(url, "Codeforces");
        RemoteProblemVO reprint = new RemoteProblemVO();
        reprint.setProblemId("CF2194A");
        reprint.setContent("公开题面 $n$");
        when(luogu.fetch("CF2194A")).thenReturn(reprint);
        assertSame(reprint, provider.fetch("cf2194a"));
        assertEquals("公开题面 $n$", reprint.getContent());
        assertTrue(reprint.getSource().contains("Codeforces 2194A"));
        assertTrue(reprint.getSource().contains("https://www.luogu.com.cn/problem/CF2194A"));
        verify(luogu).fetch("CF2194A");
        doThrow(new RemoteProblemConnectionException("连接超时", new IOException("timeout")))
                .when(client).get(url, "Codeforces");
        assertSame(reprint, provider.fetch("2194A"));
        when(luogu.fetch("CF2194A")).thenThrow(missing);
        assertSame(missing, assertThrows(ProblemException.class, () -> provider.fetch("2194A")));
    }

    /**
     * 显式指定本机代理端口后，验证生产客户端直连及经代理的完整题面读取。
     */
    @Test
    @EnabledIfSystemProperty(named = "codeforces.proxyPort", matches = "[0-9]+")
    void fetchesCodeforcesWithAndWithoutProxy() {
        ProxySelector original = ProxySelector.getDefault();
        int port = Integer.parseInt(System.getProperty("codeforces.proxyPort"));
        try {
            for (ProxySelector selector : List.of(HttpClient.Builder.NO_PROXY,
                    ProxySelector.of(new InetSocketAddress("127.0.0.1", port)))) {
                ProxySelector.setDefault(selector);
                RemoteProblemHttpClient client = new RemoteProblemHttpClient();
                client.initialize();
                LuoguProblemProvider luogu = new LuoguProblemProvider();
                ReflectionTestUtils.setField(luogu, "httpClient", client);
                ReflectionTestUtils.setField(luogu, "objectMapper", new ObjectMapper());
                CodeforcesProblemProvider cf = new CodeforcesProblemProvider();
                ReflectionTestUtils.setField(cf, "httpClient", client);
                ReflectionTestUtils.setField(cf, "luoguProvider", luogu);
                RemoteProblemVO draft = cf.fetch("2194A");
                assertEquals("CF2194A", draft.getProblemId());
                assertEquals("Lawn Mower", draft.getTitle());
                assertFalse(draft.getContent().isBlank());
                assertEquals(1, draft.getProblemCaseList().size());
                assertTrue(draft.getSource().contains("Codeforces 2194A"));
            }
        } finally {
            ProxySelector.setDefault(original);
        }
    }

    /**
     * 力扣中文题面及示例正确拆分，不虚构传统输入输出和资源限制。
     * @throws IOException JSON 编码失败
     */
    @Test
    void parsesLeetCodeChineseAndEnglishExamples() throws IOException {
        String chinese = "<p>返回下标。</p>";
        for (int index = 1; index <= 4; index++) {
            chinese += "<p><strong>示例 " + index + "：</strong></p><pre><strong>输入：</strong>nums = [1,2]\n"
                    + "<strong>输出：</strong>[0,1]\n<strong>解释：</strong>说明" + index + "</pre><img src='/sample.png'>";
        }
        chinese += "<p><strong>提示：</strong></p><ul><li><code>n &lt; 10<sup>5</sup></code></li></ul>";
        RemoteProblemVO draft = leetCode().parse("two-sum", "https://leetcode.cn/", leetCodePage(chinese, false));
        assertEquals("两数之和", draft.getTitle());
        assertEquals("LC1", draft.getProblemId());
        assertEquals(3, draft.getProblemCaseList().size());
        assertEquals("nums = [1,2]", draft.getProblemCaseList().get(0).getInput());
        assertEquals("[0,1]", draft.getProblemCaseList().get(0).getOutput());
        assertTrue(draft.getContent().contains("说明3"));
        assertTrue(draft.getContent().contains("https://leetcode.cn/sample.png"));
        assertFalse(draft.getContent().contains("说明4"));
        assertTrue(draft.getContent().contains("原题函数定义"));
        assertTrue(draft.getContent().contains("10^5"));
        assertEquals("", draft.getInput());
        assertEquals("", draft.getOutput());
        assertNull(draft.getTimeLimit());
        assertNull(draft.getMemoryLimit());
        assertTrue(draft.getNotice().contains("函数式接口"));
        String english = "<p>Return indices.</p><p><strong>Example 1:</strong></p>"
                + "<p><strong>Input:</strong> nums = [1,2]</p><p><strong>Output:</strong> [0,1]</p>"
                + "<p><strong>Explanation:</strong> sum</p><p>Constraints:</p><ul><li>n &gt; 0</li></ul>";
        draft = leetCode().parse("two-sum", "https://leetcode.cn/", leetCodePage(english, false)
                .replace("translatedContent", "unused").replace("translatedTitle", "unusedTitle"));
        assertEquals("Two Sum", draft.getTitle());
        assertEquals("nums = [1,2]", draft.getProblemCaseList().get(0).getInput());
        assertEquals("[0,1]", draft.getProblemCaseList().get(0).getOutput());
        assertTrue(draft.getContent().contains("sum"));
    }

    /**
     * 缺失或受限题面返回可解释的业务错误，不生成空白成功草稿。
     * @throws IOException JSON 编码失败
     */
    @Test
    void rejectsUnavailablePages() throws IOException {
        assertThrows(ProblemException.class, () -> new AtCoderProblemProvider().parse("abc480_a", "https://atcoder.jp/", "login"));
        assertThrows(ProblemException.class, () -> new CodeforcesProblemProvider().parse("2194A", "https://codeforces.com/", "challenge"));
        for (String html : List.of("login", "<script id='__NEXT_DATA__'></script>", "<script id='__NEXT_DATA__'>{bad}</script>")) {
            assertEquals(503, assertThrows(ProblemException.class, () -> leetCode().parse("two-sum", "https://leetcode.cn/", html)).getCode());
        }
        String paid = leetCodePage("<p>premium</p>", true);
        assertEquals(403, assertThrows(ProblemException.class, () -> leetCode().parse("two-sum", "https://leetcode.cn/", paid)).getCode());
    }

    /**
     * 平台题号仅用于构造固定域名地址，超长本地建议编号清空交给管理员填写。
     * @throws IOException JSON 编码失败
     */
    @Test
    void usesCanonicalUrlsAndNormalizesLocalId() throws IOException {
        RemoteProblemHttpClient client = mock(RemoteProblemHttpClient.class);
        AtCoderProblemProvider atCoder = new AtCoderProblemProvider();
        ReflectionTestUtils.setField(atCoder, "httpClient", client);
        URI atUrl = URI.create("https://atcoder.jp/contests/past202010-open/tasks/past202010_task_a?lang=en");
        when(client.get(atUrl, "AtCoder")).thenReturn(ATCODER.formatted(""));
        RemoteProblemServiceImpl service = new RemoteProblemServiceImpl();
        ReflectionTestUtils.setField(service, "providers", List.of(atCoder));
        ReflectionTestUtils.setField(service, "images", mock(RemoteProblemImages.class));
        RemoteProblemDTO request = new RemoteProblemDTO();
        request.setPlatform("atcoder");
        request.setProblemId("past202010-open/past202010_task_a");
        assertEquals("", service.fetch(request).getProblemId());
        verify(client).get(atUrl, "AtCoder");
        CodeforcesProblemProvider codeforces = new CodeforcesProblemProvider();
        ReflectionTestUtils.setField(codeforces, "httpClient", client);
        URI cfUrl = URI.create("https://codeforces.com/problemset/problem/2194/B1?locale=en");
        when(client.get(cfUrl, "Codeforces")).thenReturn(CODEFORCES.formatted(""));
        assertEquals("CF2194B1", codeforces.fetch("cf2194b1").getProblemId());
        LeetCodeProblemProvider leetcode = leetCode();
        ReflectionTestUtils.setField(leetcode, "httpClient", client);
        URI lcUrl = URI.create("https://leetcode.cn/problems/two-sum/description/");
        when(client.get(lcUrl, "力扣")).thenReturn(leetCodePage("<p>Statement</p>", false));
        assertEquals("LC1", leetcode.fetch("Two-Sum").getProblemId());
        for (String invalid : List.of("../test", "https://localhost", "1?contest=2")) {
            assertThrows(ProblemException.class, () -> atCoder.fetch(invalid));
            assertThrows(ProblemException.class, () -> codeforces.fetch(invalid));
            assertThrows(ProblemException.class, () -> leetcode.fetch(invalid));
        }
        assertThrows(ProblemException.class, () -> leetcode.fetch("1"));
        verify(client).get(cfUrl, "Codeforces");
        verify(client).get(lcUrl, "力扣");
        verifyNoMoreInteractions(client);
    }

    /**
     * 使用显式指定目录内的真实公开页面快照，并输出草稿供浏览器验证回填。
     * @throws IOException 快照读写失败
     */
    @Test
    @EnabledIfSystemProperty(named = "platform.fixtures", matches = ".+")
    void parsesRealPublicSnapshots() throws IOException {
        Path directory = Path.of(System.getProperty("platform.fixtures"));
        RemoteProblemVO at = new AtCoderProblemProvider().parse("abc480_a", "https://atcoder.jp/contests/abc480/tasks/abc480_a",
                Files.readString(directory.resolve("atcoder-public.html")));
        assertEquals("ADjacent", at.getTitle());
        assertEquals(3, at.getProblemCaseList().size());
        assertTrue(at.getContent().contains("$S_{i,j}$"));
        assertEquals(1024, at.getMemoryLimit());
        assertTrue(at.getInput().contains("$S_{1,1}S_{1,2}$"));
        assertFalse(at.getInput().contains("```"));
        RemoteProblemVO lc = leetCode().parse("two-sum", "https://leetcode.cn/problems/two-sum/description/",
                Files.readString(directory.resolve("leetcode-public.html")));
        assertEquals("两数之和", lc.getTitle());
        assertEquals(3, lc.getProblemCaseList().size());
        assertEquals("nums = [2,7,11,15], target = 9", lc.getProblemCaseList().get(0).getInput());
        assertEquals("[0,1]", lc.getProblemCaseList().get(0).getOutput());
        assertEquals("[0,1]", lc.getProblemCaseList().get(2).getOutput());
        RemoteProblemVO cf = new CodeforcesProblemProvider().parse("2194A", "https://codeforces.com/problemset/problem/2194/A",
                Files.readString(directory.resolve("cf-origin.html")));
        assertEquals("Lawn Mower", cf.getTitle());
        assertEquals(1000, cf.getTimeLimit());
        assertEquals(256, cf.getMemoryLimit());
        assertEquals(1, cf.getProblemCaseList().size());
        LuoguProblemProvider luogu = new LuoguProblemProvider();
        ReflectionTestUtils.setField(luogu, "objectMapper", new ObjectMapper());
        RemoteProblemVO reprint = luogu.parse("CF2194A", Files.readString(directory.resolve("cf-luogu.html")));
        assertEquals(cf.getTitle(), reprint.getTitle());
        assertEquals(cf.getTimeLimit(), reprint.getTimeLimit());
        assertEquals(250, reprint.getMemoryLimit());
        assertEquals(cf.getProblemCaseList().get(0).getInput().strip(), reprint.getProblemCaseList().get(0).getInput().strip());
        assertEquals(cf.getProblemCaseList().get(0).getOutput().strip(), reprint.getProblemCaseList().get(0).getOutput().strip());
        Files.writeString(directory.resolve("platform-drafts.json"), new ObjectMapper().writeValueAsString(
                Map.of("atcoder", at, "leetcode", lc, "codeforces", cf)));
    }

    /**
     * 显式启用真实请求，验证 CF 主流程及主站 403 后的公开转载读取。
     */
    @Test
    @EnabledIfSystemProperty(named = "codeforces.live", matches = "true")
    void fetchesCodeforcesAndPublicReprintWithProductionClient() {
        RemoteProblemHttpClient client = new RemoteProblemHttpClient();
        client.initialize();
        LuoguProblemProvider luogu = new LuoguProblemProvider();
        ReflectionTestUtils.setField(luogu, "httpClient", client);
        ReflectionTestUtils.setField(luogu, "objectMapper", new ObjectMapper());
        CodeforcesProblemProvider cf = new CodeforcesProblemProvider();
        ReflectionTestUtils.setField(cf, "httpClient", client);
        ReflectionTestUtils.setField(cf, "luoguProvider", luogu);
        assertEquals("Lawn Mower", cf.fetch("2194A").getTitle());
        RemoteProblemHttpClient blocked = mock(RemoteProblemHttpClient.class);
        when(blocked.get(URI.create("https://codeforces.com/problemset/problem/2194/A?locale=en"), "Codeforces"))
                .thenThrow(new RemoteProblemHttpException("受限", 403));
        ReflectionTestUtils.setField(cf, "httpClient", blocked);
        RemoteProblemVO draft = cf.fetch("2194A");
        assertEquals("Lawn Mower", draft.getTitle());
        assertEquals(1, draft.getProblemCaseList().size());
        assertTrue(draft.getSource().contains("题面转载自洛谷"));
    }

    /**
     * 显式启用后用生产客户端各请求一次公开页面，覆盖实际 TLS 和响应解码。
     */
    @Test
    @EnabledIfSystemProperty(named = "platform.live", matches = "true")
    void fetchesPublicPagesWithProductionClient() {
        RemoteProblemHttpClient client = new RemoteProblemHttpClient();
        client.initialize();
        AtCoderProblemProvider at = new AtCoderProblemProvider();
        ReflectionTestUtils.setField(at, "httpClient", client);
        assertEquals("ADjacent", at.fetch("abc480_a").getTitle());
        LeetCodeProblemProvider lc = leetCode();
        ReflectionTestUtils.setField(lc, "httpClient", client);
        assertEquals("两数之和", lc.fetch("two-sum").getTitle());
    }

    /**
     * 创建注入 JSON 解析器的力扣适配器。
     * @return 可解析测试页面的适配器
     */
    private LeetCodeProblemProvider leetCode() {
        LeetCodeProblemProvider provider = new LeetCodeProblemProvider();
        ReflectionTestUtils.setField(provider, "objectMapper", new ObjectMapper());
        return provider;
    }

    /**
     * 构造与公开页面同结构的 SSR 数据，便于覆盖中英文和权限分支。
     * @param content 题面 HTML
     * @param paid 是否会员专享
     * @return 包含题目数据的 HTML
     * @throws IOException JSON 编码失败
     */
    private String leetCodePage(String content, boolean paid) throws IOException {
        Map<String, Object> question = Map.of("titleSlug", "two-sum", "title", "Two Sum", "translatedTitle", "两数之和",
                "questionFrontendId", "1", "translatedContent", content, "content", content, "isPaidOnly", paid,
                "metaData", "{\"name\":\"twoSum\"}");
        var root = Map.of("props", Map.of("pageProps", Map.of("dehydratedState", Map.of("queries",
                List.of(Map.of("state", Map.of("data", Map.of("question", question))))))));
        return "<script id='__NEXT_DATA__'>" + new ObjectMapper().writeValueAsString(root) + "</script>";
    }
}
