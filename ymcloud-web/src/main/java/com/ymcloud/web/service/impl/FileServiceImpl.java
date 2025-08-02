package com.ymcloud.web.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.FileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: FileServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  23:02
 * @Description: FileServiceImpl
 */
@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements FileService {

    @Override
    public void saveFile(MultipartFile file, File fileRecord) {

    }

    @Override
    public void deleteFileByUrl(String url, FileBizType fileBizType) {

    }

}
