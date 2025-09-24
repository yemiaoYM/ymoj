package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.contest.ContestNotExistException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.pojo.dto.admin.ContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.query.admin.AdminContestPageQuery;
import com.ymcloud.pojo.vo.PageVO;
import com.ymcloud.web.service.admin.AdminContestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin.impl
 * @ClassName: AdminContestServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  19:09
 * @Description: 管理端比赛服务实现类
 */
@Service
public class AdminContestServiceImpl implements AdminContestService {

    @Autowired
    private ContestEntityService contestEntityService;

    @Autowired
    private ContestUserEntityService contestUserEntityService;

    @Autowired
    private ContestProblemEntityService contestProblemEntityService;

    /**
     * 分页查询比赛列表
     * @param pageQuery 查询条件
     * @return 比赛列表
     */
    @Override
    public PageVO<Contest> queryList(AdminContestPageQuery pageQuery) {
        //创建分页实体
        Page<Contest> page = Page.of(pageQuery.getPageNo(), pageQuery.getPageSize());
        //条件分页查询比赛列表
        Page<Contest> record = contestEntityService.lambdaQuery()
                .like(StrUtil.isNotBlank(pageQuery.getKeyword()), Contest::getTitle, pageQuery.getKeyword())
                .or(StrUtil.isNotBlank(pageQuery.getKeyword()))
                .like(StrUtil.isNotBlank(pageQuery.getKeyword()), Contest::getCreatorUsername, pageQuery.getKeyword())
                .orderByDesc(Contest::getStartTime)
                .page(page);
        return PageVO.of(record, Contest.class);
    }

    /**
     * 根据ID查询比赛详情
     * @param id 比赛ID
     * @return 比赛信息
     */
    @Override
    public Contest getContestById(Long id) {
        Contest contest = contestEntityService.getById(id);
        if (contest == null) {
            throw new ContestNotExistException();
        }
        return contest;
    }

    /**
     * 添加比赛
     * @param contestDTO 比赛信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addContest(ContestDTO contestDTO) {
        //验证比赛时间
        validateContestTime(contestDTO.getStartTime(), contestDTO.getEndTime());
        //构造实体
        Contest contest = BeanUtil.copyProperties(contestDTO, Contest.class);
        //如果是私有，必须设置密码
        if (contest.getVisibility() == 1 && StrUtil.isBlank(contest.getPassword())) {
            throw new ContestException("私有比赛必须设置密码", HttpStatus.BAD_REQUEST);
        }
        //比赛创建者信息
        User user = SecurityUtils.getUser();
        contest.setCreatorId(user.getId());
        contest.setCreatorUsername(user.getUsername());
        //计算比赛时长（分钟）
        if (contestDTO.getStartTime() != null && contestDTO.getEndTime() != null) {
            long duration = LocalDateTimeUtil.between(contestDTO.getStartTime(), contestDTO.getEndTime()).toMinutes();
            contest.setDuration(duration);
        }
        boolean save = contestEntityService.save(contest);
        if (!save) {
            throw new ContestException("添加比赛失败，请稍后再试");
        }
    }

    /**
     * 修改比赛
     * @param id 比赛ID
     * @param contestDTO 比赛信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContest(Long id, ContestDTO contestDTO) {
        //检查比赛是否存在
        Contest existContest = contestEntityService.getById(id);
        if (existContest == null) {
            throw new ContestNotExistException();
        }
        //验证比赛时间
        validateContestTime(contestDTO.getStartTime(), contestDTO.getEndTime());
        //修改字段
        Contest contest = BeanUtil.copyProperties(contestDTO, Contest.class);
        contest.setId(id);
        //保留原有的创建者信息
        contest.setCreatorId(existContest.getCreatorId());
        contest.setCreatorUsername(existContest.getCreatorUsername());
        //如果是私有，必须设置密码
        if (contest.getVisibility() == 1 && StrUtil.isBlank(contest.getPassword())) {
            throw new ContestException("私有比赛必须设置密码", HttpStatus.BAD_REQUEST);
        }
        //重新计算比赛时长
        if (contestDTO.getStartTime() != null && contestDTO.getEndTime() != null) {
            long duration = LocalDateTimeUtil.between(contestDTO.getStartTime(), contestDTO.getEndTime()).toMinutes();
            contest.setDuration(duration);
        }
        boolean update = contestEntityService.updateById(contest);
        if (!update) {
            throw new ContestException("修改比赛失败，请稍后再试");
        }
    }

    /**
     * 根据ID删除比赛
     * @param ids 比赛ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContestById(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            throw new ContestException("比赛ID不能为空");
        }
        //检查比赛是否存在
        List<Contest> contests = contestEntityService.listByIds(ids);
        if (contests.size() != ids.size()) {
            throw new ContestException("部分比赛不存在或已被删除");
        }
        //检查是否有进行中的比赛
        LocalDateTime now = LocalDateTime.now();
        boolean hasRunningContest = contests.stream()
                .anyMatch(contest -> contest.getStartTime() != null && contest.getEndTime() != null
                        && now.isAfter(contest.getStartTime()) && now.isBefore(contest.getEndTime()));
        if (hasRunningContest) {
            throw new ContestException("不能删除正在进行中的比赛");
        }
        //删除比赛相关数据
        for (Long contestId : ids) {
            //删除比赛用户关联
            contestUserEntityService.lambdaUpdate()
                    .eq(ContestUser::getContestId, contestId)
                    .remove();
            //删除比赛题目关联
            contestProblemEntityService.lambdaUpdate()
                    .eq(ContestProblem::getContestId, contestId)
                    .remove();
        }
        //删除比赛
        boolean remove = contestEntityService.removeByIds(ids);
        if (!remove) {
            throw new ContestException("删除比赛失败，请稍后再试");
        }
    }

    /**
     * 更新比赛可见性
     * @param id 比赛ID
     * @param visibility 可见性：0=公开，1=私有
     * @param password 密码（私有比赛时必填）
     */
    @Override
    public void updateContestVisibility(Long id, Integer visibility, String password) {
        //检查可见性参数
        if (visibility == null || (visibility < 0 || visibility > 1)) {
            throw new ContestException("可见性参数错误", HttpStatus.BAD_REQUEST);
        }
        //检查比赛是否存在
        Contest contest = contestEntityService.getById(id);
        if (contest == null) {
            throw new ContestNotExistException();
        }
        //私有比赛必须设置密码
        if (visibility == 1) {
            if (StrUtil.isBlank(password)) {
                throw new ContestException("私有比赛必须设置密码", HttpStatus.BAD_REQUEST);
            }
            if (password.length() < 5 || password.length() > 20) {
                throw new ContestException("密码长度必须在5-20位之间", HttpStatus.BAD_REQUEST);
            }
        }
        //更新可见性和密码
        boolean update = contestEntityService.lambdaUpdate()
                .eq(Contest::getId, id)
                .set(Contest::getVisibility, visibility)
                .set(Contest::getPassword, visibility == 1 ? password : null)
                .update();
        if (!update) {
            throw new ContestException("修改比赛可见性失败，请稍后再试");
        }
    }


    /**
     * 克隆比赛
     * @param id 源比赛ID
     * @param title 新比赛标题
     * @param visibility 访问权限
     * @param password 密码（私有比赛时必填）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cloneContest(Long id, String title, Integer visibility, String password) {
        //验证参数
        if (StrUtil.isBlank(title)) {
            throw new ContestException("比赛标题不能为空", HttpStatus.BAD_REQUEST);
        }
        if (title.length() > 100) {
            throw new ContestException("比赛标题长度不能超过100个字符", HttpStatus.BAD_REQUEST);
        }
        if (visibility == null || (visibility < 0 || visibility > 1)) {
            throw new ContestException("访问权限参数错误", HttpStatus.BAD_REQUEST);
        }
        //私有比赛必须设置密码
        if (visibility == 1) {
            if (StrUtil.isBlank(password)) {
                throw new ContestException("私有比赛必须设置密码", HttpStatus.BAD_REQUEST);
            }
            if (password.length() < 5 || password.length() > 20) {
                throw new ContestException("密码长度必须在5-20位之间", HttpStatus.BAD_REQUEST);
            }
        }
        
        //检查源比赛是否存在
        Contest sourceContest = contestEntityService.getById(id);
        if (sourceContest == null) {
            throw new ContestNotExistException();
        }
        
        //创建新比赛（克隆）
        Contest newContest = BeanUtil.copyProperties(sourceContest, Contest.class);
        newContest.setId(null); //清空ID，让数据库自动生成
        newContest.setTitle(title); //使用用户输入的标题
        newContest.setSourceId(sourceContest.getId()); //记录源比赛ID
        
        //设置创建者为当前用户
        User user = SecurityUtils.getUser();
        newContest.setCreatorId(user.getId());
        newContest.setCreatorUsername(user.getUsername());
        
        //设置访问权限和密码
        newContest.setVisibility(visibility);
        newContest.setPassword(visibility == 1 ? password : null);
        
        //设置默认时间（明天开始，持续原有时长）
        LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        newContest.setStartTime(tomorrow);
        if (sourceContest.getDuration() != null && sourceContest.getDuration() > 0) {
            newContest.setEndTime(tomorrow.plusMinutes(sourceContest.getDuration()));
        } else {
            //如果原比赛没有时长，默认2小时
            newContest.setEndTime(tomorrow.plusHours(2));
            newContest.setDuration(120L);
        }
        
        boolean save = contestEntityService.save(newContest);
        if (!save) {
            throw new ContestException("克隆比赛失败，请稍后再试");
        }
        
        //克隆比赛题目关联
        List<ContestProblem> sourceProblems = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, id)
                .list();
        if (CollUtil.isNotEmpty(sourceProblems)) {
            List<ContestProblem> newProblems = sourceProblems.stream()
                    .map(problem -> {
                        ContestProblem newProblem = BeanUtil.copyProperties(problem, ContestProblem.class);
                        newProblem.setId(null);
                        newProblem.setContestId(newContest.getId());
                        return newProblem;
                    }).toList();
            contestProblemEntityService.saveBatch(newProblems);
        }
    }

    /**
     * 验证比赛时间
     * @param startTime 开始时间
     * @param endTime 结束时间
     */
    private void validateContestTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new ContestException("比赛开始时间和结束时间不能为空");
        }
        if (startTime.isAfter(endTime)) {
            throw new ContestException("比赛开始时间不能晚于结束时间");
        }
        if (endTime.isBefore(LocalDateTime.now().plusMinutes(10))) {
            throw new ContestException("比赛结束时间至少要在当前时间10分钟之后");
        }
    }

}
