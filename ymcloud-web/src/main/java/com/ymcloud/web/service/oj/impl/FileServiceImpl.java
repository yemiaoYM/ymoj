package com.ymcloud.web.service.oj.impl;

import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.web.service.oj.FileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 保持原业务接口，存储位置由默认存储配置决定。
 */
@Service
public class FileServiceImpl implements FileService {

    private final StoredFileService storedFiles;

    public FileServiceImpl(StoredFileService storedFiles) {
        this.storedFiles = storedFiles;
    }

    @Override
    public void saveFile(MultipartFile file, File fileRecord) {
        try (InputStream input = file.getInputStream()) {
            storedFiles.save(input, file.getSize(), fileRecord);
        } catch (IOException e) {
            throw new FileException("读取上传文件失败", e);
        }
    }

    @Override
    public void deleteFileByUrl(String url) {
        File file = storedFiles.resolvePublicUrl(url);
        if (file != null) {
            storedFiles.delete(file.getId());
        }
    }
}
