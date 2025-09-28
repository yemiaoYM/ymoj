package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.bo.ProblemCaseInfo;
import com.ymcloud.pojo.dto.admin.ProblemDTO;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.query.admin.AdminProblemPageQuery;
import com.ymcloud.pojo.vo.common.PageVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin
 * @ClassName: AdminProblemService
 * @Author: Yemiao
 * @CreateTime: 2025-09-21  21:35
 * @Description: 管理端题目服务接口
 */
public interface AdminProblemService {

    /**
     * 分页查询题目列表
     * @param pageQuery 查询条件
     * @return 题目列表
     */
    PageVO<Problem> queryList(AdminProblemPageQuery pageQuery);

    /**
     * 根据ID查询题目详情
     * @param id 题目ID
     * @return 题目信息
     */
    Problem getProblemById(Long id);

    /**
     * 添加题目
     * @param problemDTO 题目信息
     */
    void addProblem(ProblemDTO problemDTO);

    /**
     * 修改题目
     * @param id 题目ID
     * @param problemDTO 题目信息
     */
    void updateProblem(Long id, ProblemDTO problemDTO);

    /**
     * 根据ID删除题目
     * @param ids 题目ID列表
     */
    void deleteProblemById(List<Long> ids);

    /**
     * 修改题目可见性
     * @param id 题目ID
     * @param visibility 可见性：1=公开，2=私有，3=比赛专用
     */
    void updateProblemVisibility(Long id, Integer visibility);

    /**
     * 获取题目的测试样例文件列表
     * @param problemId 题目ID
     * @return 测试样例信息列表
     */
    List<ProblemCaseInfo> getTestCaseFiles(Long problemId);

    /**
     * 上传测试样例文件（支持单个或多个）
     * @param problemId 题目ID
     * @param files 文件数组
     */
    void uploadTestCaseFile(Long problemId, MultipartFile[] files);

    /**
     * 下载测试样例文件
     * @param problemId 题目ID
     * @param fileName 文件名
     * @return 文件资源
     */
    ResponseEntity<Resource> downloadTestCaseFile(Long problemId, String fileName);

    /**
     * 删除测试样例文件
     * @param problemId 题目ID
     * @param fileName 文件名
     */
    void deleteTestCaseFile(Long problemId, String fileName);
}
