package com.ymcloud.web.controller.admin.problem;

import com.ymcloud.common.result.Result;
import com.ymcloud.pojo.dto.admin.TagClassificationDTO;
import com.ymcloud.pojo.dto.admin.TagDTO;
import com.ymcloud.pojo.entity.TagClassification;
import com.ymcloud.pojo.vo.admin.TagWithClassificationVO;
import com.ymcloud.web.service.admin.AdminTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.controller.admin.problem
 * @ClassName: AdminTagController
 * @Author: Yemiao
 * @CreateTime: 2025-09-23  23:11
 * @Description: 管理端标签控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/tag")
public class AdminTagController {

    @Autowired
    private AdminTagService adminTagService;

    /**
     * 获取标签和分类列表
     * @return 标签和分类列表
     */
    @PreAuthorize("hasAuthority('problem:tag:list')")
    @GetMapping("/list")
    public Result<List<TagWithClassificationVO>> getTagsAndClassification() {
        log.info("获取标签和分类列表");
        List<TagWithClassificationVO> data = adminTagService.getTagsAndClassification();
        return Result.ok(data);
    }

    /**
     * 添加标签
     * @param tag 标签信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:tag:add')")
    @PostMapping
    public Result<Void> addTag(@Validated @RequestBody TagDTO tag) {
        log.info("添加标签：{}", tag);
        adminTagService.addTag(tag);
        return Result.ok();
    }

    /**
     * 更新标签
     * @param tag 标签信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:tag:edit')")
    @PutMapping("/{id}")
    public Result<Void> updateTag(@PathVariable("id") Long id,@Validated @RequestBody TagDTO tag) {
        log.info("更新标签：{}", tag);
        adminTagService.updateTag(id, tag);
        return Result.ok();
    }

    /**
     * 删除标签
     * @param id 标签ID
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:tag:remove')")
    @DeleteMapping("/{id}")
    public Result<Void> deleteTag(@PathVariable("id") Long id) {
        log.info("删除标签，ID：{}", id);
        adminTagService.deleteTag(id);
        return Result.ok();
    }

    /**
     * 添加标签分类
     * @param tagClassification 标签分类信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:tag:add')")
    @PostMapping("/classification")
    public Result<Void> addTagClassification(@Validated @RequestBody TagClassificationDTO tagClassification) {
        log.info("添加标签分类：{}", tagClassification);
        adminTagService.addTagClassification(tagClassification);
        return Result.ok();
    }

    /**
     * 更新标签分类
     * @param tagClassification 标签分类信息
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:tag:edit')")
    @PutMapping("/classification/{id}")
    public Result<Void> updateTagClassification(@PathVariable("id") Long id,@Validated @RequestBody TagClassificationDTO tagClassification) {
        log.info("更新标签分类：{}", tagClassification);
        adminTagService.updateTagClassification(id, tagClassification);
        return Result.ok();
    }

    /**
     * 删除标签分类
     * @param id 标签分类ID
     * @return 操作结果
     */
    @PreAuthorize("hasAuthority('problem:tag:remove')")
    @DeleteMapping("/classification/{id}")
    public Result<Void> deleteTagClassification(@PathVariable("id") Long id) {
        log.info("删除标签分类，ID：{}", id);
        adminTagService.deleteTagClassification(id);
        return Result.ok();
    }

    /**
     * 获取标签分类列表
     * @return 标签分类列表
     */
    @PreAuthorize("hasAuthority('problem:tag:list')")
    @GetMapping("/classification/list")
    public Result<List<TagClassification>> getTagClassificationList() {
        log.info("获取标签分类列表");
        List<TagClassification> data = adminTagService.getTagClassificationList();
        return Result.ok(data);
    }


}
