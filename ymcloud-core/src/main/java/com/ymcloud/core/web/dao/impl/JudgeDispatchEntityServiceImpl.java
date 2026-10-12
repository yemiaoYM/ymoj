package com.ymcloud.core.web.dao.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ymcloud.core.web.dao.JudgeDispatchEntityService;
import com.ymcloud.core.web.mapper.JudgeDispatchMapper;
import com.ymcloud.pojo.entity.JudgeDispatch;
import org.springframework.stereotype.Service;

@Service
public class JudgeDispatchEntityServiceImpl extends ServiceImpl<JudgeDispatchMapper, JudgeDispatch>
        implements JudgeDispatchEntityService {
}
