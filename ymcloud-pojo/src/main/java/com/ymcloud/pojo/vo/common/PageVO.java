package com.ymcloud.pojo.vo.common;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @PackageName: com.ymcloud.pojo.vo
 * @ClassName: PageVO
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  23:53
 * @Description: 分页统一前端返回对象
 */
@Data
public class PageVO<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 总记录数
     */
    private long total;
    /**
     * 总页数
     */
    private Long pages;
    /**
     * 当前页数据集合
     */
    private List<T> records;

    /**
     * 根据字节码把实体转化成VO
     * @param p
     * @param voClass
     * @return
     * @param <PO>
     * @param <VO>
     */
    public static <PO,VO> PageVO<VO> of(Page<PO> p,Class<VO> voClass) {
        PageVO<VO> vo = new PageVO<>();
        //总记录数
        vo.setTotal(p.getTotal());
        //总页数
        vo.setPages(p.getPages());
        //当前页数据
        List<PO> records = p.getRecords();
        if(CollUtil.isEmpty(records)){
            vo.setRecords(Collections.emptyList());
            return vo;
        }
        //拷贝PO->VO
        vo.setRecords(BeanUtil.copyToList(records,voClass));
        //返回vo
        return vo;
    }

    /**
     * 根据自定义Lambda把实体转化成VO
     * @param p
     * @param voFunction
     * @return
     * @param <PO>
     * @param <VO>
     */
    public static <PO,VO> PageVO<VO> of(Page<PO> p, Function<PO,VO> voFunction) {
        PageVO<VO> vo = new PageVO<>();
        //总记录数
        vo.setTotal(p.getTotal());
        //总页数
        vo.setPages(p.getPages());
        //当前页数据
        List<PO> records = p.getRecords();
        if(CollUtil.isEmpty(records)){
            vo.setRecords(Collections.emptyList());
            return vo;
        }
        //拷贝PO->VO
        vo.setRecords(records.stream().map(voFunction).collect(Collectors.toList()));
        //返回vo
        return vo;
        //exp1:
        //return PageVO.of(p,user->BeanUtil.copyProperties(user,UserVO.class));
        //exp2:
        //return PageVO.of(p,user->{
        //    //1.基础拷贝
        //    UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
        //    //2.处理特殊逻辑
        //    userVO.setOnlineTime(LocalDateTime.now());
        //    return userVO;
        //});
    }

}
