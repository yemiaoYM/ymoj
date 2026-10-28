package com.ymcloud.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.RemoteProblemHttpException;
import com.ymcloud.core.security.service.PermissionService;
import com.ymcloud.pojo.dto.admin.RemoteProblemDTO;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;
import com.ymcloud.web.controller.admin.problem.AdminRemoteProblemController;
import com.ymcloud.web.remote.LuoguProblemProvider;
import com.ymcloud.web.remote.RemoteProblemProvider;
import com.ymcloud.web.remote.RemoteProblemHttpClient;
import com.ymcloud.web.service.admin.RemoteProblemService;
import com.ymcloud.web.service.admin.impl.RemoteProblemServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-28
 * @Description: 远程题面解析、分发、请求边界和权限回归
 */
class RemoteProblemTest {

    private static final String PAGE = """
            <html><script type="application/json" id="lentille-context">
            {"status":200,"data":{"problem":{"pid":"P1002","name":"测试题目",
            "content":{"background":"背景","description":"公式 $a+b$ ![](https://example.com/image.png)",
            "formatI":"输入说明","formatO":"输出说明","hint":"提示"},
            "samples":[["1\\n","2\\n"],["3","4"],["5","6"],["7","8"]],
            "limits":{"time":[1000,2000],"memory":[128000]}}}}</script></html>
            """;

    /**
     * 验证原文、背景提示、图片公式、单位与前三组样例。
     */
    @Test
    void parsesStatementWithoutLosingMarkdown() {
        RemoteProblemVO draft = provider().parse("P1002", PAGE);
        assertEquals("测试题目", draft.getTitle());
        assertTrue(draft.getContent().contains("$a+b$ ![](https://example.com/image.png)"));
        assertTrue(draft.getContent().startsWith("## 题目背景\n\n背景"));
        assertTrue(draft.getContent().endsWith("## 说明/提示\n\n提示"));
        assertEquals("输入说明", draft.getInput());
        assertEquals("输出说明", draft.getOutput());
        assertEquals(2000, draft.getTimeLimit());
        assertEquals(125, draft.getMemoryLimit());
        assertEquals(3, draft.getProblemCaseList().size());
        assertEquals("1\n", draft.getProblemCaseList().get(0).getInput());
        assertEquals("6", draft.getProblemCaseList().get(2).getOutput());
        assertEquals("本题来源于洛谷 P1002 题目（https://www.luogu.com.cn/problem/P1002）", draft.getSource());
    }

    /**
     * 页面访问限制、协议变化和题号不匹配均不得返回空白成功草稿。
     */
    @Test
    void rejectsUnavailableAndMalformedPages() {
        LuoguProblemProvider provider = provider();
        for (String page : List.of("<html>请登录</html>", PAGE.replace("200,", "403,"),
                PAGE.replace("P1002", "P1003"), PAGE.replace("\"samples\":[", "\"samples\":null,\"other\":["),
                "<script id='lentille-context'>{invalid}</script>")) {
            assertEquals(503, assertThrows(ProblemException.class, () -> provider.parse("P1002", page)).getCode());
        }
    }

    /**
     * 固定域名、大小写转换、限流和超时失败均经过真实 HTTP 客户端边界，且无自动重试。
     */
    @Test
    void fetchesOnceAndReportsUpstreamFailures() {
        LuoguProblemProvider provider = provider();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(ReflectionTestUtils.getField(provider, "httpClient"), "client", builder.build());
        server.expect(requestTo("https://www.luogu.com.cn/problem/P1002"))
                .andRespond(withSuccess(PAGE, MediaType.TEXT_HTML));
        assertEquals("P1002", provider.fetch("p1002").getProblemId());
        server.verify();
        for (HttpStatus status : List.of(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN,
                HttpStatus.TOO_MANY_REQUESTS, HttpStatus.FOUND)) {
            server.reset();
            server.expect(requestTo("https://www.luogu.com.cn/problem/P1002")).andRespond(withStatus(status));
            ProblemException failure = assertThrows(ProblemException.class, () -> provider.fetch("P1002"));
            assertEquals(status == HttpStatus.NOT_FOUND ? 404 : 503, failure.getCode());
            if (status != HttpStatus.NOT_FOUND) {
                assertEquals(status.value(), ((RemoteProblemHttpException) failure).getRemoteStatus());
            }
            server.verify();
        }
        server.reset();
        server.expect(requestTo("https://www.luogu.com.cn/problem/P1002"))
                .andRespond(withException(new IOException("timeout")));
        assertEquals(503, assertThrows(ProblemException.class, () -> provider.fetch("P1002")).getCode());
        server.verify();
        server.reset();
        assertThrows(ProblemException.class, () -> provider.fetch("https://localhost"));
        assertThrows(ProblemException.class, () -> provider.fetch("P1002?contestId=1"));
        server.verify();
    }

    /**
     * 新平台仅注册适配器即可分发，未知平台不触发网络调用。
     */
    @Test
    void dispatchesRegisteredPlatforms() {
        RemoteProblemProvider other = mock(RemoteProblemProvider.class);
        when(other.platform()).thenReturn("other");
        RemoteProblemVO draft = new RemoteProblemVO();
        draft.setProblemId("A1");
        when(other.fetch("A1")).thenReturn(draft);
        RemoteProblemServiceImpl service = new RemoteProblemServiceImpl();
        ReflectionTestUtils.setField(service, "providers", List.of(provider(), other));
        RemoteProblemDTO request = request("other", "A1");
        assertSame(draft, service.fetch(request));
        assertEquals(400, assertThrows(ProblemException.class,
                () -> service.fetch(request("unknown", "P1002"))).getCode());
        verify(other).fetch("A1");
    }

    /**
     * HTTP 入口拒绝缺失字段、网址、路径及过长题号，不调用业务服务。
     * @throws Exception MVC 调用失败
     */
    @Test
    void validatesHttpInput() throws Exception {
        RemoteProblemService service = mock(RemoteProblemService.class);
        AdminRemoteProblemController controller = new AdminRemoteProblemController();
        ReflectionTestUtils.setField(controller, "remoteProblemService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        ObjectMapper mapper = new ObjectMapper();
        for (RemoteProblemDTO request : List.of(request("luogu", ""), request("", "P1002"),
                request("luogu", "../P1002"), request("luogu", "https://localhost"),
                request("luogu", "A".repeat(129)))) {
            mvc.perform(post("/admin/problem/remote").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        mvc.perform(post("/admin/problem/remote").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request("luogu", "P1002")))).andExpect(status().isOk());
        verify(service).fetch(any());
    }

    /**
     * 使用实际方法权限代理验证未登录、只读、通配符角色和新增权限。
     */
    @Test
    void requiresAddPermission() {
        try (var context = new AnnotationConfigApplicationContext()) {
            RemoteProblemService service = mock(RemoteProblemService.class);
            context.register(SecurityConfig.class, PermissionService.class, AdminRemoteProblemController.class);
            context.registerBean(RemoteProblemService.class, () -> service);
            context.refresh();
            var controller = context.getBean(AdminRemoteProblemController.class);
            for (String authority : List.of("problem:problem:list", "*:*:*")) {
                SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "", authority));
                assertThrows(AccessDeniedException.class, () -> controller.fetch(request("luogu", "P1002")));
            }
            SecurityContextHolder.clearContext();
            assertThrows(AccessDeniedException.class, () -> controller.fetch(request("luogu", "P1002")));
            verifyNoInteractions(service);
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("editor", "", "problem:problem:add"));
            controller.fetch(request("luogu", "P1002"));
            verify(service).fetch(any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 显式指定本地公开页面快照时验证实际洛谷页面结构，不默认访问网络。
     * @throws IOException 读取快照失败
     */
    @Test
    @EnabledIfSystemProperty(named = "luogu.fixture", matches = ".+")
    void parsesRealPublicPage() throws IOException {
        RemoteProblemVO draft = provider().parse("P1002", Files.readString(Path.of(System.getProperty("luogu.fixture"))));
        assertTrue(draft.getTitle().contains("过河卒"));
        assertEquals("6 6 3 3\n", draft.getProblemCaseList().get(0).getInput());
        assertEquals(1000, draft.getTimeLimit());
        assertEquals(125, draft.getMemoryLimit());
    }

    /**
     * 显式启用时通过生产客户端单次访问公开题目，验证实际网络链路。
     */
    @Test
    @EnabledIfSystemProperty(named = "luogu.live", matches = "true")
    void fetchesRealPublicPage() {
        LuoguProblemProvider provider = provider();
        ((RemoteProblemHttpClient) ReflectionTestUtils.getField(provider, "httpClient")).initialize();
        RemoteProblemVO draft = provider.fetch("P1002");
        assertTrue(draft.getTitle().contains("过河卒"));
        assertEquals("6 6 3 3\n", draft.getProblemCaseList().get(0).getInput());
        assertEquals(125, draft.getMemoryLimit());
    }

    /**
     * 创建注入 JSON 解析器的真实平台适配器。
     * @return 可解析页面的适配器
     */
    private LuoguProblemProvider provider() {
        LuoguProblemProvider provider = new LuoguProblemProvider();
        ReflectionTestUtils.setField(provider, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(provider, "httpClient", new RemoteProblemHttpClient());
        return provider;
    }

    /**
     * 创建边界测试请求。
     * @param platform 平台标识
     * @param id 题号
     * @return 请求数据
     */
    private RemoteProblemDTO request(String platform, String id) {
        RemoteProblemDTO request = new RemoteProblemDTO();
        request.setPlatform(platform);
        request.setProblemId(id);
        return request;
    }

    @Configuration
    @EnableMethodSecurity
    static class SecurityConfig {}
}
