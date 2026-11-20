package com.ymcloud.web.service.file;

import com.ymcloud.pojo.entity.ProblemCase;
import com.ymcloud.pojo.vo.admin.ProblemCaseListVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-19
 * @Description: 管理员发布测试点以及 Worker 读取已发布数据
 */
public interface ProblemCaseService {

    /**
     * 查询管理员可编辑的测试点
     * @param problemId 题目主键
     * @return 清单及发布版本，未发布时版本为空
     */
    ProblemCaseListVO list(Long problemId);

    /**
     * 上传并发布一批成对文件
     * @param problemId 题目主键
     * @param version 编辑基线
     * @param files 输入和输出文件
     * @return 已提交的版本
     */
    Long upload(Long problemId, Long version, MultipartFile[] files);

    /**
     * 移除选中的测试点，保留不可变文件
     * @param problemId 题目主键
     * @param version 编辑基线
     * @param names 待移除的测试点名称
     * @return 已提交的版本
     */
    Long delete(Long problemId, Long version, List<String> names);

    /**
     * 获取当前发布记录
     * @param problemId 题目主键
     * @return 当前记录，尚未发布时为空
     */
    ProblemCase current(Long problemId);

    /**
     * 为已认证的 Worker 或已授权的管理员打开题目测试文件
     * @param problemId 题目主键
     * @param fileId 不可变文件主键
     * @return 调用方负责关闭的输入流
     */
    InputStream openFile(Long problemId, Long fileId);

    /**
     * 打开管理员所管理题目的测试文件
     * @param problemId 题目主键
     * @param fileId 不可变文件主键
     * @return 调用方负责关闭的输入流
     */
    InputStream openManagedFile(Long problemId, Long fileId);
}
