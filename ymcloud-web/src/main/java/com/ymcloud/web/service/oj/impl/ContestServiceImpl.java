package com.ymcloud.web.service.oj.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.contest.ContestNotExistException;
import com.ymcloud.common.exception.contest.ContestProblemNotExistException;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.LanguageEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.dto.RegisterContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.query.oj.ContestPageQuery;
import com.ymcloud.pojo.query.oj.ContestProblemSubmissionPageQuery;
import com.ymcloud.pojo.query.oj.ContestRankPageQuery;
import com.ymcloud.pojo.query.oj.ContestSubmissionPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.ContestCountVO;
import com.ymcloud.pojo.vo.oj.ContestProblemTableVO;
import com.ymcloud.pojo.vo.oj.ContestProblemVO;
import com.ymcloud.pojo.vo.oj.ContestRankTableVO;
import com.ymcloud.pojo.vo.oj.ContestRankVO;
import com.ymcloud.pojo.vo.oj.ContestTableVO;
import com.ymcloud.pojo.vo.oj.ContestVO;
import com.ymcloud.pojo.vo.oj.LanguageVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import com.ymcloud.pojo.vo.oj.ProblemVO;
import com.ymcloud.pojo.vo.oj.SubmissionTableVO;
import com.ymcloud.web.service.oj.ContestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:47
 * @Description: ContestServiceImpl
 */
@Service
public class ContestServiceImpl implements ContestService {

    @Autowired
    private ResourceAccessService resourceAccess;

    @Autowired
    private ContestEntityService contestEntityService;

    @Autowired
    private ContestUserEntityService contestUserEntityService;

    @Autowired
    private ContestProblemEntityService contestProblemEntityService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private LanguageEntityService languageEntityService;

    /**
     * 获取比赛列表表单 分页查询
     * @param query 查询请求体
     * @return 比赛列表
     */
    @Override
    public PageVO<ContestTableVO> pageQuery(ContestPageQuery query) {
        //创建分页实体
        Page<Contest> page = new Page<>(query.getPageNo(),query.getPageSize());
        boolean loggedIn = new AuthenticationTrustResolverImpl().isAuthenticated(SecurityUtils.getAuthentication());
        Long currentUserId = loggedIn ? SecurityUtils.getUserId() : null;
        Page<Contest> contestPage = contestEntityService.lambdaQuery()
                .and(!(loggedIn && SecurityUtils.isAdmin()), wrapper -> wrapper
                        .ne(Contest::getVisibility, ContestConstants.VISIBILITY_PRIVATE)
                        .or(loggedIn)
                        .eq(loggedIn, Contest::getCreatorId, currentUserId))
                .like(StrUtil.isNotBlank(query.getKeyword()), Contest::getTitle, query.getKeyword())
                .orderByDesc(Contest::getStartTime)
                .page(page);
        //拷贝实体
        if(contestPage.getTotal()>0){
            //如果不为空就查询参与人数
            List<Long> contestIds = contestPage.getRecords().stream().map(Contest::getId).toList();
            Map<Long, Integer> map = contestUserEntityService.getCountByContestIds(contestIds).stream()
                    .collect(Collectors.toMap(ContestCountVO::getId, ContestCountVO::getCount));

            return PageVO.of(contestPage,contest -> {
                ContestTableVO vo = BeanUtil.copyProperties(contest, ContestTableVO.class);
                //设置比赛状态
                String contestStatus = getContestStatus(vo.getStartTime(), vo.getEndTime(), LocalDateTime.now());
                vo.setStatus(contestStatus);
                //设置参与人数
                vo.setParticipantCount(map.getOrDefault(contest.getId(), 0));
                return vo;
            });
        }
        //返回vo
        return PageVO.of(contestPage,ContestTableVO.class);
    }

    /**
     * 获取比赛详情
     * @param contestId 比赛id
     * @return 比赛详情
     */
    @Override
    public ContestVO getContestInfo(Long contestId) {
        //根据id查询
        Contest contest = contestEntityService.getById(contestId);
        //判断是否存在
        if(contest == null){
            throw new ContestNotExistException();
        }
        if (contest.getVisibility() == ContestConstants.VISIBILITY_PRIVATE) {
            resourceAccess.requireContestAccess(contest);
        }
        //拷贝前端实体
        ContestVO contestVO = BeanUtil.copyProperties(contest, ContestVO.class);
        //查询当前用户的比赛权限
        contestVO.setAccess(resourceAccess.canAccessContest(contest));
        contestVO.setCanManage(resourceAccess.canManageContest(contest));
        contestVO.setCanClone(resourceAccess.isContestManager()
                && (resourceAccess.canManageContest(contest)
                || (contestVO.getAccess() && !LocalDateTime.now().isBefore(contest.getEndTime()))));
        Long count = contestUserEntityService.lambdaQuery()
                .eq(ContestUser::getContestId, contestId)
                .count();
        contestVO.setCount(count);
        //设置now为当前时间
        contestVO.setNow(LocalDateTime.now());
        //设置比赛状态
        String contestStatus = getContestStatus(contestVO.getStartTime(), contestVO.getEndTime(), contestVO.getNow());
        contestVO.setStatus(contestStatus);
        return contestVO;
    }

    /**
     * 注册进入比赛
     * @param registerContestDTO 请求体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerContest(RegisterContestDTO registerContestDTO) {
        //1.获取请求体信息
        Long contestId = registerContestDTO.getContestId();
        String password = registerContestDTO.getPassword();
        //2.根据id查询
        Contest contest = contestEntityService.lambdaQuery()
                .eq(Contest::getId, contestId)
                .last("FOR UPDATE")
                .one();
        //判断是否存在
        if(contest == null){
            throw new ContestNotExistException();
        }
        if (contest.getVisibility() != ContestConstants.VISIBILITY_PASSWORD) {
            throw new ContestException("仅密码比赛可以验证密码取得参赛或复盘资格");
        }
        //4.验证密码
        if(!contest.getPassword().equals(password)){
            throw new ContestException("比赛密码错误，请重新输入！");
        }
        //5.获取当前用户信息
        Long userId = SecurityUtils.getUserId();
        //6.先查询该用户是否已经注册过比赛
        boolean exists = contestUserEntityService.lambdaQuery()
                .eq(ContestUser::getContestId, contestId)
                .eq(ContestUser::getUserId, userId)
                .exists();
        if(exists){
            throw new ContestException("您已注册过该比赛，请勿重复注册！");
        }
        //6.为该用户注册比赛
        ContestUser contestUser = new ContestUser();
        contestUser.setUserId(userId);
        contestUser.setContestId(contestId);
        boolean save = contestUserEntityService.save(contestUser);
        if(!save){
            throw new ContestException("注册比赛失败，请稍后再试");
        }
    }

    /**
     * 查询比赛题目列表
     * @param contestId 比赛id
     * @return 比赛题目列表
     */
    @Override
    public List<ContestProblemTableVO> getContestProblemList(Long contestId) {
        //检查当前比赛状态
        Long userId = SecurityUtils.getUserId();
        Contest contest = validateContest(contestId);
        //查询比赛题目列表
        return contestProblemEntityService.getContestProblemList(contest.getId(),userId);
    }

    /**
     * 查询比赛题目详情，仅返回比赛展示信息，不公开原题来源。
     * @param contestId 比赛id
     * @param displayId 题目展示id
     * @return 比赛题目详情
     */
    @Override
    public ProblemVO getContestProblem(Long contestId, String displayId) {
        //检查当前比赛状态
        Long userId = SecurityUtils.getUserId();
        Contest contest = validateContest(contestId);
        //查询比赛题目简单信息
        ContestProblem contestProblem = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contest.getId())
                .eq(ContestProblem::getDisplayId, displayId)
                .one();
        if (contestProblem == null) {
            throw new ContestProblemNotExistException();
        }
        //查询比赛题目详细信息
        Problem problem = problemEntityService.getById(contestProblem.getProblemId());
        if (problem == null) {
            throw new ContestProblemNotExistException();
        }
        // 按比赛展示白名单组装，原题来源不进入响应
        ProblemVO problemVO = new ProblemVO();
        problemVO.setSubmissionStartTime(contest.getStartTime());
        problemVO.setSubmissionEndTime(contest.getEndTime());
        problemVO.setServerTime(LocalDateTime.now());
        problemVO.setContent(problem.getContent());
        problemVO.setInput(problem.getInput());
        problemVO.setOutput(problem.getOutput());
        problemVO.setDifficulty(problem.getDifficulty());
        problemVO.setJudgeMode(problem.getJudgeMode());
        problemVO.setTimeLimit(problem.getTimeLimit());
        problemVO.setMemoryLimit(problem.getMemoryLimit());
        problemVO.setAuthorUsername(problem.getAuthorUsername());
        //比赛题目优先实现简单信息中的标题和标识
        problemVO.setProblemId(contestProblem.getDisplayId());
        problemVO.setTitle(contestProblem.getDisplayTitle());
        //查询题目的题面样例,将json转化为对象
        List<ProblemCaseVO> problemCaseVOList = JSON.parseArray(problem.getExamples(), ProblemCaseVO.class);
        problemVO.setProblemCaseList(problemCaseVOList);
        // 比赛题目同样使用本站语言配置，原题来源不决定执行平台
        List<LanguageVO> languageVOList = languageEntityService.getByStatusAndOj(
                LanguageConstants.ABLE,
                LanguageConstants.LOCAL_PLATFORM);
        problemVO.setLanguageList(languageVOList);
        //查询用户该题目的通过状态
        boolean isAccepted = judgeEntityService.lambdaQuery()
                .eq(Judge::getProblemId, problem.getId())
                .eq(Judge::getUserId, userId)
                .eq(Judge::getContestId, contest.getId())
                .eq(Judge::getStatus, JudgeStatus.STATUS_ACCEPTED)
                .exists();
        problemVO.setAccepted(isAccepted);
        //返回题目前端实体
        return problemVO;
    }

    /**
     * 查询用户当前比赛题目提交列表
     * @param query 分页查询实体
     * @return 提交列表
     */
    @Override
    public PageVO<SubmissionTableVO> problemSubmissionList(ContestProblemSubmissionPageQuery query) {
        //检查当前比赛状态
        Long userId = SecurityUtils.getUserId();
        Contest contest = validateContest(query.getContestId());
        //查询比赛题目简单信息
        ContestProblem contestProblem = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contest.getId())
                .eq(ContestProblem::getDisplayId, query.getDisplayId())
                .one();
        if (contestProblem == null) {
            throw new ContestProblemNotExistException();
        }
        //构造分页实体
        Page<Judge> page=new Page<>(query.getPageNo(),query.getPageSize());
        //根据限定条件进行查询
        Page<Judge> judgePage = judgeEntityService.lambdaQuery()
                .eq(Judge::getUserId, userId)
                .eq(Judge::getProblemId,contestProblem.getProblemId())
                .eq(Judge::getContestId, contest.getId())
                .orderByDesc(Judge::getCreatedTime)
                .page(page);
        //返回提交vo
        return PageVO.of(judgePage,judge->{
            SubmissionTableVO vo= BeanUtil.copyProperties(judge,SubmissionTableVO.class);
            vo.setSubmissionId(judge.getId());
            vo.setCanRejudge(resourceAccess.canManageContest(contest));
            return vo;
        });
    }

    /**
     * 获取比赛评测列表 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    @Override
    public PageVO<SubmissionTableVO> getSubmissionList(ContestSubmissionPageQuery query) {
        //获取用户信息
        Long userId = SecurityUtils.getUserId();
        //检验比赛
        Contest contest = validateContest(query.getContestId());
        //构建分页实体
        Page<Judge> page=new Page<>(query.getPageNo(),query.getPageSize());
        //分页条件查询
        Page<Judge> judgePage = judgeEntityService.lambdaQuery()
                //个人提交
                .eq(query.getIsMine(), Judge::getUserId, userId)
                .eq(query.getStatus() != null, Judge::getStatus, query.getStatus())
                .eq(Judge::getContestId, contest.getId())
                //满足其一即可
                .and(StrUtil.isNotBlank(query.getProblemKey()), wrapper -> wrapper
                        .like(Judge::getDisplayId, query.getProblemKey())
                        .or()
                        .like(Judge::getProblemTitle, query.getProblemKey())
                )
                //当查询所有人并且用户名不为空时触发
                .like(!query.getIsMine()&&StrUtil.isNotBlank(query.getUsername()), Judge::getUsername, query.getUsername())
                .orderByDesc(Judge::getCreatedTime)
                .page(page);
        //判断比赛是否结束
        LocalDateTime now = LocalDateTime.now();
        boolean isEnd = !now.isBefore(contest.getEndTime());
        boolean canManage = resourceAccess.canManageContest(contest);
        //返回vo实体
        return PageVO.of(judgePage,judge->{
            SubmissionTableVO vo= BeanUtil.copyProperties(judge,SubmissionTableVO.class);
            vo.setSubmissionId(judge.getId());
            //如果为管理员或者比赛创建者则不限制
            vo.setCanRejudge(canManage);
            if (canManage) {
                return vo;
            }
            //如果比赛没有结束则禁止查看其他人的提交时间，内存等等
            if(!isEnd&&!judge.getUserId().equals(userId)){
                vo.setTime(null);
                vo.setMemory(null);
                vo.setCodeLength(null);
            }
            return vo;
        });
    }

    /**
     * 按比赛计分模式查询排名；OI 与 IOI 暂共用每题最高分规则。
     * @param query 查询请求题
     * @return 比赛排名列表
     */
    @Override
    public ContestRankTableVO getContestRankList(ContestRankPageQuery query) {
        Long contestId = query.getContestId();
        Long userId = SecurityUtils.getUserId();
        //关键词匹配 匹配用户名，学校
        String keyword = query.getKeyword();
        //检查比赛比赛情况
        Contest contest = validateContest(contestId);
        //构造前端返回vo
        ContestRankTableVO tableVO = new ContestRankTableVO();
        //比赛模式
        tableVO.setMode(contest.getMode());
        //查询比赛题目列表
        List<ContestProblemTableVO> contestProblemList = contestProblemEntityService.getContestProblemList(contest.getId(), userId);
        List<ContestProblemVO> problemList = BeanUtil.copyToList(contestProblemList, ContestProblemVO.class);
        tableVO.setProblemInfoData(problemList);
        //分页处理
        Page<ContestRankVO> page = new Page<>(query.getPageNo(),query.getPageSize());
        //结果信息
        Page<ContestRankVO> contestRank = null;
        //根据模式获取排名信息
        if(contest.getMode() == ContestConstants.MODE_ACM){
            contestRank = contestEntityService.getAcmContestRank(page, contest.getId(),keyword);
        }else if(contest.getMode() == ContestConstants.MODE_OI
                || contest.getMode() == ContestConstants.MODE_IOI){
            contestRank = contestEntityService.getOIContestRank(page, contest.getId(),keyword);
        }
        if(contestRank!=null){
            tableVO.setUserRankData(contestRank.getRecords());
            tableVO.setTotal(contestRank.getTotal());
        }
        return tableVO;
    }

    /**
     * 检验当前用户的比赛访问资格及题面开放阶段
     * @param contestId 比赛 ID
     * @return 可访问的比赛
     */
    private Contest validateContest(Long contestId) {
        //简单校验
        if (contestId == null || contestId <= 0) {
            throw new ContestException("比赛id参数格式错误", HttpStatus.BAD_REQUEST);
        }
        //先根据比赛id查询
        Contest contest = contestEntityService.getById(contestId);
        if (contest == null) {
            throw new ContestNotExistException();
        }
        resourceAccess.requireContestContent(contest);
        return contest;
    }

    /**
     * 获取比赛状态
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param now 当前时间
     * @return 未开始、进行中或已结束
     */
    private String getContestStatus(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now){
        if(now.isBefore(startTime)){
            return ContestConstants.STATUS_NOT_STARTED;
        }else if(!now.isBefore(endTime)){
            return ContestConstants.STATUS_ENDED;
        } else {
            return ContestConstants.STATUS_RUNNING;
        }
    }
}
