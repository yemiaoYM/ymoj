package com.ymcloud.web.remote;

import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.utils.FileUtils;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.pojo.bo.FileUploadBO;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.vo.admin.RemoteProblemVO;
import com.ymcloud.web.service.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-29
 * @Description: 远程题面插图下载、格式识别与原图存储，完成后返回本站图片地址
 */
@Component
public class RemoteProblemImages {

    private static final Set<String> IMAGE_FORMATS = Set.of("png", "jpeg", "webp", "gif");
    private static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    private static final long DOWNLOAD_BUDGET_NANOS = Duration.ofSeconds(30).toNanos();

    @Autowired
    private RemoteProblemHttpClient httpClient;

    @Autowired
    private FileService storedFiles;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 将题干及输入输出说明中的插图保存到当前存储，下载和格式校验均在数据库事务之外完成
     * @param draft 已解析且公式已转换的远程草稿，成功后原地更新图片地址
     */
    public void importImages(RemoteProblemVO draft) {
        List<String> sections = List.of(draft.getContent(), draft.getInput(), draft.getOutput());
        List<List<ProblemImageReferences.Reference>> references = sections.stream()
                .map(ProblemImageReferences::find)
                .toList();
        Map<String, RemoteImage> images = new LinkedHashMap<>();
        long totalBytes = 0;
        long started = System.nanoTime();
        for (var section : references) {
            for (var reference : section) {
                if (reference.formula() != null || images.containsKey(reference.getUrl())) {
                    continue;
                }
                if (System.nanoTime() - started > DOWNLOAD_BUDGET_NANOS) {
                    throw new FileException("题面图片下载超时，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
                }
                byte[] bytes = httpClient.getImage(reference.getUrl());
                totalBytes += bytes.length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new FileException("题面图片总大小不能超过20MB", HttpStatus.BAD_REQUEST);
                }
                String format = FileUtils.getImageFormat(new ByteArrayInputStream(bytes), IMAGE_FORMATS);
                images.put(reference.getUrl(), new RemoteImage(bytes, format));
            }
        }
        List<String> fileIds = new ArrayList<>();
        Map<String, String> urls = new LinkedHashMap<>();
        if (!images.isEmpty()) {
            User user = SecurityUtils.getUser();
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                for (var entry : images.entrySet()) {
                    var image = entry.getValue();
                    FileInfo file = storedFiles.upload(new FileUploadBO(
                            "problem-image." + image.format, "image/" + image.format,
                            FileBizType.PROBLEM, null, user.getId()),
                            new ByteArrayInputStream(image.bytes), image.bytes.length);
                    fileIds.add(file.getId().toString());
                    urls.put(entry.getKey(), FileConstants.PUBLIC_URL_PREFIX + file.getId().toString());
                }
            });
        }
        draft.setFileIds(fileIds);
        draft.setContent(ProblemImageReferences.replace(sections.get(0), references.get(0), urls));
        draft.setInput(ProblemImageReferences.replace(sections.get(1), references.get(1), urls));
        draft.setOutput(ProblemImageReferences.replace(sections.get(2), references.get(2), urls));
    }

    /**
     * @Author: Yemiao
     * @CreateTime: 2026-11-19
     * @Description: 待写入存储的题面图片原文及实际格式
     */
    @RequiredArgsConstructor
    private static class RemoteImage {
        private final byte[] bytes;
        private final String format;
    }
}
