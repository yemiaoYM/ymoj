package com.ymcloud.web;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MybatisEnumTypeHandler;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.JudgeConstants;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.problem.ProblemPermissionException;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.config.MybatisConfig;
import com.ymcloud.core.security.domain.LoginUser;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.storage.TestDataService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.JudgeCaseEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.UserAcproblemEntityService;
import com.ymcloud.core.web.dao.UserEntityService;
import com.ymcloud.pojo.bo.FileAccessScope;
import com.ymcloud.pojo.bo.ProblemAccessScope;
import com.ymcloud.pojo.dto.RegisterContestDTO;
import com.ymcloud.pojo.dto.admin.CloneContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.JudgeCase;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.entity.UserAcproblem;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.enums.RoleType;
import com.ymcloud.pojo.enums.UserStatus;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.query.oj.SubmissionPageQuery;
import com.ymcloud.web.judge.JudgeMessageCodec;
import com.ymcloud.web.judge.JudgeStatusCache;
import com.ymcloud.web.judge.SpjCompilation;
import com.ymcloud.web.service.admin.AdminContestService;
import com.ymcloud.web.service.admin.AdminProblemService;
import com.ymcloud.web.service.admin.ResourceDeletionService;
import com.ymcloud.web.service.admin.impl.AdminContestServiceImpl;
import com.ymcloud.web.service.admin.impl.AdminProblemServiceImpl;
import com.ymcloud.web.service.admin.impl.ResourceDeletionServiceImpl;
import com.ymcloud.web.service.oj.ContestService;
import com.ymcloud.web.service.oj.impl.ContestServiceImpl;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-01
 * @Description: 在本机随机临时库验证资源授权、引用生命周期和业务删除，文件登记与实际文件保留
 */
@EnabledIfEnvironmentVariable(named = "YMCLOUD_RESOURCE_DB_IT", matches = "true")
class ResourcePermissionDatabaseIT {

    private static AnnotationConfigApplicationContext context;
    private static DriverManagerDataSource database;
    private static SqlSessionTemplate administrator;
    private static String schema;
    private static boolean created;
    private ProblemEntityService problems;
    private ContestEntityService contests;
    private ContestProblemEntityService links;
    private FileEntityService files;
    private AdminContestService admin;
    private ResourceDeletionService deletion;
    private ResourceAccessService access;

    @TempDir
    private Path storageDirectory;

    /**
     * 只接受本机连接，在随机库加载正式结构
     * 不对原业务库运行建表或数据脚本
     * @throws Exception 临时库初始化失败
     */
    @BeforeAll
    @SuppressWarnings("unchecked")
    static void open() throws Exception {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("sql/ymcloud.sql"))) {
            root = root.getParent();
        }
        assertNotNull(root, "找不到初始化 SQL");
        Map<String, Object> config;
        try (var stream = Files.newInputStream(root.resolve("ymcloud-web/src/main/resources/application-dev.yml"))) {
            config = new Yaml().load(stream);
        }
        Map<String, Object> datasource = (Map<String, Object>) ((Map<?, ?>) config.get("spring")).get("datasource");
        String url = String.valueOf(datasource.get("url"));
        assertTrue(url.matches("jdbc:mysql://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/[a-zA-Z0-9_]+(\\?.*)?"));
        assertFalse(url.toLowerCase().contains("password="));
        String username = String.valueOf(datasource.get("username"));
        String password = String.valueOf(datasource.get("password"));
        DriverManagerDataSource local = new DriverManagerDataSource(url, username, password);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(local);
        factory.setMapperLocations(new ClassPathResource("permission/ResourcePermissionFixtureMapper.xml"));
        administrator = new SqlSessionTemplate(factory.getObject());
        schema = "ymcloud_permission_it_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(schema.matches("ymcloud_permission_it_[0-9a-f]{32}"));
        administrator.getMapper(FixtureMapper.class).createSchema(schema);
        created = true;
        int start = url.indexOf('/', "jdbc:mysql://".length());
        int query = url.indexOf('?');
        database = new DriverManagerDataSource(url.substring(0, start + 1) + schema
                + (query < 0 ? "" : url.substring(query)), username, password);
        try (var connection = database.getConnection()) {
            assertEquals(schema, connection.getCatalog());
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                    new FileSystemResource(root.resolve("sql/ymcloud.sql")), StandardCharsets.UTF_8));
        }
        context = new AnnotationConfigApplicationContext(DatabaseConfiguration.class);
    }

    /**
     * 删除本类创建的随机库，不接受任意库名
     */
    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
        if (created && schema.matches("ymcloud_permission_it_[0-9a-f]{32}")) {
            administrator.getMapper(FixtureMapper.class).dropSchema(schema);
        }
    }

    /**
     * 清理隔离库业务记录，并装配真实 Service 事务代理及资源权限策略
     */
    @BeforeEach
    void setup() {
        for (String name : List.of("judgeDispatch", "judgeCase", "userAcproblem", "judge", "testDataVersion",
                "problemTag", "contestUser", "contestProblem", "contest", "problem", "file")) {
            context.getBean(name + "EntityServiceImpl", IService.class).remove(null);
        }
        problems = context.getBean(ProblemEntityService.class);
        contests = context.getBean(ContestEntityService.class);
        links = context.getBean(ContestProblemEntityService.class);
        files = context.getBean(FileEntityService.class);
        admin = context.getBean(AdminContestService.class);
        deletion = context.getBean(ResourceDeletionService.class);
        access = context.getBean(ResourceAccessService.class);
        reset(context.getBean(RedisCache.class), context.getBean(StoredFileService.class));
        login(8L, RoleType.CONTEST_MANAGER.getCode());
    }

    /**
     * 清除线程身份
     */
    @AfterEach
    void logout() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 多场普通赛全部结束后即时按私有访问，读取不回写题目记录
     * 克隆引用不延长保护期，后续手动公开持续有效
     */
    @Test
    void exclusiveStateRequiresAllOriginalReferencesToEnd() {
        Problem problem = problem(8L, ProblemConstants.PUBLIC);
        Contest first = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        Contest second = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        admin.addProblemsToContest(first.getId(), List.of(problem.getId()));
        admin.addProblemsToContest(second.getId(), List.of(problem.getId()));
        assertEquals(ProblemConstants.CONTEST_PROBLEM, problems.getById(problem.getId()).getVisibility());
        end(first);
        login(9L, RoleType.PROBLEM_SETTER.getCode());
        assertEquals(ProblemConstants.CONTEST_PROBLEM, access.effectiveVisibility(problems.getById(problem.getId())));
        assertThrows(ProblemPermissionException.class, () -> access.requireReadProblem(problems.getById(problem.getId())));
        end(second);
        assertEquals(ProblemConstants.PRIVATE, access.effectiveVisibility(problems.getById(problem.getId())));
        assertDoesNotThrow(() -> access.requireReadProblem(problems.getById(problem.getId())));
        assertEquals(ProblemConstants.CONTEST_PROBLEM, problems.getById(problem.getId()).getVisibility());
        login(9L, RoleType.USER.getCode());
        assertThrows(ProblemPermissionException.class, () -> access.requireReadProblem(problems.getById(problem.getId())));
        assertFalse(problems.listPublicProblemIds(LocalDateTime.now()).contains(problem.getProblemId()));
        login(8L, RoleType.CONTEST_MANAGER.getCode());
        Contest clone = contest(8L, first.getId());
        admin.addProblemsToContest(clone.getId(), List.of(problem.getId()));
        assertEquals(ProblemConstants.PRIVATE, access.effectiveVisibility(problems.getById(problem.getId())));
        login(1L, RoleType.USER.getCode());
        context.getBean(AdminProblemService.class).updateProblemVisibility(problem.getId(), ProblemConstants.PUBLIC);
        assertEquals(ProblemConstants.PUBLIC, access.effectiveVisibility(problems.getById(problem.getId())));
        assertTrue(problems.listPublicProblemIds(LocalDateTime.now()).contains(problem.getProblemId()));
    }

    /**
     * 实时状态在分页和计数前筛选，详情与选题列表一致且不改写持久状态
     * 管理归属和克隆题目权限在动态查询中保持有效
     */
    @Test
    void managementAndSelectionUseDynamicVisibilityBeforePagination() {
        AdminProblemService management = context.getBean(AdminProblemService.class);
        Problem ended = problem(8L, ProblemConstants.PUBLIC);
        Contest original = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        admin.addProblemsToContest(original.getId(), List.of(ended.getId()));
        end(original);
        Problem active = problem(8L, ProblemConstants.PRIVATE);
        Contest upcoming = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        admin.addProblemsToContest(upcoming.getId(), List.of(active.getId()));
        Problem publicProblem = problem(8L, ProblemConstants.PUBLIC);
        Contest clone = contest(8L, original.getId());
        admin.addProblemsToContest(clone.getId(), List.of(publicProblem.getId()));
        Problem privateProblem = problem(8L, ProblemConstants.PRIVATE);
        Problem foreign = problem(9L, ProblemConstants.PRIVATE);

        AdminProblemPageQuery query = new AdminProblemPageQuery();
        query.setVisibility(ProblemConstants.PRIVATE);
        query.setPageSize(1);
        var first = management.queryList(query);
        assertEquals(2, first.getTotal());
        assertEquals(List.of(privateProblem.getId()), first.getRecords().stream().map(Problem::getId).toList());
        query.setPageNo(2);
        var second = management.queryList(query);
        assertEquals(2, second.getTotal());
        assertEquals(List.of(ended.getId()), second.getRecords().stream().map(Problem::getId).toList());
        assertEquals(ProblemConstants.PRIVATE, second.getRecords().get(0).getVisibility());
        assertEquals(ProblemConstants.PRIVATE, management.getProblemById(ended.getId()).getVisibility());
        assertEquals(ProblemConstants.CONTEST_PROBLEM, problems.getById(ended.getId()).getVisibility());

        query.setPageNo(1);
        query.setVisibility(ProblemConstants.CONTEST_PROBLEM);
        var exclusive = management.queryList(query);
        assertEquals(1, exclusive.getTotal());
        assertEquals(active.getId(), exclusive.getRecords().get(0).getId());
        query.setVisibility(ProblemConstants.PUBLIC);
        assertEquals(publicProblem.getId(), management.queryList(query).getRecords().get(0).getId());

        login(9L, RoleType.CONTEST_MANAGER.getCode());
        query.setVisibility(ProblemConstants.PRIVATE);
        assertEquals(foreign.getId(), management.queryList(query).getRecords().get(0).getId());
        Contest target = contest(9L, original.getId());
        query.setPageSize(50);
        query.setVisibility(null);
        var available = admin.getAvailableProblems(target.getId(), query);
        assertEquals(4, available.getTotal());
        assertFalse(available.getRecords().stream().anyMatch(record -> record.getId().equals(active.getId())));
        assertEquals(ProblemConstants.PRIVATE, available.getRecords().stream()
                .filter(record -> record.getId().equals(ended.getId())).findFirst().orElseThrow().getVisibility());

        login(99L, RoleType.ADMIN.getCode());
        query.setVisibility(ProblemConstants.PRIVATE);
        assertEquals(3, management.queryList(query).getTotal());
    }

    /**
     * 结束时刻立即解除专属访问限制，分页展示与授权统计使用同一查询时间
     */
    @Test
    void queryTimeBoundaryReleasesExclusiveStateWithoutUpdates() {
        Problem problem = problem(8L, ProblemConstants.PRIVATE);
        Contest original = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        admin.addProblemsToContest(original.getId(), List.of(problem.getId()));
        LocalDateTime deadline = LocalDateTime.now().withNano(0).plusDays(2);
        contests.lambdaUpdate()
                .eq(Contest::getId, original.getId())
                .set(Contest::getEndTime, deadline)
                .update();
        login(9L, RoleType.PROBLEM_SETTER.getCode());
        ProblemAccessScope scope = access.problemScope();
        scope.setNow(deadline.minusSeconds(1));
        assertEquals(0, problems.countAccessible(scope));
        scope.setNow(deadline);
        assertEquals(1, problems.countAccessible(scope));
        var available = problems.pageAvailableForContest(new Page<>(1, 5), new AdminProblemPageQuery(), 0L, scope);
        assertEquals(1, available.getTotal());
        assertEquals(ProblemConstants.PRIVATE, available.getRecords().get(0).getVisibility());
        assertEquals(ProblemConstants.CONTEST_PROBLEM, problems.getById(problem.getId()).getVisibility());
    }

    /**
     * 克隆及后续新增题目均保持原权限，源比赛删除后仍保留克隆身份
     * 克隆引用仍阻止删原题
     */
    @Test
    void cloneNeverChangesVisibilityAndStillProtectsReferencedProblems() {
        Problem first = problem(8L, ProblemConstants.PUBLIC);
        Contest source = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        admin.addProblemsToContest(source.getId(), List.of(first.getId()));
        CloneContestDTO request = new CloneContestDTO();
        request.setTitle("独立克隆");
        request.setVisibility(ContestConstants.VISIBILITY_PRIVATE);
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(3));
        admin.cloneContest(source.getId(), request);
        Contest clone = contests.lambdaQuery().eq(Contest::getSourceId, source.getId()).one();
        assertNotNull(clone);
        deletion.deleteContests(List.of(source.getId()));
        assertEquals(source.getId(), contests.getById(clone.getId()).getSourceId());
        assertEquals(ProblemConstants.PRIVATE, problems.getById(first.getId()).getVisibility());
        Problem second = problem(8L, ProblemConstants.PUBLIC);
        admin.addProblemsToContest(clone.getId(), List.of(second.getId()));
        assertEquals(ProblemConstants.PUBLIC, problems.getById(second.getId()).getVisibility());
        assertThrows(ServiceException.class, () -> deletion.deleteProblems(List.of(second.getId())));
        deletion.deleteContests(List.of(clone.getId()));
        assertEquals(ProblemConstants.PUBLIC, problems.getById(second.getId()).getVisibility());
    }

    /**
     * 他人的活动专属题不能加入另一场比赛，拒绝后不留下关联或改动状态
     */
    @Test
    void foreignExclusiveProblemCannotBeReused() {
        Problem problem = problem(8L, ProblemConstants.PRIVATE);
        Contest source = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        admin.addProblemsToContest(source.getId(), List.of(problem.getId()));
        login(9L, RoleType.CONTEST_MANAGER.getCode());
        Contest target = contest(9L, source.getId());
        assertThrows(ProblemPermissionException.class,
                () -> admin.addProblemsToContest(target.getId(), List.of(problem.getId())));
        assertEquals(1, links.count());
        assertEquals(ProblemConstants.CONTEST_PROBLEM, problems.getById(problem.getId()).getVisibility());
    }

    /**
     * 普通访问范围先在 SQL 过滤，并随活动引用、作者和管理角色变化
     */
    @Test
    void sqlScopeMatchesResourcePolicy() {
        Problem publicProblem = problem(8L, ProblemConstants.PUBLIC);
        problem(8L, ProblemConstants.PRIVATE);
        login(9L, RoleType.USER.getCode());
        assertEquals(1, problems.countAccessible(access.problemScope()));
        login(9L, RoleType.PROBLEM_SETTER.getCode());
        assertEquals(2, problems.countAccessible(access.problemScope()));
        login(8L, RoleType.CONTEST_MANAGER.getCode());
        Contest contest = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        admin.addProblemsToContest(contest.getId(), List.of(publicProblem.getId()));
        assertEquals(2, problems.countAccessible(access.problemScope()));
        login(9L, RoleType.PROBLEM_SETTER.getCode());
        assertEquals(1, problems.countAccessible(access.problemScope()));
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "test", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertEquals(0, problems.countAccessible(access.problemScope()));
    }

    /**
     * 文件列表在真实数据库中按原题归属过滤记录与总数
     * 测试文件即使带有公开标记，也不能出现在公开文件筛选中
     * @throws IOException 临时测试文件写入失败
     */
    @Test
    void filePaginationRespectsOwnershipAndHiddenData() throws IOException {
        Problem own = problem(8L, ProblemConstants.PRIVATE);
        Problem foreign = problem(9L, ProblemConstants.PRIVATE);
        File ownData = registeredFile(own.getId(), FileBizType.TEST_DATA);
        registeredFile(foreign.getId(), FileBizType.TEST_DATA);
        FileAccessScope scope = new FileAccessScope();
        scope.setUserId(8L);
        scope.setProblemManager(true);
        scope.setTestData(true);
        AdminFilePageQuery query = new AdminFilePageQuery();
        Page<File> ownedPage = files.pageAuthorized(new Page<>(1, 1), query, scope);
        assertEquals(1, ownedPage.getTotal());
        assertEquals(ownData.getId(), ownedPage.getRecords().get(0).getId());
        assertEquals(FileBizType.TEST_DATA, ownedPage.getRecords().get(0).getBizType());

        scope.setAdministrator(true);
        assertEquals(2, files.pageAuthorized(new Page<>(1, 1), query, scope).getTotal());
        files.lambdaUpdate()
                .eq(File::getId, ownData.getId())
                .set(File::getIsPublic, FileConstants.ACCESS_PUBLIC)
                .update();
        query.setIsPublic(FileConstants.ACCESS_PUBLIC);
        assertEquals(0, files.pageAuthorized(new Page<>(1, 10), query, scope).getTotal());

        query.setIsPublic(null);
        scope.setAdministrator(false);
        scope.setProblemManager(false);
        assertEquals(0, files.pageAuthorized(new Page<>(1, 10), query, scope).getTotal());
    }

    /**
     * 公共提交列表按原题访问范围过滤，分享标记不能绕过私有和比赛专属限制
     * 分页总数与记录使用相同授权条件，状态枚举正确映射
     */
    @Test
    void publicSubmissionPaginationRespectsProblemAccess() {
        Problem publicProblem = problem(8L, ProblemConstants.PUBLIC);
        Problem privateProblem = problem(8L, ProblemConstants.PRIVATE);
        Problem exclusive = problem(8L, ProblemConstants.PRIVATE);
        Contest contest = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        admin.addProblemsToContest(contest.getId(), List.of(exclusive.getId()));
        JudgeEntityService judges = context.getBean(JudgeEntityService.class);
        for (Problem problem : List.of(publicProblem, privateProblem, exclusive)) {
            judges.save(Judge.builder()
                    .problemId(problem.getId())
                    .contestId(JudgeConstants.PRACTICE_CONTEST_ID)
                    .displayId(problem.getProblemId())
                    .userId(8L)
                    .language("Java")
                    .status(JudgeStatus.STATUS_ACCEPTED)
                    .share(true)
                    .build());
        }
        SubmissionPageQuery query = new SubmissionPageQuery();
        login(9L, RoleType.USER.getCode());
        Page<Judge> publicPage = judges.pagePublicSubmissions(new Page<>(1, 1), query, access.problemScope());
        assertEquals(1, publicPage.getTotal());
        assertEquals(publicProblem.getId(), publicPage.getRecords().get(0).getProblemId());
        assertEquals(JudgeStatus.STATUS_ACCEPTED, publicPage.getRecords().get(0).getStatus());

        login(9L, RoleType.PROBLEM_SETTER.getCode());
        assertEquals(2, judges.pagePublicSubmissions(new Page<>(1, 1), query, access.problemScope()).getTotal());
        login(8L, RoleType.PROBLEM_SETTER.getCode());
        assertEquals(3, judges.pagePublicSubmissions(new Page<>(1, 1), query, access.problemScope()).getTotal());
    }

    /**
     * 比赛开始或已有提交都禁止排序
     * 密码比赛赛后首次验证仍可复盘
     */
    @Test
    void structureLocksAndLatePasswordReviewUsePersistedState() {
        Problem problem = problem(8L, ProblemConstants.PRIVATE);
        Contest contest = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        admin.addProblemsToContest(contest.getId(), List.of(problem.getId()));
        Long linkId = links.lambdaQuery().eq(ContestProblem::getContestId, contest.getId()).one().getId();
        Judge judge = Judge.builder().problemId(problem.getId()).contestId(contest.getId())
                .status(JudgeStatus.STATUS_ACCEPTED).userId(8L).language("Java").build();
        context.getBean(JudgeEntityService.class).save(judge);
        assertThrows(ContestException.class, () -> admin.reorderProblems(contest.getId(), List.of(linkId)));
        end(contest);
        assertThrows(ContestException.class, () -> admin.reorderProblems(contest.getId(), List.of(linkId)));
        admin.updateContestVisibility(contest.getId(), ContestConstants.VISIBILITY_PASSWORD, "review-pass");
        login(9L, RoleType.USER.getCode());
        RegisterContestDTO request = new RegisterContestDTO();
        request.setContestId(contest.getId());
        request.setPassword("review-pass");
        context.getBean(ContestService.class).registerContest(request);
        assertDoesNotThrow(() -> access.requireContestContent(contests.getById(contest.getId())));
        assertEquals(1, context.getBean(ContestUserEntityService.class).count());
    }

    /**
     * 在途提交阻止删除，终态后移除提交和评测明细，不影响其他原题及其提交
     */
    @Test
    void problemDeletionRejectsPendingThenRemovesOnlyOwnedSubmissions() {
        Problem problem = problem(8L, ProblemConstants.PRIVATE);
        Problem other = problem(8L, ProblemConstants.PUBLIC);
        JudgeEntityService judges = context.getBean(JudgeEntityService.class);
        JudgeCaseEntityService cases = context.getBean(JudgeCaseEntityService.class);
        Judge pending = Judge.builder().problemId(problem.getId()).contestId(JudgeConstants.PRACTICE_CONTEST_ID)
                .userId(8L).language("Java").status(JudgeStatus.STATUS_PENDING).build();
        Judge retained = Judge.builder().problemId(other.getId()).contestId(JudgeConstants.PRACTICE_CONTEST_ID)
                .userId(8L).language("Java").status(JudgeStatus.STATUS_ACCEPTED).build();
        judges.saveBatch(List.of(pending, retained));
        assertThrows(ServiceException.class, () -> deletion.deleteProblems(List.of(problem.getId())));
        assertEquals(2, judges.count());
        judges.lambdaUpdate().eq(Judge::getId, pending.getId()).set(Judge::getStatus, JudgeStatus.STATUS_ACCEPTED).update();
        JudgeCase result = new JudgeCase();
        result.setJudgeId(pending.getId());
        result.setCaseIndex(1);
        result.setStatus(JudgeStatus.STATUS_ACCEPTED);
        cases.save(result);
        deletion.deleteProblems(List.of(problem.getId()));
        assertNull(problems.getById(problem.getId()));
        assertEquals(0, cases.count());
        assertNull(judges.getById(pending.getId()));
        assertNotNull(judges.getById(retained.getId()));
        assertNotNull(problems.getById(other.getId()));
    }

    /**
     * 删除题目和提交后保留历史 AC 记录，重新查询排名仍计入该题成绩
     * 回滚时不清理缓存，提交后只清理被删除提交的执行缓存
     */
    @Test
    void problemDeletionKeepsAcceptedHistoryAndRank() {
        Problem problem = problem(8L, ProblemConstants.PUBLIC);
        User user = new User();
        user.setUsername("accepted-history");
        user.setPassword("unused-test-password");
        user.setEmail("accepted-history@example.test");
        context.getBean(UserEntityService.class).save(user);
        JudgeEntityService judges = context.getBean(JudgeEntityService.class);
        UserAcproblemEntityService accepted = context.getBean(UserAcproblemEntityService.class);
        RedisCache redis = context.getBean(RedisCache.class);
        Judge submission = Judge.builder()
                .problemId(problem.getId())
                .contestId(JudgeConstants.PRACTICE_CONTEST_ID)
                .userId(user.getId())
                .username(user.getUsername())
                .displayId(problem.getProblemId())
                .language("Java")
                .status(JudgeStatus.STATUS_ACCEPTED)
                .executionId(UUID.randomUUID().toString())
                .build();
        judges.save(submission);
        UserAcproblem record = UserAcproblem.builder()
                .submissionId(submission.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .problemId(problem.getId())
                .displayId(problem.getProblemId())
                .build();
        accepted.save(record);
        UserAcproblem history = accepted.getById(record.getId());
        var ranking = accepted.listUserAcRank();
        assertEquals(1, ranking.size());
        assertEquals(1L, ranking.get(0).getCount());

        TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            deletion.deleteProblems(List.of(problem.getId()));
            throw new IllegalStateException("模拟删除事务回滚");
        }));
        assertNotNull(problems.getById(problem.getId()));
        assertNotNull(judges.getById(submission.getId()));
        assertEquals(history, accepted.getById(record.getId()));
        verifyNoInteractions(redis);

        deletion.deleteProblems(List.of(problem.getId()));
        assertNull(problems.getById(problem.getId()));
        assertNull(judges.getById(submission.getId()));
        assertEquals(history, accepted.getById(record.getId()));
        assertEquals(ranking, accepted.listUserAcRank());
        verify(redis).deleteObject(List.of(CacheConstants.JUDGE_KEY + submission.getId() + ":" + submission.getExecutionId()));
        verifyNoMoreInteractions(redis);
    }

    /**
     * 删除比赛只移除本场提交，原题、普通练习和其他比赛提交保持不变
     */
    @Test
    void contestDeletionKeepsOriginalAndOtherSubmissionScopes() {
        Problem problem = problem(8L, ProblemConstants.PUBLIC);
        Contest removed = contest(8L, 900L);
        Contest retained = contest(8L, 900L);
        admin.addProblemsToContest(removed.getId(), List.of(problem.getId()));
        admin.addProblemsToContest(retained.getId(), List.of(problem.getId()));
        JudgeEntityService judges = context.getBean(JudgeEntityService.class);
        for (Long contestId : List.of(removed.getId(), retained.getId(), JudgeConstants.PRACTICE_CONTEST_ID)) {
            judges.save(Judge.builder().problemId(problem.getId()).contestId(contestId)
                    .userId(8L).language("Java").status(JudgeStatus.STATUS_ACCEPTED).build());
        }
        deletion.deleteContests(List.of(removed.getId()));
        assertNull(contests.getById(removed.getId()));
        assertNotNull(contests.getById(retained.getId()));
        assertEquals(ProblemConstants.PUBLIC, problems.getById(problem.getId()).getVisibility());
        assertEquals(2, judges.count());
        assertFalse(judges.lambdaQuery().eq(Judge::getContestId, removed.getId()).exists());
        assertEquals(1, links.count());
    }

    /**
     * 题目删除支持事务回滚，提交后仍保留文件登记和实际文件
     * @param type 题目关联的文件类型
     * @throws Exception 临时文件读写失败
     */
    @ParameterizedTest
    @EnumSource(value = FileBizType.class, names = {"TEST_DATA", "PROBLEM_ATTACHMENT", "IMAGE"})
    void problemDeletionKeepsFileRecordsAndPhysicalFiles(FileBizType type) throws Exception {
        Problem problem = problem(8L, ProblemConstants.PRIVATE);
        File file = registeredFile(problem.getId(), type);
        Path physicalFile = Path.of(file.getStoragePath());
        TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            deletion.deleteProblems(List.of(problem.getId()));
            throw new IllegalStateException("模拟业务事务失败");
        }));
        assertNotNull(problems.getById(problem.getId()));
        assertNotNull(files.getById(file.getId()));
        assertEquals("1 2\n", Files.readString(physicalFile, StandardCharsets.UTF_8));
        verifyNoInteractions(context.getBean(RedisCache.class));
        deletion.deleteProblems(List.of(problem.getId()));
        assertNull(problems.getById(problem.getId()));
        assertEquals(file, files.getById(file.getId()));
        assertEquals("1 2\n", Files.readString(physicalFile, StandardCharsets.UTF_8));
        verifyNoInteractions(context.getBean(StoredFileService.class));
    }

    /**
     * 删除比赛后附件及图片登记和实际文件均保持不变
     * @param type 比赛关联的文件类型
     * @throws IOException 临时文件读写失败
     */
    @ParameterizedTest
    @EnumSource(value = FileBizType.class, names = {"CONTEST_ATTACHMENT", "IMAGE"})
    void contestDeletionKeepsFileRecordsAndPhysicalFiles(FileBizType type) throws IOException {
        Contest contest = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        File file = registeredFile(contest.getId(), type);
        deletion.deleteContests(List.of(contest.getId()));
        assertNull(contests.getById(contest.getId()));
        assertEquals(file, files.getById(file.getId()));
        assertEquals("1 2\n", Files.readString(Path.of(file.getStoragePath()), StandardCharsets.UTF_8));
        verifyNoInteractions(context.getBean(StoredFileService.class));
    }

    /**
     * 加题持有原题锁时，删除必须等关联提交后重新检查，不能产生悬空引用
     * @throws Exception 并发事务未按时完成
     */
    @Test
    void concurrentReferenceCommitPreventsProblemDeletion() throws Exception {
        Problem problem = problem(8L, ProblemConstants.PRIVATE);
        Contest contest = contest(8L, ContestConstants.ORIGINAL_SOURCE_ID);
        TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        try {
            var result = transaction.execute(status -> {
                problems.lambdaQuery()
                        .eq(Problem::getId, problem.getId())
                        .last("FOR UPDATE")
                        .one();
                var future = executor.submit(() -> {
                    login(8L, RoleType.CONTEST_MANAGER.getCode());
                    entered.countDown();
                    try {
                        assertThrows(ServiceException.class, () -> deletion.deleteProblems(List.of(problem.getId())));
                    } finally {
                        logout();
                    }
                });
                assertTrue(assertDoesNotThrow(() -> entered.await(5, TimeUnit.SECONDS)));
                assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
                admin.addProblemsToContest(contest.getId(), List.of(problem.getId()));
                return future;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            result.get(10, TimeUnit.SECONDS);
            assertNotNull(problems.getById(problem.getId()));
            assertEquals(1, links.count());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 创建文件登记及对应的临时文件，返回数据库中的完整记录用于比较
     * @param resourceId 文件所属题目或比赛 ID
     * @param type 文件业务类型
     * @return 已持久化文件登记
     * @throws IOException 临时文件写入失败
     */
    private File registeredFile(Long resourceId, FileBizType type) throws IOException {
        Path physicalFile = storageDirectory.resolve("retained-file");
        Files.writeString(physicalFile, "1 2\n", StandardCharsets.UTF_8);
        File file = new File();
        file.setFileName("retained-file");
        file.setStoragePath(physicalFile.toString());
        file.setStorageProfile("local");
        file.setBucket("private");
        file.setObjectKey("retained-file");
        file.setStorageType(FileConstants.STORAGE_LOCAL);
        file.setBizType(type);
        file.setBizId(resourceId);
        file.setIsPublic(FileConstants.ACCESS_PRIVATE);
        files.save(file);
        return files.getById(file.getId());
    }

    /**
     * 创建具有完整时间范围的待开始比赛
     * @param owner 创建者 ID
     * @param source 原创标记或克隆来源 ID
     * @return 已持久化比赛
     */
    private Contest contest(long owner, long source) {
        Contest contest = new Contest();
        contest.setTitle("隔离权限测试");
        contest.setCreatorId(owner);
        contest.setCreatorUsername("isolated");
        contest.setSourceId(source);
        contest.setVisibility(ContestConstants.VISIBILITY_PUBLIC);
        contest.setMode(ContestConstants.MODE_ACM);
        contest.setStartTime(LocalDateTime.now().plusDays(1));
        contest.setEndTime(LocalDateTime.now().plusDays(2));
        contests.save(contest);
        return contest;
    }

    /**
     * 创建指定作者与状态的原题
     * @param owner 原作者 ID
     * @param visibility 持久化可见状态
     * @return 已持久化原题
     */
    private Problem problem(long owner, int visibility) {
        Problem problem = new Problem();
        problem.setProblemId(UUID.randomUUID().toString());
        problem.setTitle("隔离原题");
        problem.setAuthorId(owner);
        problem.setVisibility(visibility);
        problems.save(problem);
        return problem;
    }

    /**
     * 将测试比赛推进到已结束，不改变生产时间校验规则
     * @param contest 测试比赛
     */
    private void end(Contest contest) {
        contests.lambdaUpdate()
                .eq(Contest::getId, contest.getId())
                .set(Contest::getStartTime, LocalDateTime.now().minusDays(2))
                .set(Contest::getEndTime, LocalDateTime.now().minusDays(1))
                .update();
    }

    /**
     * 使用真实认证上下文设置有效角色，不模拟权限服务
     * @param id 当前用户 ID
     * @param role 当前角色
     */
    private void login(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername("isolated");
        user.setStatus(UserStatus.OK);
        LoginUser principal = new LoginUser(user, List.of(role), List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /**
     * 临时库名称已由测试初始化校验，SQL 只放在对应 XML
     */
    interface FixtureMapper {

        /**
         * 创建专属随机测试库
         * @param schema 已校验的测试库名
         */
        void createSchema(@Param("schema") String schema);

        /**
         * 移除本测试创建的随机库
         * @param schema 已校验的测试库名
         */
        void dropSchema(@Param("schema") String schema);
    }

    /**
     * 装配真实 Mapper、DAO、事务和业务服务，缓存与文件服务使用隔离桩
     */
    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.ymcloud.core.web.mapper")
    @ComponentScan("com.ymcloud.core.web.dao.impl")
    @Import({ResourceAccessService.class, AdminContestServiceImpl.class, AdminProblemServiceImpl.class, ContestServiceImpl.class,
            ResourceDeletionServiceImpl.class, JudgeStatusCache.class})
    static class DatabaseConfiguration {

        /**
         * 提供本类已验证的隔离库连接
         * @return 临时库数据源
         */
        @Bean
        DataSource dataSource() {
            return database;
        }

        /**
         * 加载真实 Mapper XML 和生产枚举映射
         * @param dataSource 隔离库
         * @return MyBatis 会话工厂
         * @throws Exception XML 加载失败
         */
        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setDefaultEnumTypeHandler(MybatisEnumTypeHandler.class);
            configuration.addInterceptor(new MybatisConfig().mybatisPlusInterceptor());
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*Mapper.xml"));
            return factory.getObject();
        }

        /**
         * 装配真实数据库事务管理器
         * @param dataSource 隔离库
         * @return 事务管理器
         */
        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        /**
         * 隔离缓存，不连接实际 Redis
         * @return 缓存桩
         */
        @Bean
        RedisCache redis() {
            return mock(RedisCache.class);
        }

        /**
         * 满足既有缓存组件字段注入，不连接 Redis
         * @return Redis 模板桩
         */
        @Bean
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        /**
         * 隔离封面访问，不写实际存储
         * @return 文件服务桩
         */
        @Bean
        StoredFileService storedFiles() {
            return mock(StoredFileService.class);
        }

        /**
         * 隔离测试数据文件操作，查询题目不访问实际存储
         * @return 测试数据服务桩
         */
        @Bean
        TestDataService testData() {
            return mock(TestDataService.class);
        }

        /**
         * 隔离权限测试未使用的 SPJ 编译入口
         * @return 编译服务桩
         */
        @Bean
        SpjCompilation spjCompilation() {
            return mock(SpjCompilation.class);
        }

        /**
         * 满足编译组件的消息依赖，不连接 RabbitMQ
         * @return 隔离消息模板
         */
        @Bean
        RabbitTemplate judgeRabbitTemplate() {
            return mock(RabbitTemplate.class);
        }

        /**
         * 隔离权限测试未使用的判题消息编解码
         * @return 编解码器桩
         */
        @Bean
        JudgeMessageCodec judgeMessageCodec() {
            return mock(JudgeMessageCodec.class);
        }

    }
}
