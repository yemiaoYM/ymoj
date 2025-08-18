package com.ymcloud.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
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
import com.ymcloud.pojo.query.ContestRankPageQuery;
import com.ymcloud.pojo.query.ContestSubmissionPageQuery;
import com.ymcloud.pojo.vo.*;
import com.ymcloud.web.service.ContestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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
     * 获取比赛评测列表 分页查询
     * @param query 查询实体
     * @return 分页实体
     */
    @Override
    public PageVO<SubmissionTableVO> getSubmissionList(ContestSubmissionPageQuery query) {
        //获取用户信息
        Long userId = SecurityUtils.getUserId();
        //检验比赛
        Contest contest = validateContest(query.getContestId(), userId);
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
        boolean isEnd = now.isAfter(contest.getEndTime());
        //返回vo实体
        return PageVO.of(judgePage,judge->{
            SubmissionTableVO vo= BeanUtil.copyProperties(judge,SubmissionTableVO.class);
            vo.setSubmissionId(judge.getId());
            if(!isEnd&&!judge.getUserId().equals(userId)){
                //如果比赛没有结束则禁止查看其他人的提交时间，内存等等
                vo.setTime(null);
                vo.setMemory(null);
                vo.setCodeLength(null);
            }
            return vo;
        });
    }

    /**
     * 获取比赛排名列表
     * @param query 查询请求题
     * @return 比赛排名列表
     */
    @Override
    public ContestRankTableVO getContestRankList(ContestRankPageQuery query) {
        Long contestId = query.getContestId();
        Long userId = SecurityUtils.getUserId();
        String keyword = query.getKeyword();
        //检查比赛比赛情况
        Contest contest = validateContest(contestId, userId);
        //构造前端返回vo
        ContestRankTableVO tableVO = new ContestRankTableVO();
        //比赛模式
        tableVO.setMode(contest.getMode());
        //查询比赛题目列表
        List<ContestProblem> contestProblemList = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .orderByAsc(ContestProblem::getDisplayId)
                .list();
        List<ContestProblemVO> problemList = BeanUtil.copyToList(contestProblemList, ContestProblemVO.class);
        //查询所有比赛用户所有提交情况
        List<ContestSubmissionRecord> recordList = judgeEntityService.
                getContestSubmissionRecordList(contest.getId(), contest.getEndTime(), keyword);
        //如果是空直接返回即可
        if(CollectionUtil.isEmpty(recordList)){
            return tableVO;
        }
        //按用户分组
        Map<String, List<ContestSubmissionRecord>> userMap = recordList.stream()
                .collect(Collectors.groupingBy(ContestSubmissionRecord::getUsername));
        List<ContestUserRankVO> contestUserRankVOList = new ArrayList<>();
        //比赛开始时间
        LocalDateTime startTime = contest.getStartTime();
        //遍历每个用户的比赛记录
        userMap.forEach((key, value) -> {
            ContestUserRankVO contestUserRankVO = new ContestUserRankVO();
            contestUserRankVO.setUsername(key);
            //取第一跳记录的学校即可
            contestUserRankVO.setSchool(value.get(0).getSchool());
            //统计用户每个题目的提交情况
            Map<String,ContestUserSubmissionVO> submissionInfo=new HashMap<>();
            int ac=0;
            int total=0;
            int totalScore=0;
            long totalTime=0;
            for(ContestProblemVO problem : problemList){
                //获取当前题目的提交信息
                List<ContestSubmissionRecord> list = value.stream()
                        .filter(o -> problem.getDisplayId().equals(o.getDisplayId())).toList();
                if(CollectionUtil.isEmpty(list))continue;
                //构造提交详情实体
                ContestUserSubmissionVO submissionVO = new ContestUserSubmissionVO();
                //状态信息
                int errorNum=0;
                int score=0;
                for(ContestSubmissionRecord record : list){
                    //如果是评测进行状态则属于最后一个状态，此状态也不计入
                    if(record.getStatus()==JudgeStatus.STATUS_PENDING
                            ||record.getStatus()==JudgeStatus.STATUS_JUDGING
                            ||record.getStatus()==JudgeStatus.STATUS_COMPILING){
                        break;
                    }
                    //总提交数加一
                    total++;
                    //获取当前题目的最高得分
                    if(record.getScore()!=null){
                        score=Math.max(score,record.getScore());
                    }
                    //只统计第一次通过前的情况
                    if(record.getStatus()==JudgeStatus.STATUS_ACCEPTED){
                        //只有通过的题目才算罚时
                        long acTime = LocalDateTimeUtil.between(startTime, record.getSubmitTime()).toMinutes() +
                                errorNum * 20L;
                        submissionVO.setAc(true);
                        submissionVO.setAcTime(acTime);
                        //用户通过数加一
                        ac++;
                        //将此题的罚时加给总罚时
                        totalTime+=acTime;
                        break;
                    }else{
                        errorNum++;
                    }
                }
                submissionVO.setErrorNum(errorNum);
                submissionVO.setScore(score);
                submissionInfo.put(problem.getDisplayId(),submissionVO);
                //设置此题目的通过数，总提交数，总分数
                totalScore+=score;
                if(submissionVO.isAc()){
                    problem.setAc(problem.getAc()+1);
                    problem.setTotal(problem.getTotal()+errorNum+1);
                }else {
                    problem.setTotal(problem.getTotal()+errorNum);
                }
            }
            contestUserRankVO.setAc(ac);
            contestUserRankVO.setTotal(total);
            contestUserRankVO.setTotalScore(totalScore);
            contestUserRankVO.setTotalTime(totalTime);
            contestUserRankVO.setSubmissionInfo(submissionInfo);
            contestUserRankVOList.add(contestUserRankVO);
        });
        //设置题目数据
        tableVO.setProblemInfoData(problemList);
        //排名排序
        if (contest.getMode() == ContestConstants.MODE_ACM) {
            //acm模式排名：先按照通过数排名，再按照总时间排名
            contestUserRankVOList.sort(Comparator
                    .comparingInt(ContestUserRankVO::getAc).reversed()
                    .thenComparingLong(ContestUserRankVO::getTotalTime)
            );
            //设置排名
            for(int i=0;i<contestUserRankVOList.size();i++){
                ContestUserRankVO vo = contestUserRankVOList.get(i);
                //实际排名
                vo.setOrder(i+1);
                //展示排名
                if(i==0){
                    vo.setRank(1);
                }else{
                    ContestUserRankVO preVo = contestUserRankVOList.get(i - 1);
                    if(vo.getAc()==preVo.getAc()&&vo.getTotalTime()==preVo.getTotalTime()){
                        vo.setRank(preVo.getRank());
                    }else vo.setRank(preVo.getRank()+1);
                }
            }
        }
        else {
            //io模式排名：先按照总分数排名，再按照通过数排名，再按照总时间排名
            contestUserRankVOList.sort(Comparator
                    .comparingInt(ContestUserRankVO::getTotalScore).reversed()
                    .thenComparingInt(ContestUserRankVO::getAc).reversed()
                    .thenComparingLong(ContestUserRankVO::getTotalTime)
            );
            //设置排名
            for(int i=0;i<contestUserRankVOList.size();i++){
                ContestUserRankVO vo = contestUserRankVOList.get(i);
                //实际排名
                vo.setOrder(i+1);
                //展示排名
                if(i==0){
                    vo.setRank(1);
                }else{
                    ContestUserRankVO preVo = contestUserRankVOList.get(i - 1);
                    if(vo.getTotalScore()== preVo.getTotalScore()
                            && vo.getAc()==preVo.getAc()
                            && vo.getTotalTime()==preVo.getTotalTime()){
                        vo.setRank(preVo.getRank());
                    }else vo.setRank(preVo.getRank()+1);
                }
            }
        }
        //分页参数
        Integer pageNo = query.getPageNo();
        Integer pageSize = query.getPageSize();
        if (pageNo == null || pageNo <= 0) pageNo = 1;
        if (pageSize == null || pageSize <= 0) pageSize = 50;
        //总记录数
        long total = contestUserRankVOList.size();
        //计算当前页的起止索引
        int fromIndex = (pageNo - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, (int) total);
        List<ContestUserRankVO> records = contestUserRankVOList.subList(fromIndex, toIndex);
        //设置用户排名数据
        tableVO.setUserRankData(records);
        //返回vo实体
        tableVO.setTotal(total);
        return tableVO;
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
