package com.ymcloud.web.service.file.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.ymcloud.common.constant.CacheConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.common.utils.SpringUtils;
import com.ymcloud.core.storage.client.StorageClient;
import com.ymcloud.core.storage.factory.StorageFactory;
import com.ymcloud.core.storage.model.Options;
import com.ymcloud.core.storage.model.PutObjectResult;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.pojo.bo.FileUploadBO;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.file.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-11-18
 * @Description: 文件上传登记、读取及事务清理的默认实现
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    @Autowired
    private MultipartProperties multipart;

    @Autowired
    private FileEntityService fileEntityService;

    @Autowired
    private StorageFactory storageFactory;

    /**
     * 根据id获取文件
     * @param fileId 文件主键
     * @return 已存在的文件元数据
     */
    @Cacheable(cacheNames = CacheConstants.FILE_KEY, key = "#fileId")
    @Override
    public FileInfo getById(Long fileId) {
        FileInfo file = fileEntityService.getById(fileId);
        if (file == null) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        return file;
    }

    /**
     * 打开文件
     * @param fileId 文件主键
     * @return 文件内容及响应头
     */
    @Override
    public ResponseEntity<Resource> open(Long fileId) {
        FileInfo file = readableFile(fileId);
        InputStream open = storageFactory.instance(file.getService()).open(file.getFileName());
        //是否加缓存
        CacheControl cacheControl = file.getBizType() == FileBizType.GENERAL
                ? CacheControl.maxAge(Duration.ofDays(7)).cachePublic()
                : CacheControl.noStore();
        return ResponseEntity.ok()
                .contentType(safeContentType(file.getFileType()))
                .contentLength(file.getFileSize())
                .cacheControl(cacheControl)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(open));
    }

    /**
     * 下载文件
     * @param fileId 文件主键
     * @return 文件内容及响应头
     */
    @Override
    public ResponseEntity<Resource> download(Long fileId) {
        FileInfo file = readableFile(fileId);
        InputStream open = storageFactory.instance(file.getService())
                .open(file.getFileName());

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(safeContentType(file.getFileType()))
                .contentLength(file.getFileSize())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(open));
    }

    /**
     * 读取通用文件接口可处理的元数据，隐藏判题专用文件。
     * @param fileId 文件主键
     * @return 可继续执行普通文件访问流程的记录
     */
    private FileInfo readableFile(Long fileId) {
        FileInfo file = SpringUtils.getAopProxy(this).getById(fileId);
        if (file.getBizType() == FileBizType.TEST_DATA) {
            throw new FileException("文件不存在", HttpStatus.NOT_FOUND);
        }
        return file;
    }

    /**
     * 文件管理主动删除普通文件，数据库提交后删除固定位置的对象
     * @param fileIds 待删除文件 ID，业务授权及引用检查由调用方完成
     */
    @Override
    public void delete(List<Long> fileIds) {
        List<FileInfo> files = fileEntityService.listByIds(fileIds);
        if (files.stream().anyMatch(file -> file.getBizType() == FileBizType.TEST_DATA)) {
            throw new FileException("测试文件由题目测试清单管理", HttpStatus.CONFLICT);
        }
        for (FileInfo file : files) {
            storageFactory.instance(file.getService()).delete(file.getFileName());
        }
        fileEntityService.removeByIds(fileIds);
    }

    /**
     * 通过 S3 客户端流式上传并将文件登记和对象补偿绑定到业务事务
     * @param request 已授权的文件属性与业务归属
     * @param input 调用方负责关闭的输入流
     * @param size 本次上传的字节数，允许空测试文件
     * @return 已保存的文件记录
     */
    @Override
    public FileInfo upload(FileUploadBO request, InputStream input, long size) {
        if (size < 0 || size > multipart.getMaxFileSize().toBytes()) {
            throw new FileException("文件大小超出限制", HttpStatus.BAD_REQUEST);
        }
        FileInfo file = new FileInfo();
        file.setId(IdWorker.getId());
        file.setOriginalName(safeName(request.getFileName()));
        file.setFileType(safeContentType(request.getContentType()).toString());
        file.setBizType(request.getBizType());
        file.setBizId(request.getBizId());
        file.setCreatedBy(request.getUploaderId());
        file.setUpdatedBy(request.getUploaderId());

        String ext = FileUtil.extName(file.getOriginalName());
        String suffix = ext.isEmpty() ? "" : "." + ext;
        if (suffix.length() > 16) {
            throw new FileException("文件后缀过长", HttpStatus.BAD_REQUEST);
        }
        file.setFileSuffix(suffix);

        StorageClient client = storageFactory.instance();
        String key = client.buildPathKey(file.getOriginalName());
        file.setFileName(key);
        file.setService(client.clientId());
        try(UploadInputStream body = new UploadInputStream(input, size)) {
            PutObjectResult result = client.upload(key, body, size, Options.builder().setContentType(file.getFileType()));
            if (body.remaining != 0 || result.getSize() != size || input.read() != -1) {
                throw new FileException("文件大小与上传声明不一致", HttpStatus.BAD_REQUEST);
            }
            file.setUrl(result.getUrl());
            file.setFileSize(result.getSize());
            if (!fileEntityService.save(file)) {
                throw new FileException("保存文件记录失败");
            }
            return file;
        } catch (IOException e) {
            throw new FileException("读取上传内容失败", e);
        }
    }

    /**
     * 校验原始文件名，拒绝路径分隔符与控制字符
     * @param name 原始文件名，必须为非空且最多 255 个字符
     * @return 通过校验的原始文件名
     * @throws FileException 文件名不合法
     */
    private String safeName(String name) {
        if (name == null
                || name.isBlank()
                || name.length() > 255
                || name.contains("/")
                || name.contains("\\")
                || name.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw new FileException("非法文件名");
        }
        return name;
    }

    /**
     * 保留合法媒体类型，缺失或不合法时使用二进制类型
     * @param type 声明的媒体类型，可为空
     * @return 合法媒体类型或 application/octet-stream
     */
    private MediaType safeContentType(String type) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(type);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return mediaType;
    }

    /**
     * 将本次上传限制在声明长度内，调用方仍持有原始流
     */
    private static final class UploadInputStream extends InputStream {

        private final InputStream source;
        private long remaining;

        /**
         * @param source 原始上传内容
         * @param size 本次可读字节数
         */
        private UploadInputStream(InputStream source, long size) {
            this.source = source;
            this.remaining = size;
        }

        /**
         * 读取声明长度内的下一个字节
         * @return 字节值，到达末尾时返回 -1
         * @throws IOException 原始流读取失败
         */
        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int value = source.read();
            if (value != -1) {
                remaining--;
            }
            return value;
        }

        /**
         * 最多读取剩余声明长度的内容
         * @param bytes 接收内容的缓冲区
         * @param offset 起始偏移
         * @param length 本次请求长度
         * @return 读取的字节数，到达末尾时返回 -1
         * @throws IOException 原始流读取失败
         */
        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (remaining == 0) {
                return -1;
            }
            int count = source.read(bytes, offset, (int) Math.min(length, remaining));
            if (count > 0) {
                remaining -= count;
            }
            return count;
        }
    }
}
