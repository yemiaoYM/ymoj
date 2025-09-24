package com.ymcloud.web.service.admin.impl;

import cn.hutool.core.bean.BeanUtil;
import com.ymcloud.common.exception.ServiceException;
import com.ymcloud.core.web.dao.ProblemTagEntityService;
import com.ymcloud.core.web.dao.TagClassificationEntityService;
import com.ymcloud.core.web.dao.TagEntityService;
import com.ymcloud.pojo.dto.admin.TagClassificationDTO;
import com.ymcloud.pojo.dto.admin.TagDTO;
import com.ymcloud.pojo.entity.ProblemTag;
import com.ymcloud.pojo.entity.Tag;
import com.ymcloud.pojo.entity.TagClassification;
import com.ymcloud.pojo.vo.admin.TagWithClassificationVO;
import com.ymcloud.web.service.admin.AdminTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @PackageName: com.ymcloud.web.service.admin.impl
 * @ClassName: AdminTagServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-09-23  23:13
 * @Description: 管理端标签服务实现类
 */
@Service
public class AdminTagServiceImpl implements AdminTagService {

    @Autowired
    private TagEntityService tagEntityService;

    @Autowired
    private TagClassificationEntityService tagClassificationEntityService;

    @Autowired
    private ProblemTagEntityService problemTagEntityService;

    /**
     * 获取标签和分类列表
     * @return 标签和分类列表
     */
    @Override
    public List<TagWithClassificationVO> getTagsAndClassification() {
        //获取所有标签分类，按优先级排序
        List<TagClassification> classifications = tagClassificationEntityService.lambdaQuery()
                .orderByAsc(TagClassification::getPriority)
                .list();
        //获取所有标签
        List<Tag> allTags = tagEntityService.list();
        //按父标签ID分组
        Map<Long, List<Tag>> tagsByParentId = allTags.stream()
                .collect(Collectors.groupingBy(
                    tag -> tag.getParentId() == null ? -1L : tag.getParentId()
                ));
        List<TagWithClassificationVO> result = new ArrayList<>();
        //处理有分类的标签
        for (TagClassification classification : classifications) {
            TagWithClassificationVO vo = new TagWithClassificationVO();
            vo.setClassification(classification);
            vo.setTagList(tagsByParentId.getOrDefault(classification.getId(), new ArrayList<>()));
            result.add(vo);
        }
        //处理未分类的标签（parentId为null或不存在对应分类）
        List<Tag> unclassifiedTags = tagsByParentId.getOrDefault(-1L, new ArrayList<>());
        if (!unclassifiedTags.isEmpty()) {
            TagWithClassificationVO unclassifiedVO = new TagWithClassificationVO();
            unclassifiedVO.setClassification(null);
            unclassifiedVO.setTagList(unclassifiedTags);
            result.add(unclassifiedVO);
        }
        return result;
    }

    /**
     * 添加标签
     * @param tagDTO 标签信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTag(TagDTO tagDTO) {
        //检查标签名称是否已存在
        boolean exists = tagEntityService.lambdaQuery()
                .eq(Tag::getName, tagDTO.getName())
                .exists();
        if (exists) {
            throw new ServiceException("标签名称已存在");
        }
        //如果指定了父标签ID，检查父标签是否存在
        if (tagDTO.getParentId() != null) {
            TagClassification classification = tagClassificationEntityService.getById(tagDTO.getParentId());
            if (classification == null) {
                throw new ServiceException("指定的标签分类不存在");
            }
        }

        Tag tag = BeanUtil.copyProperties(tagDTO, Tag.class);
        boolean saved = tagEntityService.save(tag);
        if (!saved) {
            throw new ServiceException("标签添加失败");
        }
    }

    /**
     * 更新标签
     * @param id 标签ID
     * @param tagDTO 标签信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTag(Long id, TagDTO tagDTO) {
        //检查标签是否存在
        Tag existingTag = tagEntityService.getById(id);
        if (existingTag == null) {
            throw new ServiceException("标签不存在");
        }
        //检查标签名称是否与其他标签重复（排除自己）
        boolean nameExists = tagEntityService.lambdaQuery()
                .eq(Tag::getName, tagDTO.getName())
                .ne(Tag::getId, id)
                .exists();
        if (nameExists) {
            throw new ServiceException("标签名称已存在");
        }
        //如果指定了父标签ID，检查父标签是否存在
        if (tagDTO.getParentId() != null) {
            TagClassification classification = tagClassificationEntityService.getById(tagDTO.getParentId());
            if (classification == null) {
                throw new ServiceException("指定的标签分类不存在");
            }
        }
        //使用lambdaUpdate强制更新所有字段，包括null值
        boolean updated = tagEntityService.lambdaUpdate()
                .eq(Tag::getId, id)
                .set(Tag::getName, tagDTO.getName())
                .set(Tag::getColor, tagDTO.getColor())
                .set(Tag::getParentId, tagDTO.getParentId()) // 强制更新parentId，即使为null
                .update();
        if (!updated) {
            throw new ServiceException("标签更新失败");
        }
    }

    /**
     * 删除标签
     * @param id 标签ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        //检查标签是否存在
        Tag tag = tagEntityService.getById(id);
        if (tag == null) {
            throw new ServiceException("标签不存在");
        }
        //删除标签关联
        problemTagEntityService.lambdaUpdate()
                .eq(ProblemTag::getTagId, id)
                .remove();
        //删除标签
        boolean removed = tagEntityService.removeById(id);
        if (!removed) {
            throw new ServiceException("标签删除失败");
        }
    }

    /**
     * 获取标签分类列表
     * @return 标签分类列表
     */
    @Override
    public List<TagClassification> getTagClassificationList() {
        return tagClassificationEntityService.lambdaQuery()
                .orderByAsc(TagClassification::getPriority)
                .list();
    }

    /**
     * 添加标签分类
     * @param tagClassificationDTO 标签分类信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTagClassification(TagClassificationDTO tagClassificationDTO) {
        //检查分类名称是否已存在
        boolean exists = tagClassificationEntityService.lambdaQuery()
                .eq(TagClassification::getName, tagClassificationDTO.getName())
                .exists();
        if (exists) {
            throw new ServiceException("标签分类名称已存在");
        }
        //保存标签分类
        TagClassification classification = BeanUtil.copyProperties(tagClassificationDTO, TagClassification.class);
        boolean saved = tagClassificationEntityService.save(classification);
        if (!saved) {
            throw new ServiceException("标签分类添加失败");
        }
    }

    /**
     * 更新标签分类
     * @param id 标签分类ID
     * @param tagClassificationDTO 标签分类信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTagClassification(Long id, TagClassificationDTO tagClassificationDTO) {
        //检查标签分类是否存在
        TagClassification existingClassification = tagClassificationEntityService.getById(id);
        if (existingClassification == null) {
            throw new ServiceException("标签分类不存在");
        }
        //检查分类名称是否与其他分类重复（排除自己）
        boolean nameExists = tagClassificationEntityService.lambdaQuery()
                .eq(TagClassification::getName, tagClassificationDTO.getName())
                .ne(TagClassification::getId, id)
                .exists();
        if (nameExists) {
            throw new ServiceException("标签分类名称已存在");
        }
        //更新标签分类信息
        TagClassification updateClassification = BeanUtil.copyProperties(tagClassificationDTO, TagClassification.class);
        updateClassification.setId(id);
        //保存更新
        boolean updated = tagClassificationEntityService.updateById(updateClassification);
        if (!updated) {
            throw new ServiceException("标签分类更新失败");
        }
    }

    /**
     * 删除标签分类
     * @param id 标签分类ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTagClassification(Long id) {
        //检查标签分类是否存在
        TagClassification classification = tagClassificationEntityService.getById(id);
        if (classification == null) {
            throw new ServiceException("标签分类不存在");
        }
        //标签删除后将标签的父标签ID设置为null
        boolean update = tagEntityService.lambdaUpdate()
                        .eq(Tag::getParentId, id)
                        .set(Tag::getParentId, null)
                        .update();
        if (!update) {
            throw new ServiceException("标签父标签ID设置失败");
        }
        //删除标签分类
        boolean removed = tagClassificationEntityService.removeById(id);
        if (!removed) {
            throw new ServiceException("标签分类删除失败");
        }
    }

}
