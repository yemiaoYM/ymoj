package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.problem.ProblemException;
import com.ymcloud.common.exception.problem.ProblemNotExistException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.pojo.bo.ProblemCaseInfo;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.admin.ProblemVO;
import com.ymcloud.pojo.vo.common.PageVO;
import com.ymcloud.pojo.vo.oj.ProblemCaseVO;
import com.ymcloud.web.service.admin.AdminProblemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * @PackageName: com.ymcloud.web.service.admin.impl
 * @ClassName: AdminProblemServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-21  21:45
 * @Description: 管理端题目服务实现类
 */
@Slf4j
@Service
public class AdminProblemServiceImpl implements AdminProblemService {

    private static final long MAX_TEST_CASE_FILE_SIZE = 50L * 1024 * 1024;

    @Value("${ymcloud.file.base-dir}")
    private String fileUploadPath;

    @Autowired
    private ProblemEntityService problemEntityService;

    @Autowired
    private ProblemTagEntityService problemTagEntityService;

    /**
     * 分页查询题目列表
     * @param pageQuery 查询条件
     * @return 题目列表
     */
    @Override
    public PageVO<Problem> queryList(AdminProblemPageQuery pageQuery) {
        //创建分页实体
        Page<Problem> page = Page.of(pageQuery.getPageNo(), pageQuery.getPageSize());
        //条件分页查询题目列表（管理端可以查看所有可见性的题目）
        Page<Problem> record = problemEntityService.lambdaQuery()
                .like(StrUtil.isNotBlank(pageQuery.getKeyword()), Problem::getTitle, pageQuery.getKeyword())
                .or(StrUtil.isNotBlank(pageQuery.getKeyword()))
                .like(StrUtil.isNotBlank(pageQuery.getKeyword()), Problem::getProblemId, pageQuery.getKeyword())
                .eq(pageQuery.getDifficulty() != null, Problem::getDifficulty, pageQuery.getDifficulty())
                .eq(pageQuery.getVisibility() != null, Problem::getVisibility, pageQuery.getVisibility())
                .orderByDesc(Problem::getCreatedTime)
                .page(page);

        return PageVO.of(record, Problem.class);
    }

    /**
     * 根据ID查询题目详情
     * @param id 题目ID
     * @return 题目信息
     */
    @Override
    public ProblemVO getProblemById(Long id) {
        Problem problem = problemEntityService.getById(id);
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        ProblemVO problemVO = BeanUtil.copyProperties(problem, ProblemVO.class);
        //查询题目的题面样例,将json转化为对象
        List<ProblemCaseVO> problemCaseVOList = JSON.parseArray(problem.getExamples(), ProblemCaseVO.class);
        problemVO.setProblemCaseList(problemCaseVOList);
        //查询题目关联的标签ID列表
        List<Long> tagList = problemTagEntityService.lambdaQuery()
                .eq(ProblemTag::getProblemId, problem.getId())
                .list()
                .stream()
                .map(ProblemTag::getTagId)
                .toList();
        problemVO.setTagList(tagList);
        return problemVO;
    }

    /**
     * 添加题目
     * @param problemDTO 题目信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addProblem(ProblemDTO problemDTO) {
        //题目展示id需保证唯一
        String problemId = problemDTO.getProblemId();
        boolean exists = problemEntityService.lambdaQuery().eq(Problem::getProblemId, problemId).exists();
        if (exists) {
            throw new ProblemException("该题目展示id已被使用");
        }
        //构造实体
        Problem problem = BeanUtil.copyProperties(problemDTO, Problem.class);
        //题目创建者信息
        User user = SecurityUtils.getUser();
        problem.setAuthorId(user.getId());
        problem.setAuthorUsername(user.getUsername());
        //题目测试用例
        List<ProblemCaseVO> problemCaseList = problemDTO.getProblemCaseList();
        if (CollUtil.isNotEmpty(problemCaseList)) {
            String examples = JSON.toJSONString(problemCaseList);
            problem.setExamples(examples);
        }
        //将mb转化为kb
        problem.setMemoryLimit(problemDTO.getMemoryLimit()*1024);
        //暂不支持远程题目
        problem.setIsRemote(false);
        //如果没有设置可见性，默认为私有
        if (problem.getVisibility() == null) {
            problem.setVisibility(2);
        }
        boolean save = problemEntityService.save(problem);
        if (!save) {
            throw new ProblemException("添加题目失败，请稍后再试");
        }
        //题目标签
        List<Long> tagList = problemDTO.getTagList();
        if (CollUtil.isNotEmpty(tagList)) {
            List<ProblemTag> problemTags = tagList.stream()
                    .map(tagId -> {
                        ProblemTag problemTag = new ProblemTag();
                        problemTag.setProblemId(problem.getId());
                        problemTag.setTagId(tagId);
                        return problemTag;
                    }).toList();
            problemTagEntityService.saveBatch(problemTags);
        }
    }

    /**
     * 修改题目
     * @param id 题目ID
     * @param problemDTO 题目信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProblem(Long id, ProblemDTO problemDTO) {
        //检查题目是否存在
        Problem existProblem = problemEntityService.getById(id);
        if (existProblem == null) {
            throw new ProblemNotExistException();
        }
        //检查题目展示id是否重复（排除当前题目）
        String problemId = problemDTO.getProblemId();
        boolean exists = problemEntityService.lambdaQuery()
                .eq(Problem::getProblemId, problemId)
                .ne(Problem::getId, id)
                .exists();
        if (exists) {
            throw new ProblemException("该题目展示id已被使用");
        }
        //构造实体
        Problem problem = BeanUtil.copyProperties(problemDTO, Problem.class);
        problem.setId(id);
        problem.setMemoryLimit(problemDTO.getMemoryLimit()*1024);
        //保留原有的创建者信息
        problem.setAuthorId(existProblem.getAuthorId());
        problem.setAuthorUsername(existProblem.getAuthorUsername());
        //题目测试用例
        List<ProblemCaseVO> problemCaseList = problemDTO.getProblemCaseList();
        if (CollUtil.isNotEmpty(problemCaseList)) {
            String examples = JSON.toJSONString(problemCaseList);
            problem.setExamples(examples);
        }
        //暂不支持远程题目
        problem.setIsRemote(false);
        boolean update = problemEntityService.updateById(problem);
        if (!update) {
            throw new ProblemException("修改题目失败，请稍后再试");
        }
        //更新题目标签
        //先删除原有标签关联
        problemTagEntityService.lambdaUpdate()
                .eq(ProblemTag::getProblemId, id)
                .remove();
        //添加新的标签关联
        List<Long> tagList = problemDTO.getTagList();
        if (CollUtil.isNotEmpty(tagList)) {
            List<ProblemTag> problemTags = tagList.stream()
                    .map(tagId -> {
                        ProblemTag problemTag = new ProblemTag();
                        problemTag.setProblemId(id);
                        problemTag.setTagId(tagId);
                        return problemTag;
                    }).toList();
            problemTagEntityService.saveBatch(problemTags);
        }
    }

    /**
     * 根据ID删除题目
     * @param ids 题目ID列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProblemById(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            throw new ProblemException("题目id不能为空");
        }
        //检查题目是否存在
        List<Problem> problems = problemEntityService.listByIds(ids);
        if (problems.size() != ids.size()) {
            throw new ProblemException("部分题目不存在或已被删除");
        }
        //删除题目标签关联
        problemTagEntityService.lambdaUpdate()
                .in(ProblemTag::getProblemId, ids)
                .remove();
        //删除题目
        boolean remove = problemEntityService.removeByIds(ids);
        if (!remove) {
            throw new ProblemException("删除题目失败，请稍后再试");
        }
    }

    /**
     * 修改题目可见性
     * @param id 题目ID
     * @param visibility 可见性：1=公开，2=私有，3=比赛专用
     */
    @Override
    public void updateProblemVisibility(Long id, Integer visibility) {
        //检查可见性参数
        if (visibility == null || (visibility < 1 || visibility > 3)) {
            throw new ProblemException("可见性参数错误", HttpStatus.BAD_REQUEST);
        }
        //检查题目是否存在
        Problem problem = problemEntityService.getById(id);
        if (problem == null) {
            throw new ProblemNotExistException();
        }
        //更新可见性
        boolean update = problemEntityService.lambdaUpdate()
                .eq(Problem::getId, id)
                .set(Problem::getVisibility, visibility)
                .update();
        if (!update) {
            throw new ProblemException("修改题目可见性失败，请稍后再试");
        }
    }

    /**
     * 获取题目的测试样例文件列表
     * @param problemId 题目ID
     * @return 测试样例信息列表
     */
    @Override
    public List<ProblemCaseInfo> getTestCaseFiles(Long problemId) {
        //检查题目是否存在
        getProblemById(problemId);
        //构造题目测试样例目录路径
        String problemDir = fileUploadPath + "/private/problem/" + problemId;
        String infoJsonPath = problemDir + "/info.json";
        try {
            //检查info.json文件是否存在
            File infoFile = FileUtil.file(infoJsonPath);
            if (!infoFile.exists()) {
                return List.of();
            }
            //读取info.json文件内容
            String jsonContent = FileUtil.readUtf8String(infoFile);
            //解析JSON为ProblemCaseInfo列表
            return JSON.parseArray(jsonContent, ProblemCaseInfo.class);
        } catch (Exception e) {
            throw new ProblemException("读取测试样例配置文件失败",e);
        }
    }

    /**
     * 上传测试样例文件（支持单个或多个）
     * @param problemId 题目ID
     * @param files 文件数组
     */
    @Override
    public void uploadTestCaseFile(Long problemId, MultipartFile[] files) {
        //检查题目是否存在
        getProblemById(problemId);
        if (files == null || files.length == 0) {
            throw new ProblemException("请选择要上传的文件");
        }
        //构造题目测试样例目录路径
        String problemDir = fileUploadPath + "/private/problem/" + problemId;
        String inDir = problemDir + "/in";
        String outDir = problemDir + "/out";
        try {
            //检查目录是否存在
            FileUtil.mkdir(inDir);
            FileUtil.mkdir(outDir);
            Set<String> fileNames = new HashSet<>();
            //检查格式和参数
            for (MultipartFile file : files) {
                //检查文件是否为空
                if (file.isEmpty()) {
                    throw new ProblemException("文件不能为空");
                }
                //检查文件大小
                if (file.getSize() > MAX_TEST_CASE_FILE_SIZE) {
                    throw new ProblemException("单个文件大小不能超过50MB");
                }
                String filename = Paths.get(Objects.requireNonNull(file.getOriginalFilename())).getFileName().toString();
                //检查文件名是否为空
                if (StrUtil.isBlank(filename)) {
                    throw new ProblemException("文件名不能为空");
                }
                //检查文件扩展名
                String fileExtension = FileUtil.extName(filename);
                if (!"in".equals(fileExtension) && !"out".equals(fileExtension)) {
                    throw new ProblemException("只支持上传.in和.out格式的测试样例文件");
                }
                //case1.in case1.out
                if (!fileNames.add(filename)) {
                    throw new ProblemException("存在重名文件：" + filename);
                }
            }
            //检查是否配对
            for (String fileName : fileNames) {
                String baseName = FileUtil.mainName(fileName);
                String extName = FileUtil.extName(fileName);
                String pairName = null;
                if("in".equals(extName)){
                    pairName = baseName + ".out";
                }else{
                    pairName = baseName + ".in";
                }
                if(!fileNames.contains(pairName)){
                    throw new ProblemException("文件不配对，无法上传");
                }
            }
            //处理每个上传的文件
            for (MultipartFile file : files) {
                //确定保存路径
                String fileName = Paths.get(Objects.requireNonNull(file.getOriginalFilename())).getFileName().toString();
                String extName = FileUtil.extName(fileName);
                String saveDir = "in".equals(extName) ? inDir : outDir;
                String savePath = saveDir + "/" + fileName;
                //保存文件
                file.transferTo(new File(savePath));
            }
            //重新生成info.json文件
            generateInfoJson(problemId);
        } catch (ProblemException e) {
            throw e;
        } catch (Exception e) {
            throw new ProblemException("上传文件失败",e);
        }
    }

    /**
     * 下载测试样例文件
     * @param problemId 题目ID
     * @param fileName 文件名
     * @return 文件资源
     */
    @Override
    public ResponseEntity<Resource> downloadTestCaseFile(Long problemId, String fileName) {
        //检查题目是否存在
        getProblemById(problemId);
        //检查文件名格式
        if (StrUtil.isBlank(fileName)) {
            throw new ProblemException("文件名不能为空");
        }
        //构造文件路径
        String problemDir = fileUploadPath + "/private/problem/" + problemId;
        String filePath = problemDir + "/" + fileName;
        try {
            //检查是否存在
            File file = new File(filePath);
            if (!file.exists()) {
                throw new ProblemException("文件不存在" + fileName);
            }
            Resource resource = new FileSystemResource(file);
            //设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE);
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.length())
                    .body(resource);
        } catch (Exception e) {
            throw new ProblemException("下载文件失败", e);
        }
    }

    /**
     * 删除测试样例文件（支持单个或批量）
     * @param problemId 题目ID
     * @param fileNames 文件名数组
     */
    @Override
    public void deleteTestCaseFiles(Long problemId, List<String> fileNames) {
        //检查题目是否存在
        getProblemById(problemId);
        if (CollUtil.isEmpty(fileNames)) {
            throw new ProblemException("文件名不能为空");
        }
        //构造题目测试样例目录路径
        String problemDir = fileUploadPath + "/private/problem/" + problemId;
        try {
            //验证所有文件都存在且格式正确
            for (String fileName : fileNames) {
                if (StrUtil.isBlank(fileName)) {
                    throw new ProblemException("文件名不能为空");
                }
                String fileExtension = FileUtil.extName(fileName);
                if (!"in".equals(fileExtension) && !"out".equals(fileExtension)) {
                    throw new ProblemException("只能删除.in和.out格式的测试样例文件");
                }
                String filePath = problemDir + "/" + fileName;
                if (!FileUtil.exist(filePath)) {
                    throw new ProblemException("文件不存在");
                }
                //验证是否是成对的
                String baseName = FileUtil.mainName(fileName);
                String pairName = null;
                if(fileExtension.equals("in")){
                    pairName = "/out/" + baseName + ".out";
                }else{
                    pairName = "/in/" + baseName + ".in";
                }
                if(!fileNames.contains(pairName)){
                    throw new ProblemException("文件不配对，无法删除");
                }
            }
            //执行删除操作
            for (String fileName : fileNames) {
                String filePath = problemDir + "/" + fileName;
                FileUtil.del(filePath);
            }
            //重新生成info.json文件
            generateInfoJson(problemId);
        }catch (Exception e) {
            throw new ProblemException("删除文件失败",e);
        }
    }

    /**
     * 生成或更新info.json文件
     * @param problemId 题目ID
     */
    private void generateInfoJson(Long problemId) {
        String problemDir = fileUploadPath + "/private/problem/" + problemId;
        String inDir = problemDir + "/in";
        String outDir = problemDir + "/out";
        try {
            //获取输入文件列表
            File inDirectory = new File(inDir);
            File outDirectory = new File(outDir);
            if (!inDirectory.exists() || !outDirectory.exists()) {
                return;
            }
            //获取所有.in文件
            File[] inFiles = inDirectory.listFiles((dir, name) -> name.endsWith(".in"));
            if (inFiles == null) {
                inFiles = new File[0];
            }
            List<ProblemCaseInfo> caseInfos = new ArrayList<>();
            //为每个输入文件创建测试样例信息
            int index = 1;
            for (File inFile : inFiles) {
                String baseName = FileUtil.mainName(inFile.getName()); //获取不带扩展名的文件名
                String outFileName = baseName + ".out";
                File outFile = new File(outDir + "/" + outFileName);
                //只有当对应的输出文件也存在时，才添加到配置中
                if (outFile.exists()) {
                    ProblemCaseInfo caseInfo = new ProblemCaseInfo();
                    caseInfo.setId(index++); // 使用递增的索引作为ID
                    caseInfo.setInput("/in/" + inFile.getName());
                    caseInfo.setOutput("/out/" + outFileName);
                    caseInfos.add(caseInfo);
                }
            }
            //按文件名排序（保证顺序一致性）
            caseInfos.sort(Comparator.comparing(ProblemCaseInfo::getInput));
            //写入info.json文件
            String infoJsonPath = problemDir + "/info.json";
            String jsonContent = JSON.toJSONString(caseInfos);
            FileUtil.writeUtf8String(jsonContent, infoJsonPath);
        } catch (Exception e) {
            throw new ProblemException("生成测试样例配置文件失败",e);
        }
    }
    
}
