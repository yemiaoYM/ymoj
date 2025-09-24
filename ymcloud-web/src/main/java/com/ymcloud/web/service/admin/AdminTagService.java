package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.dto.admin.TagClassificationDTO;
import com.ymcloud.pojo.dto.admin.TagDTO;
import com.ymcloud.pojo.entity.TagClassification;
import com.ymcloud.pojo.vo.admin.TagWithClassificationVO;

import java.util.List;

/**
 * @PackageName: com.ymcloud.web.service.admin
 * @ClassName: AdminTagService
 * @Author: Yemiao
 * @CreateTime: 2025-09-23  23:12
 * @Description: 管理端标签服务接口
 */
public interface AdminTagService {

    /**
     * 获取标签和分类列表
     * @return 标签和分类列表
     */
    List<TagWithClassificationVO> getTagsAndClassification();

    /**
     * 添加标签
     * @param tag 标签信息
     */
    void addTag(TagDTO tag);

    /**
     * 更新标签
     * @param tag 标签信息
     */
    void updateTag(Long id, TagDTO tag);

    /**
     * 删除标签
     * @param id 标签ID
     */
    void deleteTag(Long id);

    /**
     * 添加标签分类
     * @param tagClassification 标签分类信息
     */
    void addTagClassification(TagClassificationDTO tagClassification);

    /**
     * 更新标签分类
     * @param tagClassification 标签分类信息
     */
    void updateTagClassification(Long id, TagClassificationDTO tagClassification);

    /**
     * 删除标签分类
     * @param id 标签分类ID
     */
    void deleteTagClassification(Long id);

    /**
     * 获取标签分类列表
     * @return 标签分类列表
     */
    List<TagClassification> getTagClassificationList();
}
