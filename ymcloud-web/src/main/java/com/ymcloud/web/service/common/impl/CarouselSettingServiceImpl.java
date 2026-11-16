package com.ymcloud.web.service.common.impl;

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
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.SystemSetting;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.vo.common.CarouselSettingVO;
import com.ymcloud.web.service.common.CarouselSettingService;
import com.ymcloud.web.service.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private FileEntityService files;

    @Autowired
    private FileService storedFiles;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 直接读取数据库中的最新顺序；已删除或非公开图片不对外暴露
     * @return 是否配置及仍可公开展示的图片，空配置不回退内置图片
     */
    @Override
    public CarouselSettingVO get() {
        SystemSetting setting = settings.lambdaQuery()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.HOME_CAROUSEL)
                .one();
        if (setting == null) {
            return new CarouselSettingVO(false, List.of());
        }
        List<Long> ids;
        try {
            ids = json.readValue(setting.getSettingValue(), new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            throw new ServiceException("轮播配置损坏，请联系管理员", e);
        }
        if (ids == null || ids.size() > 10 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ServiceException("轮播配置损坏，请联系管理员");
        }
        if (ids.isEmpty()) {
            return new CarouselSettingVO(true, List.of());
        }
        Map<Long, File> found = files.lambdaQuery()
                .in(File::getId, ids)
                .eq(File::getBizType, FileBizType.IMAGE)
                .eq(File::getIsPublic, FileConstants.ACCESS_PUBLIC)
                .list()
                .stream()
                .collect(Collectors.toMap(File::getId, Function.identity()));
        List<CarouselSettingVO.Image> images = ids.stream()
                .map(found::get)
                .filter(java.util.Objects::nonNull)
                .map(file -> new CarouselSettingVO.Image(file.getPublicId(), FileConstants.PUBLIC_URL_PREFIX + file.getPublicId()))
                .toList();
        return new CarouselSettingVO(true, images);
    }

    /**
     * 保存轮播顺序；空数组明确关闭，移除引用不会删除可能被其他位置使用的文件
     * @param request 通过结构校验的图片 ID 列表
     */
    @Override
    public void save(CarouselSettingDTO request) {
        List<String> publicIds = request.getFileIds();
        if (new HashSet<>(publicIds).size() != publicIds.size()) {
            throw new ServiceException("轮播图不能重复", HttpStatus.BAD_REQUEST);
        }
        List<Long> ids = new ArrayList<>();
        for (String publicId : publicIds) {
            File file = storedFiles.getByPublicId(publicId);
            ids.add(file.getId());
            requirePublicImage(file);

        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> saveLocked(ids));
    }

    /**
     * 在短事务内锁定配置和文件，复核图片未被删除或改为私有后保存
     * @param ids 已校验内容的有序文件 ID
     */
    private void saveLocked(List<Long> ids) {
        // 与网站设置使用同一已有行锁，串行化旧数据库首次插入配置的操作
        SystemSetting anchor = settings.lambdaQuery()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.REGISTER_ENABLED)
                .last("FOR UPDATE")
                .one();
        if (anchor == null) {
            throw new ServiceException("系统配置未初始化，请联系管理员");
        }
        for (Long id : ids.stream().sorted().toList()) {
            File file = files.lambdaQuery()
                    .eq(File::getId, id)
                    .last("FOR UPDATE")
                    .one();
            requirePublicImage(file);
        }
        String value;
        try {
            value = json.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new ServiceException("轮播配置序列化失败", e);
        }
        SystemSetting current = settings.lambdaQuery()
                .eq(SystemSetting::getSettingKey, SystemSettingConstants.HOME_CAROUSEL)
                .one();
        boolean saved;
        if (current == null) {
            current = new SystemSetting();
            current.setSettingKey(SystemSettingConstants.HOME_CAROUSEL);
            current.setSettingValue(value);
            saved = settings.save(current);
        } else {
            saved = settings.lambdaUpdate()
                    .eq(SystemSetting::getSettingKey, SystemSettingConstants.HOME_CAROUSEL)
                    .set(SystemSetting::getSettingValue, value)
                    .update();
        }
        if (!saved) {
            throw new ServiceException("保存轮播图失败，请重试");
        }
    }

    /**
     * 拒绝将私有数据或非图片通过轮播配置公开
     * @param file 待引用文件；允许 null，由本方法给出业务错误
     */
    private void requirePublicImage(File file) {
        if (file == null || file.getBizType() != FileBizType.IMAGE
                || !Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic())) {
            throw new FileException("图片不存在或不是公开图片，请重新上传", HttpStatus.BAD_REQUEST);
        }
    }

}
