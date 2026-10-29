package com.ymcloud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.web.remote.NowcoderProblemProvider;
import com.ymcloud.web.remote.RemoteProblemHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 牛客题库与比赛题面的解析、匿名权限及真实页面回归
 */
class NowcoderProblemTest {

    private static final String PAGE = """
            <div class="question-title">公开题目</div>
            <div class="question-intr">时间限制：C/C++/Rust/Pascal 1秒，其他语言2秒
            空间限制：C/C++/Rust/Pascal 256 M，其他语言512 M</div>
            <div class="subject-describe"><div class="subject-question">求值
            <img src="https://www.nowcoder.com/equation?tex=x" alt="x_1"/><img src="/plot.png"/>
            <script>alert(1)</script></div>
            <h2>输入描述:</h2><pre><img src="https://hr.nowcoder.com/equation?tex=n" alt="n"/>
            <var>a_1</var></pre><h2>输出描述:</h2><pre>输出答案</pre>
            %s<h2>备注:</h2><pre>保留备注</pre></div>
            """;
    private static final String SAMPLE = """
            <div class="question-oi"><div class="question-oi-cont"><pre> a  b

            c</pre></div><div class="question-oi-cont"><pre>2</pre></div>
            <div class="question-oi-cont"><pre>样例解释</pre></div></div>
            """;

    /**
     * 保留公式、普通图片、空白及备注，第四组样例及其解释不进入草稿。
     */
    @Test
    void preservesStatementAndFirstThreeSamples() {
        var draft = new NowcoderProblemProvider().parse("142115/A", "https://ac.nowcoder.com/acm/contest/142115/A",
                PAGE.formatted(SAMPLE.repeat(3) + SAMPLE.replace("样例解释", "第四组解释")));
        assertEquals("NC142115_A", draft.getProblemId());
        assertEquals("公开题目", draft.getTitle());
        assertEquals(1000, draft.getTimeLimit());
        assertEquals(256, draft.getMemoryLimit());
        assertEquals(3, draft.getProblemCaseList().size());
        assertEquals(" a  b\n\nc", draft.getProblemCaseList().get(0).getInput());
        assertTrue(draft.getContent().contains("$x_1$"));
        assertTrue(draft.getContent().contains("https://ac.nowcoder.com/plot.png"));
        assertTrue(draft.getInput().contains("$n$"));
        assertFalse(draft.getInput().contains("```"));
        assertTrue(draft.getContent().contains("保留备注"));
        assertTrue(draft.getContent().contains("样例解释"));
        assertFalse(draft.getContent().contains("第四组解释"));
        assertFalse(draft.getContent().contains("alert(1)"));
        assertTrue(draft.getSource().contains("142115/A"));
    }

    /**
     * 两种入口均构造固定域名地址，非法标识不触发请求。
     */
    @Test
    void normalizesBankAndContestIdentifiers() {
        var client = mock(RemoteProblemHttpClient.class);
        var provider = new NowcoderProblemProvider();
        ReflectionTestUtils.setField(provider, "httpClient", client);
        URI bank = URI.create("https://ac.nowcoder.com/acm/problem/326157");
        URI contest = URI.create("https://ac.nowcoder.com/acm/contest/142115/A");
        when(client.get(bank, "牛客")).thenReturn(PAGE.formatted(""));
        when(client.get(contest, "牛客")).thenReturn(PAGE.formatted(""));
        assertEquals("NC326157", provider.fetch("nc326157").getProblemId());
        assertEquals("NC326157", provider.fetch("326157").getProblemId());
        assertEquals("NC142115_A", provider.fetch("142115/a").getProblemId());
        for (String invalid : List.of("0", "NC", "142115/", "../326157", "142115/A?x=1", "https://ac.nowcoder.com")) {
            assertThrows(ProblemException.class, () -> provider.fetch(invalid));
        }
        verify(client, times(2)).get(bank, "牛客");
        verify(client).get(contest, "牛客");
        verifyNoMoreInteractions(client);
    }

    /**
     * 登录页、私有比赛、收费比赛及缺失题面均不能产生成功草稿。
     */
    @Test
    void rejectsRestrictedAndIncompletePages() {
        var provider = new NowcoderProblemProvider();
        for (String flag : List.of("needPassword", "needCharge")) {
            String page = PAGE.formatted("") + "<script>window.pageInfo={settingInfo:{\"" + flag + "\":true}}</script>";
            assertEquals(403, assertThrows(ProblemException.class,
                    () -> provider.parse("1/A", "https://ac.nowcoder.com/", page)).getCode());
        }
        for (String page : List.of("<html>请登录</html>", PAGE.formatted("").replace("subject-question", "missing"),
                PAGE.formatted("").replace("输出描述", "其他段落"),
                PAGE.formatted("<div class='question-oi'></div>"))) {
            assertEquals(503, assertThrows(ProblemException.class,
                    () -> provider.parse("1", "https://ac.nowcoder.com/", page)).getCode());
        }
    }

    /**
     * 显式启用后匿名读取两种入口，输出真实草稿供浏览器回填验证。
     * @throws IOException 草稿快照写入失败
     */
    @Test
    @EnabledIfSystemProperty(named = "nowcoder.live", matches = "true")
    void fetchesPublicBankAndContest() throws IOException {
        var client = new RemoteProblemHttpClient();
        client.initialize();
        var provider = new NowcoderProblemProvider();
        ReflectionTestUtils.setField(provider, "httpClient", client);
        var bank = provider.fetch("NC326157");
        var contest = provider.fetch("142115/A");
        assertEquals("小红的单复数", bank.getTitle());
        assertEquals(bank.getContent(), contest.getContent());
        assertEquals(bank.getInput(), contest.getInput());
        assertTrue(bank.getInput().contains("$s"));
        assertFalse(bank.getInput().contains("```"));
        assertEquals(1000, bank.getTimeLimit());
        assertEquals(256, bank.getMemoryLimit());
        assertEquals(3, bank.getProblemCaseList().size());
        assertEquals("boxes", bank.getProblemCaseList().get(0).getInput());
        assertEquals("box", bank.getProblemCaseList().get(0).getOutput());
        assertTrue(bank.getSource().contains("/problem/326157"));
        assertTrue(contest.getSource().contains("/contest/142115/A"));
        Files.writeString(Path.of("target/nowcoder-drafts.json"),
                new ObjectMapper().writeValueAsString(Map.of("bank", bank, "contest", contest)));
    }
}
