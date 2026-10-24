package com.ymcloud.web.service.common.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.constant.HttpStatus;
import com.ymcloud.common.constant.SystemSettingConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.security.utils.SecurityUtils;
import com.ymcloud.core.storage.StoredFileService;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.dao.SystemSettingEntityService;
import com.ymcloud.pojo.dto.admin.CarouselSettingDTO;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.entity.SystemSetting;
import com.ymcloud.pojo.entity.User;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.pojo.vo.common.CarouselSettingVO;
import com.ymcloud.web.service.common.CarouselSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

    @Autowired
    private SystemSettingEntityService settings;

    @Autowired
    private FileEntityService files;

    @Autowired
    private StoredFileService storedFiles;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 直接读取数据库中的最新顺序；已删除或非公开图片不对外暴露。
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
            ids = json.readValue(setting.getSettingValue(), new TypeReference<List<Long>>() { });
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
                .filter(file -> file != null && file.getUrl() != null && file.getUrl().startsWith("/files/public/"))
                .map(file -> new CarouselSettingVO.Image(file.getId().toString(), file.getUrl()))
                .toList();
        return new CarouselSettingVO(true, images);
    }

    /**
     * 校验后保存轮播图片；重新编码去掉元数据及附加内容，存储失败沿用事务补偿。
     * @param upload 不超过 5 MiB、16:9 的 JPEG 或 PNG 图片
     * @return 可加入轮播的文件 ID 和公开地址；尚不改变线上轮播顺序
     */
    @Override
    public CarouselSettingVO.Image upload(MultipartFile upload) {
        if (upload.isEmpty() || upload.getSize() > MAX_IMAGE_BYTES) {
            throw new ServiceException("图片不能为空且不能超过5MB", HttpStatus.BAD_REQUEST);
        }
        try (InputStream input = upload.getInputStream()) {
            BufferedImage image = readImage(input);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_IMAGE_BYTES) {
                throw new ServiceException("图片处理后超过5MB，请降低分辨率", HttpStatus.BAD_REQUEST);
            }
            User user = SecurityUtils.getUser();
            File file = new File();
            file.setFileName("carousel.png");
            file.setFileType("image/png");
            file.setBizType(FileBizType.IMAGE);
            file.setIsPublic(FileConstants.ACCESS_PUBLIC);
            file.setUploaderId(user.getId());
            file.setUploaderUsername(user.getUsername());
            storedFiles.save(new ByteArrayInputStream(bytes), bytes.length, file);
            return new CarouselSettingVO.Image(file.getId().toString(), file.getUrl());
        } catch (IOException e) {
            throw new ServiceException("图片读取失败，请重新选择", HttpStatus.BAD_REQUEST, e);
        }
    }

    /**
     * 保存轮播顺序；空数组明确关闭，移除引用不会删除可能被其他位置使用的文件。
     * @param request 通过结构校验的图片 ID 列表
     */
    @Override
    public void save(CarouselSettingDTO request) {
        List<Long> ids = new ArrayList<>();
        try {
            for (String id : request.getFileIds()) {
                ids.add(Long.parseLong(id));
            }
        } catch (NumberFormatException e) {
            throw new ServiceException("图片ID超出范围", HttpStatus.BAD_REQUEST);
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ServiceException("轮播图不能重复", HttpStatus.BAD_REQUEST);
        }
        for (Long id : ids) {
            File file = files.lambdaQuery()
                    .eq(File::getId, id)
                    .one();
            requirePublicImage(file);
            // 先校验不可变文件内容，再进入短事务；其他图片入口可能未校验比例。
            try (InputStream input = storedFiles.open(file)) {
                readImage(input);
            } catch (IOException e) {
                throw new ServiceException("轮播图片读取失败", e);
            }
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> saveLocked(ids));
    }

    /**
     * 在短事务内锁定配置和文件，复核图片未被删除或改为私有后保存。
     * @param ids 已校验内容的有序文件 ID
     */
    private void saveLocked(List<Long> ids) {
        // 与网站设置使用同一已有行锁，串行化旧数据库首次插入配置的操作。
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
     * 拒绝将私有数据或非图片通过轮播配置公开。
     * @param file 待引用文件；允许 null，由本方法给出业务错误
     */
    private void requirePublicImage(File file) {
        if (file == null || file.getBizType() != FileBizType.IMAGE
                || !Integer.valueOf(FileConstants.ACCESS_PUBLIC).equals(file.getIsPublic())
                || file.getUrl() == null || !file.getUrl().startsWith("/files/public/")) {
            throw new ServiceException("图片不存在或不是公开图片，请重新上传", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * 先读取尺寸再解码，拒绝非图片、过大图片及非 16:9 图片。
     * @param input 调用方负责关闭的图片流，最多读取 5 MiB 加一个探测字节
     * @return 已解码的 JPEG 或 PNG；宽度 16～3840，高度 9～2160
     * @throws IOException 图片流损坏或无法读取
     */
    private BufferedImage readImage(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new ServiceException("图片不能超过5MB", HttpStatus.BAD_REQUEST);
        }
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ServiceException("请选择有效的JPEG或PNG图片", HttpStatus.BAD_REQUEST);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName();
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (!("PNG".equalsIgnoreCase(format)
                        || "JPEG".equalsIgnoreCase(format))
                        || width < 16
                        || height < 9
                        || width > 3840
                        || height > 2160
                        || width * 9 != height * 16) {
                    throw new ServiceException("图片必须为16:9的JPEG或PNG，最大3840×2160", HttpStatus.BAD_REQUEST);
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }
}
