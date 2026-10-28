package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.contest.ContestException;
import com.ymcloud.common.exception.contest.ContestNotExistException;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.ContestProblemEntityService;
import com.ymcloud.core.web.dao.ContestUserEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.dto.admin.ContestDTO;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.ContestProblem;
import com.ymcloud.pojo.entity.ContestUser;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.query.admin.AdminContestPageQuery;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.ContestProblemTableVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.web.service.admin.AdminContestService;
import com.ymcloud.web.service.common.PublicImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
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

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private PublicImageService publicImages;

    @Autowired
    private StoredFileService storedFiles;

    /**
     * 上传封面并返回统一公开地址，文件先独立保存供表单草稿引用。
     * @param file 经过前端裁剪的 JPEG 或 PNG
     * @return 已登记的公开图片地址
     */
    @Override
    public String uploadImage(MultipartFile file) {
        return publicImages.upload(file, "contest.png", 1, 1).getUrl();
    }

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
        validateContestImage(contestDTO.getImage());
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
        validateContestImage(contestDTO.getImage());
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
        //获取比赛中已有的题目ID列表
        List<Long> existingProblemIds = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .list()
                .stream()
                .map(ContestProblem::getProblemId)
                .toList();
        //创建分页查询
        Page<Problem> page = Page.of(pageQuery.getPageNo(), pageQuery.getPageSize());
        //构建查询条件
        Page<Problem> result = problemEntityService.lambdaQuery()
                .like(StrUtil.isNotBlank(pageQuery.getKeyword()), Problem::getTitle, pageQuery.getKeyword())
                .or(StrUtil.isNotBlank(pageQuery.getKeyword()))
                .like(StrUtil.isNotBlank(pageQuery.getKeyword()), Problem::getProblemId, pageQuery.getKeyword())
                .eq(pageQuery.getDifficulty() != null, Problem::getDifficulty, pageQuery.getDifficulty())
                .notIn(CollUtil.isNotEmpty(existingProblemIds), Problem::getId, existingProblemIds) //排除已添加的题目
                .orderByDesc(Problem::getCreatedTime)
                .page(page);
        return PageVO.of(result, Problem.class);
    }

    /**
     * 批量添加题目到比赛（会将题目权限设置为比赛专用）
     * @param contestId 比赛ID
     * @param problemIds 题目ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addProblemsToContest(Long contestId, List<Long> problemIds) {
        //参数验证
        if (CollUtil.isEmpty(problemIds)) {
            throw new ContestException("题目ID列表不能为空", HttpStatus.BAD_REQUEST);
        }
        if (problemIds.size() > 20) {
            throw new ContestException("一次最多只能添加20道题目", HttpStatus.BAD_REQUEST);
        }
        //检查比赛是否存在
        Contest contest = contestEntityService.getById(contestId);
        if (contest == null) {
            throw new ContestNotExistException();
        }
        //检查题目是否存在
        List<Problem> problems = problemEntityService.listByIds(problemIds);
        if (problems.size() != problemIds.size()) {
            throw new ContestException("部分题目不存在或已被删除");
        }
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
        boolean updateVisibility = problemEntityService.lambdaUpdate()
                .in(Problem::getId, problemIds)
                .set(Problem::getVisibility, 3) //3=比赛专用
                .update();
        if (!updateVisibility) {
            throw new ContestException("更新题目权限失败，请稍后再试");
        }
        //获取当前比赛题目的最大展示ID
        ContestProblem maxContestProblem = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .orderByDesc(ContestProblem::getDisplayId)
                .last("LIMIT 1")
                .one();
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
     * 更新比赛题目信息
     * @param contestId 比赛ID
     * @param problemId 题目ID
     * @param displayId 展示ID（如：A, B, C）
     * @param displayTitle 在比赛中显示的题目标题
     * @param color 气球颜色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContestProblem(Long contestId, Long problemId, String displayId, String displayTitle, String color) {
        //参数验证
        if (StrUtil.isBlank(displayId)) {
            throw new ContestException("展示ID不能为空", HttpStatus.BAD_REQUEST);
        }
        if (displayId.length() > 5) {
            throw new ContestException("展示ID长度不能超过5个字符", HttpStatus.BAD_REQUEST);
        }
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
        Contest contest = contestEntityService.getById(contestId);
        if (contest == null) {
            throw new ContestNotExistException();
        }
        //检查比赛题目关联是否存在
        ContestProblem contestProblem = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .eq(ContestProblem::getId, problemId)
                .one();
        if (contestProblem == null) {
            throw new ContestException("该题目不在比赛中");
        }
        //检查展示ID是否与其他题目冲突
        ContestProblem existingProblem = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .eq(ContestProblem::getDisplayId, displayId)
                .ne(ContestProblem::getId, contestProblem.getId())
                .one();
        if (existingProblem != null) {
            throw new ContestException("展示ID已被其他题目使用，请选择其他ID");
        }
        //更新题目信息
        boolean update = contestProblemEntityService.lambdaUpdate()
                .eq(ContestProblem::getId, contestProblem.getId())
                .set(ContestProblem::getDisplayId, displayId)
                .set(ContestProblem::getDisplayTitle, displayTitle)
                .set(ContestProblem::getColor, color)
                .update();
        if (!update) {
            throw new ContestException("更新题目信息失败，请稍后再试");
        }
    }

    /**
     * 从比赛中移除题目（会将题目权限设置为私有）
     * @param contestId 比赛ID
     * @param contestProblemIds 题目ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeContestProblem(Long contestId, List<Long> contestProblemIds) {
        //参数验证
        if (CollUtil.isEmpty(contestProblemIds)) {
            throw new ContestException("题目ID列表不能为空", HttpStatus.BAD_REQUEST);
        }
        //检查比赛是否存在
        Contest contest = contestEntityService.getById(contestId);
        if (contest == null) {
            throw new ContestNotExistException();
        }
        //检查题目是否在比赛中
        List<ContestProblem> contestProblems = contestProblemEntityService.lambdaQuery()
                .eq(ContestProblem::getContestId, contestId)
                .in(ContestProblem::getId, contestProblemIds)
                .list();
        if (contestProblems.size() != contestProblemIds.size()) {
            throw new ContestException("部分题目不在比赛中");
        }
        //删除比赛题目关联
        List<Long> ids = contestProblems.stream()
                .map(ContestProblem::getId)
                .toList();
        boolean remove = contestProblemEntityService.removeByIds(ids);
        if (!remove) {
            throw new ContestException("移除题目失败，请稍后再试");
        }
        //将题目权限设置为私有，等待管理员手动公开
        List<Long> problemIds = contestProblems.stream()
                .map(ContestProblem::getProblemId)
                .toList();
        boolean updateVisibility = problemEntityService.lambdaUpdate()
                .in(Problem::getId, problemIds)
                .set(Problem::getVisibility, 2) //2=私有
                .update();
        if (!updateVisibility) {
            throw new ContestException("更新题目权限失败，请稍后再试");
        }
    }

    /**
     * 非空封面必须引用统一存储中的公开图片，不能通过比赛暴露私有文件。
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
        if (startTime.isAfter(endTime)) {
            throw new ContestException("比赛开始时间不能晚于结束时间");
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
