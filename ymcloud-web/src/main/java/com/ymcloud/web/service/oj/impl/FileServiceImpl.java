package com.ymcloud.web.service.oj.impl;

import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.ProblemConstants;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.utils.RedisCache;
import com.ymcloud.core.web.dao.ContestEntityService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.ProblemEntityService;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.Problem;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.file.StoredFileService;
import com.ymcloud.web.service.oj.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * @Author: Yemiao
 * @CreateTime: 2025-09-14
 * @Description: 保持原业务接口，存储位置由默认存储配置决定
 */
@Service
public class FileServiceImpl implements FileService {

    @Autowired
    private StoredFileService storedFiles;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private FileEntityService fileEntityService;

    @Autowired
    private ProblemEntityService problems;

    @Autowired
    private ContestEntityService contests;

    /**
     * 校验公开路径和文件记录，拒绝私有文件及测试数据。
     * @param path 公开 URL 前缀后的路径，包含开头的斜杠
     * @return 已登记且允许公开读取的文件元数据，仅供服务端使用
     * @throws FileException 路径非法、记录不可公开或缺少存储配置标识
     */
    @Override
    public File resolvePublicFile(String path) {
        if (path == null || !path.startsWith("/") || path.length() > 1000
                || path.contains("%") || path.contains("\\")
                || path.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw new FileException("非法公开文件路径", HttpStatus.NOT_FOUND);
        }
        String url = "/files/public" + path;
        boolean forbidden = fileEntityService.lambdaQuery()
                .eq(File::getUrl, url)
                .and(record -> record.ne(File::getIsPublic, FileConstants.ACCESS_PUBLIC)
                        .or()
                        .isNull(File::getIsPublic)
                        .or()
                        .eq(File::getBizType, FileBizType.TEST_DATA))
                .exists();
        if (forbidden) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        File file = storedFiles.resolvePublicUrl(url);
        if (file == null || file.getStorageProfile() == null || file.getStorageProfile().isBlank()
                || file.getBizType() == FileBizType.TEST_DATA
                || !Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic())) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        if (file.getBizType() == FileBizType.PROBLEM_ATTACHMENT) {
            Problem problem = problems.getById(file.getBizId());
            if (problem == null || problem.getVisibility() != ProblemConstants.PUBLIC
                    || problems.hasActiveContestReference(problem.getId(), LocalDateTime.now())) {
                throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
            }
        } else if (file.getBizType() == FileBizType.CONTEST_ATTACHMENT) {
            Contest contest = contests.getById(file.getBizId());
            if (contest == null || contest.getVisibility() != ContestConstants.VISIBILITY_PUBLIC
                    || LocalDateTime.now().isBefore(contest.getStartTime())) {
                throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
            }
        }
        return file;
    }

    /**
     * 打开已经过公开访问校验的文件。
     * @param file resolvePublicFile 返回的元数据，不能来自请求体
     * @return 文件流，由调用方或 HTTP 资源处理器关闭
     */
    @Override
    public InputStream openPublicFile(File file) {
        return storedFiles.open(file);
    }

    /**
     * 保存上传内容及文件记录，读取结束后关闭上传流。
     * @param file 待保存的上传文件
     * @param fileRecord 已设置业务类型和访问范围的文件记录
     * @throws FileException 上传内容读取失败
     */
    @Override
    public void saveFile(MultipartFile file, File fileRecord) {
        try (InputStream input = file.getInputStream()) {
            storedFiles.save(input, file.getSize(), fileRecord);
        } catch (IOException e) {
            throw new FileException("读取上传文件失败", e);
        }
    }

    /**
     * 删除公开文件；图片删除提交后失效轮播列表，避免保留失效引用。
     * @param url 已登记的公开文件 URL；未找到时不做操作
     */
    @Override
    public void deleteFileByUrl(String url) {
        File file = storedFiles.resolvePublicUrl(url);
        if (file != null) {
            storedFiles.delete(file.getId());
            if (file.getBizType() == FileBizType.IMAGE) {
                evictCarouselAfterCommit();
            }
        }
    }

    /**
     * 图片删除提交后删除公开缓存；外层事务回滚时保留旧缓存。
     * Redis 故障继续上抛，不能把清理失败伪装为全部成功。
     */
    private void evictCarouselAfterCommit() {
        String key = CacheConstants.CACHE_NAME + ":" + CacheConstants.HOME_CAROUSEL_KEY;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 数据库成功提交后，删除与 Spring Cache 相同命名的键
                 */
                @Override
                public void afterCommit() {
                    redisCache.deleteObject(key);
                }
            });
        } else {
            redisCache.deleteObject(key);
        }
    }
}
