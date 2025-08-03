package com.ymcloud.web.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.FileConstants;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.common.exception.file.FileException;
import com.ymcloud.core.web.mapper.FileMapper;
import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.enums.FileBizType;
import com.ymcloud.web.service.FileService;
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
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements FileService {

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
                //本地存储完整路径
                String projectDir = System.getProperty("user.dir");
                String relativePath = BASE_DIR +FileUtil.FILE_SEPARATOR
                        + accessDir + FileUtil.FILE_SEPARATOR
                        + subDir;
                String storageDir = projectDir + FileUtil.FILE_SEPARATOR + relativePath;
                //创建当前文件存储文件夹
                FileUtil.mkdir(storageDir);
                //保存文件到本地
                String savePath = storageDir + FileUtil.FILE_SEPARATOR + fileRecord.getFileName();
                file.transferTo(FileUtil.file(savePath));
                //设置URL和存储路径
                fileRecord.setStoragePath(relativePath + FileUtil.FILE_SEPARATOR + fileRecord.getFileName());
                String url = "/"+BASE_DIR+"/"+accessDir+"/"+subDir+"/"+fileRecord.getFileName();
                fileRecord.setUrl(url);
                //向数据库中插入数据
                boolean b = save(fileRecord);
                if(!b){
                    throw new FileException("系统保存文件失败，请稍后再试");
                }
            }catch (IOException e){
                throw new ServiceException("系统IO流异常，请稍后再试");
            }
        }else{
            throw new FileException("其他存储类型暂不支持");
        }
    }

    /**
     * 更加url删除文件
     * @param url url
     */
    @Override
    public void deleteFileByUrl(String url) {
        String api = "/"+BASE_DIR+"/";
        if(url.startsWith(api)){
            //删除本地文件
            String projectDir = System.getProperty("user.dir");
            String fullPath = projectDir + url.replace("/", FileUtil.FILE_SEPARATOR);
            FileUtil.del(fullPath);
            //删除数据库记录
            lambdaUpdate().eq(File::getUrl, url).remove();
        }
    }

}
