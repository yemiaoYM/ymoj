package com.ymcloud.web.controller.common;

import com.ymcloud.web.service.file.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: Yemiao
 * @CreateTime: 2026-10-11
 * @Description: 文件接口
 */
@RestController
@RequestMapping("/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> open(@PathVariable("fileId") Long fileId) {
        return fileService.open(fileId);
    }

    @GetMapping("download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable("fileId") Long fileId) {
        return fileService.download(fileId);
    }

}
