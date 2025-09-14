package com.ymcloud.web.service.oj.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.web.dao.FileEntityService;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.oj.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: FileServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  23:02
 * @Description: FileServiceImpl
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {

    @Autowired
    private FileEntityService fileEntityService;

    /**
     * 系统文件存储路径
     */
    @Value("${ymcloud.file.base-dir}")
    private String BASE_DIR;

    /**
     * 保存文件并插入数据库
     * @param file 源文件
     * @param fileRecord 文件记录
     */
    @Override
    public void saveFile(MultipartFile file, File fileRecord) {
        //设置访问目录权限
        String accessDir = (fileRecord.getIsPublic() == FileConstants.ACCESS_PUBLIC)
                ? FileConstants.DIR_PUBLIC
                : FileConstants.DIR_PRIVATE;
        //业务子目录
        //默认为图像图片
        String subDir=FileConstants.SUB_DIR_IMAGE;
        if(fileRecord.getBizType()==FileBizType.CONTEST_ATTACHMENT){
            subDir=FileConstants.SUB_DIR_CONTEST;
        }else if(fileRecord.getBizType()==FileBizType.PROBLEM_ATTACHMENT){
            subDir=FileConstants.SUB_DIR_PROBLEM;
        }else if(fileRecord.getBizType()==FileBizType.ANNOUNCEMENT_ATTACHMENT){
            subDir=FileConstants.SUB_DIR_ANNOUNCEMENT;
        }
        //文件存储在本地
        if(fileRecord.getStorageType()==FileConstants.STORAGE_LOCAL){
            try {
                //本地存储路径
                String filename = BASE_DIR+"/"+accessDir+"/"+subDir+"/"+fileRecord.getFileName();
                //创建当前文件
                java.io.File img = FileUtil.touch(filename);
                file.transferTo(img.getAbsoluteFile());
                //设置URL和存储路径
                fileRecord.setStoragePath(filename);
                String url = "/files/"+accessDir+"/"+subDir+"/"+fileRecord.getFileName();
                fileRecord.setUrl(url);
                //向数据库中插入数据
                boolean b = fileEntityService.save(fileRecord);
                if(!b){
                    throw new FileException("系统保存文件失败，请稍后再试");
                }
            }catch (IOException e){
                log.error(e.getMessage());
                throw new ServiceException("系统IO流异常，请稍后再试");
            }
        }else{
            throw new FileException("其他存储类型暂不支持");
        }
    }

    /**
     * 根据url删除文件
     * @param url url
     */
    @Override
    public void deleteFileByUrl(String url) {
        String api = "/files";
        if(url.startsWith(api)){
            //删除本地文件
            String fullPath = BASE_DIR+url.substring(api.length());
            FileUtil.del(fullPath);
            //删除数据库记录
            fileEntityService.lambdaUpdate().eq(File::getUrl, url).remove();
        }
    }

}
