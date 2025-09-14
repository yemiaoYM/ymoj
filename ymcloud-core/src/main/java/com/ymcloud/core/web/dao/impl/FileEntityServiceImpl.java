package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.entity.File;
import org.springframework.stereotype.Service;

/**
 * @PackageName: com.ymcloud.core.web.dao.impl
 * @ClassName: FileEntityServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-13  22:41
 * @Description: FileEntityServiceImpl
 */
@Service
public class FileEntityServiceImpl extends ServiceImpl<FileMapper, File>
        implements FileEntityService {
}
