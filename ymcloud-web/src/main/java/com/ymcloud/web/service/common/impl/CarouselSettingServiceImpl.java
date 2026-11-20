package com.ymcloud.web.service.common.impl;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.SystemSettingConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.SystemSettingEntityService;
import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import com.ymcloud.pojo.entity.FileInfo;
import com.ymcloud.pojo.entity.SystemSetting;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.common.CarouselSettingService;
import com.ymcloud.web.service.file.FileService;
import com.ymcloud.web.service.file.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-24
 * @Description: 校验轮播图片并保存有序文件引用，复用系统配置和统一存储
 */
@Service
public class CarouselSettingServiceImpl implements CarouselSettingService {

    @Autowired
    private SystemSettingEntityService settings;

    @Autowired
    private ImageService imageService;

    /**
     * 获取轮播图图片列表
     * @return 图片ApiUrl列表
     */
    @Override
    public List<String> list() {
        SystemSetting setting = settings.lambdaQuery()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.HOME_CAROUSEL)
                .one();
        if (setting == null) {
            return List.of();
        }
        return JSON.parseArray(setting.getSettingValue(), String.class);
    }

    /**
     * 上传轮播图
     * @param file 图片
     * @return 轮播图列表
     */
    @Override
    public List<String> uploadCarousel(MultipartFile file) {
        //上传图片
        Long fileId = imageService.upload(file);
        SystemSetting setting = settings.lambdaQuery()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.HOME_CAROUSEL)
                .one();
        List<String> list = JSON.parseArray(setting.getSettingValue(), String.class);
        list.add(FileConstants.PUBLIC_URL_PREFIX + fileId);
        return list;
    }

    /**
     * 保存轮播顺序；空数组明确关闭，移除引用不会删除可能被其他位置使用的文件
     * @param dto 通过结构校验的图片 ID 列表
     */
    @Override
    public void save(CarouselSettingDTO dto) {
        String value = JSON.toJSONString(dto.getImgApiUrl());
        settings.lambdaUpdate()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.HOME_CAROUSEL)
                .set(SystemSetting::getSettingValue, value)
                .update();
    }

}
