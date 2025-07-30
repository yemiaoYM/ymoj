package com.ymcloud.pojo.query;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PageQuery {
    /**
     * 页码
     */
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNo=1;
    /**
     * 页码大小
     */
    @Min(value = 1, message = "每页大小不能小于1")
    @Max(value = 50, message = "每页大小不能超过50")
    private Integer pageSize=5;
    /**
     * 排序字段
     */
    private String sortBy;
    /**
     * 是否升序
     */
    private Boolean isAsc=true;

    public <T> Page<T> toMpPage(OrderItem ...items){
        //1.分页条件
        Page<T> page = Page.of(pageNo, pageSize);
        //2.排序条件
        if(sortBy!=null&&!sortBy.isBlank()){
            //sortBy不为空
            page.addOrder(new OrderItem().setColumn(sortBy).setAsc(isAsc));
        }else if(items!=null){
            //默认排序
            page.addOrder(items);
        }
        return page;
    }

    public <T> Page<T> toMpPage(String defaultSortBy,Boolean defaultAsc){
        return toMpPage(new OrderItem().setColumn(defaultSortBy).setAsc(defaultAsc));
    }

    public <T> Page<T> toMpPageDefaultSortByCreateTime(){
        return toMpPage(OrderItem.desc("created_time"));
    }

    public <T> Page<T> toMpPageDefaultSortByUpdateTime(){
        return toMpPage(OrderItem.asc("updated_time"));
    }
}
