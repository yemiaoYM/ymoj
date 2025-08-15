package com.ymcloud.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.LanguageConstants;
import com.ymcloud.common.exception.contest.*;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.mapper.ContestMapper;
import com.ymcloud.core.web.mapper.LanguageMapper;
import com.ymcloud.pojo.dto.RegisterContestDTO;
import com.ymcloud.pojo.entity.*;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.query.ContestPageQuery;
import com.ymcloud.pojo.query.ContestProblemSubmissionPageQuery;
import com.ymcloud.pojo.vo.*;
import com.ymcloud.web.service.ContestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: ContestServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:47
 * @Description: ContestServiceImpl
 */
@Service
public class ContestServiceImpl extends ServiceImpl<ContestMapper, Contest> implements ContestService {

    @Autowired
    private ContestUserEntityService contestUserEntityService;

    @Autowired
    private ContestProblemEntityService contestProblemEntityService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private JudgeEntityService judgeEntityService;

    @Autowired
    private LanguageMapper languageMapper;

    /**
     * 获取比赛列表表单 分页查询
     * @param query 查询请求体
     * @return 比赛列表
     */
    @Override
    public PageVO<ContestTableVO> pageQuery(ContestPageQuery query) {
        //创建分页实体
        Page<Contest> page = new Page<>(query.getPageNo(),query.getPageSize());
        Page<Contest> contestPage = lambdaQuery()
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
        Contest contest = getById(contestId);
        if(contest==null){
            throw new ContestNotExistException();
        }
        //拷贝前端实体
        ContestVO contestVO = BeanUtil.copyProperties(contest, ContestVO.class);
        //查询当前用户的比赛权限
        if (contest.getVisibility().equals(ContestConstants.VISIBILITY_PUBLIC)) {
            //公开比赛每个人都有权限访问
            contestVO.setAccess(true);
        }else {
            //私有比赛需要用户注册报名该比赛
            Long userId = SecurityUtils.getUserId();
            contestVO.setAccess(isAccess(contestId, userId));
            //查询参与人数
            Long count = contestUserEntityService.lambdaQuery()
                    .eq(ContestUser::getContestId, contestId)
                    .count();
            contestVO.setCount(count);
        }
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
    public void registerContest(RegisterContestDTO registerContestDTO) {
        //1.获取请求体信息
        Long contestId = registerContestDTO.getContestId();
        String password = registerContestDTO.getPassword();
        //2.根据id查询
        Contest contest = getById(contestId);
        if(contest==null){
            throw new ContestNotExistException();
        }
        //3.公开赛不需要注册报名
        if(contest.getVisibility().equals(ContestConstants.VISIBILITY_PUBLIC)){
            throw new ContestException("公开赛不需要注册报名");
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
        Contest contest = validateContest(contestId, userId);
        //查询比赛题目列表
        return contestProblemEntityService.getContestProblemList(contest.getId(),userId,contest.getEndTime());
    }

    /**
     * 查询比赛题目详情
     * @param contestId 比赛id
     * @param displayId 题目展示id
     * @return 比赛题目详情
     */
    @Override
    public ProblemVO getContestProblem(Long contestId, String displayId) {
        //检查当前比赛状态
        Long userId = SecurityUtils.getUserId();
        Contest contest = validateContest(contestId, userId);
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
        //拷贝实体
        ProblemVO problemVO = BeanUtil.copyProperties(problem, ProblemVO.class);
        //比赛题目优先实现简单信息中的标题和标识
        problemVO.setProblemId(contestProblem.getDisplayId());
        problemVO.setTitle(contestProblem.getDisplayTitle());
        //查询题目的题面样例,将json转化为对象
        List<ProblemCaseVO> problemCaseVOList = JSON.parseArray(problem.getExamples(), ProblemCaseVO.class);
        problemVO.setProblemCaseList(problemCaseVOList);
        //查询题目的语言配置
        List<LanguageVO> languageVOList = languageMapper.getByStatusAndOj(LanguageConstants.ABLE, problem.getSource());
        problemVO.setLanguageList(languageVOList);
        //查询用户该题目的通过状态
        boolean isAccepted = judgeEntityService.lambdaQuery()
                .eq(Judge::getProblemId, problem.getId())
                .eq(Judge::getUserId, userId)
                .eq(Judge::getContestId, contest.getId())
                .eq(Judge::getShare, JudgeStatus.STATUS_ACCEPTED)
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
        Contest contest = validateContest(query.getContestId(), userId);
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
            return vo;
        });
    }

    /**
     * 检验比赛
     */
    private Contest validateContest(Long contestId,Long userId) {
        //先根据比赛id查询
        Contest contest = getById(contestId);
        if(contest==null){
            throw new ContestNotExistException();
        }
        //验证比赛当前状态
        LocalDateTime now = LocalDateTime.now();
        if(contest.getStartTime().isAfter(now)){
            //比赛还未开始
            throw new ContestNotStartException();
        }
        //如果比赛不是公共的，需验证用户的权限
        if(!contest.getVisibility().equals(ContestConstants.VISIBILITY_PUBLIC)){
            if(!isAccess(contestId, userId)){
                throw new ContestNotAccessException();
            }
        }
        return contest;
    }

    /**
     * 查询当前用户的比赛权限
     * @param contestId 比赛id
     * @param userId 用户id
     * @return 是否具有权限
     */
    private Boolean isAccess(Long contestId, Long userId){
        return contestUserEntityService.lambdaQuery()
                .eq(ContestUser::getContestId, contestId)
                .eq(ContestUser::getUserId, userId)
                .exists();
    }

    /**
     * 获取比赛状态
     */
    private String getContestStatus(LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now){
        if(now.isBefore(startTime)){
            return ContestConstants.STATUS_NOT_STARTED;
        }else if(now.isAfter(endTime)){
            return ContestConstants.STATUS_ENDED;
        }else return ContestConstants.STATUS_RUNNING;
    }
}
