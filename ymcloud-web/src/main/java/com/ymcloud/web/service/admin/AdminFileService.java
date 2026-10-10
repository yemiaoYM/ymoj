package com.ymcloud.web.service.admin;

import com.ymcloud.pojo.entity.File;
import com.ymcloud.pojo.query.admin.AdminFilePageQuery;
import com.ymcloud.pojo.vo.admin.AdminFileVO;
import com.ymcloud.pojo.vo.common.PageVO;
import org.springframework.web.multipart.MultipartFile;

public interface AdminFileService {

    PageVO<AdminFileVO> queryList(AdminFilePageQuery query);

    File requireDownload(Long id);

    AdminFileVO upload(MultipartFile file, Integer isPublic);

    void delete(Long id);
}
