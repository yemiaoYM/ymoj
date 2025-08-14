package com.ymcloud.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.contest.ContestNotAccessException;
import com.ymcloud.common.exception.contest.ContestNotExistException;
import com.ymcloud.common.exception.contest.ContestNotStartException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.mapper.ContestMapper;
import com.ymcloud.core.web.mapper.ContestProblemMapper;
import com.ymcloud.pojo.dto.RegisterContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.enums.JudgeStatus;
import com.ymcloud.pojo.query.ContestPageQuery;
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
    private ContestProblemMapper contestProblemMapper;

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
        if (contest.getVisibility().equals(ContestConstants.VISIBILITY_PUBLIC)) {
            contestVO.setAccess(true);
        }else {
            //查询当前用户的比赛权限
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
     * 查询比赛题目详情
     * @param contestId 比赛id
     * @param displayId 题目展示id
     * @return 比赛题目详情
     */
    @Override
    public ProblemVO getContestProblem(Long contestId, String displayId) {




        return null;
    }

    /**
     * 用户获取当前比赛题目提交状态
     * @param contestId 比赛id
     * @param displayId 题目展示id
     * @return 状态
     */
    @Override
    public JudgeStatus getContestProblemSubmissionStatus(Long contestId, String displayId) {
        return null;
    }

    /**
     * 查询比赛题目列表
     * @param contestId 比赛id
     * @return 比赛题目列表
     */
    @Override
    public List<ContestProblemTableVO> getContestProblemList(Long contestId) {
        //查询比赛
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
        //获取当前用户信息
        Long userId = SecurityUtils.getUserId();
        //比赛不是公共的，需验证用户的权限
        if(!contest.getVisibility().equals(ContestConstants.VISIBILITY_PUBLIC)){
            if(!isAccess(contestId, userId)){
                throw new ContestNotAccessException();
            }
        }
        //查询比赛题目列表
        return contestProblemMapper.getContestProblemList(contestId,userId,contest.getEndTime());
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
