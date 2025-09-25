package com.ymcloud.pojo.vo.oj;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: UserCalendarHeatmapVO
 * @Author: Yemiao
 * @CreateTime: 2025-08-02  15:00
 * @Description: 用户最近一年提交日历热力图数据
 */
@Data
public class UserCalendarHeatmapVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 当前日期
     */
    private String date;
    /**
     * 最近一年提交数据 日期+数量
     */
    private List<HashMap<String,Object>> dataList;

}
