package com.ymcloud.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.common.constant.ContestConstants;
import com.ymcloud.core.web.mapper.ContestMapper;
import com.ymcloud.core.web.mapper.ContestUserMapper;
import com.ymcloud.pojo.entity.Contest;
import com.ymcloud.pojo.query.ContestPageQuery;
import com.ymcloud.pojo.vo.ContestCountVO;
import com.ymcloud.pojo.vo.ContestTableVO;
import com.ymcloud.pojo.vo.PageVO;
import com.ymcloud.web.service.ContestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @PackageName: com.ymcloud.web.service.impl
 * @ClassName: ContestServiceImpl
 * @Author: Yemiao
 * @CreateTime: 2025-07-22  19:47
 * @Description: ContestServiceImpl
 */
@Service
public class ContestServiceImpl extends ServiceImpl<ContestMapper, Contest> implements ContestService {

    @Autowired
    private ContestUserMapper contestUserMapper;

    /**
     * 获取比赛列表表单 分页查询
     * @param query
     * @return
     */
    @Override
    public PageVO<ContestTableVO> pageQuery(ContestPageQuery query) {
        //创建分页实体
        Page<Contest> page = new Page<>(query.getPageNo(),query.getPageSize());
        Page<Contest> contestPage = lambdaQuery().like(StrUtil.isNotBlank(query.getKeyword()), Contest::getTitle, query.getKeyword())
                .page(page);
        //拷贝实体
        if(contestPage.getTotal()>0){
            //如果不为空就查询参与人数
            List<Long> contestIds = contestPage.getRecords().stream().map(Contest::getId).toList();
            Map<Long, Integer> map = contestUserMapper.getCountByContestIds(contestIds).stream()
                    .collect(Collectors.toMap(ContestCountVO::getId, ContestCountVO::getCount));

            return PageVO.of(contestPage,contest -> {
                ContestTableVO vo = BeanUtil.copyProperties(contest, ContestTableVO.class);
                //判断当前比赛状态
                LocalDateTime startTime = vo.getStartTime();
                LocalDateTime endTime = vo.getEndTime();
                LocalDateTime now = LocalDateTime.now();
                if(now.isBefore(startTime)){
                    vo.setStatus(ContestConstants.STATUS_NOT_STARTED);
                }else if(now.isAfter(endTime)){
                    vo.setStatus(ContestConstants.STATUS_ENDED);
                }else vo.setStatus(ContestConstants.STATUS_RUNNING);
                //设置参与人数
                vo.setParticipantCount(map.getOrDefault(contest.getId(), 0));
                return vo;
            });
        }
        //返回vo
        return PageVO.of(contestPage,ContestTableVO.class);
    }
}
