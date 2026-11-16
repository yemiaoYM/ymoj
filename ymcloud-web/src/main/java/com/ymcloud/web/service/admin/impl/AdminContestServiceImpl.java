package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.contest.ContestNotExistException;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.service.ResourceAccessService;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.JudgeEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.dto.admin.CloneContestDTO;
import com.ymcloud.pojo.dto.admin.ContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Judge;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminContestPageQuery;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.AdminContestVO;
import com.ymcloud.pojo.vo.admin.ContestProblemTableVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminContestService;
import com.ymcloud.web.service.admin.ResourceDeletionService;
import com.ymcloud.web.service.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  19:09
 * @Description: 管理端比赛服务实现类
 */
@Service
public class AdminContestServiceImpl implements AdminContestService {

    private static final int MAX_CONTEST_PROBLEMS = 15;

    @Autowired
    private ResourceAccessService resourceAccess;

    @Autowired
    private ResourceDeletionService deletion;

    @Autowired
    private ContestEntityService contestEntityService;

    @Autowired
    private JudgeEntityService judges;

    @Autowired
    private ContestProblemEntityService contestProblemEntityService;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private FileService storedFiles;

    /**
     * 分页查询比赛列表
     * @param pageQuery 查询条件
     * @return 比赛列表
     */
    @Override
    public PageVO<Contest> queryList(AdminContestPageQuery pageQuery) {
        resourceAccess.requireContestManager();
        //创建分页实体
        Page<Contest> page = Page.of(pageQuery.getPageNo(), pageQuery.getPageSize());
        //条件分页查询比赛列表
        Page<Contest> record = contestEntityService.lambdaQuery()
                .eq(!SecurityUtils.isAdmin(), Contest::getCreatorId, SecurityUtils.getUserId())
                .and(StrUtil.isNotBlank(pageQuery.getKeyword()), query -> query
                        .like(Contest::getTitle, pageQuery.getKeyword())
                        .or()
                        .like(Contest::getCreatorUsername, pageQuery.getKeyword()))
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
    public AdminContestVO getContestById(Long id) {
        Contest contest = contestEntityService.getById(id);
        if (contest == null) {
            throw new ContestNotExistException();
        }
        resourceAccess.requireManageContest(contest);
        AdminContestVO view = BeanUtil.copyProperties(contest, AdminContestVO.class);
        view.setStructureLocked(!LocalDateTime.now().isBefore(contest.getStartTime())
                || judges.lambdaQuery()
                .eq(Judge::getContestId, id)
                .exists());
        return view;
    }

    /**
     * 添加比赛
     * @param contestDTO 比赛信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addContest(ContestDTO contestDTO) {
        resourceAccess.requireContestManager();
        //验证比赛时间
        validateContestTime(contestDTO.getStartTime(), contestDTO.getEndTime());
        validateContestImage(contestDTO.getImage());
        //构造实体
        Contest contest = BeanUtil.copyProperties(contestDTO, Contest.class);
        contest.setSourceId(ContestConstants.ORIGINAL_SOURCE_ID);
        if (contest.getVisibility() != ContestConstants.VISIBILITY_PASSWORD) {
            contest.setPassword(null);
        }
        // 如果是密码比赛，必须设置密码
        if (contest.getVisibility() == ContestConstants.VISIBILITY_PASSWORD && StrUtil.isBlank(contest.getPassword())) {
            throw new ContestException("密码比赛必须设置密码", HttpStatus.BAD_REQUEST);
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
        Contest existContest = lockContest(id);
        if (!Objects.equals(existContest.getMode(), contestDTO.getMode())
                || !Objects.equals(existContest.getStartTime(), contestDTO.getStartTime())) {
            requireStructureEditable(existContest);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(existContest.getEndTime()) && contestDTO.getEndTime().isAfter(now)) {
            throw new ContestException("已结束比赛不能重新开放提交", HttpStatus.BAD_REQUEST);
        }
        //验证比赛时间
        validateContestTime(contestDTO.getStartTime(), contestDTO.getEndTime());
        validateContestImage(contestDTO.getImage());
        //修改字段
        Contest contest = BeanUtil.copyProperties(contestDTO, Contest.class);
        contest.setId(id);
        contest.setSourceId(existContest.getSourceId());
        if (contest.getVisibility() != ContestConstants.VISIBILITY_PASSWORD) {
            contest.setPassword(null);
        }
        //保留原有的创建者信息
        contest.setCreatorId(existContest.getCreatorId());
        contest.setCreatorUsername(existContest.getCreatorUsername());
        // 如果是密码比赛，必须设置密码
        if (contest.getVisibility() == ContestConstants.VISIBILITY_PASSWORD && StrUtil.isBlank(contest.getPassword())) {
            throw new ContestException("密码比赛必须设置密码", HttpStatus.BAD_REQUEST);
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
    public void deleteContestById(List<Long> ids) {
        deletion.deleteContests(ids);
    }

    /**
     * 更新比赛可见性
     * @param id 比赛ID
     * @param visibility 可见性：0=公开，1=密码，2=私有
     * @param password 密码（密码比赛时必填）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContestVisibility(Long id, Integer visibility, String password) {
        //检查可见性参数
        if (visibility == null || (visibility < ContestConstants.VISIBILITY_PUBLIC || visibility > ContestConstants.VISIBILITY_PRIVATE)) {
            throw new ContestException("可见性参数错误", HttpStatus.BAD_REQUEST);
        }
        //检查比赛是否存在
        lockContest(id);
        // 密码比赛必须设置密码
        if (visibility == ContestConstants.VISIBILITY_PASSWORD) {
            if (StrUtil.isBlank(password)) {
                throw new ContestException("密码比赛必须设置密码", HttpStatus.BAD_REQUEST);
            }
            if (password.length() < 5 || password.length() > 20) {
                throw new ContestException("密码长度必须在5-20位之间", HttpStatus.BAD_REQUEST);
            }
        }
        //更新可见性和密码
        boolean update = contestEntityService.lambdaUpdate()
                .eq(Contest::getId, id)
                .set(Contest::getVisibility, visibility)
                .set(Contest::getPassword, visibility == ContestConstants.VISIBILITY_PASSWORD ? password : null)
                .update();
        if (!update) {
            throw new ContestException("修改比赛可见性失败，请稍后再试");
        }
    }

    /**
     * 克隆比赛
     * @param id 源比赛ID
     * @param request 新比赛标题、访问类型、密码和时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void cloneContest(Long id, CloneContestDTO request) {
        resourceAccess.requireContestManager();
        validateContestTime(request.getStartTime(), request.getEndTime());
        Integer visibility = request.getVisibility();
        String password = request.getPassword();
        // 密码比赛必须设置密码
        if (visibility == ContestConstants.VISIBILITY_PASSWORD) {
            if (StrUtil.isBlank(password)) {
                throw new ContestException("密码比赛必须设置密码", HttpStatus.BAD_REQUEST);
            }
            if (password.length() < 5 || password.length() > 20) {
                throw new ContestException("密码长度必须在5-20位之间", HttpStatus.BAD_REQUEST);
            }
        }
        
        //检查源比赛是否存在
        Contest sourceContest = contestEntityService.lambdaQuery()
                .eq(Contest::getId, id)
                .last("FOR UPDATE")
                .one();
        if (sourceContest == null) {
            throw new ContestNotExistException();
        }
        if (!resourceAccess.canManageContest(sourceContest)) {
            resourceAccess.requireContestAccess(sourceContest);
            if (LocalDateTime.now().isBefore(sourceContest.getEndTime())) {
                throw new ContestException("只能克隆本人比赛或已结束且有权访问的比赛", HttpStatus.FORBIDDEN);
            }
        }
        
        //创建新比赛（克隆）
        Contest newContest = BeanUtil.copyProperties(sourceContest, Contest.class);
        newContest.setId(null); //清空ID，让数据库自动生成
        newContest.setTitle(request.getTitle());
        newContest.setSourceId(sourceContest.getId()); //记录源比赛ID
        
        //设置创建者为当前用户
        User user = SecurityUtils.getUser();
        newContest.setCreatorId(user.getId());
        newContest.setCreatorUsername(user.getUsername());
        
        //设置访问权限和密码
        newContest.setVisibility(visibility);
        newContest.setPassword(visibility == ContestConstants.VISIBILITY_PASSWORD ? password : null);
        
        newContest.setStartTime(request.getStartTime());
        newContest.setEndTime(request.getEndTime());
        newContest.setDuration(LocalDateTimeUtil.between(request.getStartTime(), request.getEndTime()).toMinutes());
        newContest.setCreatedTime(null);
        newContest.setUpdatedTime(null);
        
        boolean save = contestEntityService.save(newContest);
        if (!save) {
            throw new ContestException("克隆比赛失败，请稍后再试");
        }
        
        //克隆比赛题目关联
        List<ContestProblem> sourceProblems = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, id)
                .list();
        if (sourceProblems.size() > MAX_CONTEST_PROBLEMS) {
            throw new ContestException("一场比赛最多15道题目，无法克隆", HttpStatus.BAD_REQUEST);
        }
        if (CollUtil.isNotEmpty(sourceProblems)) {
            List<Problem> originals = problemEntityService.lambdaQuery()
                    .in(Problem::getId, sourceProblems.stream().map(ContestProblem::getProblemId).toList())
                    .orderByAsc(Problem::getId)
                    .last("FOR UPDATE")
                    .list();
            resourceAccess.requireUseProblems(originals);
            List<ContestProblem> newProblems = sourceProblems.stream()
                    .map(problem -> {
                        ContestProblem newProblem = BeanUtil.copyProperties(problem, ContestProblem.class);
                        newProblem.setId(null);
                        newProblem.setContestId(newContest.getId());
                        return newProblem;
                    }).toList();
            if (!contestProblemEntityService.saveBatch(newProblems)) {
                throw new ContestException("克隆题目关联失败，操作已回滚");
            }
        }
    }

    /**
     * 获取比赛题目列表
     * @param contestId 比赛ID
     * @return 比赛题目列表
     */
    @Override
    public List<ContestProblemTableVO> getContestProblems(Long contestId) {
        //检查比赛是否存在
        Contest contest = contestEntityService.getById(contestId);
        if (contest == null) {
            throw new ContestNotExistException();
        }
        resourceAccess.requireManageContest(contest);
        //查询比赛题目关联表
        List<ContestProblem> contestProblems = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .orderByAsc(ContestProblem::getDisplayId)
                .list();
        if (CollUtil.isEmpty(contestProblems)) {
            return CollUtil.newArrayList();
        }
        //获取题目ID列表
        List<Long> problemIds = contestProblems.stream()
                .map(ContestProblem::getProblemId)
                .toList();
        //批量查询题目信息
        List<Problem> problems = problemEntityService.listByIds(problemIds);
        if (CollUtil.isEmpty(problems)) {
            return CollUtil.newArrayList();
        }
        //构建题目ID到题目信息的映射
        Map<Long, Problem> problemMap = problems.stream()
                .collect(Collectors.toMap(Problem::getId, problem -> problem));
        //构建返回结果
        return contestProblems.stream()
                .map(contestProblem -> {
                    ContestProblemTableVO vo = new ContestProblemTableVO();
                    vo.setId(contestProblem.getId());
                    vo.setDisplayId(contestProblem.getDisplayId());
                    vo.setDisplayTitle(contestProblem.getDisplayTitle());
                    vo.setColor(contestProblem.getColor());
                    vo.setProblemId(contestProblem.getProblemId());
                    
                    Problem problem = problemMap.get(contestProblem.getProblemId());
                    if (problem != null) {
                        vo.setProblemDisplayId(problem.getProblemId());
                        vo.setProblemDisplayTitle(problem.getTitle());
                        vo.setAuthorUsername(problem.getAuthorUsername());
                    }
                    return vo;
                }).toList();
    }

    /**
     * 获取可添加到比赛的题目列表（包括公开和私有题目）
     * @param contestId 比赛ID
     * @param pageQuery 查询条件
     * @return 可添加的题目列表
     */
    @Override
    public PageVO<Problem> getAvailableProblems(Long contestId, AdminProblemPageQuery pageQuery) {
        //检查比赛是否存在
        Contest contest = contestEntityService.getById(contestId);
        if (contest == null) {
            throw new ContestNotExistException();
        }
        resourceAccess.requireManageContest(contest);
        Page<Problem> page = Page.of(pageQuery.getPageNo(), pageQuery.getPageSize());
        Page<Problem> result = problemEntityService.pageAvailableForContest(
                page, pageQuery, contestId, resourceAccess.problemScope());
        return PageVO.of(result, Problem.class);
    }

    /**
     * 未锁定比赛可批量选题
     * 仅非克隆赛将原题设为比赛专属
     * @param contestId 比赛ID
     * @param problemIds 题目ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void addProblemsToContest(Long contestId, List<Long> problemIds) {
        //参数验证
        if (CollUtil.isEmpty(problemIds)) {
            throw new ContestException("题目ID列表不能为空", HttpStatus.BAD_REQUEST);
        }
        if (problemIds.size() > MAX_CONTEST_PROBLEMS) {
            throw new ContestException("一场比赛最多15道题目", HttpStatus.BAD_REQUEST);
        }
        //检查比赛是否存在
        Contest contest = lockContest(contestId);
        requireStructureEditable(contest);
        long count = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .count();
        if (count + problemIds.size() > MAX_CONTEST_PROBLEMS) {
            throw new ContestException("一场比赛最多15道题目", HttpStatus.BAD_REQUEST);
        }
        //检查题目是否存在
        List<Problem> problems = problemEntityService.lambdaQuery()
                .in(Problem::getId, problemIds)
                .orderByAsc(Problem::getId)
                .last("FOR UPDATE")
                .list();
        if (problems.size() != problemIds.size()) {
            throw new ContestException("部分题目不存在或已被删除");
        }
        resourceAccess.requireUseProblems(problems);
        //检查题目是否已经在比赛中
        List<Long> existingProblemIds = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .in(ContestProblem::getProblemId, problemIds)
                .list()
                .stream()
                .map(ContestProblem::getProblemId)
                .toList();
        if (CollUtil.isNotEmpty(existingProblemIds)) {
            throw new ContestException("部分题目已经在比赛中，请勿重复添加");
        }
        //将题目权限设置为比赛专用
        if (contest.getSourceId() == ContestConstants.ORIGINAL_SOURCE_ID) {
            problemEntityService.lambdaUpdate()
                    .in(Problem::getId, problemIds)
                    .set(Problem::getVisibility, ProblemConstants.CONTEST_PROBLEM)
                    .update();
        }
        //获取当前比赛题目的最大展示ID
        ContestProblem maxContestProblem = contestProblemEntityService.getLastDisplayProblem(contestId);
        String maxDisplayId = maxContestProblem != null ? maxContestProblem.getDisplayId() : null;
        //生成新的展示ID起始值
        int startIndex = 0;
        if (StrUtil.isNotBlank(maxDisplayId)) {
            //如果最大展示ID是字母，转换为数字
            if (maxDisplayId.length() == 1 && Character.isLetter(maxDisplayId.charAt(0))) {
                startIndex = maxDisplayId.charAt(0) - 'A' + 1;
            }
        }
        //构建比赛题目关联记录
        List<ContestProblem> contestProblems = new java.util.ArrayList<>();
        Map<Long, Problem> problemMap = problems.stream()
                .collect(Collectors.toMap(Problem::getId, problem -> problem));
        
        for (int i = 0; i < problemIds.size(); i++) {
            Long problemId = problemIds.get(i);
            Problem problem = problemMap.get(problemId);
            
            ContestProblem contestProblem = new ContestProblem();
            contestProblem.setContestId(contestId);
            contestProblem.setProblemId(problemId);
            //生成展示ID（A, B, C...）
            contestProblem.setDisplayId(String.valueOf((char) ('A' + startIndex + i)));
            contestProblem.setDisplayTitle(problem.getTitle()); //默认使用原题目标题
            contestProblem.setColor(generateRandomColor()); //生成随机颜色
            
            contestProblems.add(contestProblem);
        }
        
        //批量保存
        boolean save = contestProblemEntityService.saveBatch(contestProblems);
        if (!save) {
            throw new ContestException("添加题目到比赛失败，请稍后再试");
        }
    }

    /**
     * 更新比赛题目的标题和气球颜色，保留当前展示 ID
     * @param contestId 比赛ID
     * @param problemId 比赛题目关联 ID
     * @param displayTitle 在比赛中显示的题目标题
     * @param color 气球颜色
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void updateContestProblem(Long contestId, Long problemId, String displayTitle, String color) {
        //参数验证
        if (StrUtil.isBlank(displayTitle)) {
            throw new ContestException("题目标题不能为空", HttpStatus.BAD_REQUEST);
        }
        if (displayTitle.length() > 100) {
            throw new ContestException("题目标题长度不能超过100个字符", HttpStatus.BAD_REQUEST);
        }
        if (StrUtil.isBlank(color)) {
            throw new ContestException("气球颜色不能为空", HttpStatus.BAD_REQUEST);
        }
        if (!color.matches("^#[0-9a-fA-F]{6}$")) {
            throw new ContestException("气球颜色格式不正确，请使用十六进制颜色代码", HttpStatus.BAD_REQUEST);
        }
        //检查比赛是否存在
        lockContest(contestId);
        //检查比赛题目关联是否存在
        ContestProblem contestProblem = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .eq(ContestProblem::getId, problemId)
                .one();
        if (contestProblem == null) {
            throw new ContestException("该题目不在比赛中");
        }
        //更新题目信息
        boolean update = contestProblemEntityService.lambdaUpdate()
                .eq(ContestProblem::getId, contestProblem.getId())
                .set(ContestProblem::getDisplayTitle, displayTitle)
                .set(ContestProblem::getColor, color)
                .update();
        if (!update) {
            throw new ContestException("更新题目信息失败，请稍后再试");
        }
    }

    /**
     * 从未锁定比赛移除题目，非克隆赛按剩余引用释放专属状态
     * @param contestId 比赛ID
     * @param contestProblemIds 题目ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void removeContestProblem(Long contestId, List<Long> contestProblemIds) {
        //参数验证
        if (CollUtil.isEmpty(contestProblemIds)) {
            throw new ContestException("题目ID列表不能为空", HttpStatus.BAD_REQUEST);
        }
        //检查比赛是否存在
        Contest contest = lockContest(contestId);
        requireStructureEditable(contest);
        //检查题目是否在比赛中
        List<ContestProblem> contestProblems = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .in(ContestProblem::getId, contestProblemIds)
                .list();
        if (contestProblems.size() != contestProblemIds.size()) {
            throw new ContestException("部分题目不在比赛中");
        }
        List<Long> problemIds = contestProblems.stream()
                .map(ContestProblem::getProblemId)
                .toList();
        problemEntityService.lambdaQuery()
                .in(Problem::getId, problemIds)
                .orderByAsc(Problem::getId)
                .last("FOR UPDATE")
                .list();
        //删除比赛题目关联
        List<Long> ids = contestProblems.stream()
                .map(ContestProblem::getId)
                .toList();
        boolean remove = contestProblemEntityService.removeByIds(ids);
        if (!remove) {
            throw new ContestException("移除题目失败，请稍后再试");
        }
        if (contest.getSourceId() == ContestConstants.ORIGINAL_SOURCE_ID) {
            problemEntityService.releaseContestProblems(problemIds, LocalDateTime.now());
        }
    }

    /**
     * 将完整题目列表按指定顺序编号，仅允许未开始且没有提交的比赛
     * @param contestId 比赛 ID
     * @param ids 按目标顺序排列的比赛题目关联 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void reorderProblems(Long contestId, List<Long> ids) {
        if (ids == null || ids.isEmpty()
                        || ids.size() > MAX_CONTEST_PROBLEMS
                        || ids.stream().anyMatch(Objects::isNull)
                        || new HashSet<>(ids).size() != ids.size()) {
            throw new ContestException("题目顺序参数无效", HttpStatus.BAD_REQUEST);
        }
        Contest contest = lockContest(contestId);
        requireStructureEditable(contest);
        List<ContestProblem> current = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .list();
        if (!new HashSet<>(ids).equals(current.stream().map(ContestProblem::getId).collect(Collectors.toSet()))) {
            throw new ContestException("比赛题目已变化，请刷新后调整", HttpStatus.CONFLICT);
        }
        List<ContestProblem> updates = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            ContestProblem problem = new ContestProblem();
            problem.setId(ids.get(i));
            problem.setDisplayId(String.valueOf((char) ('A' + i)));
            updates.add(problem);
        }
        contestProblemEntityService.updateDisplayIds(contestId, updates);
    }

    /**
     * 比赛开始或产生提交后锁定赛制、开始时间和题目结构，调用方须先锁定比赛
     * @param contest 已锁定的比赛
     */
    private void requireStructureEditable(Contest contest) {
        if (!LocalDateTime.now().isBefore(contest.getStartTime())) {
            throw new ContestException("比赛已开始，不能调整赛制、开始时间或题目结构", HttpStatus.BAD_REQUEST);
        }
        if (judges.lambdaQuery()
                .eq(Judge::getContestId, contest.getId())
                .exists()) {
            throw new ContestException("比赛已有提交，不能调整赛制、开始时间或题目结构", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 锁定比赛，串行化题目数量及展示编号的修改
     * @param contestId 比赛 ID
     * @return 已锁定的比赛
     */
    private Contest lockContest(Long contestId) {
        Contest contest = contestEntityService.lambdaQuery()
                .eq(Contest::getId, contestId)
                .last("FOR UPDATE")
                .one();
        if (contest == null) {
            throw new ContestNotExistException();
        }
        resourceAccess.requireManageContest(contest);
        return contest;
    }

    /**
     * 非空封面必须引用统一存储中的公开图片，不能通过比赛暴露私有文件
     * @param image 封面地址；null 表示使用默认图
     */
    private void validateContestImage(String image) {
        if (image == null) {
            return;
        }
        File file = storedFiles.resolvePublicUrl(image);
        if (file == null || file.getBizType() != FileBizType.IMAGE) {
            throw new FileException("封面不存在或不是公开图片，请重新上传", HttpStatus.BAD_REQUEST);
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
        if (!startTime.isBefore(endTime)) {
            throw new ContestException("比赛结束时间必须晚于开始时间");
        }
    }

    /**
     * 生成随机颜色
     * @return 十六进制颜色代码
     */
    private String generateRandomColor() {
        String[] colors = {
            "#ff4757", "#ff6348", "#ffa502", "#ffb142", "#f1c40f",
            "#2ed573", "#7bed9f", "#70a1ff", "#5352ed", "#a4b0be",
            "#ff3838", "#ff9ff3", "#54a0ff", "#5f27cd", "#00d2d3",
            "#ff9f43", "#10ac84", "#ee5a24", "#0abde3", "#3867d6"
        };
        return colors[new java.util.Random().nextInt(colors.length)];
    }

}
