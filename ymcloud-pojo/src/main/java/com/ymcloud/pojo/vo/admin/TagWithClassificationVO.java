package com.ymcloud.pojo.vo.admin;

import com.ymcloud.pojo.entity.Tag;
import com.ymcloud.pojo.entity.TagClassification;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo.admin
 * @ClassName: TagWithClassificationVO
 * @Author: Yemiao
 * @CreateTime: 2025-09-24  16:29
 * @Description: 标签包含分类实体
 */
@Data
public class TagWithClassificationVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 标签分类
     */
    private TagClassification classification;

    /**
     * 标签列表
     */
    private List<Tag> tagList;
}
